package com.kabigonis.sorairo

import android.content.Context
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.SystemClock
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import java.util.WeakHashMap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Top bar (Waybar) status updates, workspace indicator and Dynamic Island glue.
// Moved verbatim from MainActivity.kt as extension functions on MainActivity.

internal const val BAR_TRANSPARENT = 0
internal const val BAR_TRANSLUCENT = 1
internal const val BAR_SOLID = 2

private val topBarViews = WeakHashMap<MainActivity, FrameLayout>()

/** The saved bar mode; light themes default to translucent because a clear bar is unreadable on them. */
internal fun MainActivity.barMode(): Int =
    prefs.getInt("bar_mode", if (isLightTheme(pal)) BAR_TRANSLUCENT else BAR_TRANSPARENT)
        .coerceIn(BAR_TRANSPARENT, BAR_SOLID)

private fun MainActivity.barColorFor(mode: Int): Int {
    val rgb = pal.cBg and 0x00FFFFFF
    return when (mode) {
        BAR_SOLID -> rgb or (0xFF shl 24)
        BAR_TRANSLUCENT -> rgb or (0x99 shl 24)
        else -> Color.TRANSPARENT
    }
}

/** Registers the bar, applies the saved mode and makes a double tap on its empty space cycle the modes. */
internal fun MainActivity.setupTopBar(bar: FrameLayout) {
    topBarViews[this] = bar
    bar.background = ColorDrawable(barColorFor(barMode()))
    val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onDoubleTap(e: MotionEvent): Boolean {
            cycleBarMode()
            return true
        }
    })
    bar.setOnTouchListener { _, ev -> detector.onTouchEvent(ev) }
}

/** Transparent -> translucent -> solid (theme colour) -> transparent, like Omarchy's Waybar. */
internal fun MainActivity.cycleBarMode() {
    prefs.edit().putInt("bar_mode", (barMode() + 1) % 3).apply()
    val bar = topBarViews[this] ?: return
    val from = (bar.background as? ColorDrawable)?.color ?: Color.TRANSPARENT
    val to = barColorFor(barMode())
    ValueAnimator.ofArgb(from, to).apply {
        duration = 220L
        addUpdateListener { bar.background = ColorDrawable(it.animatedValue as Int) }
        start()
    }
    restyleOmarchyActionTray()
}

internal fun MainActivity.updateWsBar() {
    wsLabels.forEachIndexed { i, t ->
        if (i == curWs) {
            t.text = "●"
            t.setTextColor(pal.cFg)
        } else {
            t.text = "${i + 1}"
            t.setTextColor(pal.cDim)
        }
    }
}

internal fun MainActivity.toggleIsland() {
    val now = SystemClock.elapsedRealtime()
    val current = islandCore.snapshot(now).current
    if (current == null) {
        islandEmptyExpanded = !islandEmptyExpanded
        islandCore.tap(now)
    } else {
        islandEmptyExpanded = false
        islandCore.tap(now)
    }
    refreshIsland()
}

internal fun MainActivity.refreshIsland() {
    // Dynamic Island is disabled for this build. Keep the normal clock visible.
    clock.visibility = View.VISIBLE
    handler.removeCallbacks(islandWake)
}

internal fun MainActivity.updateStatus() {
    clock.text = SimpleDateFormat("EEEE h:mm a", Locale.getDefault())
        .format(Date()).replace("AM", "am").replace("PM", "pm")
    refreshIsland()

    val now = SystemClock.elapsedRealtime()
    val rx = TrafficStats.getTotalRxBytes()
    val tx = TrafficStats.getTotalTxBytes()
    if (lastT == 0L) {
        lastT = now; lastRx = rx; lastTx = tx
    } else if (now - lastT >= 1000 && rx >= 0 && tx >= 0) {
        val dt = now - lastT
        downBps = ((rx - lastRx) * 1000 / dt).coerceAtLeast(0)
        upBps = ((tx - lastTx) * 1000 / dt).coerceAtLeast(0)
        lastT = now; lastRx = rx; lastTx = tx
    }

    val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    status.text = headphones()?.let { "♪ $it" } ?: ""
    status.visibility = if (status.text.isEmpty()) View.GONE else View.VISIBLE
    sampleBatteryCurrent(bm)
    refreshBarIcons(bm)
    refreshOmarchyActionTray()
    updateNowPlaying()
}
