package com.kabigonis.sorairo

import android.animation.LayoutTransition
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.ScrollView
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

// Custom views for the world clock popup.

internal fun withAlpha(color: Int, a: Int): Int = (color and 0x00FFFFFF) or (a shl 24)

/** True if the screen point (rawX, rawY) is inside this view. */
internal fun View.containsRaw(rawX: Float, rawY: Float): Boolean {
    val loc = IntArray(2)
    getLocationOnScreen(loc)
    return rawX >= loc[0] && rawX <= loc[0] + width && rawY >= loc[1] && rawY <= loc[1] + height
}

/** Classic line globe: ring, meridians, equator and two parallels. */
internal class GlobeIconView(context: Context, tint: Int) : View(context) {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = tint
        strokeCap = Paint.Cap.ROUND
    }
    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        val s = min(width, height).toFloat()
        if (s <= 0f) return
        canvas.save()
        canvas.translate((width - s) / 2f, (height - s) / 2f)
        canvas.scale(s / 100f, s / 100f)
        line.strokeWidth = 7f
        canvas.drawCircle(50f, 50f, 44f, line)
        line.strokeWidth = 5.5f
        oval.set(30f, 6f, 70f, 94f)
        canvas.drawOval(oval, line)
        canvas.drawLine(6f, 50f, 94f, 50f, line)
        line.strokeWidth = 5f
        val hw = Math.sqrt(44.0 * 44.0 - 26.0 * 26.0).toFloat()
        canvas.drawLine(50f - hw, 24f, 50f + hw, 24f, line)
        canvas.drawLine(50f - hw, 76f, 50f + hw, 76f, line)
        canvas.restore()
    }
}

/** Small thin-line weather glyph. Night swaps the sun for a moon. */
internal class WeatherIconView(context: Context, tint: Int) : View(context) {
    private var kind = WeatherKind.CLOUDY
    private var day = true
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = tint; strokeWidth = 1.7f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = tint }

    private val cloud = Path().apply {
        val parts = listOf(
            Path().apply { addCircle(7.2f, 14f, 3.5f, Path.Direction.CW) },
            Path().apply { addCircle(12f, 11.5f, 4.6f, Path.Direction.CW) },
            Path().apply { addCircle(16.8f, 14.2f, 3.3f, Path.Direction.CW) },
            Path().apply { addRect(7.2f, 14f, 16.8f, 17.5f, Path.Direction.CW) }
        )
        set(parts[0])
        for (i in 1 until parts.size) op(parts[i], Path.Op.UNION)
    }
    private val moon = Path().apply {
        set(Path().apply { addCircle(11f, 12f, 6.5f, Path.Direction.CW) })
        op(Path().apply { addCircle(15f, 9.5f, 5.6f, Path.Direction.CW) }, Path.Op.DIFFERENCE)
    }

    fun set(kind: WeatherKind, day: Boolean) {
        if (this.kind != kind || this.day != day) {
            this.kind = kind
            this.day = day
            invalidate()
        }
    }

    private fun drawCloud(c: Canvas, dx: Float, dy: Float, sc: Float) {
        c.save()
        c.translate(dx, dy)
        c.scale(sc, sc)
        c.drawPath(cloud, stroke)
        c.restore()
    }

    private fun cloudAt(dx: Float, dy: Float, sc: Float): Path =
        Path(cloud).apply { transform(Matrix().apply { setScale(sc, sc); postTranslate(dx, dy) }) }

    private fun sunOrMoon(c: Canvas, cx: Float, cy: Float, sc: Float) {
        if (day) {
            c.drawCircle(cx, cy, 4.2f * sc, stroke)
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val ca = cos(a).toFloat()
                val sa = sin(a).toFloat()
                c.drawLine(cx + ca * 6.3f * sc, cy + sa * 6.3f * sc, cx + ca * 8.6f * sc, cy + sa * 8.6f * sc, stroke)
            }
        } else {
            c.save()
            c.translate(cx, cy)
            c.scale(sc, sc)
            c.translate(-11f, -12f)
            c.drawPath(moon, stroke)
            c.restore()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val s = min(width, height) / 24f
        if (s <= 0f) return
        canvas.save()
        canvas.translate((width - 24f * s) / 2f, (height - 24f * s) / 2f)
        canvas.scale(s, s)
        when (kind) {
            WeatherKind.CLEAR -> sunOrMoon(canvas, 12f, 12f, 1f)
            WeatherKind.PARTLY -> {
                canvas.save()
                canvas.clipOutPath(cloudAt(2f, 4f, 0.82f))
                sunOrMoon(canvas, 8f, 8f, 0.62f)
                canvas.restore()
                drawCloud(canvas, 2f, 4f, 0.82f)
            }
            WeatherKind.CLOUDY -> drawCloud(canvas, 0f, 0f, 1f)
            WeatherKind.FOG -> {
                drawCloud(canvas, 0f, -3.5f, 1f)
                canvas.drawLine(5f, 17.5f, 19f, 17.5f, stroke)
                canvas.drawLine(8f, 20.5f, 16f, 20.5f, stroke)
            }
            WeatherKind.DRIZZLE -> {
                drawCloud(canvas, 0f, -3.5f, 1f)
                canvas.drawLine(8.5f, 17f, 8.5f, 19f, stroke)
                canvas.drawLine(12f, 18.4f, 12f, 20.4f, stroke)
                canvas.drawLine(15.5f, 17f, 15.5f, 19f, stroke)
            }
            WeatherKind.RAIN -> {
                drawCloud(canvas, 0f, -3.5f, 1f)
                canvas.drawLine(9f, 16.2f, 7.8f, 20f, stroke)
                canvas.drawLine(12.5f, 16.2f, 11.3f, 20f, stroke)
                canvas.drawLine(16f, 16.2f, 14.8f, 20f, stroke)
            }
            WeatherKind.SNOW -> {
                drawCloud(canvas, 0f, -3.5f, 1f)
                canvas.drawCircle(8.5f, 17.6f, 1f, solid)
                canvas.drawCircle(12f, 20.6f, 1f, solid)
                canvas.drawCircle(15.5f, 17.6f, 1f, solid)
            }
            WeatherKind.STORM -> {
                drawCloud(canvas, 0f, -3.5f, 1f)
                val bolt = Path().apply {
                    moveTo(13f, 15.6f)
                    lineTo(10.5f, 19.4f)
                    lineTo(13.2f, 19.4f)
                    lineTo(11f, 23f)
                }
                canvas.drawPath(bolt, stroke)
            }
        }
        canvas.restore()
    }
}

