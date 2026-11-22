package com.kabigonis.sorairo

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Rect
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs

// Window tile layout ViewGroup (moved verbatim from MainActivity.kt).

/**
 * Positions the existing workspace window views without recreating them.
 * Only explicit configuration changes animate; parent size changes snap.
 */
internal class TileLayout(context: Context, private val gapPx: Int) : ViewGroup(context) {
    private val animator = ValueAnimator()
    private var mode = "dwindle"
    private var ratio = 0.5f
    private var order = listOf("terminal", "pinned", "bottom")
    private var visible = setOf("terminal", "pinned", "bottom")
    private var lastWidth = 0
    private var lastHeight = 0

    init { clipChildren = false }

    var moveModeActive: () -> Boolean = { false }
    var onMoveTap: (String) -> Unit = {}
    private var moveTracking = false
    private var moveDownX = 0f
    private var moveDownY = 0f
    private val moveSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop * 2

    private fun tileAt(x: Int, y: Int): String? {
        for (i in childCount - 1 downTo 0) {
            val c = getChildAt(i)
            if (c.visibility != View.VISIBLE) continue
            if (x >= c.left && x < c.right && y >= c.top && y < c.bottom) return c.tag as? String
        }
        return null
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = moveModeActive()

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!moveModeActive() && !moveTracking) return super.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                moveTracking = true; moveDownX = ev.x; moveDownY = ev.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                val tracked = moveTracking
                moveTracking = false
                if (tracked && abs(ev.x - moveDownX) <= moveSlop && abs(ev.y - moveDownY) <= moveSlop) {
                    tileAt(ev.x.toInt(), ev.y.toInt())?.let { onMoveTap(it) }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> { moveTracking = false; return true }
        }
        return moveTracking
    }

    fun configure(newMode: String, newRatio: Float, newOrder: List<String>,
                  newVisible: Set<String>, animate: Boolean = true) {
        val nextMode = newMode.takeIf { it in setOf("dwindle", "master", "columns", "rows") } ?: "dwindle"
        val nextRatio = newRatio.coerceIn(0.3f, 0.7f)
        val nextOrder = (newOrder.filter { it in setOf("terminal", "pinned", "bottom") } +
            listOf("terminal", "pinned", "bottom")).distinct()
        val changed = mode != nextMode || ratio != nextRatio || order != nextOrder || visible != newVisible
        if (!changed) return
        val previouslyVisible = visible
        mode = nextMode
        ratio = nextRatio
        order = nextOrder
        visible = newVisible
        val targets = calculateRects(width, height).toMutableMap()
        val hiddenAfter = previouslyVisible - newVisible
        val children = childrenForIds()
        children.forEach { (id, child) ->
            child.visibility = if (id in previouslyVisible || id in newVisible) View.VISIBLE else View.GONE
        }
        if (animate && width > 0 && height > 0) {
            hiddenAfter.forEach { id ->
                val child = children[id]
                if (child != null) {
                    val cx = (child.left + child.right) / 2
                    val cy = (child.top + child.bottom) / 2
                    targets[id] = Rect(cx, cy, cx, cy)
                }
            }
            animateTo(targets, hiddenAfter)
        } else {
            animator.cancel()
            hiddenAfter.forEach { children[it]?.visibility = View.GONE }
            applyRects(targets)
        }
    }

    private fun childrenForIds(): Map<String, View> =
        (0 until childCount).mapNotNull { i ->
            val child = getChildAt(i)
            (child.tag as? String)?.let { it to child }
        }.toMap()

    private fun calculateRects(w: Int, h: Int): Map<String, Rect> {
        val ids = order.filter { it in visible && childrenForIds().containsKey(it) }
        if (ids.isEmpty()) return emptyMap()
        val l = paddingLeft
        val t = paddingTop
        val rw = (w - paddingLeft - paddingRight).coerceAtLeast(0)
        val rh = (h - paddingTop - paddingBottom).coerceAtLeast(0)
        val gap = gapPx.coerceAtMost(minOf(rw, rh).coerceAtLeast(0))
        val result = linkedMapOf<String, Rect>()
        fun put(id: String, x: Int, y: Int, width: Int, height: Int) {
            result[id] = Rect(x, y, x + width.coerceAtLeast(0), y + height.coerceAtLeast(0))
        }
        when {
            ids.size == 1 -> put(ids[0], l, t, rw, rh)
            mode == "rows" -> {
                val each = (rh - gap * (ids.size - 1)).coerceAtLeast(0) / ids.size
                ids.forEachIndexed { i, id ->
                    val top = t + i * (each + gap)
                    put(id, l, top, rw, if (i == ids.lastIndex) t + rh - top else each)
                }
            }
            mode == "columns" -> {
                val each = (rw - gap * (ids.size - 1)).coerceAtLeast(0) / ids.size
                ids.forEachIndexed { i, id ->
                    val left = l + i * (each + gap)
                    put(id, left, t, if (i == ids.lastIndex) l + rw - left else each, rh)
                }
            }
            mode == "master" -> {
                // Master keeps the first window prominent; the remaining windows
                // share a vertical stack on the right.
                val leftWidth = ((rw - gap) * ratio).toInt().coerceIn(0, (rw - gap).coerceAtLeast(0))
                put(ids[0], l, t, leftWidth, rh)
                val rightX = l + leftWidth + gap
                val rightW = (l + rw - rightX).coerceAtLeast(0)
                val rest = ids.drop(1)
                val each = (rh - gap * (rest.size - 1)).coerceAtLeast(0) / rest.size
                rest.forEachIndexed { i, id ->
                    val top = t + i * (each + gap)
                    put(id, rightX, top, rightW, if (i == rest.lastIndex) t + rh - top else each)
                }
            }
            else -> {
                // Dwindle uses an even primary split, with remaining windows
                // stacked in the secondary area.
                val leftWidth = ((rw - gap) * ratio).toInt().coerceIn(0, (rw - gap).coerceAtLeast(0))
                put(ids[0], l, t, leftWidth, rh)
                val rightX = l + leftWidth + gap
                val rightW = (l + rw - rightX).coerceAtLeast(0)
                val rest = ids.drop(1)
                val each = (rh - gap * (rest.size - 1)).coerceAtLeast(0) / rest.size
                rest.forEachIndexed { i, id ->
                    val top = t + i * (each + gap)
                    put(id, rightX, top, rightW, if (i == rest.lastIndex) t + rh - top else each)
                }
            }
        }
        return result
    }

    private fun applyRects(rects: Map<String, Rect>) {
        val children = childrenForIds()
        rects.forEach { (id, rect) ->
            children[id]?.let { child ->
                val childWidth = (rect.right - rect.left).coerceAtLeast(0)
                val childHeight = (rect.bottom - rect.top).coerceAtLeast(0)
                child.measure(
                    MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY)
                )
                child.layout(rect.left, rect.top, rect.right, rect.bottom)
            }
        }
        invalidate()
    }

    private fun animateTo(targets: Map<String, Rect>, hiddenAfter: Set<String>) {
        animator.cancel()
        val children = childrenForIds()
        val starts = targets.mapValues { (id, _) ->
            val child = children[id]
            if (child != null && child.width > 0 && child.height > 0) {
                Rect(child.left, child.top, child.right, child.bottom)
            } else {
                val target = targets.getValue(id)
                val cx = (target.left + target.right) / 2
                val cy = (target.top + target.bottom) / 2
                Rect(cx, cy, cx, cy)
            }
        }
        animator.removeAllUpdateListeners()
        animator.removeAllListeners()
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                hiddenAfter.filter { it !in visible }.forEach { childrenForIds()[it]?.visibility = View.GONE }
            }
        })
        animator.setFloatValues(0f, 1f)
        animator.duration = 220
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { a ->
            val f = a.animatedValue as Float
            val current = targets.mapValues { (id, end) ->
                val start = starts[id] ?: end
                Rect(
                    (start.left + (end.left - start.left) * f).toInt(),
                    (start.top + (end.top - start.top) * f).toInt(),
                    (start.right + (end.right - start.right) * f).toInt(),
                    (start.bottom + (end.bottom - start.bottom) * f).toInt()
                )
            }
            applyRects(current)
        }
        animator.start()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
        // Children are measured and laid out at their tile size in applyRects().
        // Measuring them full-size here used to clobber their real size whenever
        // a descendant called requestLayout(), leaving windows blank until toggled.
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = right - left
        val h = bottom - top
        if (w != lastWidth || h != lastHeight) {
            lastWidth = w
            lastHeight = h
            animator.cancel()
            applyRects(calculateRects(w, h))
        } else if (!animator.isRunning) {
            // A descendant (pins list, player, monitor...) asked for layout. Always
            // re-place every tile, otherwise those windows stay unlaid-out/blank.
            applyRects(calculateRects(w, h))
        }
    }
}

