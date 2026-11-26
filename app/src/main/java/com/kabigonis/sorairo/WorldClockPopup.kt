package com.kabigonis.sorairo

import android.animation.LayoutTransition
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

// World clock popup: a globe icon beside the top-bar clock opens a card per city with local time,
// weather, offset from home and a day/night track. Matches the other top-bar panels (showBarDialog).

// ---------------------------------------------------------------- views

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
 * turns sun-coloured while the sun is up. The knob can glide in from the left edge.
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
    private var anim: ValueAnimator? = null
    private var sweeping = false
    private var dragging = false

    /** Called with the knob position (0..1, snapped to 15 minutes) while the user drags it. */
    var onScrub: ((Float) -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()

    /** Moves the knob to [fraction] (0..1). With [durationMs] > 0 it glides there smoothly. */
    fun setFraction(fraction: Float, durationMs: Long = 0L, delayMs: Long = 0L, fromStart: Boolean = false) {
        val target = fraction.coerceIn(0f, 1f)
        if (durationMs <= 0L) {
            if (sweeping || dragging) return
            if (shown != target) {
                shown = target
                invalidate()
            }
            return
        }
        anim?.cancel()
        sweeping = true
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
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    sweeping = false
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
                dragging = true
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
        paint.color = if (sun.isUp(shown * 24.0)) dayKnob else nightKnob
        canvas.drawCircle(left + (right - left) * shown, cy, uiDp(6).toFloat(), paint)
    }
}

/** A dotted, draggable globe: land lights up on the day side and every city is pinned on it. */
internal class GlobeModelView(
    context: Context,
    private val landColor: Int,
    private val oceanColor: Int,
    ringColor: Int,
    private val markerColor: Int,
    labelColor: Int,
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
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = ringColor }
    private val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = markerColor }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = labelColor; typeface = labelFont }

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
        val rho = Math.hypot(px, py)
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
                if (!moved && Math.hypot((e.x - downX).toDouble(), (e.y - downY).toDouble()) > slop) {
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

    override fun onDraw(canvas: Canvas) {
        val r = radius()
        if (r <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        ringPaint.strokeWidth = maxOf(1f, uiDp(1).toFloat())
        canvas.drawCircle(cx, cy, r, ringPaint)

        val la0 = Math.toRadians(lat0)
        val lo0 = Math.toRadians(lon0)
        val sl0 = sin(la0)
        val cl0 = cos(la0)
        val sunLa = Math.toRadians(sunLat)
        val sunLo = Math.toRadians(sunLon)
        val sunSin = sin(sunLa)
        val sunCos = cos(sunLa)
        val d = WorldMap.dots
        val landR = uiDp(2) * 0.9f
        val seaR = uiDp(1) * 0.8f
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

        labelPaint.textSize = uiDp(10).toFloat()
        pinPaint.strokeWidth = maxOf(1f, uiDp(1).toFloat())
        markers.forEachIndexed { i, m ->
            val la = Math.toRadians(m.lat)
            val dl = Math.toRadians(m.lon) - lo0
            val cosc = sl0 * sin(la) + cl0 * cos(la) * cos(dl)
            if (cosc > 0.05) {
                val px = cx + (r * cos(la) * sin(dl)).toFloat()
                val py = cy - (r * (cl0 * sin(la) - sl0 * cos(la) * cos(dl))).toFloat()
                val focused = i == focusIndex
                dotPaint.color = markerColor
                canvas.drawCircle(px, py, uiDp(if (focused) 4 else 3).toFloat(), dotPaint)
                canvas.drawCircle(px, py, uiDp(if (focused) 8 else 6).toFloat(), pinPaint)
                canvas.drawText(m.name, px + uiDp(11), py + labelPaint.textSize / 3f, labelPaint)
            }
        }
        preview?.let { m ->
            val la = Math.toRadians(m.lat)
            val dl = Math.toRadians(m.lon) - lo0
            val cosc = sl0 * sin(la) + cl0 * cos(la) * cos(dl)
            if (cosc > 0.05) {
                val px = cx + (r * cos(la) * sin(dl)).toFloat()
                val py = cy - (r * (cl0 * sin(la) - sl0 * cos(la) * cos(dl))).toFloat()
                pinPaint.color = withAlpha(markerColor, 0xB0)
                canvas.drawCircle(px, py, uiDp(5).toFloat(), pinPaint)
                canvas.drawCircle(px, py, uiDp(9).toFloat(), pinPaint)
                canvas.drawText(m.name, px + uiDp(12), py + labelPaint.textSize / 3f, labelPaint)
                pinPaint.color = markerColor
            }
        }
    }
}

/** A ScrollView that stops growing at [maxHeightPx] so the popup never outgrows the screen. */
internal class BoundedScrollView(context: Context, private val maxHeightPx: Int) : ScrollView(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cap = min(maxHeightPx, MeasureSpec.getSize(heightMeasureSpec).takeIf { it > 0 } ?: maxHeightPx)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST))
    }
}

