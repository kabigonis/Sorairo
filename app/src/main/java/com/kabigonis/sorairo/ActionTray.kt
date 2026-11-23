package com.kabigonis.sorairo

import android.app.AlarmClock
import android.app.Dialog
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
import java.util.WeakHashMap

private val actionIconViews = WeakHashMap<MainActivity, MutableMap<OmarchyGlyph, View>>()
private val actionTrayCollapseCallbacks = WeakHashMap<MainActivity, Runnable>()
private val actionTooltipWindows = WeakHashMap<MainActivity, PopupWindow>()
private val actionTooltipCallbacks = WeakHashMap<MainActivity, Runnable>()
private val actionTrayExpanded = WeakHashMap<MainActivity, Boolean>()

/** Tray stays in the right status group; only pinned/active glyphs show while collapsed. */
internal fun MainActivity.createOmarchyActionTray(): View {
    val p = pal
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL or Gravity.END
        setPadding(dp(2), 0, dp(2), 0)
        isClickable = true
        setOnClickListener { revealOmarchyActionTray() }
        setOnHoverListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_HOVER_ENTER,
                android.view.MotionEvent.ACTION_HOVER_MOVE -> revealOmarchyActionTray()
                android.view.MotionEvent.ACTION_HOVER_EXIT -> scheduleOmarchyActionTrayCollapse(650L)
            }
            false
        }
    }
    val icons = linkedMapOf<OmarchyGlyph, View>()

    fun add(glyph: OmarchyGlyph, label: () -> String, action: () -> Unit) {
        val icon = OmarchyActionIconView(this, glyph, p.cDim, p.cBlue, p.cAnsi[1])
        val hit = android.widget.FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            contentDescription = label()
            setOnClickListener {
                icon.animate().scaleX(1.18f).scaleY(1.18f).setDuration(85L)
                    .withEndAction { icon.animate().scaleX(1f).scaleY(1f).setDuration(110L).start() }.start()
                action()
                scheduleOmarchyActionTrayCollapse(900L)
            }
            setOnLongClickListener {
                showOmarchyTooltip(this, label())
                true
            }
            addView(icon, android.widget.FrameLayout.LayoutParams(dp(14), dp(16), Gravity.CENTER))
        }
        hit.tag = glyph
        icons[glyph] = hit
        row.addView(hit, LinearLayout.LayoutParams(dp(20), dp(30)))
    }

    add(OmarchyGlyph.RECORD, { "Screen Recording" }) { requestScreenRecording() }
    add(OmarchyGlyph.HAND, { "Set Reminder" }) { showReminderInput() }
    add(OmarchyGlyph.NIGHT_LIGHT, { if (isNightLightEnabled()) "Day Light" else "Night Light" }) { toggleNightLight() }
    add(OmarchyGlyph.MUTED_BELL, { "Silence Notifications" }) { toggleDoNotDisturb() }
    add(OmarchyGlyph.COFFEE, {
        if (prefs.getBoolean("tray_stay_awake", false)) "Allow Idle Lock & Screensaver" else "Stay Awake"
    }) { toggleStayAwake() }
    add(OmarchyGlyph.SUDO, { "Disable Passwordless Sudo" }) { showSudoUnavailable() }

    actionIconViews[this] = icons
    actionTrayExpanded[this] = false
    refreshOmarchyActionTray()
    return row
}

internal fun MainActivity.revealOmarchyActionTray() {
    actionTrayExpanded[this] = true
    refreshOmarchyActionTray()
    scheduleOmarchyActionTrayCollapse(4200L)
}

internal fun MainActivity.scheduleOmarchyActionTrayCollapse(delay: Long) {
    actionTrayCollapseCallbacks[this]?.let { handler.removeCallbacks(it) }
    val callback = Runnable {
        actionTrayExpanded[this] = false
        refreshOmarchyActionTray()
    }
    actionTrayCollapseCallbacks[this] = callback
    handler.postDelayed(callback, delay)
}

