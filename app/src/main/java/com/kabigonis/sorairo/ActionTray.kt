package com.kabigonis.sorairo

import android.app.AlarmClock
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.util.WeakHashMap

private val actionIconViews = WeakHashMap<MainActivity, MutableMap<OmarchyGlyph, OmarchyActionIconView>>()
private val actionTooltipWindows = WeakHashMap<MainActivity, PopupWindow>()
private val actionTooltipCallbacks = WeakHashMap<MainActivity, Runnable>()

/** Creates the fixed six-glyph strip, positioned immediately before the clock. */
internal fun MainActivity.createOmarchyActionTray(): View {
    val p = pal
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, dp(3), 0)
    }
    val icons = linkedMapOf<OmarchyGlyph, OmarchyActionIconView>()

    fun add(glyph: OmarchyGlyph, label: () -> String, action: () -> Unit) {
        val icon = OmarchyActionIconView(this, glyph, p.cDim, p.cBlue, p.cAccent)
        icons[glyph] = icon
        val hit = android.widget.FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            contentDescription = label()
            setOnClickListener {
                showOmarchyTooltip(this, label())
                action()
            }
            addView(icon, android.widget.FrameLayout.LayoutParams(dp(14), dp(16), Gravity.CENTER))
        }
        row.addView(hit, LinearLayout.LayoutParams(dp(20), dp(30)))
    }

    add(OmarchyGlyph.RECORD, { "Screen Recording" }) { showScreenRecordingMenu() }
    add(OmarchyGlyph.HAND, { "Set Reminder" }) { showReminderInput() }
    add(OmarchyGlyph.NIGHT_LIGHT, { if (isNightLightEnabled()) "Day Light" else "Night Light" }) {
        toggleNightLight()
    }
    add(OmarchyGlyph.MUTED_BELL, { "Silence Notifications" }) { toggleDoNotDisturb() }
    add(OmarchyGlyph.COFFEE, {
        if (prefs.getBoolean("tray_stay_awake", false)) "Allow Idle Lock & Screensaver" else "Stay Awake"
    }) { toggleStayAwake() }
    add(OmarchyGlyph.SUDO, { "Disable Passwordless Sudo" }) { showSudoUnavailable() }

    actionIconViews[this] = icons
    refreshOmarchyActionTray()
    return row
}

/** Repaints enabled states without rebuilding the top bar. */
internal fun MainActivity.refreshOmarchyActionTray() {
    val icons = actionIconViews[this] ?: return
    val awake = prefs.getBoolean("tray_stay_awake", false)
    val dnd = isDoNotDisturbEnabled()
    val night = isNightLightEnabled()
    icons[OmarchyGlyph.COFFEE]?.active = awake
    icons[OmarchyGlyph.MUTED_BELL]?.active = dnd
    icons[OmarchyGlyph.NIGHT_LIGHT]?.active = night
}

/** Shows the small outlined label used by the reference, anchored under the touched icon. */
private fun MainActivity.showOmarchyTooltip(anchor: View, label: String) {
    dismissOmarchyTooltip()
    val p = pal
    val text = TextView(this).apply {
        this.text = label
        textSize = 11f * uiScale
        typeface = Typeface.create("monospace", Typeface.NORMAL)
        setTextColor(p.cDim)
        setPadding(dp(9), dp(5), dp(9), dp(5))
        background = GradientDrawable().apply {
            setColor(p.cBg or (0xFF shl 24))
            setStroke(dp(1), p.cDim)
            cornerRadius = 0f
        }
    }
    val popup = PopupWindow(text, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
        isTouchable = false
        isFocusable = false
        isOutsideTouchable = false
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        elevation = 0f
        animationStyle = 0
    }
    actionTooltipWindows[this] = popup
    popup.showAsDropDown(anchor, 0, dp(3), Gravity.CENTER_HORIZONTAL)
    text.alpha = 0f
    text.translationY = -dp(3).toFloat()
    text.animate().alpha(1f).translationY(0f).setDuration(120L).start()
    val callback = Runnable {
        if (actionTooltipWindows[this] === popup) {
            text.animate().alpha(0f).translationY(-dp(2).toFloat()).setDuration(180L)
                .withEndAction {
                    if (popup.isShowing) popup.dismiss()
                    if (actionTooltipWindows[this] === popup) actionTooltipWindows.remove(this)
                }.start()
        }
    }
    actionTooltipCallbacks[this] = callback
    handler.postDelayed(callback, 1450L)
}

