package com.kabigonis.sorairo

import android.content.Context
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.SystemClock
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Top bar (Waybar) status updates, workspace indicator and Dynamic Island glue.
// Moved verbatim from MainActivity.kt as extension functions on MainActivity.

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