/**
 * A 24 hour track: the daylight stretch is brighter, the knob sits at the city's time of day and
 * turns sun-coloured while the sun is up. The knob can glide in from the left edge, can be dragged,
 * and slides off one end and back in from the other when the day rolls over.
 */
internal class DayTrackView(
    context: Context,
    private val trackColor: Int,
    private val daylightColor: Int,
    private val dayKnob: Int,
    private val nightKnob: Int
) : View(context) {
    var sun: SunWindow = SunWindow(6.0, 18.0)
        set(v) {
            if (field != v) {
                field = v
                invalidate()
            }
        }
    private var shown = 0f
    private var knobAlpha = 1f
    private var placed = false
    private var anim: ValueAnimator? = null
    private var sweeping = false
    private var dragging = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()

    /** Called with the knob position (0..1, snapped to 15 minutes) while the user drags it. */
    var onScrub: ((Float) -> Unit)? = null

    private fun endWhenDone(a: ValueAnimator) {
        a.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                sweeping = false
                knobAlpha = 1f
                invalidate()
            }
        })
    }

    /** Moves the knob to [fraction] (0..1). With [durationMs] > 0 it glides there smoothly. */
    fun setFraction(fraction: Float, durationMs: Long = 0L, delayMs: Long = 0L, fromStart: Boolean = false) {
        val target = fraction.coerceIn(0f, 1f)
        if (durationMs <= 0L) {
            if (sweeping || dragging) return
            if (!placed) {
                placed = true
                shown = target
                invalidate()
                return
            }
            if (abs(target - shown) > 0.5f) {
                wrapTo(target)
                return
            }
            if (shown != target) {
                shown = target
                invalidate()
            }
            return
        }
        anim?.cancel()
        sweeping = true
        placed = true
        if (fromStart) {
            shown = 0f
            invalidate()
        }
        anim = ValueAnimator.ofFloat(shown, target).apply {
            duration = durationMs
            startDelay = delayMs
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener {
                shown = it.animatedValue as Float
                invalidate()
            }
            endWhenDone(this)
            start()
        }
    }

    /** The day rolled over: slide off one end, fade, come back in from the other end. */
    private fun wrapTo(target: Float) {
        val forward = target < shown
        val start = shown
        anim?.cancel()
        sweeping = true
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 560L
            addUpdateListener {
                val t = it.animatedValue as Float
                if (t < 0.5f) {
                    val u = t / 0.5f
                    shown = if (forward) start + (1f - start) * u else start * (1f - u)
                    knobAlpha = 1f - u
                } else {
                    val u = (t - 0.5f) / 0.5f
                    shown = if (forward) target * u else 1f - (1f - target) * u
                    knobAlpha = u
                }
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    sweeping = false
                    knobAlpha = 1f
                    shown = target
                    invalidate()
                }
            })
            start()
        }
    }

    private fun scrubTo(x: Float) {
        val pad = uiDp(7).toFloat()
        val f = ((x - pad) / (width - 2 * pad)).coerceIn(0f, 1f)
        shown = Math.round(f * 96f) / 96f
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val cb = onScrub ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                anim?.cancel()
                sweeping = false
                knobAlpha = 1f
                dragging = true
                placed = true
                scrubTo(e.x)
                cb(shown)
            }
            MotionEvent.ACTION_MOVE -> if (dragging) {
                scrubTo(e.x)
                cb(shown)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        sweeping = false
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val pad = uiDp(7).toFloat()
        val left = pad
        val right = width - pad
        val cy = height / 2f
        val th = uiDp(3).toFloat()
        paint.color = trackColor
        rect.set(left, cy - th / 2, right, cy + th / 2)
        canvas.drawRoundRect(rect, th / 2, th / 2, paint)
        paint.color = daylightColor
        for ((from, to) in sun.segments()) {
            val x0 = left + (right - left) * (from / 24.0).toFloat()
            val x1 = left + (right - left) * (to / 24.0).toFloat()
            rect.set(x0, cy - th / 2, x1, cy + th / 2)
            canvas.drawRoundRect(rect, th / 2, th / 2, paint)
        }
        val knob = if (sun.isUp(shown * 24.0)) dayKnob else nightKnob
        paint.color = withAlpha(knob, (255 * knobAlpha).toInt().coerceIn(0, 255))
        canvas.drawCircle(left + (right - left) * shown, cy, uiDp(6).toFloat(), paint)
    }
}

