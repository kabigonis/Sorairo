package com.kabigonis.sorairo

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.PathParser
import kotlin.math.min

/**
 * Status glyphs for the Omarchy-style action tray.
 *
 * Omarchy's Waybar draws Material Design Icons through a Nerd Font, so these are the same kind of
 * shapes: solid, 24x24 grid, no hairline strokes. Drawing them as paths keeps them sharp at any
 * UI scale and avoids emoji or font substitution.
 */
internal enum class OmarchyGlyph {
    RECORD, REMINDER, NIGHT_LIGHT, MUTED_BELL, COFFEE, SUDO, EXPANDER
}

internal class OmarchyActionIconView(
    context: Context,
    private val glyph: OmarchyGlyph,
    private val idleColor: Int,
    private val activeColor: Int
) : View(context) {

    private var animatedTint: Int = idleColor
    private var pulse: ValueAnimator? = null

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

    /** Slow fade used by the screen-recording indicator while a recording is running. */
    var pulsing: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            pulse?.cancel()
            pulse = null
            if (value) {
                pulse = ValueAnimator.ofFloat(1f, 0.35f).apply {
                    duration = 900L
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    addUpdateListener { this@OmarchyActionIconView.alpha = it.animatedValue as Float }
                    start()
                }
            } else {
                alpha = 1f
            }
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val shape: Path = buildShape(glyph)

    override fun onDetachedFromWindow() {
        pulse?.cancel()
        pulse = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = min(width, height).toFloat() / 24f
        if (scale <= 0f) return
        canvas.save()
        canvas.translate((width - 24f * scale) / 2f, (height - 24f * scale) / 2f)
        canvas.scale(scale, scale)
        fill.color = animatedTint
        line.color = animatedTint
        when (glyph) {
            OmarchyGlyph.EXPANDER -> {
                line.strokeWidth = 2.8f
                val chevron = Path().apply {
                    moveTo(14.5f, 6f)
                    lineTo(8.5f, 12f)
                    lineTo(14.5f, 18f)
                }
                canvas.drawPath(chevron, line)
            }
            OmarchyGlyph.MUTED_BELL -> {
                canvas.drawPath(shape, fill)
                line.strokeWidth = 2.6f
                canvas.drawLine(3.2f, 3.2f, 20.8f, 20.8f, line)
            }
            else -> canvas.drawPath(shape, fill)
        }
        canvas.restore()
    }

    private companion object {
        const val COFFEE_PATH =
            "M2,21H20V19H2M20,8V5H18V8M20,3H4V13A4,4 0 0,0 8,17H14A4,4 0 0,0 18,13V10H20A2,2 0 0,0 22,8V5C22,3.89 21.1,3 20,3Z"
        const val BELL_PATH =
            "M21,19V20H3V19L5,17V11C5,7.9 7.03,5.17 10,4.29V4A2,2 0 0,1 14,4V4.29C16.97,5.17 19,7.9 19,11V17L21,19M14,21A2,2 0 0,1 12,23A2,2 0 0,1 10,21Z"
        const val MOON_PATH = "M20.5,14.5A9,9 0 1 1 9.5,3.5A7,7 0 0 0 20.5,14.5Z"
        const val ALARM_PATH =
            "M12,20A7,7 0 0,1 5,13A7,7 0 0,1 12,6A7,7 0 0,1 19,13A7,7 0 0,1 12,20M12,4A9,9 0 0,0 3,13A9,9 0 0,0 12,22A9,9 0 0,0 21,13A9,9 0 0,0 12,4M12.5,8H11V14L15.75,16.85L16.5,15.62L12.5,13.25V8M7.88,3.39L6.6,1.86L2,5.71L3.29,7.24L7.88,3.39M22,5.72L17.4,1.86L16.11,3.39L20.71,7.25L22,5.72Z"
        // Ring with a centre dot (the second circle winds the other way to cut the hole).
        const val RECORD_PATH =
            "M12,3A9,9 0 1 0 12,21A9,9 0 1 0 12,3ZM12,5A7,7 0 1 1 12,19A7,7 0 1 1 12,5ZM12,8.5A3.5,3.5 0 1 0 12,15.5A3.5,3.5 0 1 0 12,8.5Z"
        // Password field: box with two dots, plus the text cursor I-beam (Omarchy's red glyph).
        const val SUDO_PATH =
            "M2,7H15V17H2ZM4,9V15H13V9ZM6,10.6A1.4,1.4 0 1 1 6,13.4A1.4,1.4 0 1 1 6,10.6ZM10.5,10.6A1.4,1.4 0 1 1 10.5,13.4A1.4,1.4 0 1 1 10.5,10.6ZM17,5H19V19H17ZM14.5,3H21.5V5H14.5ZM14.5,19H21.5V21H14.5Z"

        fun parse(data: String): Path = try {
            PathParser.createPathFromPathData(data) ?: Path()
        } catch (_: Exception) {
            Path()
        }

        fun buildShape(glyph: OmarchyGlyph): Path = when (glyph) {
            OmarchyGlyph.COFFEE -> parse(COFFEE_PATH)
            OmarchyGlyph.NIGHT_LIGHT -> parse(MOON_PATH)
            OmarchyGlyph.REMINDER -> parse(ALARM_PATH)
            OmarchyGlyph.RECORD -> parse(RECORD_PATH)
            OmarchyGlyph.SUDO -> parse(SUDO_PATH)
            OmarchyGlyph.EXPANDER -> Path()
            OmarchyGlyph.MUTED_BELL -> {
                // Cut a gap around the slash so the bell reads as "broken" like the Omarchy glyph.
                val bell = parse(BELL_PATH)
                try {
                    val gap = Path()
                    val gapPaint = Paint().apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 6.4f
                        strokeCap = Paint.Cap.BUTT
                    }
                    val slash = Path().apply {
                        moveTo(3.2f, 3.2f)
                        lineTo(20.8f, 20.8f)
                    }
                    gapPaint.getFillPath(slash, gap)
                    bell.op(gap, Path.Op.DIFFERENCE)
                } catch (_: Exception) {
                    // keep the plain bell; the slash is still drawn on top
                }
                bell
            }
        }
    }
}
