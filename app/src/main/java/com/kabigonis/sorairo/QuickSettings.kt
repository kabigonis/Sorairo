package com.kabigonis.sorairo

import android.bluetooth.BluetoothManager
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import kotlin.math.roundToInt
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast

// VPN picker and system-setting helpers (the old quick-settings overlay was removed).
// Moved verbatim from MainActivity.kt as extension functions on MainActivity.

internal fun MainActivity.openSystem(action: String) {
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
    fillVpnPicker()
    setScroll.scrollTo(0, 0)
    showOverlay()
}
