package com.kabigonis.sorairo

import android.app.Activity
import android.app.AlarmManager
import android.app.Dialog
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Omarchy-style action tray that sits just left of the clock.
 *
 * Like Waybar's tray-expander: collapsed, only indicators that are currently ON are visible
 * (recording, reminder, night light, silenced, stay awake, sudo) plus a chevron. Tap the chevron
 * (or hover it with a mouse) to reveal every action.
 */
private class TrayState {
    val icons = linkedMapOf<OmarchyGlyph, View>()
    val labels = HashMap<OmarchyGlyph, () -> String>()
    /** What each icon is currently animating towards; refresh only animates when this changes. */
    val shown = HashMap<OmarchyGlyph, Boolean>()
    var expanded = false
    var collapse: Runnable? = null
    var tooltip: PopupWindow? = null
    var tooltipHide: Runnable? = null
}

private val trayStates = WeakHashMap<MainActivity, TrayState>()

internal fun MainActivity.createOmarchyActionTray(): View {
    val p = pal
    val st = TrayState()
    trayStates[this] = st
    val red = p.cAnsi[1]
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL or Gravity.END
        // The empty area beside the sudo indicator is the tray expander.
        isClickable = true
        isFocusable = true
        setOnClickListener { revealOmarchyActionTray() }
        setOnHoverListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> revealOmarchyActionTray()
                MotionEvent.ACTION_HOVER_EXIT -> scheduleOmarchyActionTrayCollapse(900L)
            }
            false
        }
    }

    fun add(glyph: OmarchyGlyph, activeColor: Int, label: () -> String, onClick: () -> Unit) {
        val icon = OmarchyActionIconView(this, glyph, p.cDim, activeColor)
        val hit = FrameLayout(this).apply {
            visibility = View.GONE
            isClickable = true
            isFocusable = true
            contentDescription = label()
            setOnClickListener {
                icon.animate().scaleX(1.18f).scaleY(1.18f).setDuration(85L)
                    .withEndAction { icon.animate().scaleX(1f).scaleY(1f).setDuration(110L).start() }.start()
                onClick()
                scheduleOmarchyActionTrayCollapse(2500L)
            }
            setOnLongClickListener {
                showOmarchyTooltip(this, label())
                true
            }
            setOnHoverListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_HOVER_ENTER -> revealOmarchyActionTray()
                    MotionEvent.ACTION_HOVER_EXIT -> scheduleOmarchyActionTrayCollapse(900L)
                }
                false
            }
            addView(icon, FrameLayout.LayoutParams(dp(15), dp(15), Gravity.CENTER))
        }
        st.icons[glyph] = hit
        st.labels[glyph] = label
        st.shown[glyph] = false
        row.addView(hit, LinearLayout.LayoutParams(dp(26), dp(34)))
    }

    add(OmarchyGlyph.RECORD, red, {
        if (ScreenRecordService.recording) "Stop Screen Recording" else "Start Screen Recording"
    }) { toggleScreenRecording() }
    add(OmarchyGlyph.REMINDER, p.cBlue, {
        val ms = reminderRemainingMs()
        if (ms > 0) "Reminder in ${(ms + 59_999L) / 60_000L} min" else "Set Reminder"
    }) { showReminderInput() }
    add(OmarchyGlyph.NIGHT_LIGHT, p.cBlue, {
        if (isNightLightEnabled()) "Day Light" else "Night Light"
    }) {
        sudoIf(hasSecureSettings(), "Switch Night Light") { setNightLight(!isNightLightEnabled()) }
    }
    add(OmarchyGlyph.MUTED_BELL, p.cBlue, {
        if (isDoNotDisturbEnabled()) "Allow Notifications" else "Silence Notifications"
    }) { setDoNotDisturb(!isDoNotDisturbEnabled()) }
    add(OmarchyGlyph.COFFEE, p.cBlue, {
        if (isStayAwakeOn()) "Allow Idle Lock & Screensaver" else "Stay Awake"
    }) {
        sudoIf(Settings.System.canWrite(this), "Change the system screen timeout") {
            setStayAwake(!isStayAwakeOn())
        }
    }
    add(OmarchyGlyph.SUDO, red, {
        if (sudoActive()) "Sudo unlocked" else "Sudo locked"
    }) { showSudoPanel() }
    ScreenRecordService.listener = WeakReference(this)
    refreshOmarchyActionTray()
    return row
}

