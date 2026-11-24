package com.kabigonis.sorairo

import android.app.AlarmManager
import android.app.Dialog
import android.app.PendingIntent
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
        setPadding(0, 0, 0, 0)
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
        row.addView(hit, LinearLayout.LayoutParams(dp(22), dp(30)))
    }

    add(OmarchyGlyph.RECORD, {
        if (prefs.getBoolean("screen_recording_active", false)) "Stop Screen Recording" else "Screen Recording"
    }) {
        if (prefs.getBoolean("screen_recording_active", false)) stopScreenRecording()
        else requestScreenRecording()
    }
    add(OmarchyGlyph.HAND, { "Set Reminder" }) { showReminderInput() }
    add(OmarchyGlyph.NIGHT_LIGHT, {
        when (isNightLightEnabled()) {
            true -> "Night Light On"
            false -> "Night Light Off"
            null -> "Night Light Settings"
        }
    }) { toggleNightLight() }
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
    val recording = prefs.getBoolean("screen_recording_active", false)
    val dnd = isDoNotDisturbEnabled()
    val night = isNightLightEnabled() == true
    val visible = mapOf(
        OmarchyGlyph.RECORD to (expanded || recording),
        OmarchyGlyph.HAND to expanded,
        OmarchyGlyph.NIGHT_LIGHT to (expanded || night),
        OmarchyGlyph.MUTED_BELL to (expanded || dnd),
        OmarchyGlyph.COFFEE to (expanded || awake),
        OmarchyGlyph.SUDO to true
    )
    icons.entries.forEachIndexed { index, (glyph, hit) ->
        val shouldShow = visible[glyph] == true
        hit.animate().cancel()
        if (shouldShow) {
            if (hit.visibility != View.VISIBLE) {
                hit.visibility = View.VISIBLE
                hit.alpha = 0f
                hit.translationY = dp(4).toFloat()
                hit.scaleX = 0.88f
                hit.scaleY = 0.88f
                hit.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(if (expanded) (icons.size - index - 1) * 22L else 0L)
                    .setDuration(190L)
                    .start()
            }
        } else if (hit.visibility == View.VISIBLE) {
            hit.animate()
                .alpha(0f)
                .translationY(dp(3).toFloat())
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setStartDelay(0L)
                .setDuration(145L)
                .withEndAction {
                    if (visible[glyph] != true) {
                        hit.visibility = View.GONE
                        hit.alpha = 1f
                        hit.translationY = 0f
                        hit.scaleX = 1f
                        hit.scaleY = 1f
                    }
                }
                .start()
        }
        val icon = (hit as? ViewGroup)?.getChildAt(0) as? OmarchyActionIconView
        when (glyph) {
            OmarchyGlyph.RECORD -> icon?.active = recording
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
    if (!prefs.getBoolean("tray_stay_awake", false) || !Settings.System.canWrite(this)) return
    try {
        val previous = prefs.getInt("tray_previous_screen_timeout", 30_000)
        val wrote = Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, Int.MAX_VALUE)
        val applied = Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, previous)
        if (!wrote || (applied <= previous && applied != Int.MAX_VALUE)) {
            prefs.edit().putBoolean("tray_stay_awake", false)
                .remove("tray_previous_screen_timeout").remove("tray_applied_screen_timeout").apply()
            Toast.makeText(this, "Android refused the Stay Awake timeout change.", Toast.LENGTH_LONG).show()
        } else {
            prefs.edit().putInt("tray_applied_screen_timeout", applied).apply()
        }
    } catch (_: Exception) {
        prefs.edit().putBoolean("tray_stay_awake", false)
            .remove("tray_previous_screen_timeout").remove("tray_applied_screen_timeout").apply()
        Toast.makeText(this, "Couldn't apply the system-wide screen timeout.", Toast.LENGTH_LONG).show()
    }
}

