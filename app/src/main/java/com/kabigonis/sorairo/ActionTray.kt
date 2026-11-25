package com.kabigonis.sorairo

import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.util.DisplayMetrics
import android.media.projection.MediaProjectionManager
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
import java.lang.ref.WeakReference
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
        if (ScreenRecordService.recording) "Stop Screen Recording" else "Start Screen Recording"
    }) { toggleScreenRecording() }
    add(OmarchyGlyph.HAND, { "Set Reminder" }) { showReminderInput() }
    add(OmarchyGlyph.NIGHT_LIGHT, { if (isNightLightEnabled()) "Day Light" else "Night Light" }) { toggleNightLight() }
    add(OmarchyGlyph.MUTED_BELL, { "Silence Notifications" }) { toggleDoNotDisturb() }
    add(OmarchyGlyph.COFFEE, {
        if (prefs.getBoolean("tray_stay_awake", false)) "Allow Idle Lock & Screensaver" else "Stay Awake"
    }) { toggleStayAwake() }
    add(OmarchyGlyph.SUDO, { "Disable Passwordless Sudo" }) { showSudoPanel() }

    actionIconViews[this] = icons
    actionTrayExpanded[this] = false
    ScreenRecordService.listener = WeakReference(this)
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
    val visible = mapOf(
        OmarchyGlyph.RECORD to (expanded || ScreenRecordService.recording),
        OmarchyGlyph.HAND to (expanded || reminderRemainingMs() > 0L),
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
            OmarchyGlyph.COFFEE -> icon?.active = awake
            OmarchyGlyph.MUTED_BELL -> icon?.active = dnd
            OmarchyGlyph.NIGHT_LIGHT -> icon?.active = night
            OmarchyGlyph.RECORD -> icon?.active = ScreenRecordService.recording
            OmarchyGlyph.HAND -> icon?.active = reminderRemainingMs() > 0L
            OmarchyGlyph.SUDO -> icon?.active = sudoActive()
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
    // Universal mode: Android's global screen timeout applies across all apps.
    if (!prefs.getBoolean("tray_stay_awake", false) || !Settings.System.canWrite(this)) return
    try {
        Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, Int.MAX_VALUE)
    } catch (_: Exception) {
        Toast.makeText(this, "Couldn't apply the system-wide screen timeout.", Toast.LENGTH_LONG).show()
    }
}

internal fun MainActivity.isStayAwakeOn(): Boolean =
    prefs.getBoolean("tray_stay_awake", false)

internal fun MainActivity.setStayAwake(on: Boolean) {
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
        if (on) {
            val current = Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30_000)
            if (current != Int.MAX_VALUE) prefs.edit().putInt("tray_previous_screen_timeout", current).apply()
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, Int.MAX_VALUE)
        } else {
            val previous = prefs.getInt("tray_previous_screen_timeout", 30_000)
                .let { if (it <= 0 || it == Int.MAX_VALUE) 30_000 else it }
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, previous)
            prefs.edit().remove("tray_previous_screen_timeout").apply()
        }
        prefs.edit().putBoolean("tray_stay_awake", on).apply()
    } catch (_: Exception) {
        Toast.makeText(this, "Android couldn't change the system screen timeout.", Toast.LENGTH_LONG).show()
    }
    applyTrayStayAwake()
    refreshOmarchyActionTray()
}

private fun MainActivity.toggleStayAwake() {
    val next = !isStayAwakeOn()
    sudoIf(Settings.System.canWrite(this), "Change the system screen timeout") {
        setStayAwake(next)
    }
}

internal fun MainActivity.isDoNotDisturbEnabled(): Boolean {
    if (Build.VERSION.SDK_INT < 23) return false
    return try {
        val nm = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.isNotificationPolicyAccessGranted &&
            nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    } catch (_: Exception) { false }
}

