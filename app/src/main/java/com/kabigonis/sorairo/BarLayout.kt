package com.kabigonis.sorairo

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.TextView
import java.util.WeakHashMap
import kotlin.math.abs

// The top bar is a set of movable modules, like Waybar's modules-left / modules-center / modules-right.
// Long-press ANY module and slide it: guide lines show the three zones and the centre line, the other
// modules glide out of the way, and the layout is remembered. In the centre zone the clock stays exactly
// centred and the other centre modules hang off it; without a clock the centre group is centred as a whole.

internal const val MOD_WS = "ws"
internal const val MOD_MUSIC = "music"
internal const val MOD_TRAY = "tray"
internal const val MOD_CLOCK = "clock"
internal const val MOD_GLOBE = "globe"
internal const val MOD_STATUS = "status"
internal const val MOD_WIFI = "wifi"
internal const val MOD_AUDIO = "audio"
internal const val MOD_DISPLAY = "display"
internal const val MOD_BATTERY = "battery"

private const val ZONE_LEFT = 0
private const val ZONE_CENTER = 1
private const val ZONE_RIGHT = 2
private const val LAYOUT_KEY = "bar_layout_v2"

/** Half-width (dp) of the centre zone: a module dropped this close to the centre line joins the centre. */
private const val SNAP_DP = 120

private fun defaultZones(): List<MutableList<String>> = listOf(
    mutableListOf(MOD_WS, MOD_MUSIC),
    mutableListOf(MOD_TRAY, MOD_CLOCK, MOD_GLOBE),
    mutableListOf(MOD_STATUS, MOD_WIFI, MOD_AUDIO, MOD_DISPLAY, MOD_BATTERY)
)

private val waybars = WeakHashMap<MainActivity, WaybarLayout>()

/** Puts every top-bar module back where it started. */
internal fun MainActivity.resetBarLayout() {
    waybars[this]?.resetLayout()
}

internal class WaybarLayout(private val act: MainActivity) : FrameLayout(act) {
    private val modules = LinkedHashMap<String, View>()
    private var zones: List<MutableList<String>> = defaultZones()
    private val overlay = GuideOverlay()

    private var dragId: String? = null
    private var dragStartTx = 0f
    private var dragDownX = 0f
    private var activeZone = ZONE_CENTER
    private var pendingId: String? = null
    private var downTime = 0L
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var settleUntil = 0L
    private var trayGrowLeft = true
    private val slop = ViewConfiguration.get(act).scaledTouchSlop
    private val longPress = Runnable { pendingId?.let { startDrag(it) } }

    private var elastic: Pair<String, TextView>? = null

    /** Marks a text inside module [id] that may shrink (and end with "...") so it never runs under another zone. */
    fun setElastic(id: String, text: TextView) {
        elastic = id to text
    }

    fun addModule(id: String, view: View, w: Int = LayoutParams.WRAP_CONTENT, h: Int = LayoutParams.WRAP_CONTENT) {
        modules[id] = view
        addView(view, LayoutParams(w, h, Gravity.START or Gravity.CENTER_VERTICAL))
    }

    /** Call once every module is added: restores the saved layout and adds the guide-line overlay. */
    fun finishSetup() {
        loadLayout()
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        overlay.visibility = View.GONE
        applyTrayDirection(force = true)
        waybars[act] = this
        requestLayout()
    }

    fun resetLayout() {
        zones = defaultZones()
        act.prefs.edit().remove(LAYOUT_KEY).apply()
        applyTrayDirection()
        place(true)
        requestLayout()
    }

    // ---- saved layout ----

    private fun serialize(): String =
        zones.mapIndexed { i, l -> "${"LCR"[i]}:${l.joinToString(",")}" }.joinToString("|")

    private fun loadLayout() {
        val saved = act.prefs.getString(LAYOUT_KEY, null) ?: return
        try {
            val parts = saved.split("|")
            if (parts.size != 3) return
            val lists = parts.mapIndexed { i, p ->
                if (!p.startsWith("${"LCR"[i]}:")) return
                p.substring(2).split(",").filter { it.isNotEmpty() }.toMutableList()
            }
            val all = lists.flatten()
            if (all.size == modules.size && all.toSet() == modules.keys) zones = lists
        } catch (_: Exception) {
        }
    }

