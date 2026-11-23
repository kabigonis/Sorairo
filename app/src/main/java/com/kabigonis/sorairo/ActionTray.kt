package com.kabigonis.sorairo

import android.app.AlarmClock
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/**
 * Compact touch-first port of the Omarchy status action tray.
 *
 * Android owns privileged operations such as Night Light and screen recording;
 * these actions open the relevant system surface rather than silently pretending
 * that an ordinary app can toggle protected system state.
 */
internal fun MainActivity.createActionTray(): View {
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val reveal = txt(14f, pal.cDim, "⊙").apply {
        gravity = Gravity.CENTER
        minWidth = dp(30)
        minHeight = dp(34)
        contentDescription = "System actions"
        tooltipText = "System actions"
        setOnClickListener { toggleActionTray() }
    }
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.GONE
        alpha = 0f
    }
    val scroll = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        visibility = View.GONE
        addView(row, HorizontalScrollView.LayoutParams(-2, -2))
    }
    root.addView(reveal)
    root.addView(scroll)
    actionTray = root
    actionTrayRow = row
    actionTrayScroll = scroll
    actionTrayReveal = reveal

    fun action(glyph: String, label: String, onClick: () -> Unit) {
        val item = txt(14f, pal.cDim, glyph).apply {
            gravity = Gravity.CENTER
            minWidth = dp(30)
            minHeight = dp(34)
            setPadding(dp(3), 0, dp(3), 0)
            contentDescription = label
            tooltipText = label
            setOnClickListener {
                onClick()
                scheduleActionTrayCollapse(1500L)
            }
        }
        row.addView(item)
        actionTrayItems[label] = item
    }

    action("☕", "Stay Awake") { toggleStayAwake() }
    action("♟", "Silence Notifications") { toggleDoNotDisturb() }
    action("☾", "Night Light") { openNightLightSettings() }
    action("☼", "Day Light") { openNightLightSettings() }
    action("◷", "Set Reminder") { showReminderDialog() }
    action("◌", "Allow Idle Lock & Screensaver") { allowIdleLockAndScreensaver() }
    action("▣", "Screen Recording") { showScreenRecordingOptions() }
    refreshActionTray()
    return root
}

internal var MainActivity.actionTrayRow: LinearLayout?
    get() = actionTrayViews[this]?.first
    set(value) {
        val current = actionTrayViews[this]
        actionTrayViews[this] = value to current?.second
    }

internal var MainActivity.actionTrayScroll: HorizontalScrollView?
    get() = actionTrayViews[this]?.second
    set(value) {
        val current = actionTrayViews[this]
        actionTrayViews[this] = current?.first to value
    }

internal var MainActivity.actionTrayReveal: TextView?
    get() = actionTrayRevealViews[this]
    set(value) { actionTrayRevealViews[this] = value }

internal val MainActivity.actionTrayItems: MutableMap<String, TextView>
    get() = actionTrayItemViews.getOrPut(this) { linkedMapOf() }

private val actionTrayViews = java.util.WeakHashMap<MainActivity, Pair<LinearLayout?, HorizontalScrollView?>>()
private val actionTrayRevealViews = java.util.WeakHashMap<MainActivity, TextView>()
private val actionTrayItemViews = java.util.WeakHashMap<MainActivity, MutableMap<String, TextView>>()
private val actionTrayHideCallbacks = java.util.WeakHashMap<MainActivity, Runnable>()

internal fun MainActivity.toggleActionTray() {
    val row = actionTrayRow ?: return
    val scroll = actionTrayScroll ?: return
    if (row.visibility == View.VISIBLE) {
        collapseActionTray()
        return
    }
    handler.removeCallbacks(actionTrayHideCallbacks[this] ?: return)
    scroll.visibility = View.VISIBLE
    row.visibility = View.VISIBLE
    row.alpha = 0f
    row.translationX = -dp(8).toFloat()
    row.animate().alpha(1f).translationX(0f).setDuration(160L).start()
    scheduleActionTrayCollapse(5000L)
}

internal fun MainActivity.scheduleActionTrayCollapse(delay: Long) {
    val row = actionTrayRow ?: return
    val scroll = actionTrayScroll ?: return
    val old = actionTrayHideCallbacks[this]
    if (old != null) handler.removeCallbacks(old)
    val callback = Runnable {
        row.animate().alpha(0f).translationX(-dp(8).toFloat()).setDuration(180L)
            .withEndAction {
                row.visibility = View.GONE
                scroll.visibility = View.GONE
                row.translationX = 0f
            }.start()
    }
    actionTrayHideCallbacks[this] = callback
    handler.postDelayed(callback, delay)
}

