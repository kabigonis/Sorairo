package com.kabigonis.sorairo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.animation.ValueAnimator
import android.view.View
import kotlin.math.min

/**
 * Small, custom-drawn status glyphs matching the thin, muted Omarchy/Waybar reference.
 * The drawing intentionally avoids emoji, Material icons and font glyph substitutions.
 */
internal enum class OmarchyGlyph {
    RECORD, RECORD_VIDEO, REMINDER, NIGHT_LIGHT, MUTED_BELL, COFFEE, ADMIN_TOOLS
}

internal class OmarchyActionIconView(
    context: Context,
    private val glyph: OmarchyGlyph,
    private val idleColor: Int,
    private val activeColor: Int,
    private val accentColor: Int
) : View(context) {
    private var animatedTint: Int = idleColor
    var active: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                ValueAnimator.ofArgb(animatedTint, if (value) activeColor else idleColor).apply {
                    duration = 190L
                    addUpdateListener {
                        animatedTint = it.animatedValue as Int
                        invalidate()
                    }
                    start()
                }
            }
        }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.9f
        strokeCap = Paint.Cap.SQUARE
        strokeJoin = Paint.Join.MITER
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = min(width, height).toFloat() / 24f
        if (scale <= 0f) return
        canvas.save()
        canvas.translate((width - 24f * scale) / 2f, (height - 24f * scale) / 2f)
        canvas.scale(scale, scale)
        val color = when (glyph) {
            OmarchyGlyph.ADMIN_TOOLS -> accentColor
            else -> animatedTint
        }
        stroke.color = color
        fill.color = color
        when (glyph) {
            OmarchyGlyph.RECORD -> drawRecord(canvas)
            OmarchyGlyph.RECORD_VIDEO -> drawVideoCamera(canvas)
            OmarchyGlyph.REMINDER -> drawReminder(canvas)
            OmarchyGlyph.NIGHT_LIGHT -> drawNightLight(canvas)
            OmarchyGlyph.MUTED_BELL -> drawMutedBell(canvas)
            OmarchyGlyph.COFFEE -> drawCoffee(canvas)
            OmarchyGlyph.ADMIN_TOOLS -> drawAdminTools(canvas)
        }
        canvas.restore()
    }

    private fun drawRecord(c: Canvas) {
        c.drawCircle(12f, 12f, 7.1f, stroke)
        c.drawCircle(12f, 12f, 2.2f, fill)
    }

    private fun drawVideoCamera(c: Canvas) {
        val body = Path().apply {
            moveTo(3.2f, 6.5f)
            lineTo(14.8f, 6.5f)
            lineTo(14.8f, 17.5f)
            lineTo(3.2f, 17.5f)
            close()
        }
        c.drawPath(body, fill)
        val lens = Path().apply {
            moveTo(15.7f, 9.2f)
            lineTo(21f, 6.2f)
            lineTo(21f, 17.8f)
            lineTo(15.7f, 14.8f)
            close()
        }
        c.drawPath(lens, fill)
    }

    private fun drawReminder(c: Canvas) {
        // Compact alarm-clock silhouette: clearer at status-bar size than a hand glyph.
        c.drawCircle(12f, 13f, 7.1f, stroke)
        c.drawLine(12f, 13f, 12f, 8.8f, stroke)
        c.drawLine(12f, 13f, 15.2f, 14.8f, stroke)
        c.drawLine(7.0f, 3.8f, 4.6f, 6.0f, stroke)
        c.drawLine(17.0f, 3.8f, 19.4f, 6.0f, stroke)
        c.drawLine(8.0f, 20.0f, 6.2f, 22.0f, stroke)
        c.drawLine(16.0f, 20.0f, 17.8f, 22.0f, stroke)
    }

    private fun drawNightLight(c: Canvas) {
        val moon = Path().apply {
            moveTo(16.8f, 3.2f)
            cubicTo(13.0f, 4.0f, 10.2f, 7.3f, 10.2f, 11.3f)
            cubicTo(10.2f, 15.8f, 13.8f, 19.4f, 18.3f, 19.4f)
            cubicTo(19.2f, 19.4f, 20.0f, 19.2f, 20.8f, 18.8f)
            cubicTo(19.3f, 20.2f, 17.2f, 21.0f, 14.9f, 21.0f)
            cubicTo(9.7f, 21.0f, 5.5f, 16.8f, 5.5f, 11.6f)
            cubicTo(5.5f, 7.2f, 8.5f, 3.5f, 12.6f, 2.5f)
            cubicTo(14.1f, 2.2f, 15.6f, 2.5f, 16.8f, 3.2f)
            close()
        }
        c.drawPath(moon, stroke)
        c.drawLine(19.5f, 4.0f, 19.5f, 8.0f, stroke)
        c.drawLine(17.5f, 6.0f, 21.5f, 6.0f, stroke)
    }

    private fun drawMutedBell(c: Canvas) {
        val bell = Path().apply {
            moveTo(5.5f, 17.2f)
            lineTo(7f, 15.1f)
            lineTo(7.7f, 9.8f)
            cubicTo(8.4f, 4.6f, 15.6f, 4.6f, 16.3f, 9.8f)
            lineTo(17f, 15.1f)
            lineTo(18.5f, 17.2f)
            close()
        }
        c.drawPath(bell, stroke)
        c.drawLine(10f, 19.2f, 14f, 19.2f, stroke)
        c.drawLine(5f, 4.5f, 19f, 20f, stroke)
    }

    private fun drawCoffee(c: Canvas) {
        val cup = Path().apply {
            moveTo(4.2f, 10f)
            lineTo(17f, 10f)
            lineTo(16f, 18.2f)
            lineTo(6f, 18.2f)
            close()
        }
        c.drawPath(cup, stroke)
        c.drawArc(RectF(15.5f, 11f, 21f, 17.5f), -75f, 220f, false, stroke)
        c.drawLine(5f, 20.5f, 18f, 20.5f, stroke)
        c.drawLine(7.2f, 7.3f, 5.8f, 4.8f, stroke)
        c.drawLine(12f, 7.3f, 10.6f, 4.8f, stroke)
    }

    private fun drawAdminTools(c: Canvas) {
        // Simple, recognisable gear: eight teeth and a hollow centre.
        val gear = Path()
        for (i in 0 until 32) {
            val angle = Math.PI * 2.0 * i / 32.0 - Math.PI / 2.0
            val tooth = i % 4 in 0..1
            val radius = if (tooth) 10.0 else 8.2
            val x = 12.0 + kotlin.math.cos(angle) * radius
            val y = 12.0 + kotlin.math.sin(angle) * radius
            if (i == 0) gear.moveTo(x.toFloat(), y.toFloat()) else gear.lineTo(x.toFloat(), y.toFloat())
        }
        gear.close()
        c.drawPath(gear, stroke)
        c.drawCircle(12f, 12f, 3.1f, stroke)
    }
}