package com.kabigonis.sorairo

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
    if (!clockInitialized) return
    val enabled = prefs.getBoolean("dynamic_island", true)
    val view = islandView
    clock.visibility = if (enabled) View.GONE else View.VISIBLE
    view?.visibility = if (enabled) View.VISIBLE else View.GONE
    handler.removeCallbacks(islandWake)
    if (!enabled || view == null) return
    val now = SystemClock.elapsedRealtime()
    val snapshot = islandCore.snapshot(now)
    if (snapshot.current != null) islandEmptyExpanded = false
    val date = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
    view.render(snapshot, clock.text.toString(), date, islandEmptyExpanded)
    snapshot.nextWakeAt?.let { wakeAt ->
        handler.postDelayed(islandWake, (wakeAt - now).coerceAtLeast(0L))
    }
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

    val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
    val parts = ArrayList<String>()
    headphones()?.let { parts.add("♪ $it") }
    val net = netLabel()
    parts.add(net)
    status.text = parts.joinToString("   ")
    battText.text = "▮ ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%"
    updateNowPlaying()
}
