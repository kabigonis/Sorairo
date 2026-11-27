package com.kabigonis.sorairo

import android.animation.LayoutTransition
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
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
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

// World clock popup. The globe icon beside the top-bar clock opens it on the list of clocks;
// the globe in the title switches to a big dotted globe (and back). Views live in WorldClockViews.kt.

private class WorldRow(
    var city: WorldCity,
    val zone: ZoneId,
    val country: String?,
    val card: LinearLayout,
    val dateTv: TextView,
    val greetTv: TextView,
    val noteTv: TextView,
    val tempTv: TextView,
    val weatherIcon: WeatherIconView,
    val weatherBox: LinearLayout,
    val timeTv: TextView,
    val ampmTv: TextView,
    val zoneTv: TextView,
    val track: DayTrackView
) {
    var weather: CityWeather? = null
    var noteEdit: EditText? = null
}

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
    netPool.execute {
        // parse the built-in city list and build the globe's dot grid off the UI thread
        CityDb.popular
        WorldMap.dots
    }
    val home = ZoneId.systemDefault()
    val dateFmt = DateTimeFormatter.ofPattern("EEE MMM d", Locale.getDefault())
    var is24 = if (prefs.contains("world_24h")) {
        prefs.getBoolean("world_24h", false)
    } else {
        android.text.format.DateFormat.is24HourFormat(this)
    }
    var shiftMs = 0L
    var fahrenheit = if (prefs.contains("world_fahrenheit")) {
        prefs.getBoolean("world_fahrenheit", false)
    } else {
        usesFahrenheit(Locale.getDefault())
    }
    var globeMode = false // always opens on the clocks, never on the globe
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
    val hereTv = txt(11f, p.cDim, "").apply {
        gravity = Gravity.CENTER
        isClickable = true
    }
    root.addView(hereTv, lp(-1, -2))

    // title: World (globe | x) Clock
    val titleRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }
    titleRow.addView(txt(20f, p.cFg, "World").apply { setTypeface(typeface, Typeface.BOLD) })
    val iconFrame = FrameLayout(this)
    iconFrame.isClickable = true
    val globeIcon = GlobeIconView(act, p.cFg)
    val closeIcon = CardActionIconView(act, CardActionIconView.Kind.CLOSE, p.cFg).apply {
        alpha = 0f
        rotation = 90f
        setPadding(dp(5), dp(5), dp(5), dp(5))
    }
    iconFrame.addView(globeIcon, FrameLayout.LayoutParams(-1, -1))
    iconFrame.addView(closeIcon, FrameLayout.LayoutParams(-1, -1))
    titleRow.addView(iconFrame, lp(dp(34), dp(34)).apply { marginStart = dp(12); marginEnd = dp(12) })
    titleRow.addView(txt(20f, p.cFg, "Clock").apply { setTypeface(typeface, Typeface.BOLD) })
    root.addView(titleRow, lp(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(10) })

    // the big globe (only shown in globe mode)
    val globe = GlobeModelView(
        this, p.cFg, p.cDim, withAlpha(p.cDim, 0xA0), p.cBlue, p.cAnsi[3], p.cAnsi[3], p.cFg,
        p.cBg or (0xFF shl 24), font
    )
    val pill = txt(12f, p.cFg, "").apply {
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = box(p.cBg or (0xFF shl 24), p.cBlue)
        isClickable = true
        alpha = 0f
        visibility = View.GONE
    }
    val globeBox = FrameLayout(this)
    globeBox.visibility = View.GONE
    globeBox.addView(globe, FrameLayout.LayoutParams(-1, -1))
    globeBox.addView(
        pill,
        FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(4) }
    )
    val globeHeight = min(dp(390), (resources.displayMetrics.heightPixels * 0.55f).toInt())
    root.addView(globeBox, lp(-1, globeHeight).apply { bottomMargin = dp(10) })

    // the clocks
    val cardsBox = ReorderLayout(this)
    val scroll = BoundedScrollView(this, (resources.displayMetrics.heightPixels * 0.58f).toInt()).apply {
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

    // ---- add a city / jump to a city ----
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
        val full = rows.size >= WORLD_MAX_CITIES
        emptyTv.visibility = if (rows.isEmpty() && !globeMode) View.VISIBLE else View.GONE
        addBtn.alpha = if (full && !globeMode) 0.4f else 1f
        addBtn.text = when {
            globeMode -> "Jump to a city"
            full -> "city limit reached"
            else -> "+   Add a city"
        }
    }

    fun renderWeather(r: WorldRow) {
        val w = r.weather ?: return
        r.weatherIcon.set(weatherKindFor(w.code), w.isDay)
        r.tempTv.text = formatTemp(w.tempC, fahrenheit)
        if (r.weatherBox.alpha < 1f) {
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
        smoothText(r.greetTv, capitalizeFirst(greetingFor(r.country, t.hour)), animate)
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
        smoothText(hereTv, hereText, animate)
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

    // ---- notes ----
    val noteSize = 12f * uiScale * (uiTextSize / 13f)

    fun saveNote(r: WorldRow, text: String) {
        val clean = text.replace(Regex("[\t\r\n]+"), " ").trim().take(140)
        r.city = r.city.copy(note = clean)
        persist()
        r.noteTv.text = clean
        r.noteTv.visibility = if (clean.isEmpty()) View.GONE else View.VISIBLE
    }

    fun finishNote(r: WorldRow) {
        val e = r.noteEdit ?: return
        if (e.visibility != View.VISIBLE) return
        saveNote(r, e.text.toString())
        imm.hideSoftInputFromWindow(e.windowToken, 0)
        e.visibility = View.GONE
    }

    fun noteEditFor(r: WorldRow): EditText {
        r.noteEdit?.let { return it }
        val e = EditText(act).apply {
            hint = "Names or @github handles, comma separated"
            setHintTextColor(p.cDim)
            setTextColor(p.cFg)
            typeface = font
            textSize = noteSize
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
            background = GradientDrawable().apply {
                setColor(withAlpha(p.cBg, 0x66))
                setStroke(dp(1), p.cDim)
            }
            setPadding(dp(10), dp(8), dp(10), dp(8))
            visibility = View.GONE
        }
        e.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                finishNote(r)
                true
            } else {
                false
            }
        }
        r.noteEdit = e
        r.card.addView(e, r.card.indexOfChild(r.track), lp(-1, -2).apply { topMargin = dp(6) })
        return e
    }

    fun toggleNote(r: WorldRow) {
        val current = r.noteEdit
        if (current != null && current.visibility == View.VISIBLE) {
            finishNote(r)
            return
        }
        val e = noteEditFor(r)
        e.setText(r.city.note)
        e.setSelection(e.text.length)
        e.visibility = View.VISIBLE
        r.noteTv.visibility = View.GONE
        e.requestFocus()
        e.postDelayed({ imm.showSoftInput(e, InputMethodManager.SHOW_IMPLICIT) }, 120L)
    }

    fun removeRow(r: WorldRow) {
        r.noteEdit?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
        rows.remove(r)
        persist()
        cardsBox.removeView(r.card)
        globe.focusIndex = -1
        syncMarkers()
        refreshChrome()
    }

    fun buildRow(city: WorldCity): WorldRow? {
        val z = city.zone ?: return null
        val country = CityDb.countryOf(city.name, city.zoneId)
        val card = LinearLayout(act).apply {
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
        val dateTv = txt(11f, p.cDim, "")
        val greetTv = txt(11f, withAlpha(p.cFg, 0xB8), "")
        val noteTv = txt(10f, p.cDim, city.note).apply {
            visibility = if (city.note.isEmpty()) View.GONE else View.VISIBLE
        }
        val leftCol = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        leftCol.addView(nameTv, lp(-1, -2))
        leftCol.addView(dateTv, lp(-1, -2).apply { topMargin = dp(2) })
        leftCol.addView(greetTv, lp(-1, -2).apply { topMargin = dp(1) })
        leftCol.addView(noteTv, lp(-1, -2).apply { topMargin = dp(1) })

        val tempTv = txt(11f, p.cDim, "")
        val weatherIcon = WeatherIconView(act, p.cDim)
        val weatherBox = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            alpha = 0f
        }
        weatherBox.addView(tempTv, lp(-2, -2).apply { marginEnd = dp(6) })
        weatherBox.addView(weatherIcon, lp(dp(14), dp(14)))
        val noteBtn = CardActionIconView(act, CardActionIconView.Kind.NOTE, p.cDim).apply {
            setPadding(dp(4), dp(4), dp(4), dp(4))
            isClickable = true
            contentDescription = "Add a note"
        }
        val closeBtn = CardActionIconView(act, CardActionIconView.Kind.CLOSE, p.cDim).apply {
            setPadding(dp(4), dp(4), dp(4), dp(4))
            isClickable = true
            contentDescription = "Remove this clock"
        }
        val actions = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }
        actions.addView(weatherBox, lp(-2, -2).apply { marginEnd = dp(6) })
        actions.addView(noteBtn, lp(dp(26), dp(26)))
        actions.addView(closeBtn, lp(dp(26), dp(26)))

        val timeTv = txt(22f, p.cFg, "").apply { setTypeface(typeface, Typeface.BOLD) }
        val ampmTv = txt(11f, p.cDim, "").apply { setPadding(dp(3), 0, 0, 0) }
        val timeBox = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        timeBox.addView(timeTv, lp(-2, -2))
        timeBox.addView(ampmTv, lp(-2, -2))
        val zoneTv = txt(10f, p.cDim, "").apply { gravity = Gravity.END }

        val rightCol = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        rightCol.addView(actions, lp(-2, -2))
        rightCol.addView(timeBox, lp(-2, -2))
        rightCol.addView(zoneTv, lp(-2, -2))

        val top = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        top.addView(leftCol, lp(0, -2).apply { weight = 1f })
        top.addView(rightCol, lp(-2, -2).apply { marginStart = dp(8) })

        val track = DayTrackView(
            act,
            withAlpha(p.cDim, 0x40),
            withAlpha(p.cDim, 0xD0),
            p.cAnsi[3],
            p.cFg
        )
        card.addView(top, lp(-1, -2))
        card.addView(track, lp(-1, dp(28)).apply { topMargin = dp(2) })

        val row = WorldRow(
            city, z, country, card, dateTv, greetTv, noteTv, tempTv, weatherIcon, weatherBox,
            timeTv, ampmTv, zoneTv, track
        )
        noteBtn.setOnClickListener { toggleNote(row) }
        closeBtn.setOnClickListener { removeRow(row) }
        track.onScrub = { f ->
            val now0 = dayFraction(ZonedDateTime.now(z))
            shiftMs = ((f - now0) * 86_400_000.0).toLong()
            updateAll(false)
        }
        timeBox.setOnClickListener {
            is24 = !is24
            prefs.edit().putBoolean("world_24h", is24).apply()
            updateAll(false)
        }
        weatherBox.setOnClickListener {
            if (row.weather != null) {
                fahrenheit = !fahrenheit
                prefs.edit().putBoolean("world_fahrenheit", fahrenheit).apply()
                for (other in rows) {
                    val w = other.weather ?: continue
                    smoothText(other.tempTv, formatTemp(w.tempC, fahrenheit), true)
                }
            }
        }
        card.layoutTransition = LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
            setDuration(200L)
        }
        return row
    }

    fun sweepIn(r: WorldRow, delayMs: Long) {
        val t = ZonedDateTime.now(r.zone)
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

    cardsBox.canStartDrag = { card, rawX, rawY ->
        val r = rows.firstOrNull { it.card === card }
        r != null && !r.track.containsRaw(rawX, rawY) && r.noteEdit?.visibility != View.VISIBLE
    }
    cardsBox.onReordered = { from, to ->
        if (from in rows.indices && to in rows.indices) {
            rows.add(to, rows.removeAt(from))
            persist()
            syncMarkers()
        }
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
        if (globeMode) globeBox.visibility = View.VISIBLE
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

    fun setGlobeMode(on: Boolean) {
        if (globeMode == on) return
        if (searchRow.visibility == View.VISIBLE) closeSearch()
        hidePick()
        globeMode = on
        globeBox.visibility = if (on) View.VISIBLE else View.GONE
        scroll.visibility = if (on) View.GONE else View.VISIBLE
        refreshChrome()
        globeIcon.animate().cancel()
        closeIcon.animate().cancel()
        globeIcon.animate().alpha(if (on) 0f else 1f).rotation(if (on) -90f else 0f).setDuration(240L).start()
        closeIcon.animate().alpha(if (on) 1f else 0f).rotation(if (on) 0f else 90f).setDuration(240L).start()
        if (on) {
            val first = rows.firstOrNull()
            if (first != null) {
                globe.focusIndex = 0
                globe.jumpTo(first.city.lat, first.city.lon - 70.0)
                globe.focusOn(first.city.lat, first.city.lon, 1000L)
            } else {
                globe.jumpTo(20.0, 0.0)
            }
        }
    }

    /** A city picked from the list: added on the clocks screen, offered on the globe screen. */
    fun choose(c: DbCity) {
        if (globeMode) {
            closeSearch()
            showPick(c)
        } else {
            addCity(c.toCity())
        }
    }

    pill.setOnClickListener {
        val c = pendingPick
        hidePick()
        if (c != null) addCity(c.toCity())
    }
    globe.onPick = { spot ->
        val c = if (spot == null) null else CityDb.pick(spot.first, spot.second)
        if (c == null) hidePick() else showPick(c)
    }
    iconFrame.setOnClickListener { setGlobeMode(!globeMode) }

    resultsList.setOnItemClickListener { _, _, position, _ ->
        results.getOrNull(position)?.let { choose(it) }
    }
    input.doAfterTextChanged { if (!suppressQuery) onQueryChanged() }
    input.setOnEditorActionListener { _, actionId, _ ->
        if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH) {
            results.getOrNull(highlighted)?.let { choose(it) }
            true
        } else {
            false
        }
    }
    // hardware keyboard: arrows move the highlight, Enter picks it
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
                    results.getOrNull(highlighted)?.let { choose(it) }
                    true
                }
                else -> false
            }
        }
    }
    cancelBtn.setOnClickListener { closeSearch() }
    addBtn.setOnClickListener {
        if (!globeMode && rows.size >= WORLD_MAX_CITIES) return@setOnClickListener
        hidePick()
        for (r in rows) finishNote(r)
        addBtn.visibility = View.GONE
        searchRow.visibility = View.VISIBLE
        if (globeMode) globeBox.visibility = View.GONE
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
        for (r in rows) {
            val e = r.noteEdit
            if (e != null && e.visibility == View.VISIBLE) saveNote(r, e.text.toString())
        }
        imm.hideSoftInputFromWindow(input.windowToken, 0)
    }
    dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

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

    // soften later changes (rows added or removed, switching between clocks and globe, search opening)
    for (vg in listOf<ViewGroup>(root, cardsBox, addArea)) {
        vg.layoutTransition = LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
            setDuration(220L)
        }
    }
    handler.postDelayed(tick, 1000L)
}
