package com.kabigonis.sorairo

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import java.util.Locale

// Shared pieces for the four top-bar icon panels: battery, display, audio and network.

internal enum class BarKind { BATTERY, DISPLAY, AUDIO, WIFI }

// ---- pure helpers (unit tested) ----

internal const val DNS_CLOUDFLARE = "one.one.one.one"
internal const val DNS_GOOGLE = "dns.google"

/** 0 = DHCP / automatic, 1 = Cloudflare, 2 = Google, 3 = custom hostname. */
internal fun dnsProviderFor(mode: String?, host: String?): Int = when {
    mode != "hostname" -> 0
    host.equals(DNS_CLOUDFLARE, ignoreCase = true) -> 1
    host.equals(DNS_GOOGLE, ignoreCase = true) -> 2
    else -> 3
}

internal fun formatRate(bytesPerSec: Long): String = when {
    bytesPerSec < 1000L -> "$bytesPerSec B/s"
    bytesPerSec < 1_000_000L -> String.format(Locale.US, "%.1f KB/s", bytesPerSec / 1000f)
    else -> String.format(Locale.US, "%.1f MB/s", bytesPerSec / 1_000_000f)
}

internal fun formatBytes(bytes: Long): String = when {
    bytes < 0L -> "n/a"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024f)
    bytes < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
    else -> String.format(Locale.US, "%.2f GB", bytes / (1024f * 1024f * 1024f))
}

/** Average of the successful probes (ms) and the failure percentage. A failed probe is -1. */
internal fun pingStats(results: List<Long>): Pair<Long?, Int?> {
    if (results.isEmpty()) return Pair(null, null)
    val ok = results.filter { it >= 0L }
    val loss = ((results.size - ok.size) * 100f / results.size).toInt()
    return Pair(if (ok.isEmpty()) null else ok.average().toLong(), loss)
}

/** Number of lit arcs (1..3) for a Wi-Fi RSSI in dBm. */
internal fun wifiArcs(rssi: Int): Int = when {
    rssi >= -60 -> 3
    rssi >= -72 -> 2
    else -> 1
}

private val HOSTNAME = Regex("^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$")

/** Cleans what the user typed into a Private DNS hostname, or null if it is not one. */
internal fun normalizeDnsHost(raw: String): String? {
    var h = raw.trim()
    for (prefix in listOf("https://", "http://", "tls://")) h = h.removePrefix(prefix)
    h = h.trimEnd('/')
    return if (HOSTNAME.matches(h)) h else null
}

internal fun formatScale(scale: Float): String =
    if (scale == scale.toInt().toFloat()) "${scale.toInt()}x" else "${scale}x"

// ---- connection state for the Wi-Fi icon ----

internal class NetState(
    val connected: Boolean,
    val onWifi: Boolean,
    val arcs: Int,
    val ssid: String?,
    val vpn: Boolean = false
)

