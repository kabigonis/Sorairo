package com.kabigonis.sorairo

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import java.util.WeakHashMap

// The centre of the top bar holds three movable modules: the action tray, the clock and the
// world-clock globe. Long-press one and slide it to put it somewhere else (like Waybar in Omarchy);
// the others glide out of the way and the order is remembered.
//
// The clock always stays centred on the bar. Modules before it in the order sit to its left, modules
// after it sit to its right, so moving the clock past a module simply flips that module's side.

private const val ID_TRAY = "T"
private const val ID_CLOCK = "C"
private const val ID_GLOBE = "G"
private const val ORDER_KEY = "bar_center_order"
private val DEFAULT_ORDER = listOf(ID_TRAY, ID_CLOCK, ID_GLOBE)

private class CenterBar(val group: FrameLayout, val views: Map<String, View>) {
    var order: List<String> = DEFAULT_ORDER
    var dragId: String? = null
    var dragDownRaw = 0f
    var dragStartX = 0f
}

private val centerBars = WeakHashMap<MainActivity, CenterBar>()

private fun MainActivity.trayContentWidth(tray: LinearLayout): Float {
    var sum = 0
    for (i in 0 until tray.childCount) sum += tray.getChildAt(i).layoutParams?.width?.coerceAtLeast(0) ?: 0
    return sum.toFloat()
}

private fun MainActivity.slotWidth(c: CenterBar, id: String): Float = when (id) {
    ID_CLOCK -> c.views.getValue(id).width.toFloat()
    ID_GLOBE -> dp(30).toFloat()
    else -> trayContentWidth(c.views.getValue(ID_TRAY) as LinearLayout)
}

/** Space between two neighbouring boxes; negative because each box has invisible slack around its icon. */
private fun MainActivity.slotGap(a: String, b: String): Float =
    when (listOf(a, b).sorted().joinToString("")) {
        "CT" -> -dp(4).toFloat()
        "CG" -> -dp(8).toFloat()
        else -> -dp(3).toFloat()
    }

/** Left edge of each module's box (in the centre group's coordinates) for the given order. */
private fun MainActivity.slotTargets(c: CenterBar, order: List<String>): Map<String, Float> {
    val res = HashMap<String, Float>()
    val clockW = slotWidth(c, ID_CLOCK)
    val clockLeft = (c.group.width - clockW) / 2f
    val ci = order.indexOf(ID_CLOCK)
    res[ID_CLOCK] = clockLeft
    var cursor = clockLeft
    var prev = ID_CLOCK
    for (k in ci - 1 downTo 0) {
        val id = order[k]
        val right = cursor - slotGap(id, prev)
        val left = right - slotWidth(c, id)
        res[id] = left
        cursor = left
        prev = id
    }
    cursor = clockLeft + clockW
    prev = ID_CLOCK
    for (k in ci + 1 until order.size) {
        val id = order[k]
        val left = cursor + slotGap(id, prev)
        res[id] = left
        cursor = left + slotWidth(c, id)
        prev = id
    }
    return res
}

/**
 * Places every module (except the one being dragged). The tray row is wider than its glyphs so a tap
 * beside them reveals the options; its width is trimmed so it never covers a neighbouring module.
 */
internal fun MainActivity.layoutCenterBar(animate: Boolean = false) {
    val c = centerBars[this] ?: return
    if (c.group.width == 0) return
    val order = c.order
    val targets = slotTargets(c, order)
    val ci = order.indexOf(ID_CLOCK)
    for ((id, v) in c.views) {
        if (id == c.dragId) continue
        var x = targets.getValue(id)
        if (id == ID_TRAY) {
            val tray = v as LinearLayout
            val w = slotWidth(c, id)
            val i = order.indexOf(id)
            val onLeft = i < ci
            val maxZone = dp(168).toFloat()
            val neighbour = order.getOrNull(if (onLeft) i - 1 else i + 1)
            val room = when {
                neighbour == null -> maxZone - w
                onLeft -> targets.getValue(id) - (targets.getValue(neighbour) + slotWidth(c, neighbour))
                else -> targets.getValue(neighbour) - (targets.getValue(id) + w)
            }
            val zone = (w + room.coerceAtLeast(0f)).coerceIn(dp(22).toFloat(), maxZone).toInt()
            val lp = tray.layoutParams
            if (lp != null && lp.width != zone) {
                lp.width = zone
                tray.layoutParams = lp
            }
            val gravity = Gravity.CENTER_VERTICAL or (if (onLeft) Gravity.END else Gravity.START)
            if (tray.gravity != gravity) tray.gravity = gravity
            x = if (onLeft) targets.getValue(id) + w - zone else targets.getValue(id)
        }
        if (animate) {
            v.animate().translationX(x).setDuration(180L).start()
        } else if (v.translationX != x) {
            v.animate().cancel()
            v.translationX = x
        }
    }
}

