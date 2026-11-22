package com.kabigonis.sorairo

import android.animation.ArgbEvaluator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.view.View

// Window tiling and focus: layout mode/ratio, window visibility, move/swap/focus helpers.
// Moved verbatim from MainActivity.kt as extension functions on MainActivity.

internal fun MainActivity.bottomWindowScopeAll(): Boolean =
    prefs.getBoolean("bottom_window_scope_all", false)

internal fun MainActivity.bottomWindowFor(ws: Int): String {
    val key = if (bottomWindowScopeAll()) "bottom_window_shared" else "bottom_window_$ws"
    return prefs.getString(key, "monitor")?.takeIf { it == "player" } ?: "monitor"
}

internal fun MainActivity.setBottomWindowForCurrent(value: String) {
    val choice = if (value == "player") "player" else "monitor"
    val edit = prefs.edit()
    if (bottomWindowScopeAll()) {
        edit.putString("bottom_window_shared", choice)
    } else {
        edit.putString("bottom_window_$curWs", choice)
    }
    edit.apply()
}

internal fun MainActivity.setBottomWindowForWorkspace(ws: Int, value: String) {
    val choice = if (value == "player") "player" else "monitor"
    val edit = prefs.edit()
    if (bottomWindowScopeAll()) {
        edit.putString("bottom_window_shared", choice)
        for (i in 0..4) edit.putString("bottom_window_$i", choice)
    } else {
        edit.putString("bottom_window_$ws", choice)
    }
    edit.apply()
}

internal fun MainActivity.layoutScopeAll(): Boolean = prefs.getBoolean("layout_scope_all", false)

// Visibility can be shared by every workspace or stored for each individual
// window instance (terminal/pinned/bottom in a particular workspace).
internal fun MainActivity.windowVisibilityScopeAll(): Boolean =
    prefs.getBoolean("home_visibility_scope_all", true)

internal fun MainActivity.windowShown(ws: Int, id: String): Boolean {
    val legacy = prefs.getBoolean("home_show_$id", true)
    if (windowVisibilityScopeAll()) return legacy
    return prefs.getBoolean("home_show_${id}_$ws", legacy)
}

internal fun MainActivity.setWindowShown(ws: Int, id: String, shown: Boolean) {
    val edit = prefs.edit()
    if (windowVisibilityScopeAll()) {
        edit.putBoolean("home_show_$id", shown)
    } else {
        edit.putBoolean("home_show_${id}_$ws", shown)
    }
    edit.apply()
}

internal fun MainActivity.layoutModeFor(ws: Int): String {
    val key = if (layoutScopeAll()) "window_layout_mode_global" else "window_layout_mode_$ws"
    return prefs.getString(key, "dwindle")?.takeIf { it in setOf("dwindle", "master", "columns", "rows") } ?: "dwindle"
}

internal fun MainActivity.layoutRatioFor(ws: Int): Float {
    val key = if (layoutScopeAll()) "window_layout_ratio_global" else "window_layout_ratio_$ws"
    return (prefs.getInt(key, 50).coerceIn(30, 70) / 100f)
}

internal fun MainActivity.applyTileLayout(ws: Int, animate: Boolean = true) {
    val shown = buildSet {
        if (windowShown(ws, "terminal")) add("terminal")
        if (windowShown(ws, "pinned")) add("pinned")
        if (windowShown(ws, "bottom")) add("bottom")
    }
    tileLayouts[ws]?.configure(layoutModeFor(ws), layoutRatioFor(ws),
        tileOrder(ws), shown, animate)
}

internal fun MainActivity.setLayoutMode(ws: Int, value: String) {
    val edit = prefs.edit()
    if (layoutScopeAll()) edit.putString("window_layout_mode_global", value)
    else edit.putString("window_layout_mode_$ws", value)
    edit.apply()
    if (layoutScopeAll()) for (i in 0..4) applyTileLayout(i) else applyTileLayout(ws)
}

internal fun MainActivity.setLayoutRatio(ws: Int, value: Int) {
    val edit = prefs.edit()
    if (layoutScopeAll()) edit.putInt("window_layout_ratio_global", value)
    else edit.putInt("window_layout_ratio_$ws", value)
    edit.apply()
    if (layoutScopeAll()) for (i in 0..4) applyTileLayout(i) else applyTileLayout(ws)
}

internal fun MainActivity.tileOrder(ws: Int): List<String> {
    val key = if (layoutScopeAll()) "tile_order_global" else "tile_order_$ws"
    val saved = prefs.getString(key, "").orEmpty().split(",").filter { it in tileIds }
    return (saved + tileIds).distinct()
}

