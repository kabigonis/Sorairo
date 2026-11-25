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
    RECORD, RECORD_VIDEO, HAND, NIGHT_LIGHT, MUTED_BELL, COFFEE, SUDO
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
            else -> animatedTint
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
        // Omarchy reminder glyph: a compact closed hand with one index finger raised.
        // Keep the thumb visibly extended to the left and the wrist squared off.
        val p = Path().apply {
            moveTo(8.1f, 20.8f)
            lineTo(5.2f, 17.2f)
            lineTo(3.4f, 14.1f)
            cubicTo(2.8f, 12.9f, 3.7f, 11.8f, 4.8f, 12.3f)
            lineTo(7.4f, 14.5f)
            lineTo(7.4f, 6.0f)
            cubicTo(7.4f, 4.4f, 8.4f, 3.4f, 9.7f, 3.4f)
            cubicTo(11.0f, 3.4f, 11.8f, 4.4f, 11.8f, 6.0f)
            lineTo(11.8f, 12.1f)
            lineTo(12.3f, 11.0f)
            cubicTo(12.9f, 9.9f, 14.3f, 9.8f, 15.0f, 10.8f)
            lineTo(16.2f, 12.5f)
            lineTo(17.1f, 13.3f)
            cubicTo(18.2f, 14.2f, 18.5f, 15.4f, 18.0f, 16.8f)
            lineTo(16.4f, 20.8f)
            close()
        }
        c.drawPath(p, fill)
        // Short wrist cuff gives the same compact silhouette as the reference.
        c.drawLine(8.2f, 20.7f, 16.5f, 20.7f, stroke)
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
