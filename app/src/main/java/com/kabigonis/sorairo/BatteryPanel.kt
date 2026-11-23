package com.kabigonis.sorairo

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

// Omarchy-style battery panel. Tap the battery % in the top bar (or run `battery`).

// ---- pure helpers (unit tested) ----

/** Current magnitude in µA. Some devices report mA instead; values that small are scaled up. */
internal fun currentMicroamps(raw: Long): Float? {
    if (raw == 0L || raw == Long.MIN_VALUE || raw == Int.MIN_VALUE.toLong()) return null
    return if (abs(raw) < 20_000L) abs(raw) * 1000f else abs(raw).toFloat()
}

internal fun batteryPowerFromUa(voltageMv: Int, ua: Float): Float? {
    if (voltageMv <= 0 || ua <= 0f) return null
    return ua / 1_000_000f * (voltageMv / 1000f)
}

/** Power in watts from voltage (mV) and the raw current reading. */
internal fun batteryPowerW(voltageMv: Int, currentRaw: Long): Float? =
    currentMicroamps(currentRaw)?.let { batteryPowerFromUa(voltageMv, it) }

/** Estimated full-charge energy in Wh from the charge counter (µAh) at the current percentage. */
internal fun batterySizeWh(chargeCounterUah: Long, pct: Int, voltageMv: Int): Float? {
    if (chargeCounterUah <= 0L || pct < 5 || voltageMv <= 0) return null
    val fullUah = chargeCounterUah / (pct / 100f)
    return fullUah / 1_000_000f * (voltageMv / 1000f)
}

internal fun dischargeMinutesFromUa(chargeCounterUah: Long, ua: Float): Int? {
    if (chargeCounterUah <= 0L || ua <= 0f) return null
    val minutes = chargeCounterUah / ua * 60f
    return if (minutes.isFinite() && minutes < 100_000f) minutes.roundToInt() else null
}

/** Minutes left while discharging, from remaining charge (µAh) and the raw current reading. */
internal fun dischargeMinutes(chargeCounterUah: Long, currentRaw: Long): Int? =
    currentMicroamps(currentRaw)?.let { dischargeMinutesFromUa(chargeCounterUah, it) }

internal fun formatMinutes(minutes: Int): String =
    if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"

/** Time-left is an estimate, so show it in 5-minute steps instead of flickering every minute. */
internal fun roundMinutesForDisplay(minutes: Int): Int =
    if (minutes >= 20) ((minutes + 2) / 5) * 5 else minutes

/** Time-weighted exponential moving average: spikes are damped, slow trends still come through. */
internal fun smoothValue(previous: Float?, sample: Float, dtMs: Long, tauMs: Long = 45_000L): Float {
    if (previous == null || dtMs <= 0L) return sample
    val alpha = 1f - exp(-dtMs.toFloat() / tauMs.toFloat())
    return previous + alpha * (sample - previous)
}

// ---- smoothed current draw, fed by the top bar's status loop so it is warm when the panel opens ----

private var smoothedUa: Float? = null
private var smoothedAt = 0L
private var smoothedCharging: Boolean? = null

internal fun sampleBatteryCurrent(bm: BatteryManager) {
    val charging = bm.isCharging
    val ua = currentMicroamps(bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)) ?: return
    val now = SystemClock.elapsedRealtime()
    val stale = smoothedUa == null || now - smoothedAt > 120_000L || smoothedCharging != charging
    smoothedUa = if (stale) ua else smoothValue(smoothedUa, ua, now - smoothedAt)
    smoothedAt = now
    smoothedCharging = charging
}

/** Subtitle under "Battery". Mirrors Omarchy: "SPENDING JOULES" while running on the battery. */
internal fun batteryStateLabel(status: Int, plugged: Boolean): String = when {
    status == BatteryManager.BATTERY_STATUS_FULL -> "FULL"
    status == BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
    plugged -> "PLUGGED IN"
    else -> "SPENDING JOULES"
}

private fun healthName(code: Int) = when (code) {
    BatteryManager.BATTERY_HEALTH_GOOD -> "good"
    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheating"
    BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over voltage"
    BatteryManager.BATTERY_HEALTH_COLD -> "cold"
    else -> "unknown"
}

/** The optional one-time adb command that lets the panel switch Battery Saver itself (for power users). */
internal fun MainActivity.batterySaverGrantCommand(): String =
    "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"

/** Battery Saver can only be switched directly if the user granted WRITE_SECURE_SETTINGS over adb. */
internal fun MainActivity.canSetBatterySaver(): Boolean =
    checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

internal fun MainActivity.setBatterySaver(on: Boolean): Boolean {
    if (!canSetBatterySaver()) return false
    return try {
        Settings.Global.putInt(contentResolver, "low_power", if (on) 1 else 0)
    } catch (_: Exception) {
        false
    }
}

