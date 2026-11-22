package com.kabigonis.sorairo

import android.Manifest
import android.provider.Settings
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.net.wifi.ScanResult
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog

// Sorairo Wi-Fi panel and discovery prompt.
// Moved verbatim from MainActivity.kt as extension functions on MainActivity.

@Suppress("DEPRECATION")
internal fun MainActivity.showWifiPanel() {
    val p = pal
    val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
    val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
    val hasWifi = cm.getNetworkCapabilities(cm.activeNetwork)
        ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(14))
        background = box(p.cBg, p.cDim)
    }
    root.addView(txt(11f, p.cBlue, "~/network").apply { setPadding(0, 0, 0, dp(5)) })
    root.addView(txt(22f, p.cFg, "Wi-Fi").apply { setPadding(0, 0, 0, dp(3)) })
    root.addView(txt(11f, p.cDim, "CURRENT CONNECTION").apply { setPadding(0, 0, 0, dp(12)) })

    val current = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = box(p.cSel, p.cAccent)
    }
    val currentName = try {
        wifi.connectionInfo.ssid?.removePrefix("\"")?.removeSuffix("\"")
            ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
            ?: if (hasWifi) "Connected network · name hidden by Android" else "Not connected"
    } catch (_: Exception) {
        if (hasWifi) "Connected network · name hidden by Android" else "Not connected"
    }
    current.addView(txt(14f, onFill(p.cFg, p.cSel), currentName))
    current.addView(txt(11f, onFill(p.cAccent, p.cSel), if (hasWifi) "connected" else "not connected").apply {
        setPadding(0, dp(3), 0, 0)
    })
    root.addView(current)

    root.addView(txt(10f, p.cDim, "NEARBY NETWORKS").apply { setPadding(0, dp(14), 0, dp(6)) })
    val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    wifiNetworkList = list
    root.addView(list)
    val status = txt(10f, p.cDim, "DISCOVERY OFF")
    wifiScanStatus = status
    root.addView(status.apply { setPadding(0, dp(5), 0, 0) })

    fun action(label: String, primary: Boolean = false, onClick: () -> Unit) =
        txt(12f, if (primary) onFill(p.cBg, p.cAccent) else onFill(p.cFg, p.cSel), label).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = box(if (primary) p.cAccent else p.cSel, if (primary) p.cAccent else p.cDim)
            setOnClickListener { onClick() }
        }

    fun locationEnabled(): Boolean = if (Build.VERSION.SDK_INT >= 28) {
        (getSystemService(LOCATION_SERVICE) as android.location.LocationManager).isLocationEnabled
    } else {
        Settings.Secure.getInt(contentResolver, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF) != Settings.Secure.LOCATION_MODE_OFF
    }

    fun renderNetworks(message: String? = null) {
        list.removeAllViews()
        val permitted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!permitted) {
            status.text = "DISCOVERY OFF · LOCATION ACCESS NOT GRANTED"
            list.addView(txt(12f, p.cDim, "Nearby network names need Android's Location permission. Your current connection is shown above.")
                .apply { setPadding(0, dp(6), 0, dp(8)) })
            list.addView(action("Enable discovery", true) {
                showWifiDiscoveryPrompt {
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 741)
                }
            })
            return
        }
        if (!locationEnabled()) {
            status.text = "DEVICE LOCATION IS OFF"
            list.addView(txt(12f, p.cDim, "Turn on Location only when you want Sorairo to discover nearby networks.")
                .apply { setPadding(0, dp(6), 0, dp(8)) })
            list.addView(action("Enable Location") {
                showWifiDiscoveryPrompt {
                    openSystem(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                }
            })
            return
        }
        val results = try { wifi.scanResults } catch (_: Exception) { emptyList<ScanResult>() }
        val unique = results.filter { it.SSID.isNotBlank() && it.SSID != "<unknown ssid>" }
            .distinctBy { it.SSID }.sortedByDescending { it.level }
        if (unique.isEmpty()) {
            status.text = message ?: "NO RESULTS YET · TAP REFRESH"
            list.addView(txt(12f, p.cDim, "No nearby networks returned yet.")
                .apply { setPadding(0, dp(6), 0, dp(8)) })
        } else {
            status.text = "${unique.size} NETWORKS FOUND"
            unique.take(12).forEach { result ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(10), dp(9), dp(8), dp(9))
                    background = box(p.cBg, p.cDim)
                    setOnClickListener {
                        wifiDialog?.dismiss()
                        openSystem(Settings.ACTION_WIFI_SETTINGS)
                    }
                }
                val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                info.addView(txt(13f, p.cFg, result.SSID))
                val secure = result.capabilities.contains("WPA") || result.capabilities.contains("WEP")
                info.addView(txt(10f, p.cDim, if (secure) "secured · open Android to connect" else "open · open Android to connect")
                    .apply { setPadding(0, dp(2), 0, 0) })
                row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(txt(11f, p.cAccent, "${result.level} dBm"))
                list.addView(row)
                list.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(1)) })
            }
        }
    }

    fun beginScan() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || !locationEnabled()) {
            renderNetworks()
            return
        }
        status.text = "SCANNING…"
        try {
            if (!wifi.startScan()) renderNetworks("SCAN THROTTLED · SHOWING LAST RESULTS")
        } catch (_: SecurityException) {
            renderNetworks("SCAN PERMISSION DENIED")
        } catch (_: Exception) {
            renderNetworks("SCAN UNAVAILABLE")
        }
    }

    val actions = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END
        setPadding(0, dp(12), 0, 0)
    }
    actions.addView(action("Refresh") { beginScan() }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
    actions.addView(action("Network settings", true) {
        wifiDialog?.dismiss()
        openSystem(Settings.ACTION_WIFI_SETTINGS)
    })
    root.addView(actions)

    wifiDialog?.dismiss()
    wifiDialog = AlertDialog.Builder(this).create().apply {
        setView(root)
        setOnDismissListener {
            wifiScanReceiver?.let { receiver -> try { unregisterReceiver(receiver) } catch (_: Exception) {} }
            wifiScanReceiver = null
            wifiNetworkList = null
            wifiScanStatus = null
            wifiDialog = null
        }
        show()
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setLayout(minOf(resources.displayMetrics.widthPixels - dp(40), dp(460)), -2)
    }
    val receiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                val updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                renderNetworks(if (updated) "SCAN COMPLETE" else "SCAN THROTTLED · LAST RESULTS")
            }
        }
    }
    wifiScanReceiver = receiver
    try {
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, android.content.IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION), Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, android.content.IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
    } catch (_: Exception) {}
    renderNetworks()
    // Do not request location or start scanning just by opening the Wi-Fi panel.
}

internal fun MainActivity.showWifiDiscoveryPrompt(onContinue: () -> Unit) {
    val p = pal
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(10))
        background = box(p.cBg, p.cDim)
    }
    content.addView(txt(11f, p.cBlue, "~/network/discovery"))
    content.addView(txt(18f, p.cFg, "Discover nearby networks").apply {
        setPadding(0, dp(8), 0, dp(6))
    })
    content.addView(txt(12f, p.cDim,
        "Android requires Location permission and Location services to scan nearby Wi-Fi names. Sorairo only requests this when you choose network discovery. Your current connection remains available without enabling discovery.")
        .apply { setPadding(0, 0, 0, dp(12)) })
    AlertDialog.Builder(this).create().apply {
        setView(content)
        setButton(AlertDialog.BUTTON_NEGATIVE, "CANCEL") { _, _ -> }
        setButton(AlertDialog.BUTTON_POSITIVE, "CONTINUE") { _, _ -> onContinue() }
        show()
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setLayout(minOf(resources.displayMetrics.widthPixels - dp(40), dp(420)), -2)
    }
}