    // ---- geometry ----

    private fun dp(v: Int): Float = act.dp(v).toFloat()

    private fun present(id: String): Boolean {
        val v = modules[id] ?: return false
        return v.visibility != View.GONE && v.width > 0
    }

    private fun widthOf(id: String): Float = if (present(id)) modules.getValue(id).width.toFloat() else 0f

    /** Space between the right edge of [a] and the left edge of its right-hand neighbour [b]; negative = overlap
     *  (the clock, tray and globe boxes carry invisible slack around their glyphs). */
    private fun gap(a: String, b: String): Float = when {
        (a == MOD_CLOCK && b == MOD_TRAY) || (a == MOD_TRAY && b == MOD_CLOCK) -> -dp(4)
        (a == MOD_CLOCK && b == MOD_GLOBE) || (a == MOD_GLOBE && b == MOD_CLOCK) -> -dp(8)
        a == MOD_TRAY || b == MOD_TRAY || a == MOD_GLOBE || b == MOD_GLOBE -> -dp(3)
        else -> 0f
    }

    /** Left edge of each present module for the given zone lists. */
    private fun targets(lists: List<List<String>>): HashMap<String, Float> {
        val res = HashMap<String, Float>()
        val w = width.toFloat()

        var cur = 0f
        var prev: String? = null
        for (id in lists[ZONE_LEFT]) {
            if (!present(id)) continue
            if (prev != null) cur += gap(prev, id)
            res[id] = cur
            cur += widthOf(id)
            prev = id
        }

        cur = w
        prev = null
        for (id in lists[ZONE_RIGHT].asReversed()) {
            if (!present(id)) continue
            if (prev != null) cur -= gap(id, prev)
            cur -= widthOf(id)
            res[id] = cur
            prev = id
        }

        val c = lists[ZONE_CENTER].filter { present(it) }
        val ci = c.indexOf(MOD_CLOCK)
        if (ci >= 0) {
            val cw = widthOf(MOD_CLOCK)
            val clockLeft = (w - cw) / 2f
            res[MOD_CLOCK] = clockLeft
            var cursor = clockLeft
            var next = MOD_CLOCK
            for (k in ci - 1 downTo 0) {
                val id = c[k]
                cursor -= gap(id, next)
                cursor -= widthOf(id)
                res[id] = cursor
                next = id
            }
            cursor = clockLeft + cw
            var before = MOD_CLOCK
            for (k in ci + 1 until c.size) {
                val id = c[k]
                cursor += gap(before, id)
                res[id] = cursor
                cursor += widthOf(id)
                before = id
            }
        } else if (c.isNotEmpty()) {
            var total = 0f
            for ((i, id) in c.withIndex()) {
                total += widthOf(id)
                if (i > 0) total += gap(c[i - 1], id)
            }
            var cursor = (w - total) / 2f
            var before: String? = null
            for (id in c) {
                if (before != null) cursor += gap(before, id)
                res[id] = cursor
                cursor += widthOf(id)
                before = id
            }
        }
        return res
    }