private fun MainActivity.dismissOmarchyTooltip() {
    actionTooltipCallbacks[this]?.let { handler.removeCallbacks(it) }
    actionTooltipCallbacks.remove(this)
    actionTooltipWindows[this]?.let { if (it.isShowing) it.dismiss() }
    actionTooltipWindows.remove(this)
}

/** Stay Awake is scoped to the launcher window; clearing it restores normal Android sleep behaviour. */
internal fun MainActivity.applyTrayStayAwake() {
    if (prefs.getBoolean("tray_stay_awake", false)) {
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

private fun MainActivity.toggleStayAwake() {
    val enabled = !prefs.getBoolean("tray_stay_awake", false)
    prefs.edit().putBoolean("tray_stay_awake", enabled).apply()
    applyTrayStayAwake()
    refreshOmarchyActionTray()
}

private fun MainActivity.isDoNotDisturbEnabled(): Boolean {
    if (Build.VERSION.SDK_INT < 23) return false
    return try {
        val nm = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.isNotificationPolicyAccessGranted &&
            nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    } catch (_: Exception) { false }
}

private fun MainActivity.toggleDoNotDisturb() {
    if (Build.VERSION.SDK_INT < 23) return
    val nm = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
    if (!nm.isNotificationPolicyAccessGranted) {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        Toast.makeText(this, "Allow Sorairo to manage Do Not Disturb, then tap again", Toast.LENGTH_LONG).show()
        return
    }
    try {
        val currentlyOn = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        nm.setInterruptionFilter(
            if (currentlyOn) NotificationManager.INTERRUPTION_FILTER_ALL
            else NotificationManager.INTERRUPTION_FILTER_NONE
        )
        refreshOmarchyActionTray()
    } catch (_: SecurityException) {
        Toast.makeText(this, "Do Not Disturb access was not granted", Toast.LENGTH_SHORT).show()
    }
}

private fun MainActivity.isNightLightEnabled(): Boolean = try {
    Settings.Secure.getInt(contentResolver, "night_display_activated", 0) == 1
} catch (_: Exception) { false }

private fun MainActivity.toggleNightLight() {
    val turnOn = !isNightLightEnabled()
    try {
        Settings.Secure.putInt(contentResolver, "night_display_activated", if (turnOn) 1 else 0)
        refreshOmarchyActionTray()
    } catch (_: SecurityException) {
        try {
            startActivity(Intent(Settings.ACTION_NIGHT_DISPLAY_SETTINGS))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
        }
        Toast.makeText(this, "Enable Night Light in Android settings. Direct switching needs the optional secure-settings adb grant.", Toast.LENGTH_LONG).show()
    }
}

private fun MainActivity.showReminderInput() {
    val p = pal
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(14), dp(18), dp(12))
        background = GradientDrawable().apply {
            setColor(p.cBg or (0xFF shl 24))
            setStroke(dp(1), p.cBlue)
            cornerRadius = 0f
        }
    }
    root.addView(txt(13f, p.cDim, "Remind in minutes..."))
    val input = EditText(this).apply {
        inputType = android.text.InputType.TYPE_CLASS_NUMBER
        setText("5")
        setSelection(text.length)
        textSize = 14f * uiScale
        typeface = Typeface.create("monospace", Typeface.NORMAL)
        setTextColor(p.cFg)
        setHintTextColor(p.cDim)
        background = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(dp(1), p.cDim)
        }
        setPadding(dp(10), dp(9), dp(10), dp(9))
    }
    root.addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
    var dialog: AlertDialog? = null
    val actions = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END
        setPadding(0, dp(8), 0, 0)
    }
    actions.addView(txt(12f, p.cDim, "CANCEL").apply {
        setPadding(dp(10), dp(8), dp(10), dp(8))
        setOnClickListener { dialog?.dismiss() }
    })
    actions.addView(txt(12f, p.cBlue, "SET").apply {
        setPadding(dp(10), dp(8), 0, dp(8))
        setOnClickListener {
            val minutes = input.text.toString().toIntOrNull()?.takeIf { it in 1..1440 }
            if (minutes == null) {
                Toast.makeText(this@showReminderInput, "Enter 1–1440 minutes", Toast.LENGTH_SHORT).show()
            } else {
                try {
                    startActivity(Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                        putExtra(AlarmClock.EXTRA_MESSAGE, "Sorairo reminder")
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    })
                    dialog?.dismiss()
                } catch (_: Exception) {
                    Toast.makeText(this@showReminderInput, "No compatible Clock app found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    })
    root.addView(actions)
    dialog = AlertDialog.Builder(this).create().apply {
        setView(root)
        show()
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setLayout(minOf(resources.displayMetrics.widthPixels - dp(40), dp(400)), -2)
    }
}

private fun MainActivity.showScreenRecordingMenu() {
    val p = pal
    val labels = listOf("With no audio", "With device audio", "With device + microphone audio")
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(14))
        background = GradientDrawable().apply {
            setColor(p.cBg or (0xFF shl 24))
            setStroke(dp(1), p.cBlue)
            cornerRadius = 0f
        }
    }
    root.addView(txt(13f, p.cDim, "Screenrecord..."))
    var menu: AlertDialog? = null
    labels.forEachIndexed { index, label ->
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(11), dp(10), dp(11))
            if (index == 0) setBackgroundColor(p.cSel)
            isClickable = true
            setOnClickListener {
                menu?.dismiss()
                showAndroidScreenRecordingGuide(label)
            }
        }
        val videoMark = TextView(this).apply {
            text = "▣"
            textSize = 15f
            setTextColor(if (index == 0) p.cBlue else p.cFg)
            typeface = Typeface.create("monospace", Typeface.BOLD)
        }
        row.addView(videoMark, LinearLayout.LayoutParams(dp(25), -2))
        row.addView(txt(13f, if (index == 0) p.cBlue else p.cFg, label))
        root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
    }
    menu = AlertDialog.Builder(this).create().apply {
        setView(root)
        show()
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setLayout(minOf(resources.displayMetrics.widthPixels - dp(40), dp(480)), -2)
        window?.setDimAmount(0.45f)
    }
}