internal fun MainActivity.showBatteryPanel() {
    val p = pal
    val evaluator = ArgbEvaluator()
    var dialog: AlertDialog? = null

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(18), dp(20), dp(16))
        background = box(p.cBg or (0xFF shl 24), p.cBlue)
    }

    // ---- built once; render() only updates values so nothing flashes or resets ----
    val stateText = txt(10f, p.cDim, "")
    val pctText = txt(32f, p.cBlue, "").apply { setTypeface(typeface, Typeface.BOLD) }
    val head = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val battIcon = BatteryIconView(this, p.cBlue, p.cBg or (0xFF shl 24))
    head.addView(battIcon, LinearLayout.LayoutParams(dp(24), dp(32)).apply { marginEnd = dp(14) })
    val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    titles.addView(txt(17f, p.cFg, "Battery").apply { setTypeface(typeface, Typeface.BOLD) })
    titles.addView(stateText)
    head.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
    head.addView(pctText)
    root.addView(head)

    val fillBar = View(this).apply { setBackgroundColor(p.cBlue); pivotX = 0f; scaleX = 0f }
    val track = FrameLayout(this).apply {
        setBackgroundColor(p.cDim and 0x00FFFFFF or (0x55 shl 24))
        addView(fillBar, FrameLayout.LayoutParams(-1, -1))
    }
    root.addView(track, LinearLayout.LayoutParams(-1, dp(5)).apply { topMargin = dp(14); bottomMargin = dp(14) })

    fun statRow(label: String, col: LinearLayout): TextView {
        val value = txt(12f, p.cFg, "--").apply { gravity = Gravity.END }
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(txt(12f, p.cDim, label), LinearLayout.LayoutParams(0, -2, 1f))
            addView(value)
        })
        return value
    }
    val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val sizeV = statRow("Battery size", left)
    val cyclesV = statRow("Charge cycles", left)
    val healthV = statRow("Health", left)
    val leftV = statRow("Time left", right)
    val drawLabel = txt(12f, p.cDim, "Discharging")
    val drawV = txt(12f, p.cFg, "--").apply { gravity = Gravity.END }
    right.addView(LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(drawLabel, LinearLayout.LayoutParams(0, -2, 1f))
        addView(drawV)
    })
    val tempV = statRow("Temperature", right)
    root.addView(LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(left, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(14) })
        addView(right, LinearLayout.LayoutParams(0, -2, 1f))
    })

    root.addView(txt(10f, p.cDim, "POWER PROFILE").apply { setPadding(0, dp(16), 0, dp(6)) })

    // profile buttons: colours cross-fade instead of snapping
    class Btn(val tv: TextView, val bg: GradientDrawable, var active: Boolean? = null)
    fun makeBtn(name: String, onClick: () -> Unit): Btn {
        val bg = GradientDrawable().apply {
            setColor(p.cSel and 0x00FFFFFF)
            setStroke(dp(2), p.cDim)
        }
        val tv = txt(13f, p.cFg, name).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), dp(10))
            background = bg
            setOnClickListener { onClick() }
        }
        return Btn(tv, bg)
    }
    fun style(b: Btn, active: Boolean, animate: Boolean) {
        if (b.active == active) return
        val first = b.active == null
        b.active = active
        val fill0 = if (active) p.cSel and 0x00FFFFFF else p.cSel
        val fill1 = if (active) p.cSel else p.cSel and 0x00FFFFFF
        val stroke1 = if (active) p.cBlue else p.cDim
        val stroke0 = if (active) p.cDim else p.cBlue
        val text1 = if (active) onFill(p.cFg, p.cSel) else p.cFg
        val text0 = if (active) p.cFg else onFill(p.cFg, p.cSel)
        fun paint(f: Float) {
            b.bg.setColor(evaluator.evaluate(f, fill0, fill1) as Int)
            b.bg.setStroke(dp(2), evaluator.evaluate(f, stroke0, stroke1) as Int)
            b.tv.setTextColor(evaluator.evaluate(f, text0, text1) as Int)
        }
        if (first || !animate) {
            paint(1f)
        } else {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 200L
                addUpdateListener { paint(it.animatedValue as Float) }
                start()
            }
        }
    }

    lateinit var render: (Boolean) -> Unit
    val saverBtn = makeBtn("Power-saver") { }
    val balancedBtn = makeBtn("Balanced") { }
    fun switchTo(on: Boolean) {
        val current = (getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
        if (current == on) return
        if (setBatterySaver(on)) {
            style(saverBtn, on, true)       // respond instantly...
            style(balancedBtn, !on, true)
            handler.postDelayed({ if (dialog?.isShowing == true) render(true) }, 700) // ...then confirm
        } else {
            Toast.makeText(this, "Opening Battery Saver settings", Toast.LENGTH_SHORT).show()
            try {
                startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
            }
        }
    }
    saverBtn.tv.setOnClickListener { switchTo(true) }
    balancedBtn.tv.setOnClickListener { switchTo(false) }
    root.addView(LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(saverBtn.tv, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
        addView(balancedBtn.tv, LinearLayout.LayoutParams(0, -2, 1f))
    })
    // Normal users never see adb instructions: without the optional grant the buttons simply open Android's settings.
    val hint = txt(10f, p.cDim, "Tap a profile to open Android's Battery Saver settings")
        .apply { setPadding(0, dp(8), 0, 0) }
    root.addView(hint)

    render = { animate ->
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        sampleBatteryCurrent(bm)
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        // some chargers report "not charging" while plugged in, so look at the plug as well
        val plugged = (sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val charging = bm.isCharging || plugged
        val full = status == BatteryManager.BATTERY_STATUS_FULL
        val mv = sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val tempC = (sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE)
            .takeIf { it != Int.MIN_VALUE }?.div(10f)
        val health = healthName(sticky?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0) ?: 0)
        val counter = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        // EXTRA_CYCLE_COUNT exists from API 34; read by its key so older SDKs just get -1.
        val cycles = (sticky?.getIntExtra("android.os.extra.CYCLE_COUNT", -1) ?: -1).takeIf { it >= 0 }
        val ua = smoothedUa
        val watts = ua?.let { batteryPowerFromUa(mv, it) }
        val sizeWh = batterySizeWh(counter, pct, mv)
        val minutesLeft: Int? = when {
            full -> null
            charging -> if (Build.VERSION.SDK_INT >= 28) {
                bm.computeChargeTimeRemaining().takeIf { it > 0 }?.let { (it / 60_000L).toInt() }
            } else null
            else -> ua?.let { dischargeMinutesFromUa(counter, it) }
        }

        stateText.text = batteryStateLabel(status, plugged)
        battIcon.update(pct / 100f, charging)
        pctText.text = "$pct%"
        val target = pct / 100f
        if (animate && root.isAttachedToWindow) fillBar.animate().scaleX(target).setDuration(350).start()
        else { fillBar.animate().cancel(); fillBar.scaleX = target }
        sizeV.text = sizeWh?.let { "~${it.roundToInt()}Wh" } ?: "n/a"
        cyclesV.text = cycles?.toString() ?: "n/a"
        healthV.text = health
        leftV.text = if (full) "full" else minutesLeft?.let { formatMinutes(roundMinutesForDisplay(it)) } ?: "--"
        drawLabel.text = if (charging) "Charging" else "Discharging"
        drawV.text = watts?.let { "%.1fW".format(it) } ?: "n/a"
        tempV.text = tempC?.let { "%.1f\u00B0C".format(it) } ?: "n/a"

        val saver = (getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
        style(saverBtn, saver, animate)
        style(balancedBtn, !saver, animate)
        hint.visibility = if (canSetBatterySaver()) View.GONE else View.VISIBLE
    }

    render(false)
    val tick = object : Runnable {
        override fun run() {
            if (dialog?.isShowing == true) {
                render(true)
                handler.postDelayed(this, 2000)
            }
        }
    }
    dialog = AlertDialog.Builder(this).create().apply {
        setView(root)
        setOnDismissListener { handler.removeCallbacks(tick) }
        show()
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setLayout(minOf(resources.displayMetrics.widthPixels - dp(40), dp(440)), -2)
    }
    anchorDialogToTop(dialog, Gravity.END, dp(12))
    handler.postDelayed(tick, 2000)
}

/** Explains the optional adb grant (Settings > Permissions). Normal users can ignore it entirely. */
internal fun MainActivity.showBatterySaverGrantHelp() {
    val p = pal
    val cmd = batterySaverGrantCommand()
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(14))
        background = box(p.cBg or (0xFF shl 24), p.cBlue)
    }
    root.addView(txt(15f, p.cBlue, "~/battery-saver-switch"))
    root.addView(txt(
        12f, p.cFg,
        "Optional, for power users. Android doesn't let apps switch Battery Saver by themselves, so this " +
            "one-time grant lets the battery panel do it in one tap. Without it, the Power-saver button " +
            "just opens Android's Battery Saver settings."
    ).apply { setPadding(0, dp(8), 0, dp(10)) })
    root.addView(txt(11f, p.cDim, "Turn on Developer options > Wireless debugging, pair adb, then run once:"))
    root.addView(txt(11f, p.cFg, cmd).apply {
        setPadding(dp(10), dp(8), dp(10), dp(8))
        background = box(p.cSel, p.cDim)
        setTextIsSelectable(true)
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    root.addView(txt(10f, p.cDim, "Undo any time: adb shell pm revoke $packageName android.permission.WRITE_SECURE_SETTINGS")
        .apply { setPadding(0, dp(8), 0, dp(10)) })
    var dialog: AlertDialog? = null
    val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    actions.addView(txt(13f, p.cAccent, "COPY COMMAND").apply {
        setPadding(0, dp(8), dp(18), dp(8))
        setOnClickListener {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("adb command", cmd))
            Toast.makeText(this@showBatterySaverGrantHelp, "Copied", Toast.LENGTH_SHORT).show()
        }
    })
    actions.addView(txt(13f, p.cDim, "CLOSE").apply {
        setPadding(0, dp(8), 0, dp(8))
        setOnClickListener { dialog?.dismiss() }
    })
    root.addView(actions)
    dialog = AlertDialog.Builder(this).create().apply {
        setView(root)
        show()
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setLayout(minOf(resources.displayMetrics.widthPixels - dp(40), dp(460)), -2)
    }
}