internal fun MainActivity.refreshOmarchyActionTray() {
    val icons = actionIconViews[this] ?: return
    val expanded = actionTrayExpanded[this] == true
    val awake = prefs.getBoolean("tray_stay_awake", false)
    val dnd = isDoNotDisturbEnabled()
    val night = isNightLightEnabled()
    (icons[OmarchyGlyph.COFFEE] as? View)?.visibility = if (expanded || awake) View.VISIBLE else View.GONE
    (icons[OmarchyGlyph.MUTED_BELL] as? View)?.visibility = if (expanded || dnd) View.VISIBLE else View.GONE
    (icons[OmarchyGlyph.NIGHT_LIGHT] as? View)?.visibility = if (expanded || night) View.VISIBLE else View.GONE
    (icons[OmarchyGlyph.RECORD] as? View)?.visibility = if (expanded) View.VISIBLE else View.GONE
    (icons[OmarchyGlyph.HAND] as? View)?.visibility = if (expanded) View.VISIBLE else View.GONE
    (icons[OmarchyGlyph.SUDO] as? View)?.visibility = View.VISIBLE
    // Pinned states use the reference's blue active tint.
    icons.values.forEach { hit ->
        val icon = (hit as? ViewGroup)?.getChildAt(0) as? OmarchyActionIconView
        when (hit.tag as? OmarchyGlyph) {
            OmarchyGlyph.COFFEE -> icon?.active = awake
            OmarchyGlyph.MUTED_BELL -> icon?.active = dnd
            OmarchyGlyph.NIGHT_LIGHT -> icon?.active = night
            else -> Unit
        }
    }
}

private fun MainActivity.showOmarchyTooltip(anchor: View, label: String) {
    actionTooltipCallbacks[this]?.let { handler.removeCallbacks(it) }
    actionTooltipWindows[this]?.let { if (it.isShowing) it.dismiss() }
    val p = pal
    val text = TextView(this).apply {
        this.text = label
        textSize = 11f * uiScale
        typeface = Typeface.create("monospace", Typeface.NORMAL)
        setTextColor(p.cFg)
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
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        elevation = 0f
    }
    actionTooltipWindows[this] = popup
    popup.showAsDropDown(anchor, 0, dp(3), Gravity.CENTER_HORIZONTAL)
    text.alpha = 0f
    text.translationY = -dp(3).toFloat()
    text.animate().alpha(1f).translationY(0f).setDuration(120L).start()
    val callback = Runnable {
        text.animate().alpha(0f).translationY(-dp(2).toFloat()).setDuration(180L)
            .withEndAction { if (popup.isShowing) popup.dismiss() }
            .start()
    }
    actionTooltipCallbacks[this] = callback
    handler.postDelayed(callback, 1450L)
}

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
    val nm = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
    if (!nm.isNotificationPolicyAccessGranted) {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        Toast.makeText(this, "Allow Sorairo to manage Do Not Disturb, then tap again", Toast.LENGTH_LONG).show()
        return
    }
    try {
        val on = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_NONE)
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
        val changed = Settings.Secure.putInt(contentResolver, "night_display_activated", if (turnOn) 1 else 0)
        if (!changed) throw SecurityException("Night Light is read-only")
        refreshOmarchyActionTray()
    } catch (_: Exception) {
        try { startActivity(Intent(Settings.ACTION_NIGHT_DISPLAY_SETTINGS)) }
        catch (_: Exception) { startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
        Toast.makeText(this, "Switch Night Light in Android settings. Direct switching needs the optional secure-settings grant.", Toast.LENGTH_LONG).show()
    }
}

private fun MainActivity.showReminderInput() {
    val p = pal
    val root = trayDialogRoot(p.cBg, p.cBlue).apply {
        addView(txt(13f, p.cDim, "Remind in minutes..."))
        val input = EditText(this@showReminderInput).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("5")
            setSelection(text.length)
            textSize = 14f * uiScale
            typeface = Typeface.create("monospace", Typeface.NORMAL)
            setTextColor(p.cFg)
            setHintTextColor(p.cDim)
            background = GradientDrawable().apply { setColor(Color.TRANSPARENT); setStroke(dp(1), p.cDim) }
            setPadding(dp(10), dp(9), dp(10), dp(9))
        }
        addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        addView(trayDialogActions(
            "CANCEL" to { activeDialog?.dismiss() },
            "SET" to {
                val minutes = input.text.toString().toIntOrNull()?.takeIf { it in 1..1440 }
                if (minutes == null) Toast.makeText(this@showReminderInput, "Enter 1–1440 minutes", Toast.LENGTH_SHORT).show()
                else {
                    try {
                        startActivity(Intent(AlarmClock.ACTION_SET_TIMER).apply {
                            putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                            putExtra(AlarmClock.EXTRA_MESSAGE, "Sorairo reminder")
                        })
                        activeDialog?.dismiss()
                    } catch (_: Exception) { Toast.makeText(this@showReminderInput, "No compatible Clock app found", Toast.LENGTH_SHORT).show() }
                }
            }
        ))
    }
    showTrayDialog(root, 400)
}