internal fun MainActivity.revealOmarchyActionTray() {
    val st = trayStates[this] ?: return
    st.expanded = true
    refreshOmarchyActionTray()
    scheduleOmarchyActionTrayCollapse(4200L)
}

internal fun MainActivity.scheduleOmarchyActionTrayCollapse(delay: Long) {
    val st = trayStates[this] ?: return
    st.collapse?.let { handler.removeCallbacks(it) }
    val callback = Runnable {
        st.expanded = false
        refreshOmarchyActionTray()
    }
    st.collapse = callback
    handler.postDelayed(callback, delay)
}

/**
 * Safe to call as often as you like (the status tick calls it every 2 s). It only animates an icon
 * when its target state actually changes; the old version cancelled running animations on every
 * call, which left icons stuck half-faded.
 */
internal fun MainActivity.refreshOmarchyActionTray() {
    val st = trayStates[this] ?: return
    val expanded = st.expanded
    val active = mapOf(
        OmarchyGlyph.RECORD to ScreenRecordService.recording,
        OmarchyGlyph.REMINDER to (reminderRemainingMs() > 0L),
        OmarchyGlyph.NIGHT_LIGHT to isNightLightEnabled(),
        OmarchyGlyph.MUTED_BELL to isDoNotDisturbEnabled(),
        OmarchyGlyph.COFFEE to isStayAwakeOn(),
        OmarchyGlyph.SUDO to sudoActive()
    )
    val order = st.icons.keys.toList()
    order.forEachIndexed { index, glyph ->
        val hit = st.icons[glyph] ?: return@forEachIndexed
        val isActive = active[glyph] == true
        val want = expanded || isActive
        if (st.shown[glyph] != want) {
            st.shown[glyph] = want
            if (want) {
                // Expanding reveals from the chevron outwards.
                val delay = if (expanded) (order.size - 1 - index) * 22L else 0L
                animateTrayIn(hit, delay)
            } else {
                animateTrayOut(hit) { st.shown[glyph] != true }
            }
        }
        hit.contentDescription = st.labels[glyph]?.invoke()
        val icon = (hit as? ViewGroup)?.getChildAt(0) as? OmarchyActionIconView
        icon?.active = isActive
        if (glyph == OmarchyGlyph.RECORD) icon?.pulsing = isActive
    }
}

private fun MainActivity.animateTrayIn(hit: View, delay: Long) {
    hit.animate().cancel()
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
        .setStartDelay(delay)
        .setDuration(190L)
        .start()
}

private fun MainActivity.animateTrayOut(hit: View, stillHidden: () -> Boolean) {
    hit.animate().cancel()
    hit.animate()
        .alpha(0f)
        .translationY(dp(3).toFloat())
        .scaleX(0.9f)
        .scaleY(0.9f)
        .setStartDelay(0L)
        .setDuration(145L)
        .withEndAction {
            if (stillHidden()) {
                hit.visibility = View.GONE
                hit.alpha = 1f
                hit.translationY = 0f
                hit.scaleX = 1f
                hit.scaleY = 1f
            }
        }
        .start()
}

private fun MainActivity.showOmarchyTooltip(anchor: View, label: String) {
    val st = trayStates[this] ?: return
    st.tooltipHide?.let { handler.removeCallbacks(it) }
    st.tooltip?.let { if (it.isShowing) it.dismiss() }
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
    st.tooltip = popup
    popup.showAsDropDown(anchor, 0, dp(3), Gravity.CENTER_HORIZONTAL)
    text.alpha = 0f
    text.translationY = -dp(3).toFloat()
    text.animate().alpha(1f).translationY(0f).setDuration(120L).start()
    val callback = Runnable {
        text.animate().alpha(0f).translationY(-dp(2).toFloat()).setDuration(180L)
            .withEndAction { if (popup.isShowing) popup.dismiss() }
            .start()
    }
    st.tooltipHide = callback
    handler.postDelayed(callback, 1450L)
}

// ---------------------------------------------------------------- Stay Awake

private const val KEY_AWAKE = "tray_stay_awake"
private const val KEY_PREV_TIMEOUT = "tray_previous_screen_timeout"

internal fun MainActivity.isStayAwakeOn(): Boolean = prefs.getBoolean(KEY_AWAKE, false)

private fun MainActivity.applyKeepScreenOn(on: Boolean) {
    if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}

/**
 * Stay Awake works in two tiers so the button never dead-ends:
 * - always: the launcher window keeps the screen on while Sorairo is in front;
 * - with "Modify system settings": Android's screen timeout is also raised, so it applies in every app.
 */