private fun MainActivity.toggleStayAwake() {
    val enabled = !prefs.getBoolean("tray_stay_awake", false)
    if (!Settings.System.canWrite(this)) {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
            })
            Toast.makeText(this, "Allow Modify system settings, then tap Stay Awake again.", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Allow Sorairo to modify system settings.", Toast.LENGTH_LONG).show()
        }
        return
    }
    try {
        val current = Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30_000)
        if (enabled) {
            val wrote = Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, Int.MAX_VALUE)
            val applied = Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, current)
            if (!wrote || (applied <= current && current != Int.MAX_VALUE)) {
                Toast.makeText(this, "Android didn't accept the Stay Awake timeout.", Toast.LENGTH_LONG).show()
                return
            }
            prefs.edit().putInt("tray_previous_screen_timeout", current)
                .putInt("tray_applied_screen_timeout", applied)
                .putBoolean("tray_stay_awake", true).apply()
        } else {
            val previous = prefs.getInt("tray_previous_screen_timeout", 30_000)
            val applied = prefs.getInt("tray_applied_screen_timeout", Int.MAX_VALUE)
            if (current == applied) {
                val restored = Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, previous)
                val actual = Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, current)
                if (!restored || actual != previous) {
                    Toast.makeText(this, "Android couldn't restore the previous timeout.", Toast.LENGTH_LONG).show()
                    return
                }
            }
            prefs.edit().putBoolean("tray_stay_awake", false)
                .remove("tray_previous_screen_timeout").remove("tray_applied_screen_timeout").apply()
        }
    } catch (_: Exception) {
        Toast.makeText(this, "Android couldn't change the system screen timeout.", Toast.LENGTH_LONG).show()
    }
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
    if (Build.VERSION.SDK_INT < 23) {
        Toast.makeText(this, "Do Not Disturb needs Android 6 or newer.", Toast.LENGTH_SHORT).show()
        return
    }
    if (!nm.isNotificationPolicyAccessGranted) {
        try { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
        catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        Toast.makeText(this, "Allow Sorairo to manage Do Not Disturb, then tap again.", Toast.LENGTH_LONG).show()
        return
    }
    try {
        val enabling = nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        // Change the current filter only; keep the user's saved DND policy untouched.
        nm.setInterruptionFilter(
            if (enabling) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
        val expected = if (enabling) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_ALL
        if (nm.currentInterruptionFilter != expected) {
            Toast.makeText(this, "Android didn't apply the Do Not Disturb change.", Toast.LENGTH_SHORT).show()
        }
        refreshOmarchyActionTray()
    } catch (_: SecurityException) {
        Toast.makeText(this, "Do Not Disturb access was not granted", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(this, "Couldn't change Do Not Disturb.", Toast.LENGTH_SHORT).show()
    }
}


/** Null means Android doesn't expose a readable Night Light state on this device. */
private fun MainActivity.isNightLightEnabled(): Boolean? = try {
    when (Settings.Secure.getString(contentResolver, "night_display_activated")) {
        "1" -> true
        "0" -> false
        else -> null
    }
} catch (_: Exception) { null }

private fun MainActivity.openNightLightSettings() {
    try { startActivity(Intent(Settings.ACTION_NIGHT_DISPLAY_SETTINGS)) }
    catch (_: Exception) {
        try { startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
        catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }
}

private fun MainActivity.toggleNightLight() {
    val current = isNightLightEnabled()
    if (current == null || !canSetBatterySaver()) {
        openNightLightSettings()
        return
    }
    val target = !current
    val written = try {
        Settings.Secure.putInt(contentResolver, "night_display_activated", if (target) 1 else 0)
    } catch (_: Exception) { false }
    val actual = isNightLightEnabled()
    if (!written || actual != target) openNightLightSettings()
    else refreshOmarchyActionTray()
}

private fun MainActivity.showReminderInput() {
    val p = pal
    val root = trayDialogRoot(p.cBg, p.cBlue).apply {
        addView(txt(13f, p.cDim, "Remind in minutes..."))
        val input = EditText(this@showReminderInput).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("1")
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
        addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        addView(trayDialogActions(
            "CANCEL" to { activeDialog?.dismiss() },
            "SET" to {
                val minutes = input.text.toString().toIntOrNull()?.takeIf { it in 1..1440 }
                if (minutes == null) {
                    Toast.makeText(this@showReminderInput, "Enter 1–1440 minutes", Toast.LENGTH_SHORT).show()
                } else if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    Toast.makeText(this@showReminderInput, "Allow notifications, then tap SET again.", Toast.LENGTH_LONG).show()
                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 743)
                } else {
                    val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as AlarmManager
                    if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
                        try {
                            startActivity(Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                android.net.Uri.parse("package:$packageName")
                            ))
                            Toast.makeText(
                                this@showReminderInput,
                                "Allow Sorairo to set exact alarms, then return and tap Set again.",
                                Toast.LENGTH_LONG
                            ).show()
                        } catch (_: Exception) {
                            Toast.makeText(this@showReminderInput, "Enable Alarms & reminders access for Sorairo in Settings.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        val reminderId = prefs.getInt("tray_next_reminder_id", 10_000)
                            .coerceIn(10_000, Int.MAX_VALUE - 1)
                        prefs.edit().putInt("tray_next_reminder_id",
                            if (reminderId >= Int.MAX_VALUE - 1) 10_000 else reminderId + 1).apply()
                        val fireIntent = Intent(this@showReminderInput, ReminderReceiver::class.java).apply {
                            action = ReminderReceiver.ACTION_FIRE
                            data = android.net.Uri.parse("sorairo://reminder/$reminderId")
                            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
                        }
                        val pending = PendingIntent.getBroadcast(
                            this@showReminderInput, reminderId, fireIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
                        try {
                            if (Build.VERSION.SDK_INT >= 23) {
                                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                            } else {
                                @Suppress("DEPRECATION")
                                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                            }
                            Toast.makeText(this@showReminderInput, "Reminder set for $minutes minute(s).", Toast.LENGTH_SHORT).show()
                            activeDialog?.dismiss()
                        } catch (_: SecurityException) {
                            Toast.makeText(this@showReminderInput, "Exact alarm access is required for accurate reminders.", Toast.LENGTH_LONG).show()
                        }
                    }
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
    root.addView(txt(14f, p.cBlue, "Screen Recording"))
    root.addView(txt(10f, p.cDim,
        "Choose what to include. Android will ask you to approve screen capture before recording starts."
    ).apply { setPadding(0, dp(5), 0, dp(8)) })

    fun option(label: String, detail: String, mode: Int) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(11), dp(4), dp(11))
            setOnClickListener {
                activeDialog?.dismiss()
                try {
                    startActivity(Intent(this@showScreenRecordingMenu, ScreenRecordingActivity::class.java)
                        .putExtra(ScreenRecordingActivity.EXTRA_AUDIO_MODE, mode))
                } catch (_: Exception) {
                    Toast.makeText(this@showScreenRecordingMenu, "Couldn't open screen recording consent.", Toast.LENGTH_LONG).show()
                }
            }
        }
        row.addView(OmarchyActionIconView(this, OmarchyGlyph.RECORD_VIDEO, p.cBlue, p.cBlue, p.cBlue),
            LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(12) })
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(txt(13f, p.cFg, label))
        texts.addView(txt(10f, p.cDim, detail).apply { setPadding(0, dp(2), 0, 0) })
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(row)
        root.addView(View(this).apply { setBackgroundColor(p.cDim and 0x44FFFFFF) },
            LinearLayout.LayoutParams(-1, dp(1)))
    }
    option("With no audio", "Video only", ScreenRecordingActivity.AUDIO_NONE)
    option("With desktop audio", "Capture supported playback from other apps", ScreenRecordingActivity.AUDIO_DESKTOP)
    option("With desktop + microphone audio", "Mix supported playback and microphone input", ScreenRecordingActivity.AUDIO_DESKTOP_AND_MIC)
    root.addView(trayDialogActions("CLOSE" to { activeDialog?.dismiss() }))
    showTrayDialog(root, 480)
}

private fun MainActivity.requestScreenRecording() {
    if (prefs.getBoolean("screen_recording_active", false)) stopScreenRecording()
    else showScreenRecordingMenu()
}

private fun MainActivity.stopScreenRecording() {
    try {
        startService(Intent(this, ScreenRecorderService::class.java).setAction(ScreenRecorderService.ACTION_STOP))
    } catch (_: Exception) {
        Toast.makeText(this, "Couldn't stop the screen recorder.", Toast.LENGTH_SHORT).show()
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
