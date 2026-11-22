package com.kabigonis.sorairo

import android.bluetooth.BluetoothManager
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import kotlin.math.roundToInt
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast

// Quick Settings panel, VPN picker and system-setting helpers.
// Moved verbatim from MainActivity.kt as extension functions on MainActivity.

// ================= quick settings =================
internal fun MainActivity.openQs() {
    closeDrawer()
    askedWrite = false
    fillQs()
    qs.visibility = View.VISIBLE
    qs.alpha = 0f
    qs.animate().alpha(1f).setDuration(140).start()
}

internal fun MainActivity.closeQs() {
    if (qs.visibility != View.VISIBLE) return
    qs.animate().alpha(0f).setDuration(120).withEndAction { qs.visibility = View.GONE }.start()
}

internal fun MainActivity.openSystem(action: String) {
    closeQs()
    closeSettings()
    try {
        startActivity(Intent(action))
    } catch (_: Exception) {
        Toast.makeText(this, "Can't open that setting", Toast.LENGTH_SHORT).show()
    }
}

internal fun MainActivity.mediaAccess(): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

internal fun MainActivity.setBrightness(v: Float) {
    try {
        if (Settings.System.canWrite(this)) {
            Settings.System.putInt(
                contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(
                contentResolver, Settings.System.SCREEN_BRIGHTNESS,
                (v * 255).roundToInt().coerceIn(1, 255)
            )
        } else if (!askedWrite) {
            askedWrite = true
            closeQs()
            Toast.makeText(this, "Allow \"modify system settings\" for brightness", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
        }
    } catch (_: Exception) {
    }
}

internal fun MainActivity.launchVpn() {
    val app = allApps.find { it.pkg == prefs.getString("vpn_pkg", null) }
    if (app == null) showVpnPicker() else launchApp(app)
}

internal fun MainActivity.fillVpnPicker() {
    val p = pal
    setBox.removeAllViews()
    setBox.addView(txt(15f, p.cBlue, "~/vpn-app"))
    setBox.addView(
        txt(11f, p.cDim, "pick the app the vpn tile opens").apply { setPadding(0, dp(2), 0, dp(6)) }
    )
    setBox.addView(txt(14f, p.cAccent, "← back").apply {
        setPadding(0, dp(8), 0, dp(8))
        setOnClickListener {
            switchSettingsContent {
                fillSettings()
                setScroll.scrollTo(0, 0)
            }
        }
    })
    allApps.forEach { app ->
        val chosen = app.pkg == prefs.getString("vpn_pkg", null)
        setBox.addView(
            txt(14f, if (chosen) p.cAccent else p.cFg, displayAppLabel(app)).apply {
                setPadding(0, dp(9), 0, dp(9))
                setOnClickListener {
                    prefs.edit().putString("vpn_pkg", app.pkg).apply()
                    closeSettings()
                }
            }
        )
    }
}

internal fun MainActivity.showVpnPicker() {
    closeQs()
    fillVpnPicker()
    setScroll.scrollTo(0, 0)
    showOverlay()
}

internal fun MainActivity.fillQs() {
    val p = pal
    qsBox.removeAllViews()
    qsBox.addView(txt(15f, p.cBlue, "~/quick"))

    val am = getSystemService(AUDIO_SERVICE) as AudioManager
    val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
    val net = netLabel()
    val wifiOn = net.startsWith("wifi")
    val btOn = try {
        (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter?.isEnabled == true
    } catch (_: Exception) {
        false
    }
    val ring = am.ringerMode
    val airplane = try {
        Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    } catch (_: Exception) {
        false
    }
    val saver = (getSystemService(POWER_SERVICE) as PowerManager).isPowerSaveMode
    val micMuted = am.isMicrophoneMute
    val vpnOn = try {
        cm.getNetworkCapabilities(cm.activeNetwork)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    } catch (_: Exception) {
        false
    }
    val vpnApp = allApps.find { it.pkg == prefs.getString("vpn_pkg", null) }

    fun tile(name: String, state: String, on: Boolean, hold: (() -> Unit)? = null, tap: () -> Unit): View {
        val t = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = box(
                if (on) p.cSel else (p.cSel and 0x00FFFFFF) or (0x44 shl 24),
                if (on) p.cAccent else p.cDim
            )
            setOnClickListener { tap() }
            if (hold != null) setOnLongClickListener { hold(); true }
        }
        t.addView(txt(14f, if (on) onFill(p.cFg, p.cSel) else p.cFg, name))
        t.addView(txt(11f, if (on) onFill(p.cAccent, p.cSel) else p.cDim, state))
        return t
    }

    val ringState = when (ring) {
        AudioManager.RINGER_MODE_SILENT -> "on"
        AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
        else -> "off"
    }
    val tiles = listOf(
        tile("wifi", if (wifiOn) net else "off", wifiOn) {
            closeQs()
            showWifiPanel()
        },
        tile("bluetooth", if (btOn) "on" else "off", btOn) {
            openSystem(Settings.ACTION_BLUETOOTH_SETTINGS)
        },
        tile("silent", ringState, ring == AudioManager.RINGER_MODE_SILENT) {
            try {
                am.ringerMode =
                    if (ring == AudioManager.RINGER_MODE_SILENT) AudioManager.RINGER_MODE_NORMAL
                    else AudioManager.RINGER_MODE_SILENT
                fillQs()
            } catch (_: SecurityException) {
                Toast.makeText(this, "Allow Do Not Disturb access first", Toast.LENGTH_LONG).show()
                openSystem(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            }
        },
        tile("airplane", if (airplane) "on" else "off", airplane) {
            openSystem(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        },
        tile("battery saver", if (saver) "on" else "off", saver) {
            openSystem(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        },
        tile("mic", if (micMuted) "muted" else "live", micMuted, { openSystem(Settings.ACTION_PRIVACY_SETTINGS) }) {
            am.setMicrophoneMute(!micMuted)
            fillQs()
        },
        tile("camera", "privacy ›", false) { openSystem(Settings.ACTION_PRIVACY_SETTINGS) },
        tile(
            "vpn", if (vpnOn) "connected" else (vpnApp?.label ?: "pick app"), vpnOn,
            { showVpnPicker() }
        ) { launchVpn() }
    )

    val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    for (i in tiles.indices step 2) {
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        r.addView(tiles[i], LinearLayout.LayoutParams(dp(130), dp(62)).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        if (i + 1 < tiles.size) {
            r.addView(tiles[i + 1], LinearLayout.LayoutParams(dp(130), dp(62)).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        }
        grid.addView(r)
    }

    val bright = try {
        Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
    } catch (_: Exception) {
        0.5f
    }
    val maxV = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val vol = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxV

    fun sliderCol(label: String, init: Float, onChange: (Float) -> Unit): View {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val pct = txt(11f, p.cDim, "${(init * 100).roundToInt()}%")
        val sl = VSlider(this, p.cAccent, (p.cDim and 0x00FFFFFF) or (0x66 shl 24)) { v ->
            pct.text = "${(v * 100).roundToInt()}%"
            onChange(v)
        }
        sl.value = init
        c.addView(sl, LinearLayout.LayoutParams(dp(46), dp(250)))
        c.addView(pct, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6) })
        c.addView(txt(11f, p.cFg, label))
        return c
    }

    val body = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(10), 0, 0)
    }
    body.addView(grid)
    body.addView(
        sliderCol("bright", bright) { v -> setBrightness(v) },
        LinearLayout.LayoutParams(dp(60), -2).apply { marginStart = dp(10) }
    )
    body.addView(
        sliderCol("vol", vol) { v ->
            am.setStreamVolume(AudioManager.STREAM_MUSIC, (v * maxV).roundToInt(), 0)
        },
        LinearLayout.LayoutParams(dp(60), -2).apply { marginStart = dp(4) }
    )
    qsBox.addView(body)
}