internal fun MainActivity.setStayAwake(on: Boolean) {
    val systemWide = Settings.System.canWrite(this)
    if (systemWide) {
        try {
            if (on) {
                val current = Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 60_000)
                if (current != Int.MAX_VALUE) prefs.edit().putInt(KEY_PREV_TIMEOUT, current).apply()
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, Int.MAX_VALUE)
            } else {
                val previous = prefs.getInt(KEY_PREV_TIMEOUT, 60_000).let { if (it <= 0 || it == Int.MAX_VALUE) 60_000 else it }
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, previous)
                prefs.edit().remove(KEY_PREV_TIMEOUT).apply()
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Android refused to change the system screen timeout", Toast.LENGTH_LONG).show()
        }
    }
    prefs.edit().putBoolean(KEY_AWAKE, on).apply()
    applyKeepScreenOn(on)
    if (on && !systemWide) {
        Toast.makeText(
            this,
            "Stay Awake is on while Sorairo is in front. Allow \"Modify system settings\" to cover every app.",
            Toast.LENGTH_LONG
        ).show()
    }
    refreshOmarchyActionTray()
}

/** Called from onResume: re-applies the window flag and notices if the timeout was changed elsewhere. */
internal fun MainActivity.applyTrayStayAwake() {
    var on = isStayAwakeOn()
    if (on && Settings.System.canWrite(this) && prefs.contains(KEY_PREV_TIMEOUT)) {
        val now = Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, Int.MAX_VALUE)
        if (now != Int.MAX_VALUE) {
            // Someone changed the timeout in Android settings: Stay Awake is no longer in force.
            on = false
            prefs.edit().putBoolean(KEY_AWAKE, false).remove(KEY_PREV_TIMEOUT).apply()
        }
    }
    applyKeepScreenOn(on)
}

// ---------------------------------------------------------------- Do Not Disturb

internal fun MainActivity.isDoNotDisturbEnabled(): Boolean = try {
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.isNotificationPolicyAccessGranted &&
        nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
} catch (_: Exception) { false }

internal fun MainActivity.setDoNotDisturb(on: Boolean) {
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (!nm.isNotificationPolicyAccessGranted) {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        } catch (_: Exception) { }
        Toast.makeText(this, "Allow Sorairo to manage Do Not Disturb, then tap again", Toast.LENGTH_LONG).show()
        return
    }
    try {
        if (on) {
            // Keep media, alarms and system sounds permitted so this silences notifications
            // without turning into a media-volume mute.
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

// ---------------------------------------------------------------- Night Light

internal fun MainActivity.isNightLightEnabled(): Boolean = try {
    Settings.Secure.getInt(contentResolver, "night_display_activated", 0) == 1
} catch (_: Exception) { false }

/** Switches Night Light directly with the adb grant, otherwise opens Android's Night Light settings. */
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
        "Switch Night Light in Android settings. Tap the sudo icon to see how to enable one-tap switching.",
        Toast.LENGTH_LONG
    ).show()
    return false
}

// ---------------------------------------------------------------- Screen recording

internal fun MainActivity.toggleScreenRecording() {
    if (ScreenRecordService.recording) stopScreenRecording() else startScreenRecording()
}

internal fun MainActivity.startScreenRecording() {
    if (ScreenRecordService.recording) return
    try {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureRequest.launch(mpm.createScreenCaptureIntent())
    } catch (_: Exception) {
        Toast.makeText(this, "This device can't start a screen recording", Toast.LENGTH_LONG).show()
    }
}

internal fun MainActivity.stopScreenRecording() {
    try {
        startService(Intent(this, ScreenRecordService::class.java).setAction(ScreenRecordService.ACTION_STOP))
    } catch (_: Exception) { }
}