internal fun MainActivity.collapseActionTray() {
    val row = actionTrayRow ?: return
    val scroll = actionTrayScroll ?: return
    val old = actionTrayHideCallbacks[this]
    if (old != null) handler.removeCallbacks(old)
    row.animate().alpha(0f).translationX(-dp(8).toFloat()).setDuration(150L)
        .withEndAction {
            row.visibility = View.GONE
            scroll.visibility = View.GONE
            row.translationX = 0f
        }.start()
}

internal fun MainActivity.refreshActionTray() {
    if (!::actionTray.isInitialized) return
    val awake = prefs.getBoolean("tray_stay_awake", false)
    actionTrayItems["Stay Awake"]?.setTextColor(if (awake) pal.cBlue else pal.cDim)
    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    val dnd = Build.VERSION.SDK_INT >= 23 && nm.isNotificationPolicyAccessGranted &&
        nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    actionTrayItems["Silence Notifications"]?.setTextColor(if (dnd) pal.cBlue else pal.cDim)
    actionTrayReveal?.setTextColor(if (actionTrayRow?.visibility == View.VISIBLE) pal.cBlue else pal.cDim)
}

internal fun MainActivity.applyStayAwakePreference() {
    if (prefs.getBoolean("tray_stay_awake", false)) {
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

internal fun MainActivity.toggleStayAwake() {
    val enabled = !prefs.getBoolean("tray_stay_awake", false)
    prefs.edit().putBoolean("tray_stay_awake", enabled).apply()
    applyStayAwakePreference()
    refreshActionTray()
    Toast.makeText(this, if (enabled) "Stay Awake enabled" else "Normal screen timeout restored", Toast.LENGTH_SHORT).show()
}

internal fun MainActivity.allowIdleLockAndScreensaver() {
    prefs.edit().putBoolean("tray_stay_awake", false).apply()
    applyStayAwakePreference()
    refreshActionTray()
    try {
        startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
    } catch (_: Exception) {
        startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
    }
}

internal fun MainActivity.toggleDoNotDisturb() {
    if (Build.VERSION.SDK_INT < 23) return
    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    if (!nm.isNotificationPolicyAccessGranted) {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
        Toast.makeText(this, "Allow Sorairo to manage Do Not Disturb, then tap again", Toast.LENGTH_LONG).show()
        return
    }
    try {
        val enabled = nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        nm.setInterruptionFilter(
            if (enabled) NotificationManager.INTERRUPTION_FILTER_NONE
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
        refreshActionTray()
    } catch (_: SecurityException) {
        Toast.makeText(this, "Do Not Disturb access was not granted", Toast.LENGTH_SHORT).show()
    }
}

internal fun MainActivity.openNightLightSettings() {
    try {
        startActivity(Intent(Settings.ACTION_NIGHT_DISPLAY_SETTINGS))
    } catch (_: Exception) {
        startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
    }
    Toast.makeText(this, "Use Night Light or its schedule in Android settings", Toast.LENGTH_SHORT).show()
}

internal fun MainActivity.showReminderDialog() {
    val input = EditText(this).apply {
        inputType = android.text.InputType.TYPE_CLASS_NUMBER
        hint = "Minutes"
        setText("5")
        setSelection(text.length)
    }
    val container = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(8), dp(20), 0)
        addView(input, LinearLayout.LayoutParams(-1, -2))
    }
    AlertDialog.Builder(this)
        .setTitle("Remind in minutes")
        .setView(container)
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Set") { _, _ ->
            val minutes = input.text.toString().toIntOrNull()?.coerceIn(1, 1440)
            if (minutes == null) {
                Toast.makeText(this, "Enter 1–1440 minutes", Toast.LENGTH_SHORT).show()
            } else {
                try {
                    startActivity(Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                        putExtra(AlarmClock.EXTRA_MESSAGE, "Sorairo reminder")
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    })
                } catch (_: Exception) {
                    Toast.makeText(this, "No compatible clock app found", Toast.LENGTH_SHORT).show()
                }
            }
        }
        .show()
}

internal fun MainActivity.showScreenRecordingOptions() {
    val options = arrayOf(
        "With no audio",
        "With device audio",
        "With device + microphone audio"
    )
    AlertDialog.Builder(this)
        .setTitle("Screen recording")
        .setItems(options) { _, which ->
            val detail = when (which) {
                0 -> "Choose screen recording without audio"
                1 -> "Choose device audio in the Android recorder"
                else -> "Choose device and microphone audio in the Android recorder"
            }
            AlertDialog.Builder(this)
                .setMessage("$detail. Android controls recording permissions and audio options.")
                .setPositiveButton("Open Quick Settings") { _, _ ->
                    Toast.makeText(this, "Open Screen record from Quick Settings and select the requested audio option", Toast.LENGTH_LONG).show()
                    try {
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    } catch (_: Exception) {}
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        .show()
}