/** A dotted, draggable globe: land lights up on the day side, a line marks sunrise/sunset, cities are pinned. */
internal class GlobeModelView(
    context: Context,
    private val landColor: Int,
    private val oceanColor: Int,
    limbColor: Int,
    private val chosenColor: Int,
    private val cityColor: Int,
    sunColor: Int,
    private val labelColor: Int,
    haloColor: Int,
    labelFont: Typeface
) : View(context) {
    class Marker(val name: String, val lat: Double, val lon: Double)

    var markers: List<Marker> = emptyList()
        set(v) {
            field = v
            invalidate()
        }
    var focusIndex: Int = -1
        set(v) {
            field = v
            invalidate()
        }

    /** A tentative pin (a tapped city that has not been added yet). */
    var preview: Marker? = null
        set(v) {
            field = v
            invalidate()
        }

    /** Called when the globe is tapped: the (lat, lon) under the finger, or null for a tap off the globe. */
    var onPick: ((Pair<Double, Double>?) -> Unit)? = null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var lat0 = 20.0
    private var lon0 = 0.0
    private var sunLat = 0.0
    private var sunLon = 0.0
    private var anim: ValueAnimator? = null
    private var lastX = 0f
    private var lastY = 0f
    private val pt = FloatArray(2)
    private val boxes = ArrayList<RectF>()
    private val termPath = Path()
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val limbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = limbColor }
    private val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = chosenColor }
    private val termPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = withAlpha(sunColor, 0xB8)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = labelColor; typeface = labelFont }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = withAlpha(haloColor, 0xD0)
        strokeJoin = Paint.Join.ROUND
        typeface = labelFont
    }

    private fun tiltFor(lat: Double) = (lat * 0.6).coerceIn(-55.0, 55.0)

    private fun normLon(l: Double): Double {
        var x = l
        while (x > 180.0) x -= 360.0
        while (x < -180.0) x += 360.0
        return x
    }

    fun setSun(lat: Double, lon: Double) {
        if (abs(lat - sunLat) > 0.02 || abs(lon - sunLon) > 0.02) {
            sunLat = lat
            sunLon = lon
            invalidate()
        }
    }

    fun jumpTo(lat: Double, lon: Double) {
        anim?.cancel()
        lat0 = tiltFor(lat)
        lon0 = normLon(lon)
        invalidate()
    }

    /** Spins the globe so that (lat, lon) faces the viewer. */
    fun focusOn(lat: Double, lon: Double, durationMs: Long = 650L) {
        anim?.cancel()
        val startLat = lat0
        val startLon = lon0
        val targetLat = tiltFor(lat)
        val dLon = ((lon - startLon + 540.0) % 360.0 + 360.0) % 360.0 - 180.0
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                val t = (it.animatedValue as Float).toDouble()
                lat0 = startLat + (targetLat - startLat) * t
                lon0 = normLon(startLon + dLon * t)
                invalidate()
            }
            start()
        }
    }

    private fun radius(): Float = min(width, height) / 2f - uiDp(8)

    private fun pickAt(x: Float, y: Float) {
        val r = radius()
        if (r <= 0f) return
        val px = ((x - width / 2f) / r).toDouble()
        val py = ((height / 2f - y) / r).toDouble()
        val rho = hypot(px, py)
        if (rho > 1.0) {
            onPick?.invoke(null)
            return
        }
        val la0 = Math.toRadians(lat0)
        val lo0 = Math.toRadians(lon0)
        if (rho < 1e-9) {
            onPick?.invoke(Pair(lat0, lon0))
            return
        }
        val c = asin(rho)
        val lat = asin(cos(c) * sin(la0) + py * sin(c) * cos(la0) / rho)
        val lon = lo0 + atan2(px * sin(c), rho * cos(la0) * cos(c) - py * sin(la0) * sin(c))
        onPick?.invoke(Pair(Math.toDegrees(lat), normLon(Math.toDegrees(lon))))
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                anim?.cancel()
                downX = e.x
                downY = e.y
                lastX = e.x
                lastY = e.y
                moved = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!moved && hypot((e.x - downX).toDouble(), (e.y - downY).toDouble()) > slop) {
                    moved = true
                    lastX = e.x
                    lastY = e.y
                }
                if (moved) {
                    val r = radius().coerceAtLeast(1f)
                    lon0 = normLon(lon0 - Math.toDegrees(((e.x - lastX) / r).toDouble()))
                    lat0 = (lat0 + Math.toDegrees(((e.y - lastY) / r).toDouble())).coerceIn(-70.0, 70.0)
                    lastX = e.x
                    lastY = e.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (!moved) pickAt(e.x, e.y)
            }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    /** Projects a point on the sphere; returns how much it faces the viewer (<= 0 means hidden). */
    private fun proj(latR: Double, lonR: Double, cx: Float, cy: Float, r: Float, sl0: Double, cl0: Double, lo0: Double): Double {
        val dl = lonR - lo0
        val cdl = cos(dl)
        pt[0] = cx + (r * cos(latR) * sin(dl)).toFloat()
        pt[1] = cy - (r * (cl0 * sin(latR) - sl0 * cos(latR) * cdl)).toFloat()
        return sl0 * sin(latR) + cl0 * cos(latR) * cdl
    }

    private fun drawTerminator(canvas: Canvas, cx: Float, cy: Float, r: Float, sl0: Double, cl0: Double, lo0: Double) {
        val sLa = Math.toRadians(sunLat)
        val sLo = Math.toRadians(sunLon)
        val sx = cos(sLa) * cos(sLo)
        val sy = cos(sLa) * sin(sLo)
        val sz = sin(sLa)
        var ax = sy
        var ay = -sx
        var len = hypot(ax, ay)
        if (len < 1e-6) {
            ax = 1.0
            ay = 0.0
            len = 1.0
        }
        ax /= len
        ay /= len
        val bx = -sz * ay
        val by = sz * ax
        val bz = sx * ay - sy * ax
        termPath.reset()
        var pen = false
        val steps = 180
        for (i in 0..steps) {
            val t = 2.0 * Math.PI * i / steps
            val x = cos(t) * ax + sin(t) * bx
            val y = cos(t) * ay + sin(t) * by
            val z = sin(t) * bz
            val c = proj(asin(z.coerceIn(-1.0, 1.0)), atan2(y, x), cx, cy, r, sl0, cl0, lo0)
            if (c > 0.0) {
                if (pen) termPath.lineTo(pt[0], pt[1]) else termPath.moveTo(pt[0], pt[1])
                pen = true
            } else {
                pen = false
            }
        }
        termPaint.strokeWidth = maxOf(1f, uiDp(1) * 1.2f)
        canvas.drawPath(termPath, termPaint)
    }

    /** Draws [text] beside (px, py) unless it would run into a label that is already there. */
    private fun drawLabel(canvas: Canvas, text: String, px: Float, py: Float, color: Int, dx: Float) {
        val w = labelPaint.measureText(text)
        val h = labelPaint.textSize
        val box = RectF(px + dx - 2f, py - h * 0.6f, px + dx + w + 2f, py + h * 0.6f)
        for (b in boxes) if (RectF.intersects(b, box)) return
        boxes.add(box)
        haloPaint.textSize = labelPaint.textSize
        canvas.drawText(text, px + dx, py + h / 3f, haloPaint)
        labelPaint.color = color
        canvas.drawText(text, px + dx, py + h / 3f, labelPaint)
    }

    override fun onDraw(canvas: Canvas) {
        val r = radius()
        if (r <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        limbPaint.strokeWidth = maxOf(1f, uiDp(1).toFloat())
        canvas.drawCircle(cx, cy, r, limbPaint)

        val la0 = Math.toRadians(lat0)
        val lo0 = Math.toRadians(lon0)
        val sl0 = sin(la0)
        val cl0 = cos(la0)
        val sunLa = Math.toRadians(sunLat)
        val sunLo = Math.toRadians(sunLon)
        val sunSin = sin(sunLa)
        val sunCos = cos(sunLa)
        val d = WorldMap.dots
        val landR = maxOf(uiDp(1) * 1.1f, r * 0.0105f)
        val seaR = landR * 0.42f
        for (i in 0 until d.size) {
            val dl = d.lon[i] - lo0
            val cdl = cos(dl)
            val cosc = sl0 * d.sinLat[i] + cl0 * d.cosLat[i] * cdl
            if (cosc <= 0.03) continue
            val x = d.cosLat[i] * sin(dl)
            val y = cl0 * d.sinLat[i] - sl0 * d.cosLat[i] * cdl
            val sunDot = sunSin * d.sinLat[i] + sunCos * d.cosLat[i] * cos(d.lon[i] - sunLo)
            val day = ((sunDot + 0.1) / 0.3).coerceIn(0.0, 1.0).toFloat()
            val squash = (0.45 + 0.55 * cosc).toFloat()
            val rad: Float
            if (d.land[i]) {
                dotPaint.color = withAlpha(landColor, (70 + 185 * day).toInt())
                rad = landR * squash
            } else {
                dotPaint.color = withAlpha(oceanColor, (20 + 80 * day).toInt())
                rad = seaR * squash
            }
            canvas.drawCircle(cx + (r * x).toFloat(), cy - (r * y).toFloat(), rad, dotPaint)
        }

        drawTerminator(canvas, cx, cy, r, sl0, cl0, lo0)

        labelPaint.textSize = uiDp(11).toFloat()
        pinPaint.strokeWidth = maxOf(1f, uiDp(1).toFloat())
        boxes.clear()

        // your cities first, so their names win any collision
        markers.forEachIndexed { i, m ->
            val c = proj(Math.toRadians(m.lat), Math.toRadians(m.lon), cx, cy, r, sl0, cl0, lo0)
            if (c > 0.05) {
                val focused = i == focusIndex
                val px = pt[0]
                val py = pt[1]
                dotPaint.color = chosenColor
                canvas.drawCircle(px, py, uiDp(if (focused) 4 else 3).toFloat(), dotPaint)
                pinPaint.color = chosenColor
                canvas.drawCircle(px, py, uiDp(if (focused) 8 else 6).toFloat(), pinPaint)
                drawLabel(canvas, m.name, px, py, chosenColor, uiDp(12).toFloat())
            }
        }
        preview?.let { m ->
            val c = proj(Math.toRadians(m.lat), Math.toRadians(m.lon), cx, cy, r, sl0, cl0, lo0)
            if (c > 0.05) {
                val px = pt[0]
                val py = pt[1]
                pinPaint.color = withAlpha(chosenColor, 0xC0)
                canvas.drawCircle(px, py, uiDp(5).toFloat(), pinPaint)
                canvas.drawCircle(px, py, uiDp(9).toFloat(), pinPaint)
                drawLabel(canvas, m.name, px, py, chosenColor, uiDp(13).toFloat())
            }
        }
        // the well-known cities from the reference globes
        for (c in CityDb.globeCities) {
            if (markers.any { it.name == c.name } || preview?.name == c.name) continue
            val facing = proj(c.latR, c.lonR, cx, cy, r, sl0, cl0, lo0)
            if (facing <= 0.05) continue
            dotPaint.color = cityColor
            canvas.drawCircle(pt[0], pt[1], uiDp(2).toFloat(), dotPaint)
            if (facing > 0.35) drawLabel(canvas, c.name, pt[0], pt[1], withAlpha(labelColor, 0xE0), uiDp(6).toFloat())
        }
    }
}