/** Result of Android's "start recording?" consent screen. */
internal fun MainActivity.onScreenCaptureResult(resultCode: Int, data: Intent?) {
    if (resultCode != Activity.RESULT_OK || data == null) {
        Toast.makeText(this, "Screen recording cancelled", Toast.LENGTH_SHORT).show()
        return
    }
    var w: Int
    var h: Int
    if (Build.VERSION.SDK_INT >= 30) {
        val b = windowManager.currentWindowMetrics.bounds
        w = b.width()
        h = b.height()
    } else {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(dm)
        w = dm.widthPixels
        h = dm.heightPixels
    }
    // Keep the encoder comfortable: at most 1920 on the long side, even dimensions.
    val longSide = maxOf(w, h)
    if (longSide > 1920) {
        val s = 1920f / longSide
        w = (w * s).toInt()
        h = (h * s).toInt()
    }
    w = w / 2 * 2
    h = h / 2 * 2
    val intent = Intent(this, ScreenRecordService::class.java)
        .setAction(ScreenRecordService.ACTION_START)
        .putExtra(ScreenRecordService.EXTRA_CODE, resultCode)
        .putExtra(ScreenRecordService.EXTRA_DATA, data)
        .putExtra(ScreenRecordService.EXTRA_W, w)
        .putExtra(ScreenRecordService.EXTRA_H, h)
        .putExtra(ScreenRecordService.EXTRA_DPI, resources.displayMetrics.densityDpi)
    try {
        startForegroundService(intent)
    } catch (_: Exception) {
        Toast.makeText(this, "Couldn't start the recorder", Toast.LENGTH_LONG).show()
    }
}

// ---------------------------------------------------------------- Reminders

private const val REMINDER_REQUEST = 8801
private const val KEY_REMINDER_AT = "reminder_at"

private fun MainActivity.reminderIntent(): PendingIntent = PendingIntent.getBroadcast(
    this,
    REMINDER_REQUEST,
    Intent(this, ReminderReceiver::class.java).apply { action = ReminderReceiver.ACTION_FIRE },
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)

internal fun MainActivity.reminderRemainingMs(): Long =
    (prefs.getLong(KEY_REMINDER_AT, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

internal fun MainActivity.cancelReminder() {
    val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarm.cancel(reminderIntent())
    prefs.edit().remove(KEY_REMINDER_AT).apply()
    refreshOmarchyActionTray()
}

/** Sets a reminder. Returns true when it was scheduled; shows its own message otherwise. */
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
    val triggerAt = System.currentTimeMillis() + minutes * 60_000L
    val exactAllowed = Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()
    return try {
        // Without the exact-alarm permission fall back to an approximate alarm instead of a dead end.
        if (exactAllowed) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, reminderIntent())
        else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, reminderIntent())
        prefs.edit().putLong(KEY_REMINDER_AT, triggerAt).apply()
        Toast.makeText(
            this,
            if (exactAllowed) "Reminder set for $minutes minute(s)"
            else "Reminder set for about $minutes minute(s) (Android didn't allow exact alarms)",
            Toast.LENGTH_SHORT
        ).show()
        refreshOmarchyActionTray()
        true
    } catch (_: SecurityException) {
        Toast.makeText(this, "Android refused to schedule the reminder", Toast.LENGTH_LONG).show()
        false
    }
}

internal fun MainActivity.showReminderInput() {
    val p = pal
    val remaining = reminderRemainingMs()
    if (remaining > 0L) {
        val root = trayDialogRoot(p.cBg, p.cBlue).apply {
            addView(txt(13f, p.cBlue, "Reminder"))
            addView(txt(12f, p.cFg, "Rings in about ${(remaining + 59_999L) / 60_000L} min.").apply {
                setPadding(0, dp(9), 0, dp(4))
            })
            addView(trayDialogActions(
                "CLEAR" to { cancelReminder(); activeDialog?.dismiss() },
                "CLOSE" to { activeDialog?.dismiss() }
            ))
        }
        showTrayDialog(root, 400)
        return
    }
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
                val minutes = input.text.toString().toIntOrNull() ?: 0
                if (scheduleReminder(minutes)) activeDialog?.dismiss()
            }
        ))
    }
    showTrayDialog(root, 400)
}

// ---------------------------------------------------------------- shared dialog helpers

internal var MainActivity.activeDialog: Dialog?
    get() = trayDialogs[this]
    set(value) { trayDialogs[this] = value }
private val trayDialogs = WeakHashMap<MainActivity, Dialog>()

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
        addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        attributes = attributes.apply { dimAmount = 0.32f }
        setLayout(minOf(resources.displayMetrics.widthPixels - dp(32), dp(maxWidthDp)), ViewGroup.LayoutParams.WRAP_CONTENT)
        setGravity(Gravity.CENTER)
    }
    dialog.setOnDismissListener { if (activeDialog === dialog) activeDialog = null }
    dialog.show()
    dialog.window?.setLayout(minOf(resources.displayMetrics.widthPixels - dp(32), dp(maxWidthDp)), ViewGroup.LayoutParams.WRAP_CONTENT)
}
