package com.kabigonis.sorairo

import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build

// Test for real app windows (freeform / desktop windowing).
// When a window mode is set, apps launched from Sorairo are asked to open
// with fixed bounds instead of fullscreen. Android only honours the bounds
// when freeform windows are enabled; otherwise the app opens as usual.

internal val APP_WINDOW_MODES = listOf("off", "left", "right", "float")

internal fun MainActivity.appWindowMode(): String =
    prefs.getString("app_window_mode", "off")?.takeIf { it in APP_WINDOW_MODES } ?: "off"

internal fun MainActivity.setAppWindowMode(mode: String) {
    prefs.edit().putString("app_window_mode", mode).apply()
}

internal fun MainActivity.freeformSupported(): Boolean =
    packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)

internal fun MainActivity.freeformSettingOn(): Boolean? = try {
    android.provider.Settings.Global.getInt(contentResolver, "enable_freeform_support", 0) != 0
} catch (_: Exception) { null }

private fun MainActivity.screenRect(): Rect =
    if (Build.VERSION.SDK_INT >= 30) Rect(windowManager.currentWindowMetrics.bounds)
    else resources.displayMetrics.let { Rect(0, 0, it.widthPixels, it.heightPixels) }

internal fun MainActivity.appWindowBounds(mode: String): Rect? {
    val s = screenRect()
    val w = s.width()
    val h = s.height()
    return when (mode) {
        "left" -> Rect(s.left, s.top, s.left + w / 2, s.bottom)
        "right" -> Rect(s.left + w / 2, s.top, s.right, s.bottom)
        "float" -> Rect(s.left + w / 6, s.top + h / 6, s.right - w / 6, s.bottom - h / 6)
        else -> null
    }
}

// Returns true when the app was started (with or without bounds).
internal fun MainActivity.startAppWindowed(intent: Intent): Boolean {
    val bounds = appWindowBounds(appWindowMode())
    if (bounds == null) {
        startActivity(intent)
        return true
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        val opts = ActivityOptions.makeBasic().setLaunchBounds(bounds)
        startActivity(intent, opts.toBundle())
        true
    } catch (_: Exception) {
        startActivity(intent)
        true
    }
}