internal fun MainActivity.setDoNotDisturb(on: Boolean) {
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (!nm.isNotificationPolicyAccessGranted) {
        try { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
        catch (_: Exception) { }
        Toast.makeText(this, "Allow Sorairo to manage Do Not Disturb, then tap again", Toast.LENGTH_LONG).show()
        return
    }
    try {
        if (on) {
            var allowedCategories =
                NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS or
                    NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA
            if (Build.VERSION.SDK_INT >= 28) {
                allowedCategories = allowedCategories or NotificationManager.Policy.PRIORITY_CATEGORY_SYSTEM
            }
            val senders = NotificationManager.Policy.PRIORITY_SENDERS_ANY
            nm.notificationPolicy = if (Build.VERSION.SDK_INT >= 28) {
                val current = nm.notificationPolicy
                NotificationManager.Policy(allowedCategories, senders, senders, current.suppressedVisualEffects)
            } else {
                @Suppress("DEPRECATION")
                NotificationManager.Policy(allowedCategories, senders, senders)
            }
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        } else {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
        refreshOmarchyActionTray()
    } catch (_: SecurityException) {
        Toast.makeText(this, "Do Not Disturb access was not granted", Toast.LENGTH_SHORT).show()
    }
}

private fun MainActivity.toggleDoNotDisturb() {
    setDoNotDisturb(!isDoNotDisturbEnabled())
}

internal fun MainActivity.isNightLightEnabled(): Boolean = try {
    Settings.Secure.getInt(contentResolver, "night_display_activated", 0) == 1
} catch (_: Exception) { false }

internal fun MainActivity.setNightLight(on: Boolean): Boolean {
    if (hasSecureSettings()) {
        try {
            if (Settings.Secure.putInt(contentResolver, "night_display_activated", if (on) 1 else 0)) {
                refreshOmarchyActionTray()
                return true
            }
        } catch (_: Exception) { }
    }
    try {
        startActivity(Intent(Settings.ACTION_NIGHT_DISPLAY_SETTINGS))
    } catch (_: Exception) {
        try { startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) } catch (_: Exception) { }
    }
    Toast.makeText(
        this,
        "Switch Night Light in Android settings. Direct switching needs the optional secure-settings grant.",
        Toast.LENGTH_LONG
    ).show()
    return false
}

private fun MainActivity.toggleNightLight() {
    sudoIf(hasSecureSettings(), "Switch Night Light") {
        setNightLight(!isNightLightEnabled())
    }
}

private const val KEY_TRAY_REMINDER_AT = "reminder_at"
private const val TRAY_REMINDER_REQUEST = 8801

private fun MainActivity.trayReminderIntent(): PendingIntent = PendingIntent.getBroadcast(
    this,
    TRAY_REMINDER_REQUEST,
    Intent(this, ReminderReceiver::class.java).apply { action = ReminderReceiver.ACTION_FIRE },
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)

internal fun MainActivity.reminderRemainingMs(): Long =
    (prefs.getLong(KEY_TRAY_REMINDER_AT, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

internal fun MainActivity.cancelReminder() {
    (getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(trayReminderIntent())
    prefs.edit().remove(KEY_TRAY_REMINDER_AT).apply()
    refreshOmarchyActionTray()
}

internal fun MainActivity.scheduleReminder(minutes: Int): Boolean {
    if (minutes !in 1..1440) {
        Toast.makeText(this, "Enter 1–1440 minutes", Toast.LENGTH_SHORT).show()
        return false
    }
    if (Build.VERSION.SDK_INT >= 33 &&
        checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
        Toast.makeText(this, "Allow notifications, then set the reminder again.", Toast.LENGTH_LONG).show()
        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 743)
        return false
    }
    val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
    if (Build.VERSION.SDK_INT >= 31 && !alarm.canScheduleExactAlarms()) {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                android.net.Uri.parse("package:$packageName")))
            Toast.makeText(this, "Allow Sorairo to set exact alarms, then set the reminder again.", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Enable Alarms & reminders access for Sorairo in Settings.", Toast.LENGTH_LONG).show()
        }
        return false
    }
    val triggerAt = System.currentTimeMillis() + minutes * 60_000L
    return try {
        if (Build.VERSION.SDK_INT >= 23) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, trayReminderIntent())
        } else {
            @Suppress("DEPRECATION")
            alarm.setExact(AlarmManager.RTC_WAKEUP, triggerAt, trayReminderIntent())
        }
        prefs.edit().putLong(KEY_TRAY_REMINDER_AT, triggerAt).apply()
        refreshOmarchyActionTray()
        Toast.makeText(this, "Reminder set for $minutes minute(s).", Toast.LENGTH_SHORT).show()
        true
    } catch (_: SecurityException) {
        Toast.makeText(this, "Exact alarm access is required for accurate reminders.", Toast.LENGTH_LONG).show()
        false
    }
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
                        val fireIntent = Intent(this@showReminderInput, ReminderReceiver::class.java).apply {
                            action = ReminderReceiver.ACTION_FIRE
                        }
                        val pending = PendingIntent.getBroadcast(
                            this@showReminderInput,
                            8801,
                            fireIntent,
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
                            prefs.edit().putLong(KEY_TRAY_REMINDER_AT, triggerAt).apply()
                            refreshOmarchyActionTray()
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

internal fun MainActivity.dismissTrayDialog() {
    activeDialog?.dismiss()
}

internal fun MainActivity.trayDialogRoot(bg: Int, border: Int): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(14))
        background = GradientDrawable().apply { setColor(bg or (0xFF shl 24)); setStroke(dp(1), border); cornerRadius = 0f }
    }