/** Small line icons for the buttons on each clock card. */
internal class CardActionIconView(context: Context, private val kind: Kind, tint: Int) : View(context) {
    enum class Kind { NOTE, CLOSE }

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = tint
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 1.9f
    }
    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        val aw = width - paddingLeft - paddingRight
        val ah = height - paddingTop - paddingBottom
        val s = min(aw, ah).toFloat()
        if (s <= 0f) return
        canvas.save()
        canvas.translate(paddingLeft + (aw - s) / 2f, paddingTop + (ah - s) / 2f)
        canvas.scale(s / 24f, s / 24f)
        when (kind) {
            Kind.CLOSE -> {
                canvas.drawLine(6f, 6f, 18f, 18f, line)
                canvas.drawLine(18f, 6f, 6f, 18f, line)
            }
            Kind.NOTE -> {
                canvas.drawCircle(9.5f, 8f, 3.4f, line)
                oval.set(3f, 13.5f, 16f, 27f)
                canvas.drawArc(oval, 200f, 140f, false, line)
                canvas.drawLine(19f, 6f, 19f, 12f, line)
                canvas.drawLine(16f, 9f, 22f, 9f, line)
            }
        }
        canvas.restore()
    }
}

/**
 * Vertical list whose children can be reordered: press and hold a card, then drag it up or down.
 * Other cards slide out of the way; on release everything glides into its final place.
 */
