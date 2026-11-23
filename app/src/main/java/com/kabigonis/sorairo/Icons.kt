package com.kabigonis.sorairo

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

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

/** dp that follows Sorairo's own UI scale (Display panel), for custom views. */
internal fun View.uiDp(v: Int): Int =
    (v * resources.displayMetrics.density * ((context as? MainActivity)?.uiScale ?: 1f)).toInt()

/** Monitor glyph for the Display panel / top bar. */
class DisplayIconView(context: Context, private val color: Int) : View(context) {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = this@DisplayIconView.color }
    private val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = this@DisplayIconView.color }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val sw = minOf(w, h) * 0.11f
        line.strokeWidth = sw
        canvas.drawRoundRect(RectF(sw / 2, sw / 2, w - sw / 2, h * 0.72f), w * 0.08f, w * 0.08f, line)
        canvas.drawRect(w * 0.45f, h * 0.72f, w * 0.55f, h * 0.88f, solid)
        canvas.drawRoundRect(RectF(w * 0.26f, h * 0.88f, w * 0.74f, h * 0.88f + sw), sw / 2, sw / 2, solid)
    }
}

/** Speaker glyph: waves when audible, a cross when muted. */
class AudioIconView(context: Context, private val color: Int) : View(context) {
    private var muted = false
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = this@AudioIconView.color
    }
    private val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = this@AudioIconView.color }

    fun set(muted: Boolean) {
        if (this.muted != muted) {
            this.muted = muted
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        line.strokeWidth = minOf(w, h) * 0.1f
        val cone = Path().apply {
            moveTo(w * 0.08f, h * 0.38f)
            lineTo(w * 0.28f, h * 0.38f)
            lineTo(w * 0.5f, h * 0.18f)
            lineTo(w * 0.5f, h * 0.82f)
            lineTo(w * 0.28f, h * 0.62f)
            lineTo(w * 0.08f, h * 0.62f)
            close()
        }
        canvas.drawPath(cone, solid)
        if (muted) {
            canvas.drawLine(w * 0.64f, h * 0.36f, w * 0.94f, h * 0.64f, line)
            canvas.drawLine(w * 0.94f, h * 0.36f, w * 0.64f, h * 0.64f, line)
        } else {
            canvas.drawArc(RectF(w * 0.38f, h * 0.3f, w * 0.72f, h * 0.7f), -55f, 110f, false, line)
            canvas.drawArc(RectF(w * 0.38f, h * 0.14f, w * 0.96f, h * 0.86f), -55f, 110f, false, line)
        }
    }
}

/** Wi-Fi glyph: up to 3 arcs for signal strength, a slash when there is no connection. */
class WifiIconView(context: Context, private val color: Int) : View(context) {
    private var connected = true
    private var arcs = 3
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = this@WifiIconView.color
    }
    private val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = this@WifiIconView.color }

    fun set(connected: Boolean, arcs: Int) {
        val a = arcs.coerceIn(0, 3)
        if (this.connected != connected || this.arcs != a) {
            this.connected = connected
            this.arcs = a
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val sw = minOf(w, h) * 0.1f
        line.strokeWidth = sw
        val cx = w / 2f
        val cy = h * 0.82f
        val radii = floatArrayOf(w * 0.2f, w * 0.35f, w * 0.5f)
        for (i in 0..2) {
            line.alpha = if (connected && i < arcs) 255 else 80
            val r = radii[i]
            canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -135f, 90f, false, line)
        }
        solid.alpha = if (connected) 255 else 80
        canvas.drawCircle(cx, cy, sw * 0.8f, solid)
        if (!connected) {
            line.alpha = 255
            canvas.drawLine(w * 0.1f, h * 0.1f, w * 0.9f, h * 0.9f, line)
        }
    }
}

/** A top-bar icon with an underline that slides in while its panel is open. */
class BarIconButton(context: Context, icon: View, lineColor: Int, iconW: Int, iconH: Int) : FrameLayout(context) {
    private val underline = View(context).apply { setBackgroundColor(lineColor); alpha = 0f; scaleX = 0f }

    init {
        addView(icon, LayoutParams(iconW, iconH, Gravity.CENTER))
        addView(underline, LayoutParams(uiDp(18), uiDp(2), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = uiDp(3)
        })
        isClickable = true
    }