    private fun place(animate: Boolean) {
        if (width == 0) return
        val t = targets(zones)
        val now = SystemClock.uptimeMillis()
        if (animate) settleUntil = now + 220L
        val settling = now < settleUntil
        for ((id, v) in modules) {
            if (id == dragId) continue
            val x = t[id] ?: continue
            if (settling) {
                if (abs(v.translationX - x) > 0.5f) v.animate().translationX(x).setDuration(180L).start()
            } else if (v.translationX != x) {
                v.animate().cancel()
                v.translationX = x
            }
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        place(false)
        fitElastic()
        if (dragId != null) overlay.invalidate()
    }

    /**
     * Keeps a long song title from running under the tray/clock (or under the left modules when the music
     * module sits on the right): the text gets only the room up to the nearest module of another zone.
     */
    private fun fitElastic() {
        val (id, tv) = elastic ?: return
        if (!present(id) || tv.visibility == View.GONE || tv.width == 0) return
        val t = targets(zones)
        val left = t[id] ?: return
        val module = modules.getValue(id)
        val margin = dp(8)
        val overhead = (module.width - tv.width).coerceAtLeast(0)
        val avail: Float = when (zoneOf(id)) {
            ZONE_LEFT -> {
                val edge = (zones[ZONE_CENTER] + zones[ZONE_RIGHT]).filter { present(it) }
                    .mapNotNull { t[it] }.minOrNull()
                if (edge != null) edge - left - margin else width - left
            }
            ZONE_RIGHT -> {
                val edge = (zones[ZONE_LEFT] + zones[ZONE_CENTER]).filter { present(it) }
                    .mapNotNull { m -> t[m]?.plus(widthOf(m)) }.maxOrNull()
                if (edge != null) left + module.width - edge - margin else left + module.width
            }
            else -> return
        }
        val max = (avail - overhead).toInt().coerceAtLeast(act.dp(60))
        if (tv.maxWidth != max) post { tv.maxWidth = max }
    }

    // ---- tray direction ----

    private fun zoneOf(id: String): Int = zones.indexOfFirst { id in it }

    /** The tray's options open on the side the tray sits on: left of the centre -> open to the left, right -> right. */
    private fun applyTrayDirection(force: Boolean = false) {
        val growLeft = when (zoneOf(MOD_TRAY)) {
            ZONE_LEFT -> true
            ZONE_RIGHT -> false
            else -> {
                val c = zones[ZONE_CENTER]
                val ci = c.indexOf(MOD_CLOCK)
                ci < 0 || c.indexOf(MOD_TRAY) < ci
            }
        }
        if (force || growLeft != trayGrowLeft) {
            trayGrowLeft = growLeft
            act.setOmarchyTrayGrowLeft(growLeft)
        }
    }

    // ---- touch: long-press any module, then slide ----

    private fun moduleAt(x: Float, y: Float): String? {
        for ((id, v) in modules) {
            if (v.visibility == View.GONE || v.width == 0) continue
            val l = v.left + v.translationX
            if (x >= l && x <= l + v.width && y >= v.top && y <= v.bottom) return id
        }
        return null
    }

    private fun cancelPending() {
        removeCallbacks(longPress)
        pendingId = null
    }

    private fun superDispatch(ev: MotionEvent): Boolean = super.dispatchTouchEvent(ev)

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelPending()
                downTime = ev.downTime
                downX = ev.x
                downY = ev.y
                lastX = ev.x
                lastY = ev.y
                pendingId = moduleAt(ev.x, ev.y)
                if (pendingId != null) postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                lastX = ev.x
                lastY = ev.y
                if (dragId != null) {
                    moveDrag(ev.x)
                    return true
                }
                if (pendingId != null && (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop)) cancelPending()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelPending()
                if (dragId != null) {
                    finishDrag()
                    return true
                }
            }
            else -> if (dragId != null) return true
        }
        return superDispatch(ev)
    }

    private fun startDrag(id: String) {
        val v = modules[id] ?: return
        pendingId = null
        dragId = id
        dragStartTx = v.translationX
        dragDownX = lastX
        parent?.requestDisallowInterceptTouchEvent(true)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        // Tell the children the touch is over so a tap/press on the glyph doesn't also fire.
        val cancel = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, lastX, lastY, 0)
        superDispatch(cancel)
        cancel.recycle()
        v.animate().cancel()
        v.animate().scaleX(1.08f).scaleY(1.08f).alpha(0.92f).setDuration(120L).start()
        v.translationZ = dp(4)
        activeZone = zoneOf(id)
        overlay.visibility = View.VISIBLE
        overlay.invalidate()
    }

    private fun moveDrag(x: Float) {
        val id = dragId ?: return
        val v = modules[id] ?: return
        val w = v.width.toFloat()
        val tx = (dragStartTx + x - dragDownX).coerceIn(0f, (width - w).coerceAtLeast(0f))
        v.translationX = tx
        val cx = tx + w / 2f
        val mid = width / 2f
        val zone = when {
            abs(cx - mid) <= dp(SNAP_DP) -> ZONE_CENTER
            cx < mid -> ZONE_LEFT
            else -> ZONE_RIGHT
        }
        // Decide the drop position against the layout WITHOUT the dragged module, so the choice
        // doesn't feed back on itself while the others slide around.
        val others = zones.map { l -> l.filter { it != id } }
        val t = targets(others)
        val list = others[zone].toMutableList()
        var idx = 0
        for ((i, m) in list.withIndex()) {
            val left = t[m] ?: continue
            if (left + widthOf(m) / 2f < cx) idx = i + 1
        }
        list.add(idx, id)
        val next = others.mapIndexed { i, l -> if (i == zone) list else l.toMutableList() }
        if (next != zones) {
            zones = next
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            applyTrayDirection()
            place(true)
        }
        activeZone = zone
        overlay.invalidate()
    }

    private fun finishDrag() {
        val id = dragId ?: return
        val v = modules[id]
        dragId = null
        v?.animate()?.scaleX(1f)?.scaleY(1f)?.alpha(1f)?.setDuration(140L)?.start()
        v?.translationZ = 0f
        overlay.visibility = View.GONE
        act.prefs.edit().putString(LAYOUT_KEY, serialize()).apply()
        applyTrayDirection()
        place(true)
    }

    // ---- guide lines ----

    /** Drawn above the bar while a module is lifted: the three zones, the centre line and the module's outline. */
    private inner class GuideOverlay : View(act) {
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val fill = Paint().apply { style = Paint.Style.FILL }

        init {
            isClickable = false
            isFocusable = false
        }

        private fun withAlpha(color: Int, a: Int) = (color and 0x00FFFFFF) or (a shl 24)

        override fun onDraw(c: Canvas) {
            val id = dragId ?: return
            val p = act.pal
            val w = width.toFloat()
            val h = height.toFloat()
            val mid = w / 2f
            val snap = dp(SNAP_DP)
            val accent = p.cAccent or (0xFF shl 24)

            // zone shading: the zone the module would join is lit
            val edges = floatArrayOf(0f, (mid - snap).coerceAtLeast(0f), (mid + snap).coerceAtMost(w), w)
            for (z in 0..2) {
                fill.color = withAlpha(accent, if (z == activeZone) 0x30 else 0x0E)
                c.drawRect(edges[z], 0f, edges[z + 1], h, fill)
            }

            // zone dividers (dashed)
            stroke.strokeWidth = dp(1)
            stroke.pathEffect = DashPathEffect(floatArrayOf(dp(4), dp(4)), 0f)
            stroke.color = withAlpha(p.cDim or (0xFF shl 24), 0x99)
            c.drawLine(edges[1], 0f, edges[1], h, stroke)
            c.drawLine(edges[2], 0f, edges[2], h, stroke)

            // centre guide: solid and bright while the module is in the centre zone
            if (activeZone == ZONE_CENTER) {
                stroke.pathEffect = null
                stroke.strokeWidth = dp(2)
                stroke.color = accent
            } else {
                stroke.pathEffect = DashPathEffect(floatArrayOf(dp(3), dp(3)), 0f)
                stroke.strokeWidth = dp(1)
                stroke.color = withAlpha(accent, 0xAA)
            }
            c.drawLine(mid, 0f, mid, h, stroke)

            // outline of the lifted module
            val v = modules[id] ?: return
            val l = v.left + v.translationX
            stroke.pathEffect = null
            stroke.strokeWidth = dp(1)
            stroke.color = withAlpha(p.cFg or (0xFF shl 24), 0xCC)
            c.drawRect(l, v.top.toFloat() + dp(2), l + v.width, v.bottom.toFloat() - dp(2), stroke)
        }
    }
}