internal class ReorderLayout(context: Context) : LinearLayout(context) {
    /** Return false to stop a long press at this screen point from starting a drag (sliders, text fields). */
    var canStartDrag: ((View, Float, Float) -> Boolean)? = null
    var onReordered: ((Int, Int) -> Unit)? = null
    var onDragStart: (() -> Unit)? = null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val uiHandler = android.os.Handler(Looper.getMainLooper())
    private var candidate: View? = null
    private var downRawX = 0f
    private var downRawY = 0f
    private var dragView: View? = null
    private var dragFrom = -1
    private var dragTarget = -1
    private var origTops = IntArray(0)
    private var savedTransition: LayoutTransition? = null
    private val longPress = Runnable { startDrag() }
    private var lastRawY = 0f
    private var dragScrollStart = 0
    private val autoScroll = object : Runnable {
        override fun run() {
            val card = dragView ?: return
            val sv = parent as? ScrollView
            if (sv != null) {
                val loc = IntArray(2)
                sv.getLocationOnScreen(loc)
                val edge = uiDp(40)
                val step = uiDp(9)
                val dy = when {
                    lastRawY < loc[1] + edge -> -step
                    lastRawY > loc[1] + sv.height - edge -> step
                    else -> 0
                }
                if (dy != 0 && sv.canScrollVertically(if (dy < 0) -1 else 1)) {
                    sv.scrollBy(0, dy)
                    moveDragged(card)
                }
            }
            uiHandler.postDelayed(this, 16L)
        }
    }

