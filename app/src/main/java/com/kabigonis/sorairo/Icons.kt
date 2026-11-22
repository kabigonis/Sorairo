package com.kabigonis.sorairo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/** Calendar glyph for the popup header: binding tabs, heavy top bar and one filled day square. */
class CalendarIconView(context: Context, private val color: Int) : View(context) {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = this@CalendarIconView.color }
    private val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = this@CalendarIconView.color }

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height).toFloat()
        val sw = s * 0.09f
        line.strokeWidth = sw
        val body = RectF(s * 0.1f, s * 0.2f, s * 0.9f, s * 0.9f)
        // outline
        canvas.drawRoundRect(body, s * 0.1f, s * 0.1f, line)
        // heavy top bar
        canvas.save()
        canvas.clipRect(body.left, body.top, body.right, body.top + s * 0.2f)
        canvas.drawRoundRect(body, s * 0.1f, s * 0.1f, solid)
        canvas.restore()
        // binding tabs
        val tabW = s * 0.1f
        canvas.drawRoundRect(RectF(s * 0.27f, s * 0.06f, s * 0.27f + tabW, s * 0.32f), tabW / 2, tabW / 2, solid)
        canvas.drawRoundRect(RectF(s * 0.63f, s * 0.06f, s * 0.63f + tabW, s * 0.32f), tabW / 2, tabW / 2, solid)
        // "today" square
        canvas.drawRoundRect(RectF(s * 0.5f, s * 0.52f, s * 0.74f, s * 0.76f), s * 0.03f, s * 0.03f, solid)
    }
}

/** Battery glyph with a terminal nub, a level fill and a bolt while charging. */
class BatteryIconView(context: Context, private val color: Int, private val boltColor: Int) : View(context) {
    private var level = 0f
    private var charging = false
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = this@BatteryIconView.color }
    private val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = this@BatteryIconView.color }
    private val bolt = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    fun update(level: Float, charging: Boolean) {
        this.level = level.coerceIn(0f, 1f)
        this.charging = charging
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val sw = w * 0.1f
        line.strokeWidth = sw
        val bodyTop = h * 0.14f
        // terminal nub
        canvas.drawRoundRect(RectF(w * 0.36f, 0f, w * 0.64f, bodyTop + sw), sw / 2, sw / 2, solid)
        // body
        val body = RectF(w * 0.14f + sw / 2, bodyTop + sw / 2, w * 0.86f - sw / 2, h - sw / 2)
        canvas.drawRoundRect(body, w * 0.12f, w * 0.12f, line)
        // level fill, inset from the outline
        val gap = sw * 0.7f
        val l = body.left + sw / 2 + gap
        val r = body.right - sw / 2 - gap
        val b = body.bottom - sw / 2 - gap
        val t = body.top + sw / 2 + gap
        if (level > 0.02f) {
            val fh = (b - t) * level
            canvas.drawRoundRect(RectF(l, b - fh, r, b), sw * 0.3f, sw * 0.3f, solid)
        }
        if (charging) {
            // contrast: dark bolt on the fill, light bolt on the empty part
            bolt.color = if (level >= 0.5f) boltColor else color
            val p = Path().apply {
                moveTo(w * 0.57f, h * 0.3f)
                lineTo(w * 0.32f, h * 0.6f)
                lineTo(w * 0.49f, h * 0.6f)
                lineTo(w * 0.43f, h * 0.84f)
                lineTo(w * 0.68f, h * 0.52f)
                lineTo(w * 0.51f, h * 0.52f)
                close()
            }
            canvas.drawPath(p, bolt)
        }
    }
}
