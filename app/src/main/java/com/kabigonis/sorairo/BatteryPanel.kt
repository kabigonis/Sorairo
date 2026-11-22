package com.kabigonis.sorairo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlin.math.abs
import kotlin.math.roundToInt

// Omarchy-style battery panel. Tap the battery % in the top bar (or run `battery`).

// ---- pure helpers (unit tested) ----

/** Power in watts from voltage (mV) and current. Some devices report mA instead of µA; values that small are scaled. */
internal fun batteryPowerW(voltageMv: Int, currentRaw: Long): Float? {
    if (voltageMv <= 0 || currentRaw == 0L || currentRaw == Long.MIN_VALUE || currentRaw == Int.MIN_VALUE.toLong()) return null
    val ua = if (abs(currentRaw) < 20_000L) abs(currentRaw) * 1000f else abs(currentRaw).toFloat()
    return ua / 1_000_000f * (voltageMv / 1000f)
}

/** Estimated full-charge energy in Wh from the charge counter (µAh) at the current percentage. */
internal fun batterySizeWh(chargeCounterUah: Long, pct: Int, voltageMv: Int): Float? {
    if (chargeCounterUah <= 0L || pct < 5 || voltageMv <= 0) return null
    val fullUah = chargeCounterUah / (pct / 100f)
    return fullUah / 1_000_000f * (voltageMv / 1000f)
}

/** Minutes left while discharging, from remaining charge (µAh) and current draw. */
internal fun dischargeMinutes(chargeCounterUah: Long, currentRaw: Long): Int? {
    if (chargeCounterUah <= 0L || currentRaw == 0L || currentRaw == Long.MIN_VALUE || currentRaw == Int.MIN_VALUE.toLong()) return null
    val ua = if (abs(currentRaw) < 20_000L) abs(currentRaw) * 1000f else abs(currentRaw).toFloat()
    val minutes = chargeCounterUah / ua * 60f
    return if (minutes.isFinite() && minutes < 100_000f) minutes.roundToInt() else null
}

internal fun formatMinutes(minutes: Int): String =
    if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"

private fun healthName(code: Int) = when (code) {
    BatteryManager.BATTERY_HEALTH_GOOD -> "good"
    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheating"
    BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over voltage"
    BatteryManager.BATTERY_HEALTH_COLD -> "cold"
    else -> "unknown"
}

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
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(18), dp(20), dp(16))
        background = box(p.cBg or (0xFF shl 24), p.cBlue)
    }
    var dialog: AlertDialog? = null

    fun stat(label: String, value: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(txt(12f, p.cDim, label), LinearLayout.LayoutParams(0, -2, 1f))
        addView(txt(12f, p.cFg, value).apply { gravity = Gravity.END })
    }

    fun fill() {
        root.removeAllViews()
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        val charging = bm.isCharging
        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val full = status == BatteryManager.BATTERY_STATUS_FULL
        val mv = sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val tempC = (sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE)
            .takeIf { it != Int.MIN_VALUE }?.div(10f)
        val health = healthName(sticky?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0) ?: 0)
        val counter = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val current = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        // EXTRA_CYCLE_COUNT exists from API 34; read by its key so older SDKs just get -1.
        val cycles = (sticky?.getIntExtra("android.os.extra.CYCLE_COUNT", -1) ?: -1).takeIf { it >= 0 }
        val watts = batteryPowerW(mv, current)
        val sizeWh = batterySizeWh(counter, pct, mv)
        val minutesLeft: Int? = when {
            full -> null
            charging -> if (Build.VERSION.SDK_INT >= 28) {
                bm.computeChargeTimeRemaining().takeIf { it > 0 }?.let { (it / 60_000L).toInt() }
            } else null
            else -> dischargeMinutes(counter, current)
        }

        // header: title + state, big percentage on the right
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(txt(26f, p.cBlue, "\u25AE").apply { setPadding(0, 0, dp(12), 0) })
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(txt(17f, p.cFg, "Battery").apply { setTypeface(typeface, Typeface.BOLD) })
        titles.addView(txt(10f, p.cDim, when {
            full -> "FULL"
            charging -> "CHARGING"
            else -> "DISCHARGING"
        }))
        head.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(txt(32f, p.cBlue, "$pct%").apply { setTypeface(typeface, Typeface.BOLD) })
        root.addView(head)

        // charge bar
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bar.addView(View(this).apply { setBackgroundColor(p.cBlue) }, LinearLayout.LayoutParams(0, dp(5), pct.toFloat().coerceAtLeast(0.01f)))
        bar.addView(
            View(this).apply { setBackgroundColor(p.cDim and 0x00FFFFFF or (0x55 shl 24)) },
            LinearLayout.LayoutParams(0, dp(5), (100 - pct).toFloat().coerceAtLeast(0.01f))
        )
        root.addView(bar, LinearLayout.LayoutParams(-1, dp(5)).apply { topMargin = dp(14); bottomMargin = dp(14) })

        // stats, two columns
        val cols = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(stat("Battery size", sizeWh?.let { "~${it.roundToInt()}Wh" } ?: "n/a"))
        left.addView(stat("Charge cycles", cycles?.toString() ?: "n/a"))
        left.addView(stat("Health", health))
        right.addView(stat("Time left", if (full) "full" else minutesLeft?.let { formatMinutes(it) } ?: "--"))
        right.addView(stat(if (charging) "Charging" else "Discharging", watts?.let { "%.1fW".format(it) } ?: "n/a"))
        right.addView(stat("Temperature", tempC?.let { "%.1f\u00B0C".format(it) } ?: "n/a"))
        cols.addView(left, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(14) })
        cols.addView(right, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(cols)

        // power profile. Android only has Battery Saver, so there are two profiles.
        val saver = (getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
        root.addView(txt(10f, p.cDim, "POWER PROFILE").apply { setPadding(0, dp(16), 0, dp(6)) })
        val profiles = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun profile(name: String, active: Boolean, onClick: () -> Unit): TextView =
            txt(13f, if (active) onFill(p.cFg, p.cSel) else p.cFg, name).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(10), dp(8), dp(10))
                background = box(if (active) p.cSel else android.graphics.Color.TRANSPARENT, if (active) p.cBlue else p.cDim)
                setOnClickListener { onClick() }
            }
        fun switchTo(on: Boolean) {
            if (saver == on) return
            if (setBatterySaver(on)) {
                handler.postDelayed({ if (dialog?.isShowing == true) fill() }, 500)
            } else {
                Toast.makeText(this, "Opening Battery Saver settings", Toast.LENGTH_SHORT).show()
                try {
                    startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                }
            }
        }
        profiles.addView(profile("Power-saver", saver) { switchTo(true) }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
        profiles.addView(profile("Balanced", !saver) { switchTo(false) }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(profiles)
        if (!canSetBatterySaver()) {
            root.addView(txt(10f, p.cDim, "To switch it from here, run once over adb:\nadb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS")
                .apply { setPadding(0, dp(8), 0, 0) })
        }
    }

    fill()
    val tick = object : Runnable {
        override fun run() {
            if (dialog?.isShowing == true) {
                fill()
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
