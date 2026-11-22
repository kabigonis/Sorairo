package com.kabigonis.sorairo

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.sin
import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View

// Small custom views: visualizer bars and vertical slider (moved verbatim from MainActivity.kt).

/** Little animated equalizer bars shown next to the song name. */
class VisualizerView(c: Context) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val phases = floatArrayOf(0f, 1.3f, 2.1f, 0.7f)
    var color = Color.WHITE
    var playing = false
        set(v) {
            if (field != v) {
                field = v
                invalidate()
            }
        }

    override fun onDraw(canvas: Canvas) {
        paint.color = color
        val n = 4
        val bw = width / (n * 2f - 1f)
        val t = SystemClock.uptimeMillis() / 1000f
        for (i in 0 until n) {
            val h = if (playing) 0.25f + 0.75f * (0.5f + 0.5f * sin(t * (5f + i * 1.7f) + phases[i])) else 0.2f
            val bh = h * height
            val left = i * 2 * bw
            canvas.drawRoundRect(left, height - bh, left + bw, height.toFloat(), bw / 2, bw / 2, paint)
        }
        if (playing) postInvalidateOnAnimation()
    }
}

/** Vertical slider used for brightness and volume. */
class VSlider(
    c: Context,
    private val fillColor: Int,
    private val trackColor: Int,
    private val onChange: (Float) -> Unit
) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    var value = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val r = width / 2f
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        paint.color = trackColor
        canvas.drawRoundRect(rect, r, r, paint)
        canvas.save()
        canvas.clipRect(0f, height * (1f - value), width.toFloat(), height.toFloat())
        paint.color = fillColor
        canvas.drawRoundRect(rect, r, r, paint)
        canvas.restore()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent.requestDisallowInterceptTouchEvent(true)
                value = 1f - e.y / height
                onChange(value)
            }
        }
        return true
    }
}
