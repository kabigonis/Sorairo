package com.kabigonis.sorairo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/**
 * Small, custom-drawn status glyphs matching the thin, muted Omarchy/Waybar reference.
 * The drawing intentionally avoids emoji, Material icons and font glyph substitutions.
 */
internal enum class OmarchyGlyph {
    RECORD, RECORD_VIDEO, HAND, NIGHT_LIGHT, MUTED_BELL, COFFEE, SUDO
}

internal class OmarchyActionIconView(
    context: Context,
    private val glyph: OmarchyGlyph,
    private val idleColor: Int,
    private val activeColor: Int,
    private val accentColor: Int
) : View(context) {
    var active: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.65f
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
            OmarchyGlyph.SUDO -> accentColor
            else -> if (active) activeColor else idleColor
        }
        stroke.color = color
        fill.color = color
        when (glyph) {
            OmarchyGlyph.RECORD -> drawRecord(canvas)
            OmarchyGlyph.RECORD_VIDEO -> drawVideoCamera(canvas)
            OmarchyGlyph.HAND -> drawHand(canvas)
            OmarchyGlyph.NIGHT_LIGHT -> drawNightLight(canvas)
            OmarchyGlyph.MUTED_BELL -> drawMutedBell(canvas)
            OmarchyGlyph.COFFEE -> drawCoffee(canvas)
            OmarchyGlyph.SUDO -> drawSudo(canvas)
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

    private fun drawHand(c: Canvas) {
        val p = Path().apply {
            moveTo(7.2f, 20.1f)
            lineTo(4.8f, 14.4f)
            lineTo(4.1f, 12.3f)
            lineTo(5.2f, 11.5f)
            lineTo(7.2f, 13.2f)
            lineTo(7.1f, 7.4f)
            lineTo(7.5f, 5.8f)
            lineTo(9.1f, 5.8f)
            lineTo(9.7f, 12.2f)
            lineTo(10.1f, 4.2f)
            lineTo(11.8f, 3.4f)
            lineTo(13.2f, 4.3f)
            lineTo(13.2f, 12.1f)
            lineTo(14.1f, 6.2f)
            lineTo(15.8f, 5.7f)
            lineTo(16.9f, 7.1f)
            lineTo(16.2f, 13.4f)
            lineTo(18.1f, 10.8f)
            lineTo(19.6f, 11.1f)
            lineTo(20.0f, 12.7f)
            lineTo(17.2f, 18.7f)
            lineTo(14.5f, 21f)
            close()
        }
        c.drawPath(p, fill)
    }

    private fun drawNightLight(c: Canvas) {
        c.drawCircle(12f, 12f, 4.1f, stroke)
        val rays = arrayOf(
            12f to 2f, 12f to 5.1f, 12f to 18.9f, 12f to 22f,
            2f to 12f, 5.1f to 12f, 18.9f to 12f, 22f to 12f,
            4.7f to 4.7f, 6.9f to 6.9f, 17.1f to 17.1f, 19.3f to 19.3f,
            19.3f to 4.7f, 17.1f to 6.9f, 6.9f to 17.1f, 4.7f to 19.3f
        )
        for (i in rays.indices step 2) {
            c.drawLine(rays[i].first, rays[i].second, rays[i + 1].first, rays[i + 1].second, stroke)
        }
        // The diagonal slash is the small "night mode" cue from the reference glyph.
        c.drawLine(5.2f, 19f, 19f, 5.2f, stroke)
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

    private fun drawSudo(c: Canvas) {
        // Compact terminal/privilege glyph. Red is intentionally retained from the reference.
        c.drawRect(RectF(3.2f, 5.2f, 8.1f, 18.8f), stroke)
        c.drawRect(RectF(15.9f, 5.2f, 20.8f, 18.8f), stroke)
        c.drawRect(RectF(9.1f, 8.1f, 14.9f, 15.9f), fill)
        c.drawLine(1.8f, 12f, 22.2f, 12f, stroke)
    }
}
