package com.kabigonis.sorairo

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import android.view.View

// Network graph views and bar helper (moved verbatim from MainActivity.kt).

fun bar(pct: Int, width: Int = 12): String {
    val f = (pct.coerceIn(0, 100) * width + 50) / 100
    return "█".repeat(f) + "░".repeat(width - f)
}

class NetGraphHistory {
    val size = 62
    val down = FloatArray(size)
    val up = FloatArray(size)
    var count = 0
        private set
    var head = 0
        private set
    var peak = 1024f
        private set
    var lastPushT = 0L
        private set
    var lastDown = 0f
        private set
    var lastUp = 0f
        private set

    fun push(downBps: Float, upBps: Float, now: Long) {
        lastDown += 0.4f * (downBps.coerceAtLeast(0f) - lastDown)
        lastUp += 0.4f * (upBps.coerceAtLeast(0f) - lastUp)
        down[head] = lastDown
        up[head] = lastUp
        head = (head + 1) % size
        if (count < size) count++
        lastPushT = now

        var m = 0f
        for (i in 0 until count) {
            if (down[i] > m) m = down[i]
            if (up[i] > m) m = up[i]
        }
        peak = maxOf(m, 1024f, peak * 0.97f)
    }
}

class NetGraphView(
    context: Context,
    private val history: NetGraphHistory
) : View(context) {
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val downPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val upPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val path = Path()
    private val fillPath = Path()
    private var downColor = Color.WHITE

    fun setColors(grid: Int, d: Int, u: Int) {
        gridPaint.color = grid
        downPaint.color = d
        upPaint.color = u
        downColor = d
        updateShader()
        invalidate()
    }

    private fun updateShader() {
        val h = height.toFloat()
        if (h <= 0f) return
        val base = downColor and 0x00FFFFFF
        fillPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            base or (0x66 shl 24), base,
            Shader.TileMode.CLAMP
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateShader()
    }

    /** Builds a smooth curve; the right end glides from the previous sample to the newest one. */
    private fun buildPath(p: Path, data: FloatArray, w: Float, h: Float, frac: Float): Float {
        val n = history.count
        val size = history.size
        val step = w / (size - 2f)
        val start = (history.head - n + size) % size
        val peak = history.peak
        p.reset()
        var px = 0f
        var py = 0f
        var firstX = 0f
        for (k in 0 until n - 1) {
            val x = w - step * ((n - 2 - k) + frac)
            val y = h - 3f - (data[(start + k) % size] / peak) * (h - 8f)
            if (k == 0) {
                p.moveTo(x, y)
                firstX = x
            } else {
                p.quadTo(px, py, (px + x) / 2f, (py + y) / 2f)
            }
            px = x
            py = y
        }
        val prev = data[(start + n - 2) % size]
        val last = data[(start + n - 1) % size]
        val ty = h - 3f - ((prev + (last - prev) * frac) / peak) * (h - 8f)
        p.quadTo(px, py, (px + w) / 2f, (py + ty) / 2f)
        p.lineTo(w, ty)
        return firstX
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in 1..3) {
            val y = h * i / 4f
            c.drawLine(0f, y, w, y, gridPaint)
        }
        if (history.count >= 2) {
            val frac = ((SystemClock.elapsedRealtime() - history.lastPushT) / 1000f).coerceIn(0f, 1f)
            val firstX = buildPath(path, history.down, w, h, frac)
            fillPath.set(path)
            fillPath.lineTo(w, h)
            fillPath.lineTo(firstX, h)
            fillPath.close()
            c.drawPath(fillPath, fillPaint)
            c.drawPath(path, downPaint)
            buildPath(path, history.up, w, h, frac)
            c.drawPath(path, upPaint)
        }
        postInvalidateOnAnimation()
    }
}