// ---------------------------------------------------------------- popup

private class WorldRow(
    val city: WorldCity,
    val zone: ZoneId,
    val card: LinearLayout,
    val dateTv: TextView,
    val tempTv: TextView,
    val weatherIcon: WeatherIconView,
    val weatherBox: LinearLayout,
    val removeBtn: TextView,
    val timeTv: TextView,
    val ampmTv: TextView,
    val zoneTv: TextView,
    val track: DayTrackView
) {
    var weather: CityWeather? = null
}

private fun withAlpha(color: Int, a: Int): Int = (color and 0x00FFFFFF) or (a shl 24)

/** Changes the text and, if [animate], dips the alpha so the change reads as a soft tick. */
private fun smoothText(tv: TextView, s: String, animate: Boolean) {
    if (tv.text.toString() == s) return
    tv.text = s
    if (animate) {
        tv.animate().cancel()
        tv.alpha = 0.4f
        tv.animate().alpha(1f).setDuration(240L).start()
    }
}

internal fun MainActivity.showWorldClockPanel() {
    val act = this
    val p = pal
    val home = ZoneId.systemDefault()
    var is24 = if (prefs.contains("world_24h")) {
        prefs.getBoolean("world_24h", false)
    } else {
        android.text.format.DateFormat.is24HourFormat(this)
    }
    var shiftMs = 0L
    var showGlobe = prefs.getBoolean("world_globe", true)
    val dateFmt = DateTimeFormatter.ofPattern("EEE MMM d", Locale.getDefault())
    var fahrenheit = if (prefs.contains("world_fahrenheit")) {
        prefs.getBoolean("world_fahrenheit", false)
    } else {
        usesFahrenheit(Locale.getDefault())
    }
    var editMode = false
    var hereText = ""
    var dialog: AlertDialog? = null
    val rows = ArrayList<WorldRow>()
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)

    // ---- skeleton ----
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(14), dp(18), dp(16))
        background = box(p.cBg or (0xFF shl 24), p.cBlue)
    }
    val hereTv = txt(11f, p.cDim, "").apply { gravity = Gravity.CENTER }
    root.addView(hereTv, lp(-1, -2))

    val titleRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }
    titleRow.addView(GlobeIconView(act, p.cFg), lp(dp(32), dp(32)).apply { marginEnd = dp(12) })
    titleRow.addView(txt(20f, p.cFg, "World Clock").apply { setTypeface(typeface, Typeface.BOLD) })
    root.addView(titleRow, lp(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(8) })

    val globe = GlobeModelView(this, p.cFg, p.cDim, withAlpha(p.cDim, 0x90), p.cAnsi[3], p.cDim, font)
    val pill = txt(12f, p.cFg, "").apply {
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = box(p.cBg or (0xFF shl 24), p.cBlue)
        isClickable = true
        alpha = 0f
        visibility = View.GONE
    }
    val globeBox = FrameLayout(this)
    globeBox.visibility = if (showGlobe) View.VISIBLE else View.GONE
    globeBox.addView(globe, FrameLayout.LayoutParams(-1, -1))
    globeBox.addView(
        pill,
        FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(4) }
    )
    root.addView(globeBox, lp(-1, dp(190)).apply { bottomMargin = dp(10) })
    titleRow.setOnClickListener {
        showGlobe = !showGlobe
        prefs.edit().putBoolean("world_globe", showGlobe).apply()
        globeBox.visibility = if (showGlobe) View.VISIBLE else View.GONE
    }

    val cardsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val scroll = BoundedScrollView(this, (resources.displayMetrics.heightPixels * 0.55f).toInt()).apply {
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        addView(cardsBox, FrameLayout.LayoutParams(-1, -2))
    }
    root.addView(scroll, lp(-1, -2))

    val emptyTv = txt(12f, p.cDim, "no cities yet").apply {
        gravity = Gravity.CENTER
        setPadding(0, dp(14), 0, dp(14))
        visibility = View.GONE
    }
    root.addView(emptyTv, lp(-1, -2))

    // ---- add a city ----
    val addArea = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val addBtn = txt(13f, p.cFg, "+   Add a city").apply {
        gravity = Gravity.CENTER
        setPadding(0, dp(11), 0, dp(11))
        isClickable = true
        background = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(dp(1), p.cDim)
        }
    }
    val inputSize = 14f * uiScale * (uiTextSize / 13f)
    val input = EditText(this).apply {
        hint = "Search cities…"
        setHintTextColor(p.cDim)
        setTextColor(p.cFg)
        typeface = font
        textSize = inputSize
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        imeOptions = EditorInfo.IME_ACTION_DONE
        background = null
        setPadding(dp(14), dp(11), dp(6), dp(11))
    }
    val cancelBtn = txt(20f, p.cDim, "×").apply {
        setPadding(dp(12), dp(2), dp(14), dp(4))
        isClickable = true
    }
    val searchRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply {
            setColor(withAlpha(p.cSel, 0x40))
            setStroke(dp(1), p.cDim)
        }
        visibility = View.GONE
    }
    searchRow.addView(input, lp(0, -2).apply { weight = 1f })
    searchRow.addView(cancelBtn, lp(-2, -2))
    val statusTv = txt(11f, p.cDim, "").apply {
        setPadding(dp(4), dp(8), dp(4), dp(2))
        visibility = View.GONE
    }

    var results: List<DbCity> = emptyList()
    var highlighted = 0
    val resultsList = ListView(this).apply {
        divider = null
        dividerHeight = 0
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        selector = ColorDrawable(Color.TRANSPARENT)
        visibility = View.GONE
    }
    val cityAdapter = object : BaseAdapter() {
        override fun getCount(): Int = results.size
        override fun getItem(position: Int): Any = results[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val c = results[position]
            val row = (convertView as? LinearLayout) ?: LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                addView(txt(13f, p.cFg, "").apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }, lp(0, -2).apply { weight = 1f })
                addView(txt(10f, p.cDim, "").apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }, lp(-2, -2).apply { marginStart = dp(8); marginEnd = dp(10) })
                addView(txt(11f, p.cDim, "").apply { gravity = Gravity.END }, lp(dp(70), -2))
            }
            (row.getChildAt(0) as TextView).text = c.name
            (row.getChildAt(1) as TextView).text = c.zoneId
            val secs = runCatching { ZoneId.of(c.zoneId).rules.getOffset(Instant.now()).totalSeconds }.getOrDefault(0)
            (row.getChildAt(2) as TextView).text = formatUtcOffset(secs)
            row.background = if (position == highlighted) {
                GradientDrawable().apply {
                    setColor(withAlpha(p.cSel, 0x55))
                    setStroke(dp(1), p.cDim)
                }
            } else {
                null
            }
            return row
        }
    }
    resultsList.adapter = cityAdapter
    val listHeight = min(dp(250), (resources.displayMetrics.heightPixels * 0.34f).toInt())

    addArea.addView(addBtn, lp(-1, -2))
    addArea.addView(searchRow, lp(-1, -2))
    addArea.addView(statusTv, lp(-1, -2))
    addArea.addView(resultsList, lp(-1, listHeight).apply { topMargin = dp(4) })
    root.addView(addArea, lp(-1, -2).apply { topMargin = dp(4) })

    // ---- helpers ----
    fun persist() {
        prefs.edit().putString("world_cities", encodeWorldCities(rows.map { it.city })).apply()
    }

    fun syncMarkers() {
        globe.markers = rows.map { GlobeModelView.Marker(it.city.name, it.city.lat, it.city.lon) }
    }

    fun focusRow(r: WorldRow) {
        globe.focusIndex = rows.indexOf(r)
        globe.focusOn(r.city.lat, r.city.lon)
    }

    fun refreshChrome() {
        emptyTv.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        val full = rows.size >= WORLD_MAX_CITIES
        addBtn.alpha = if (full) 0.4f else 1f
        addBtn.text = if (full) "city limit reached" else "+   Add a city"
    }

    fun renderWeather(r: WorldRow) {
        val w = r.weather ?: return
        r.weatherIcon.set(weatherKindFor(w.code), w.isDay)
        r.tempTv.text = formatTemp(w.tempC, fahrenheit)
        if (!editMode && r.weatherBox.alpha < 1f) {
            r.weatherBox.animate().cancel()
            r.weatherBox.scaleX = 0.85f
            r.weatherBox.scaleY = 0.85f
            r.weatherBox.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280L).start()
        }
    }

    fun loadWeather(r: WorldRow) {
        WorldWeather.cached(r.city)?.let {
            r.weather = it
            renderWeather(r)
        }
        val now = System.currentTimeMillis()
        if (WorldWeather.isFresh(r.city, now)) return
        netPool.execute {
            val w = WorldWeather.fetch(r.city, now)
            if (w != null) {
                handler.post {
                    if (dialog?.isShowing == true && r.card.isAttachedToWindow) {
                        r.weather = w
                        renderWeather(r)
                    }
                }
            }
        }
    }

    fun renderRow(r: WorldRow, localNow: ZonedDateTime, instant: Instant, animate: Boolean) {
        val t = localNow.withZoneSameInstant(r.zone)
        val (hm, ap) = worldTimeParts(t, is24)
        smoothText(r.timeTv, hm, animate)
        if (r.ampmTv.text.toString() != ap) r.ampmTv.text = ap
        r.ampmTv.visibility = if (ap.isEmpty()) View.GONE else View.VISIBLE
        val rel = relativeDayLabel(t.toLocalDate(), localNow.toLocalDate())
        smoothText(r.dateTv, dateFmt.format(t) + (if (rel != null) " · $rel" else ""), animate)
        val offset = formatOffsetHours(offsetBetweenHours(r.zone, home, instant))
        smoothText(r.zoneTv, zoneAbbrev(r.zone, instant) + "  " + offset, false)
        r.track.sun = sunWindow(r.city.lat, r.city.lon, t.toLocalDate(), r.zone)
        r.track.setFraction(dayFraction(t).toFloat())
    }

    fun updateAll(animate: Boolean) {
        val instant = Instant.now().plusMillis(shiftMs)
        val localNow = ZonedDateTime.ofInstant(instant, home)
        val (hm, ap) = worldTimeParts(localNow, is24)
        val clockText = hm + (if (ap.isEmpty()) "" else " $ap")
        hereText = if (shiftMs == 0L) {
            "It's $clockText here"
        } else {
            "$clockText here  ·  ${formatShift(shiftMs)}  ·  tap to reset"
        }
        if (!editMode) smoothText(hereTv, hereText, animate)
        for (r in rows) renderRow(r, localNow, instant, animate)
        val sun = subsolarPoint(instant)
        globe.setSun(sun.first, sun.second)
    }

    fun resetShift() {
        if (shiftMs == 0L) return
        ValueAnimator.ofFloat(shiftMs.toFloat(), 0f).apply {
            duration = 450L
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                shiftMs = (it.animatedValue as Float).toLong()
                updateAll(false)
            }
            start()
        }
    }
    hereTv.setOnClickListener { resetShift() }

    fun setEditMode(on: Boolean) {
        editMode = on
        for (r in rows) {
            r.removeBtn.visibility = View.VISIBLE
            r.removeBtn.animate().cancel()
            r.removeBtn.animate().alpha(if (on) 1f else 0f).setDuration(180L).withEndAction {
                if (!editMode) r.removeBtn.visibility = View.INVISIBLE
            }.start()
            r.weatherBox.animate().cancel()
            r.weatherBox.animate().alpha(if (!on && r.weather != null) 1f else 0f).setDuration(180L).start()
        }
        smoothText(hereTv, if (on) "tap × to remove  ·  hold a city to finish" else hereText, true)
    }

    fun removeRow(r: WorldRow) {
        rows.remove(r)
        persist()
        cardsBox.removeView(r.card)
        globe.focusIndex = -1
        syncMarkers()
        refreshChrome()
        if (rows.isEmpty() && editMode) setEditMode(false)
    }

    fun buildRow(city: WorldCity): WorldRow? {
        val z = city.zone ?: return null
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                setColor(withAlpha(p.cSel, 0x55))
                cornerRadius = dp(3).toFloat()
            }
        }
        val nameTv = txt(15f, p.cFg, city.name).apply {
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val tempTv = txt(11f, p.cDim, "")
        val weatherIcon = WeatherIconView(this, p.cDim)
        val weatherBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            alpha = 0f
        }
        weatherBox.addView(tempTv, lp(-2, -2).apply { marginEnd = dp(6) })
        weatherBox.addView(weatherIcon, lp(dp(14), dp(14)))
        val removeBtn = txt(20f, p.cAnsi[1], "×").apply {
            setPadding(dp(10), 0, dp(2), dp(2))
            alpha = 0f
            visibility = View.INVISIBLE
            isClickable = true
        }
        val slot = FrameLayout(this)
        slot.addView(weatherBox, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        slot.addView(removeBtn, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        val timeTv = txt(22f, p.cFg, "").apply { setTypeface(typeface, Typeface.BOLD) }
        val ampmTv = txt(11f, p.cDim, "").apply { setPadding(dp(3), 0, 0, 0) }
        val timeBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        timeBox.addView(timeTv, lp(-2, -2))
        timeBox.addView(ampmTv, lp(-2, -2))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(nameTv, lp(0, -2).apply { weight = 1f })
        top.addView(slot, lp(-2, -2).apply { marginStart = dp(8) })
        top.addView(timeBox, lp(-2, -2).apply { marginStart = dp(14) })

        val dateTv = txt(11f, p.cDim, "")
        val zoneTv = txt(10f, p.cDim, "").apply { gravity = Gravity.END }
        val sub = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        sub.addView(dateTv, lp(0, -2).apply { weight = 1f })
        sub.addView(zoneTv, lp(-2, -2))

        val track = DayTrackView(
            this,
            withAlpha(p.cDim, 0x40),
            withAlpha(p.cDim, 0xD0),
            p.cAnsi[3],
            p.cFg
        )

        card.addView(top, lp(-1, -2))
        card.addView(sub, lp(-1, -2).apply { topMargin = dp(1) })
        card.addView(track, lp(-1, dp(28)).apply { topMargin = dp(2) })

        val row = WorldRow(city, z, card, dateTv, tempTv, weatherIcon, weatherBox, removeBtn, timeTv, ampmTv, zoneTv, track)
        card.setOnLongClickListener {
            setEditMode(!editMode)
            true
        }
        track.onScrub = { f ->
            val now0 = dayFraction(ZonedDateTime.now(z))
            shiftMs = ((f - now0) * 86_400_000.0).toLong()
            updateAll(false)
        }
        card.setOnClickListener { focusRow(row) }
        timeBox.setOnClickListener {
            is24 = !is24
            prefs.edit().putBoolean("world_24h", is24).apply()
            updateAll(false)
        }
        slot.setOnClickListener {
            if (!editMode && row.weather != null) {
                fahrenheit = !fahrenheit
                prefs.edit().putBoolean("world_fahrenheit", fahrenheit).apply()
                for (other in rows) {
                    val w = other.weather ?: continue
                    smoothText(other.tempTv, formatTemp(w.tempC, fahrenheit), true)
                }
            }
        }
        removeBtn.setOnClickListener { removeRow(row) }
        return row
    }

    fun sweepIn(r: WorldRow, delayMs: Long) {
        val z = r.zone
        val t = ZonedDateTime.now(z)
        r.track.setFraction(dayFraction(t).toFloat(), 800L, delayMs, true)
    }

    // ---- initial rows ----
    for (c in decodeWorldCities(prefs.getString("world_cities", null))) {
        val r = buildRow(c) ?: continue
        rows.add(r)
        cardsBox.addView(r.card, lp(-1, -2).apply { bottomMargin = dp(8) })
    }
    syncMarkers()
    refreshChrome()
    updateAll(false)
    rows.firstOrNull()?.let { globe.jumpTo(it.city.lat, it.city.lon - 70.0) }

    // start hidden, then stagger in once the popup is up
    rows.forEach {
        it.card.alpha = 0f
        it.card.translationY = dp(14).toFloat()
    }

    // ---- search ----
    var searchSeq = 0
    var pendingSearch: Runnable? = null
    var suppressQuery = false

    fun setStatus(text: String?) {
        if (text == null) {
            statusTv.visibility = View.GONE
        } else {
            statusTv.text = text
            statusTv.visibility = View.VISIBLE
        }
    }

    fun applyResults(list: List<DbCity>, status: String?) {
        results = list
        highlighted = 0
        cityAdapter.notifyDataSetChanged()
        resultsList.setSelection(0)
        resultsList.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        setStatus(status)
    }

    fun closeSearch() {
        searchSeq++
        pendingSearch?.let { handler.removeCallbacks(it) }
        imm.hideSoftInputFromWindow(input.windowToken, 0)
        suppressQuery = true
        input.setText("")
        suppressQuery = false
        results = emptyList()
        cityAdapter.notifyDataSetChanged()
        resultsList.visibility = View.GONE
        setStatus(null)
        searchRow.visibility = View.GONE
        addBtn.visibility = View.VISIBLE
        if (showGlobe) globeBox.visibility = View.VISIBLE
    }

    fun addCity(c: WorldCity) {
        if (rows.size >= WORLD_MAX_CITIES) {
            setStatus("city limit reached")
            return
        }
        val dup = rows.firstOrNull { it.city.zoneId == c.zoneId && it.city.name.equals(c.name, ignoreCase = true) }
        if (dup != null) {
            closeSearch()
            focusRow(dup)
            return
        }
        val r = buildRow(c) ?: return
        rows.add(r)
        cardsBox.addView(r.card, lp(-1, -2).apply { bottomMargin = dp(8) })
        persist()
        syncMarkers()
        refreshChrome()
        updateAll(false)
        focusRow(r)
        loadWeather(r)
        sweepIn(r, 120L)
        closeSearch()
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    fun runOnlineSearch(q: String) {
        val seq = ++searchSeq
        applyResults(emptyList(), "no offline match  ·  searching online…")
        netPool.execute {
            val found = searchCities(q).map { DbCity(it.name, it.detail, it.zoneId, it.lat, it.lon) }
            handler.post {
                if (seq == searchSeq && dialog?.isShowing == true) {
                    applyResults(found, if (found.isEmpty()) "no match" else null)
                }
            }
        }
    }

    fun onQueryChanged() {
        pendingSearch?.let { handler.removeCallbacks(it) }
        searchSeq++
        val q = input.text.toString().trim()
        val found = CityDb.search(q)
        applyResults(found, if (found.isEmpty() && q.isNotEmpty()) "no match" else null)
        if (found.isEmpty() && q.length >= 3) {
            val job = Runnable { runOnlineSearch(q) }
            pendingSearch = job
            handler.postDelayed(job, 500L)
        }
    }

    // tapping a city on the globe offers to add it
    var pendingPick: DbCity? = null
    var pickHide: Runnable? = null

    fun hidePick() {
        pickHide?.let { handler.removeCallbacks(it) }
        pendingPick = null
        globe.preview = null
        if (pill.visibility == View.VISIBLE) {
            pill.animate().cancel()
            pill.animate().alpha(0f).translationY(dp(8).toFloat()).setDuration(160L).withEndAction {
                if (pendingPick == null) pill.visibility = View.GONE
            }.start()
        }
    }

    fun showPick(c: DbCity) {
        pickHide?.let { handler.removeCallbacks(it) }
        val existing = rows.indexOfFirst { it.city.zoneId == c.zoneId && it.city.name.equals(c.name, ignoreCase = true) }
        val full = rows.size >= WORLD_MAX_CITIES
        pendingPick = if (existing >= 0 || full) null else c
        globe.preview = if (existing >= 0) null else GlobeModelView.Marker(c.name, c.lat, c.lon)
        if (existing >= 0) globe.focusIndex = existing
        globe.focusOn(c.lat, c.lon, 450L)
        val secs = runCatching { ZoneId.of(c.zoneId).rules.getOffset(Instant.now()).totalSeconds }.getOrDefault(0)
        pill.text = when {
            existing >= 0 -> "${c.name} is already on your list"
            full -> "city limit reached"
            else -> "+  Add ${c.name}  ·  ${formatUtcOffset(secs)}"
        }
        pill.animate().cancel()
        if (pill.visibility != View.VISIBLE || pill.alpha < 1f) {
            pill.visibility = View.VISIBLE
            pill.translationY = dp(8).toFloat()
            pill.animate().alpha(1f).translationY(0f).setDuration(220L).start()
        }
        val job = Runnable { hidePick() }
        pickHide = job
        handler.postDelayed(job, 6000L)
    }

    pill.setOnClickListener {
        val c = pendingPick
        hidePick()
        if (c != null) addCity(c.toCity())
    }
    globe.onPick = { spot ->
        val c = if (spot == null) null else CityDb.nearest(spot.first, spot.second, 1300.0)
        if (c == null) hidePick() else showPick(c)
    }

    resultsList.setOnItemClickListener { _, _, position, _ ->
        results.getOrNull(position)?.let { addCity(it.toCity()) }
    }
    input.doAfterTextChanged { if (!suppressQuery) onQueryChanged() }
    input.setOnEditorActionListener { _, actionId, _ ->
        if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH) {
            results.getOrNull(highlighted)?.let { addCity(it.toCity()) }
            true
        } else {
            false
        }
    }
    // hardware keyboard: arrows move the highlight, Enter adds it
    input.setOnKeyListener { _, keyCode, event ->
        if (event.action != KeyEvent.ACTION_DOWN) {
            false
        } else {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (results.isNotEmpty()) {
                        highlighted = min(highlighted + 1, results.size - 1)
                        cityAdapter.notifyDataSetChanged()
                        resultsList.smoothScrollToPosition(highlighted)
                    }
                    true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (results.isNotEmpty()) {
                        highlighted = maxOf(highlighted - 1, 0)
                        cityAdapter.notifyDataSetChanged()
                        resultsList.smoothScrollToPosition(highlighted)
                    }
                    true
                }
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    results.getOrNull(highlighted)?.let { addCity(it.toCity()) }
                    true
                }
                else -> false
            }
        }
    }
    cancelBtn.setOnClickListener { closeSearch() }
    addBtn.setOnClickListener {
        if (rows.size >= WORLD_MAX_CITIES) return@setOnClickListener
        if (editMode) setEditMode(false)
        hidePick()
        addBtn.visibility = View.GONE
        searchRow.visibility = View.VISIBLE
        globeBox.visibility = View.GONE
        onQueryChanged()
        input.requestFocus()
        input.postDelayed({ imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }, 160L)
    }

    // ---- show ----
    val tick = object : Runnable {
        override fun run() {
            if (dialog?.isShowing == true) {
                updateAll(true)
                handler.postDelayed(this, 1000L)
            }
        }
    }
    dialog = showBarDialog(BarKind.WORLD_CLOCK, root, 440) {
        handler.removeCallbacks(tick)
        pendingSearch?.let { handler.removeCallbacks(it) }
        pickHide?.let { handler.removeCallbacks(it) }
        searchSeq++
        imm.hideSoftInputFromWindow(input.windowToken, 0)
    }
    dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

    rows.firstOrNull()?.let {
        globe.focusIndex = 0
        globe.focusOn(it.city.lat, it.city.lon, 1100L)
    }
    rows.forEachIndexed { i, r ->
        val delay = 80L + i * 55L
        r.card.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(delay)
            .setDuration(320L)
            .setInterpolator(DecelerateInterpolator(1.8f))
            .start()
        r.track.setFraction(dayFraction(ZonedDateTime.now(r.zone)).toFloat(), 800L, delay + 120L, true)
        loadWeather(r)
    }

    // soften later changes (rows added or removed, search opening) once the first paint is done
    for (vg in listOf<ViewGroup>(root, cardsBox, addArea)) {
        vg.layoutTransition = LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
            setDuration(220L)
        }
    }
    handler.postDelayed(tick, 1000L)
}