private var MainActivity.activeDialog: Dialog?
    get() = trayDialogs[this]
    set(value) { trayDialogs[this] = value }
private val trayDialogs = WeakHashMap<MainActivity, Dialog>()

private fun MainActivity.trayDialogRoot(bg: Int, border: Int): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(14))
        background = GradientDrawable().apply { setColor(bg or (0xFF shl 24)); setStroke(dp(1), border); cornerRadius = 0f }
    }

private fun MainActivity.trayDialogActions(vararg actions: Pair<String, () -> Unit>): View =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END
        setPadding(0, dp(12), 0, 0)
        actions.forEach { (label, callback) ->
            addView(txt(12f, if (label == "SET" || label == "CLOSE") pal.cBlue else pal.cDim, label).apply {
                setPadding(dp(12), dp(9), dp(4), dp(6))
                setOnClickListener { callback() }
            })
        }
    }

private fun MainActivity.showTrayDialog(root: View, maxWidthDp: Int) {
    activeDialog?.dismiss()
    val dialog = Dialog(this)
    activeDialog = dialog
    dialog.setContentView(root)
    dialog.window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        attributes = attributes.apply { dimAmount = 0.32f }
        setLayout(minOf(resources.displayMetrics.widthPixels - dp(32), dp(maxWidthDp)), ViewGroup.LayoutParams.WRAP_CONTENT)
        setGravity(Gravity.CENTER)
    }
    dialog.setOnDismissListener { if (activeDialog === dialog) activeDialog = null }
    dialog.show()
    dialog.window?.setLayout(minOf(resources.displayMetrics.widthPixels - dp(32), dp(maxWidthDp)), ViewGroup.LayoutParams.WRAP_CONTENT)
}

private fun MainActivity.showScreenRecordingMenu() {
    val p = pal
    val root = trayDialogRoot(p.cBg, p.cBlue)
    root.addView(txt(13f, p.cDim, "Screenrecord..."))
    listOf("With no audio", "With device audio", "With device + microphone audio").forEachIndexed { index, label ->
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(11), dp(10), dp(11))
            if (index == 0) setBackgroundColor(p.cSel)
            addView(OmarchyActionIconView(this@showScreenRecordingMenu, OmarchyGlyph.RECORD_VIDEO, p.cDim, p.cBlue, p.cAnsi[1]),
                LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(8) })
            addView(txt(13f, if (index == 0) p.cBlue else p.cFg, label))
            setOnClickListener {
                activeDialog?.dismiss()
                if (index == 0) requestScreenRecording()
                else showAndroidScreenRecordingGuide(label)
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
    }
    root.addView(trayDialogActions("CLOSE" to { activeDialog?.dismiss() }))
    showTrayDialog(root, 480)
}

private fun MainActivity.showAndroidScreenRecordingGuide(mode: String) {
    val p = pal
    val root = trayDialogRoot(p.cBg, p.cBlue)
    root.addView(txt(13f, p.cBlue, "Screen Recording"))
    root.addView(txt(12f, p.cFg, "Android will ask for screen-capture permission. The selected audio mode is: $mode.")
        .apply { setPadding(0, dp(8), 0, dp(12)) })
    root.addView(trayDialogActions("CLOSE" to { activeDialog?.dismiss() }))
    showTrayDialog(root, 480)
}

private fun MainActivity.requestScreenRecording() {
    try {
        val manager = getSystemService(android.media.projection.MediaProjectionManager::class.java)
        startActivityForResult(manager.createScreenCaptureIntent(), MainActivity.REQUEST_SCREEN_CAPTURE)
    } catch (_: Exception) {
        Toast.makeText(this, "Android screen capture is unavailable on this device.", Toast.LENGTH_LONG).show()
    }
}

private fun MainActivity.showSudoUnavailable() {
    val p = pal
    val root = trayDialogRoot(p.cBg, p.cAnsi[1])
    root.addView(txt(13f, p.cAnsi[1], "Disable Passwordless Sudo"))
    root.addView(txt(12f, p.cFg, "Android does not use the Linux sudo authentication model, so this desktop-only action has no direct Android equivalent.")
        .apply { setPadding(0, dp(8), 0, dp(12)) })
    root.addView(trayDialogActions("CLOSE" to { activeDialog?.dismiss() }))
    showTrayDialog(root, 480)
}
