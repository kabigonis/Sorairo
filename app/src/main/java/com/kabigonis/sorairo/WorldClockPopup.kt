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
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

// World clock popup: a globe icon beside the top-bar clock opens a card per city with local time,
// weather, offset from home and a day/night track. Matches the other top-bar panels (showBarDialog).

// ---------------------------------------------------------------- views

/** Globe glyph: a ring with the Americas, Africa and Europe filled in. */
internal class GlobeIconView(context: Context, private val tint: Int) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = tint }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = tint; strokeWidth = 5f }
    private val land = Path()

    init {
        val lat0 = Math.toRadians(15.0)
        val lon0 = Math.toRadians(-40.0)
        val raw = Path()
        for (poly in WorldMap.polygons) {
            val n = poly.size / 2
            for (i in 0 until n) {
                val lo = Math.toRadians(poly[2 * i].toDouble())
                val la = Math.toRadians(poly[2 * i + 1].toDouble())
                val cosc = sin(lat0) * sin(la) + cos(lat0) * cos(la) * cos(lo - lon0)
                var x = cos(la) * sin(lo - lon0)
                var y = cos(lat0) * sin(la) - sin(lat0) * cos(la) * cos(lo - lon0)
                if (cosc < 0.0) {
                    val len = hypot(x, y)
                    if (len > 1e-6) {
                        x /= len
                        y /= len
                    }
                }
                val px = (50.0 + 38.0 * x).toFloat()
                val py = (50.0 - 38.0 * y).toFloat()
                if (i == 0) raw.moveTo(px, py) else raw.lineTo(px, py)
            }
            raw.close()
        }
        val disc = Path().apply { addCircle(50f, 50f, 38f, Path.Direction.CW) }
        land.set(raw)
        land.op(disc, Path.Op.INTERSECT)
    }

    override fun onDraw(canvas: Canvas) {
        val s = min(width, height).toFloat()
        if (s <= 0f) return
        canvas.save()
        canvas.translate((width - s) / 2f, (height - s) / 2f)
        canvas.scale(s / 100f, s / 100f)
        canvas.drawCircle(50f, 50f, 45f, line)
        canvas.drawPath(land, fill)
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

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                anim?.cancel()
                lastX = e.x
                lastY = e.y
            }
            MotionEvent.ACTION_MOVE -> {
                val r = radius().coerceAtLeast(1f)
                lon0 = normLon(lon0 - Math.toDegrees(((e.x - lastX) / r).toDouble()))
                lat0 = (lat0 + Math.toDegrees(((e.y - lastY) / r).toDouble())).coerceIn(-70.0, 70.0)
                lastX = e.x
                lastY = e.y
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
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
    globe.visibility = if (showGlobe) View.VISIBLE else View.GONE
    root.addView(globe, lp(-1, dp(190)).apply { bottomMargin = dp(10) })
    titleRow.setOnClickListener {
        showGlobe = !showGlobe
        prefs.edit().putBoolean("world_globe", showGlobe).apply()
        globe.visibility = if (showGlobe) View.VISIBLE else View.GONE
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
        hint = "search for a city"
        setHintTextColor(p.cDim)
        setTextColor(p.cFg)
        typeface = font
        textSize = inputSize
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        background = null
        setPadding(dp(12), dp(10), dp(6), dp(10))
    }
    val cancelBtn = txt(20f, p.cDim, "×").apply {
        setPadding(dp(12), dp(2), dp(14), dp(4))
        isClickable = true
    }
    val searchRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = box(Color.TRANSPARENT, p.cBlue)
        visibility = View.GONE
    }
    searchRow.addView(input, lp(0, -2).apply { weight = 1f })
    searchRow.addView(cancelBtn, lp(-2, -2))
    val resultsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    addArea.addView(addBtn, lp(-1, -2))
    addArea.addView(searchRow, lp(-1, -2))
    addArea.addView(resultsBox, lp(-1, -2))
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

    fun showStatus(text: String) {
        resultsBox.removeAllViews()
        resultsBox.addView(txt(11f, p.cDim, text).apply { setPadding(dp(4), dp(10), dp(4), dp(4)) }, lp(-1, -2))
    }

    fun closeSearch() {
        searchSeq++
        pendingSearch?.let { handler.removeCallbacks(it) }
        imm.hideSoftInputFromWindow(input.windowToken, 0)
        input.setText("")
        resultsBox.removeAllViews()
        searchRow.visibility = View.GONE
        addBtn.visibility = View.VISIBLE
    }

    fun addCity(s: CitySuggestion) {
        if (rows.size >= WORLD_MAX_CITIES) {
            showStatus("city limit reached")
            return
        }
        if (rows.any { it.city.zoneId == s.zoneId && it.city.name.equals(s.name, ignoreCase = true) }) {
            closeSearch()
            return
        }
        val r = buildRow(s.toCity()) ?: return
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

    fun showResults(list: List<CitySuggestion>) {
        resultsBox.removeAllViews()
        if (list.isEmpty()) {
            showStatus("no match")
            return
        }
        for (s in list) {
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(9), dp(12), dp(9))
                isClickable = true
                setBackgroundColor(withAlpha(p.cSel, 0x33))
            }
            line.addView(txt(13f, p.cFg, s.name).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, lp(-2, -2))
            line.addView(txt(11f, p.cDim, s.detail).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(10), 0, 0, 0)
            }, lp(0, -2).apply { weight = 1f })
            line.setOnClickListener { addCity(s) }
            resultsBox.addView(line, lp(-1, -2).apply { topMargin = dp(3) })
        }
    }

    fun runSearch(q: String) {
        val seq = ++searchSeq
        showStatus("searching…")
        netPool.execute {
            val found = searchCities(q)
            handler.post {
                if (seq == searchSeq && dialog?.isShowing == true) showResults(found)
            }
        }
    }

    fun scheduleSearch() {
        pendingSearch?.let { handler.removeCallbacks(it) }
        val q = input.text.toString().trim()
        if (q.length < 2) {
            searchSeq++
            resultsBox.removeAllViews()
            return
        }
        val job = Runnable { runSearch(q) }
        pendingSearch = job
        handler.postDelayed(job, 350L)
    }

    input.doAfterTextChanged { scheduleSearch() }
    input.setOnEditorActionListener { _, actionId, event ->
        val enter = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
        if (actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
            pendingSearch?.let { handler.removeCallbacks(it) }
            val q = input.text.toString().trim()
            if (q.length >= 2) runSearch(q)
            true
        } else {
            false
        }
    }
    cancelBtn.setOnClickListener { closeSearch() }
    addBtn.setOnClickListener {
        if (rows.size >= WORLD_MAX_CITIES) return@setOnClickListener
        if (editMode) setEditMode(false)
        addBtn.visibility = View.GONE
        searchRow.visibility = View.VISIBLE
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
    for (vg in listOf<ViewGroup>(root, cardsBox, addArea, resultsBox)) {
        vg.layoutTransition = LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
            setDuration(220L)
        }
    }
    handler.postDelayed(tick, 1000L)
}