internal fun MainActivity.saveTileOrder(ws: Int, order: List<String>) {
    val key = if (layoutScopeAll()) "tile_order_global" else "tile_order_$ws"
    prefs.edit().putString(key, order.joinToString(",")).apply()
}

internal fun MainActivity.tileView(ws: Int, id: String): View? = when (id) {
    "terminal" -> terminalWindowForFocus[ws]
    "pinned" -> pinWindowForFocus[ws]
    "bottom" -> bottomWindowForFocus[ws]
    else -> null
}

internal fun MainActivity.endMove() {
    val index = moveSource[curWs]
    if (index < 0) return
    moveSource[curWs] = -1
    moveHint.visibility = View.GONE
    tileView(curWs, tileIds[index])?.let { v ->
        v.animate().cancel(); v.alpha = 1f
        (v.background as? GradientDrawable)?.setStroke(dp(2), pal.cDim)
    }
}

internal fun MainActivity.startMove(ws: Int, id: String) {
    if (ws != curWs) return
    endMove(); moveSource[ws] = tileIds.indexOf(id)
    moveHint.visibility = View.VISIBLE
    tileView(ws, id)?.let { v ->
        (v.background as? GradientDrawable)?.setStroke(dp(3), pal.cAccent)
        v.animate().alpha(0.55f).setDuration(250).withEndAction {
            if (moveSource[ws] >= 0) v.animate().alpha(1f).setDuration(250).start()
        }.start()
    }
}

internal fun MainActivity.focusWindow(ws: Int, id: String) {
    val terminal = terminalWindowForFocus[ws] ?: return
    val pinned = pinWindowForFocus[ws] ?: return
    setWindowFocus(ws, tileIds.indexOf(id).coerceAtLeast(0), terminal, pinned)
}

internal fun MainActivity.moveTap(ws: Int, target: String): Boolean {
    val from = moveSource[ws]
    if (from < 0) return false
    val source = tileIds[from]
    if (source != target) {
        val order = tileOrder(ws).toMutableList()
        val a = order.indexOf(source); val b = order.indexOf(target)
        if (a >= 0 && b >= 0) {
            val tmp = order[a]; order[a] = order[b]; order[b] = tmp
            saveTileOrder(ws, order); applyTileLayout(ws, true); focusWindow(ws, source)
        }
    }
    endMove(); return true
}

internal fun MainActivity.swapTileNames(ws: Int, first: String, second: String): Boolean {
    fun resolve(q: String) = tileIds.firstOrNull { it.startsWith(q, true) || q.startsWith(it, true) }
    val aName = resolve(first) ?: return false; val bName = resolve(second) ?: return false
    val order = tileOrder(ws).toMutableList(); val a = order.indexOf(aName); val b = order.indexOf(bName)
    if (a < 0 || b < 0) return false
    val tmp = order[a]; order[a] = order[b]; order[b] = tmp
    saveTileOrder(ws, order); applyTileLayout(ws, true); focusWindow(ws, aName); return true
}

internal fun MainActivity.stepFocus(ws: Int, delta: Int) {
    val visible = tileOrder(ws).filter { windowShown(ws, it) }
    if (visible.isEmpty()) return
    val current = tileIds.getOrElse(focusedWindow[ws]) { visible[0] }
    val i = visible.indexOf(current).let { if (it < 0) 0 else it }
    focusWindow(ws, visible[(i + delta + visible.size) % visible.size])
}

internal fun MainActivity.setWindowFocus(
    ws: Int,
    focused: Int,
    terminal: View,
    pinned: View
) {
    focusedWindow[ws] = focused

    fun animateBorder(view: View, targetColor: Int) {
        val drawable = view.background as? GradientDrawable ?: return
        focusBorderAnimators.remove(view)?.cancel()

        val currentColor = focusBorderColors[view]
            ?: if (view === terminalWindowForFocus[ws]) pal.cAccent else pal.cDim
        val animator = ValueAnimator.ofObject(
            ArgbEvaluator(),
            currentColor,
            targetColor
        ).apply {
            duration = 180L
            addUpdateListener { animation ->
                val color = animation.animatedValue as Int
                drawable.setStroke(dp(2), color)
                focusBorderColors[view] = color
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (focusBorderAnimators[view] === this@apply) {
                        focusBorderAnimators.remove(view)
                    }
                }
            })
        }
        focusBorderAnimators[view] = animator
        animator.start()
    }

    val p = pal
    animateBorder(terminal, if (focused == 0) p.cAccent else p.cDim)
    animateBorder(pinned, if (focused == 1) p.cAccent else p.cDim)
    bottomWindowForFocus[ws]?.let { bottom ->
        animateBorder(bottom, if (focused == 2) p.cAccent else p.cDim)
    }
}