    fun setActive(on: Boolean) {
        underline.animate().cancel()
        underline.animate().scaleX(if (on) 1f else 0f).alpha(if (on) 1f else 0f).setDuration(180).start()
    }
}

/** Thin slider with a knob. With [segments] > 1 it snaps and draws a segmented track (Omarchy text-size style). */
class SliderView(context: Context, private val fg: Int, private val track: Int, private val segments: Int = 0) : View(context) {
    var value = 0f
        set(v) {
            field = snap(v.coerceIn(0f, 1f))
            invalidate()
        }
    var onChange: ((Float, Boolean) -> Unit)? = null
    var dragging = false
        private set
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private fun snap(v: Float): Float =
        if (segments > 1) Math.round(v * (segments - 1)) / (segments - 1).toFloat() else v

    private fun fromX(x: Float): Float {
        val pad = uiDp(8).toFloat()
        return ((x - pad) / (width - 2 * pad)).coerceIn(0f, 1f)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                dragging = true
                value = fromX(e.x)
                onChange?.invoke(value, false)
            }
            MotionEvent.ACTION_MOVE -> {
                value = fromX(e.x)
                onChange?.invoke(value, false)
            }
            MotionEvent.ACTION_UP -> {
                dragging = false
                value = fromX(e.x)
                onChange?.invoke(value, true)
            }
            MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val pad = uiDp(8).toFloat()
        val left = pad
        val right = width - pad
        val cy = height / 2f
        val knobX = left + (right - left) * value
        val th = uiDp(4).toFloat()
        if (segments > 1) {
            val gap = uiDp(4).toFloat()
            val segW = ((right - left) - gap * (segments - 1)) / segments
            for (i in 0 until segments) {
                val x0 = left + i * (segW + gap)
                paint.color = if (x0 + segW / 2 <= knobX) fg else track
                canvas.drawRoundRect(RectF(x0, cy - th / 2, x0 + segW, cy + th / 2), th / 2, th / 2, paint)
            }
        } else {
            paint.color = track
            canvas.drawRoundRect(RectF(left, cy - th / 2, right, cy + th / 2), th / 2, th / 2, paint)
            paint.color = fg
            canvas.drawRoundRect(RectF(left, cy - th / 2, knobX, cy + th / 2), th / 2, th / 2, paint)
        }
        paint.color = fg
        canvas.drawCircle(knobX, cy, uiDp(6).toFloat(), paint)
    }
}

/** Bordered switch with a sliding square: square on the right = on. */
class SwitchBoxView(context: Context, private val fg: Int, private val border: Int) : View(context) {
    private var pos = 1f
    private var on = true
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = border }
    private val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = fg }

    fun set(on: Boolean, animate: Boolean) {
        if (this.on == on && animate) return
        this.on = on
        val target = if (on) 1f else 0f
        if (!animate) {
            pos = target
            invalidate()
        } else {
            ValueAnimator.ofFloat(pos, target).apply {
                duration = 160L
                addUpdateListener { pos = it.animatedValue as Float; invalidate() }
                start()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val sw = uiDp(2).toFloat()
        line.strokeWidth = sw
        canvas.drawRoundRect(RectF(sw / 2, sw / 2, w - sw / 2, h - sw / 2), sw, sw, line)
        val pad = sw * 3
        val size = h - 2 * pad
        val x = pad + (w - 2 * pad - size) * pos
        canvas.drawRoundRect(RectF(x, pad, x + size, pad + size), sw, sw, solid)
    }
}

/** The top-bar clock. Draws an underline that grows from the centre while the calendar is open. */
class ClockView(context: Context, lineColor: Int) : TextView(context) {
    private var fraction = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lineColor; style = Paint.Style.FILL }

    fun setActive(on: Boolean) {
        ValueAnimator.ofFloat(fraction, if (on) 1f else 0f).apply {
            duration = 180L
            addUpdateListener {
                fraction = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (fraction > 0f) {
            val half = (width - paddingLeft - paddingRight) / 2f * fraction
            val cx = width / 2f
            val y = height - uiDp(5).toFloat()
            canvas.drawRect(cx - half, y, cx + half, y + uiDp(2).toFloat(), paint)
        }
    }
}