    init {
        orientation = VERTICAL
    }

    private fun cancelPending() {
        uiHandler.removeCallbacks(longPress)
        candidate = null
    }

    private fun childAt(x: Float, y: Float): View? {
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (x >= c.left && x < c.right && y >= c.top && y < c.bottom) return c
        }
        return null
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelPending()
                if (childCount > 1) {
                    val card = childAt(ev.x, ev.y)
                    if (card != null && canStartDrag?.invoke(card, ev.rawX, ev.rawY) != false) {
                        candidate = card
                        downRawX = ev.rawX
                        downRawY = ev.rawY
                        uiHandler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong() + 80L)
                    }
                }
            }
            MotionEvent.ACTION_MOVE ->
                if (dragView == null && candidate != null &&
                    hypot((ev.rawX - downRawX).toDouble(), (ev.rawY - downRawY).toDouble()) > slop
                ) {
                    cancelPending()
                }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragView != null) {
                    // released without moving after the long press: the UP never reaches onTouchEvent
                    endDrag(ev.actionMasked == MotionEvent.ACTION_UP)
                    return true
                }
                cancelPending()
            }
        }
        return dragView != null
    }

    private fun startDrag() {
        val card = candidate ?: return
        candidate = null
        dragView = card
        dragFrom = indexOfChild(card)
        dragTarget = dragFrom
        parent?.requestDisallowInterceptTouchEvent(true)
        savedTransition = layoutTransition
        layoutTransition = null
        origTops = IntArray(childCount) { getChildAt(it).top }
        dragScrollStart = (parent as? ScrollView)?.scrollY ?: 0
        lastRawY = downRawY
        uiHandler.postDelayed(autoScroll, 16L)
        card.animate().cancel()
        card.animate().scaleX(1.03f).scaleY(1.03f).translationZ(uiDp(8).toFloat()).setDuration(140L).start()
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onDragStart?.invoke()
    }

    private fun slotHeight(card: View): Float {
        val lp = card.layoutParams as? MarginLayoutParams
        return card.height + (lp?.bottomMargin ?: 0) + (lp?.topMargin ?: 0).toFloat()
    }

    /** Puts the dragged card under the finger, but never lets it leave the list. */
    private fun moveDragged(card: View) {
        val scrolled = ((parent as? ScrollView)?.scrollY ?: 0) - dragScrollStart
        val lp = card.layoutParams as? MarginLayoutParams
        val minTy = -origTops[dragFrom].toFloat()
        val maxTy = (height - card.height - (lp?.bottomMargin ?: 0) - origTops[dragFrom]).toFloat()
        card.translationY = (lastRawY - downRawY + scrolled).coerceIn(minTy, maxOf(minTy, maxTy))
        updateTarget(card)
    }

    private fun updateTarget(card: View) {
        val center = origTops[dragFrom] + card.height / 2f + card.translationY
        var target = 0
        for (i in 0 until childCount) {
            if (i == dragFrom) continue
            val c = getChildAt(i)
            if (origTops[i] + c.height / 2f < center) target++
        }
        if (target == dragTarget) return
        dragTarget = target
        val slot = slotHeight(card)
        for (i in 0 until childCount) {
            if (i == dragFrom) continue
            val shift = when {
                dragFrom < target && i in (dragFrom + 1)..target -> -slot
                dragFrom > target && i in target until dragFrom -> slot
                else -> 0f
            }
            val c = getChildAt(i)
            c.animate().cancel()
            c.animate().translationY(shift).setDuration(170L).setInterpolator(DecelerateInterpolator()).start()
        }
    }

    private fun endDrag(commit: Boolean) {
        val card = dragView ?: return
        dragView = null
        uiHandler.removeCallbacks(autoScroll)
        val from = dragFrom
        val to = if (commit) dragTarget else dragFrom
        val before = HashMap<View, Float>()
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            before[c] = c.top + c.translationY
            c.animate().cancel()
            c.translationY = 0f
        }
        card.translationZ = 0f
        if (to != from) {
            removeView(card)
            addView(card, to)
        }
        parent?.requestDisallowInterceptTouchEvent(false)
        val vto = viewTreeObserver
        vto.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                viewTreeObserver.removeOnPreDrawListener(this)
                for (i in 0 until childCount) {
                    val c = getChildAt(i)
                    val delta = (before[c] ?: c.top.toFloat()) - c.top
                    if (abs(delta) > 0.5f) {
                        c.translationY = delta
                        c.animate().translationY(0f).setDuration(240L).setInterpolator(DecelerateInterpolator(1.6f)).start()
                    }
                }
                card.animate().scaleX(1f).scaleY(1f).setDuration(200L).start()
                layoutTransition = savedTransition
                return true
            }
        })
        if (to != from) onReordered?.invoke(from, to)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val card = dragView ?: return super.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                lastRawY = ev.rawY
                moveDragged(card)
            }
            MotionEvent.ACTION_UP -> endDrag(true)
            MotionEvent.ACTION_CANCEL -> endDrag(false)
        }
        return true
    }
}

/** A ScrollView that stops growing at [maxHeightPx] so the popup never outgrows the screen. */
internal class BoundedScrollView(context: Context, var maxHeightPx: Int) : ScrollView(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cap = min(maxHeightPx, MeasureSpec.getSize(heightMeasureSpec).takeIf { it > 0 } ?: maxHeightPx)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST))
    }
}