internal fun MainActivity.trayDialogActions(vararg actions: Pair<String, () -> Unit>): View =
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

internal fun MainActivity.showTrayDialog(root: View, maxWidthDp: Int) {
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
    root.addView(txt(13f, p.cBlue, "Screen Recording"))
    root.addView(txt(
        12f,
        p.cFg,
        "To record your screen, swipe down twice from the top to open Quick Settings, then tap Screen record. Choose whether to include device audio or microphone audio, then confirm Start."
    ).apply { setPadding(0, dp(9), 0, dp(14)) })
    root.addView(trayDialogActions("CLOSE" to { activeDialog?.dismiss() }))
    showTrayDialog(root, 480)
}

internal fun MainActivity.toggleScreenRecording() {
    if (ScreenRecordService.recording) stopScreenRecording() else startScreenRecording()
}

internal fun MainActivity.startScreenRecording() {
    if (ScreenRecordService.recording) return
    try {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureRequest.launch(manager.createScreenCaptureIntent())
    } catch (_: Exception) {
        Toast.makeText(this, "This device can't start a screen recording", Toast.LENGTH_LONG).show()
    }
}

internal fun MainActivity.stopScreenRecording() {
    try {
        startService(Intent(this, ScreenRecordService::class.java).setAction(ScreenRecordService.ACTION_STOP))
    } catch (_: Exception) { }
}

internal fun MainActivity.onScreenCaptureResult(resultCode: Int, data: Intent?) {
    if (resultCode != Activity.RESULT_OK || data == null) {
        Toast.makeText(this, "Screen recording cancelled", Toast.LENGTH_SHORT).show()
        return
    }
    var width: Int
    var height: Int
    if (Build.VERSION.SDK_INT >= 30) {
        val bounds = windowManager.currentWindowMetrics.bounds
        width = bounds.width()
        height = bounds.height()
    } else {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        width = metrics.widthPixels
        height = metrics.heightPixels
    }
    val longSide = maxOf(width, height)
    if (longSide > 1920) {
        val factor = 1920f / longSide
        width = (width * factor).toInt()
        height = (height * factor).toInt()
    }
    width = width / 2 * 2
    height = height / 2 * 2
    val service = Intent(this, ScreenRecordService::class.java)
        .setAction(ScreenRecordService.ACTION_START)
        .putExtra(ScreenRecordService.EXTRA_CODE, resultCode)
        .putExtra(ScreenRecordService.EXTRA_DATA, data)
        .putExtra(ScreenRecordService.EXTRA_W, width)
        .putExtra(ScreenRecordService.EXTRA_H, height)
        .putExtra(ScreenRecordService.EXTRA_DPI, resources.displayMetrics.densityDpi)
    try {
        startForegroundService(service)
    } catch (_: Exception) {
        Toast.makeText(this, "Couldn't start the recorder", Toast.LENGTH_LONG).show()
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