/** The real network under a VPN (DNSNet, WireGuard...), preferring Wi-Fi. */
@Suppress("DEPRECATION")
internal fun underlyingNetwork(cm: ConnectivityManager): Network? {
    val real = cm.allNetworks.filter {
        val c = cm.getNetworkCapabilities(it)
        c != null && !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
            c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    return real.firstOrNull { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
        ?: real.firstOrNull()
}

@Suppress("DEPRECATION")
internal fun MainActivity.netState(): NetState {
    try {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = cm.activeNetwork ?: return NetState(false, false, 0, null)
        val activeCaps = cm.getNetworkCapabilities(active) ?: return NetState(false, false, 0, null)
        val vpn = activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        // With a VPN on top, read signal and name from the real network underneath it.
        val under = if (vpn) underlyingNetwork(cm) else null
        val caps = (if (under != null) cm.getNetworkCapabilities(under) else null) ?: activeCaps
        val onWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        var arcs = 3
        var ssid: String? = null
        if (onWifi) {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wm.connectionInfo
            arcs = wifiArcs(info.rssi)
            // Android only reveals the network name to apps that hold location access.
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                ssid = info.ssid?.trim('"')?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
            }
        }
        return NetState(true, onWifi, arcs, ssid, vpn)
    } catch (_: Exception) {
        return NetState(false, false, 0, null)
    }
}

internal fun MainActivity.isMediaMuted(): Boolean {
    val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    return am.isStreamMute(AudioManager.STREAM_MUSIC) || am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
}

/** Keeps the four top-bar glyphs in sync with the system. Called from the status loop. */
internal fun MainActivity.refreshBarIcons(bm: BatteryManager) {
    if (!iconBarInitialized) return
    val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
    barBattery.update(pct / 100f, bm.isCharging)
    barAudio.set(isMediaMuted())
    val st = netState()
    barWifi.set(st.connected, st.arcs)
}

// ---- positioning ----

/** Y position just under the top bar's icon row. */
internal fun MainActivity.barBottomY(): Int = try {
    val loc = IntArray(2)
    iconBar.getLocationOnScreen(loc)
    if (iconBar.height > 0) loc[1] + iconBar.height + dp(4) else dp(40)
} catch (_: Exception) {
    dp(40)
}

/**
 * Shows [root] as a popup under the matching top-bar icon, lights that icon's underline while it is
 * open, and runs [onDismiss] when it closes.
 */
internal fun MainActivity.showBarDialog(
    kind: BarKind,
    root: View,
    maxWidthDp: Int,
    onDismiss: () -> Unit = {}
): AlertDialog {
    val screenW = resources.displayMetrics.widthPixels
    val w = minOf(dp(maxWidthDp), screenW - dp(24))
    val btn = barButtons[kind]
    val dialog = AlertDialog.Builder(this).create().apply {
        setView(root)
        setOnDismissListener {
            btn?.setActive(false)
            onDismiss()
        }
        show()
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setLayout(w, WindowManager.LayoutParams.WRAP_CONTENT)
    }
    btn?.setActive(true)
    anchorDialogToTop(dialog, 0, 0, w, btn)
    return dialog
}

/** A bordered choice button whose fill, border and text colours cross-fade between idle and active. */
internal class ChoiceButton(
    val view: TextView,
    private val bg: GradientDrawable,
    private val strokePx: Int,
    private val fillOff: Int,
    private val fillOn: Int,
    private val strokeOff: Int,
    private val strokeOn: Int,
    private val textOff: Int,
    private val textOn: Int
) {
    private val evaluator = ArgbEvaluator()
    private var active: Boolean? = null
    private var anim: ValueAnimator? = null

    private fun paint(f: Float) {
        bg.setColor(evaluator.evaluate(f, fillOff, fillOn) as Int)
        bg.setStroke(strokePx, evaluator.evaluate(f, strokeOff, strokeOn) as Int)
        view.setTextColor(evaluator.evaluate(f, textOff, textOn) as Int)
    }

    fun set(on: Boolean, animate: Boolean = true) {
        if (active == on) return
        val first = active == null
        active = on
        anim?.cancel()
        val target = if (on) 1f else 0f
        if (first || !animate) {
            paint(target)
        } else {
            anim = ValueAnimator.ofFloat(1f - target, target).apply {
                duration = 200L
                addUpdateListener { paint(it.animatedValue as Float) }
                start()
            }
        }
    }
}

/** A themed [ChoiceButton] with the same look as the battery panel's profile buttons. */
internal fun MainActivity.choiceButton(text: String, size: Float = 12f): ChoiceButton {
    val p = pal
    val bg = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
    val tv = txt(size, p.cFg, text).apply { background = bg }
    return ChoiceButton(
        tv, bg, dp(2),
        p.cSel and 0x00FFFFFF, p.cSel,
        p.cDim, p.cBlue,
        p.cFg, onFill(p.cFg, p.cSel)
    )
}