private fun MainActivity.showAndroidScreenRecordingGuide(mode: String) {
    val p = pal
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(12))
        background = GradientDrawable().apply {
            setColor(p.cBg or (0xFF shl 24))
            setStroke(dp(1), p.cBlue)
            cornerRadius = 0f
        }
    }
    root.addView(txt(13f, p.cBlue, "Screen Recording"))
    root.addView(txt(12f, p.cFg,
        "Android controls screen capture. Swipe down twice to open Quick Settings, tap Screen record, then choose: $mode."
    ).apply { setPadding(0, dp(8), 0, dp(12)) })
    AlertDialog.Builder(this).setView(root).setPositiveButton("Close", null).show()
}

private fun MainActivity.showSudoUnavailable() {
    val p = pal
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(14), dp(18), dp(12))
        background = GradientDrawable().apply {
            setColor(p.cBg or (0xFF shl 24))
            setStroke(dp(1), p.cAccent)
            cornerRadius = 0f
        }
    }
    root.addView(txt(13f, p.cAccent, "Disable Passwordless Sudo"))
    root.addView(txt(12f, p.cFg, "Android does not use the Linux sudo authentication model, so this desktop-only action has no direct Android equivalent.")
        .apply { setPadding(0, dp(8), 0, 0) })
    AlertDialog.Builder(this).setView(root).setPositiveButton("Close", null).show()
}