private fun MainActivity.dragBoxCentre(c: CenterBar, id: String, viewX: Float): Float {
    val w = slotWidth(c, id)
    if (id != ID_TRAY) return viewX + w / 2f
    val zone = c.views.getValue(id).layoutParams.width.toFloat()
    val onLeft = c.order.indexOf(id) < c.order.indexOf(ID_CLOCK)
    val boxLeft = if (onLeft) viewX + zone - w else viewX
    return boxLeft + w / 2f
}

private fun MainActivity.startDrag(c: CenterBar, id: String, rawX: Float) {
    val v = c.views.getValue(id)
    c.dragId = id
    c.dragDownRaw = rawX
    c.dragStartX = v.translationX
    v.parent?.requestDisallowInterceptTouchEvent(true)
    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    v.animate().cancel()
    v.animate().scaleX(1.08f).scaleY(1.08f).alpha(0.92f).setDuration(120L).start()
    v.translationZ = dp(4).toFloat()
    if (id != ID_TRAY) {
        v.foreground = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(dp(1), pal.cDim)
        }
    }
}

private fun MainActivity.moveDrag(c: CenterBar, rawX: Float) {
    val id = c.dragId ?: return
    val v = c.views.getValue(id)
    val limit = c.group.width.toFloat()
    val x = (c.dragStartX + rawX - c.dragDownRaw).coerceIn(-limit, limit)
    v.translationX = x
    val centre = dragBoxCentre(c, id, x)
    val targets = slotTargets(c, c.order)
    val newOrder = c.order
        .map { it to (if (it == id) centre else targets.getValue(it) + slotWidth(c, it) / 2f) }
        .sortedBy { it.second }
        .map { it.first }
    if (newOrder != c.order) {
        c.order = newOrder
        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        layoutCenterBar(animate = true)
    }
}

private fun MainActivity.endDrag(c: CenterBar) {
    val id = c.dragId ?: return
    val v = c.views.getValue(id)
    c.dragId = null
    v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(140L).start()
    v.translationZ = 0f
    v.foreground = null
    prefs.edit().putString(ORDER_KEY, c.order.joinToString(",")).apply()
    layoutCenterBar(animate = true)
}

private fun MainActivity.attachDrag(c: CenterBar, id: String, v: View) {
    var lastRaw = 0f
    v.setOnLongClickListener {
        startDrag(c, id, lastRaw)
        true
    }
    v.setOnTouchListener { _, ev ->
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastRaw = ev.rawX
                false
            }
            MotionEvent.ACTION_MOVE -> {
                lastRaw = ev.rawX
                if (c.dragId == id) { moveDrag(c, ev.rawX); true } else false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (c.dragId == id) { endDrag(c); true } else false
            }
            else -> false
        }
    }
}

/**
 * Wires the tray row, the clock and the globe into the movable centre group. Every module must
 * already be a child of [group] with START | CENTER_VERTICAL gravity; this positions them.
 */
internal fun MainActivity.setupCenterBar(group: FrameLayout, tray: LinearLayout, globe: View) {
    val views = linkedMapOf<String, View>(ID_TRAY to tray, ID_CLOCK to clock, ID_GLOBE to globe)
    val c = CenterBar(group, views)
    val saved = prefs.getString(ORDER_KEY, null)?.split(",").orEmpty()
    if (saved.size == 3 && saved.toSet() == DEFAULT_ORDER.toSet()) c.order = saved
    centerBars[this] = c
    views.forEach { (id, v) -> attachDrag(c, id, v) }
    group.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> layoutCenterBar() }
    clock.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> layoutCenterBar() }
    group.post { layoutCenterBar() }
}
