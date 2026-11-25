package com.kabigonis.sorairo

import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// Omarchy-style World Clock. The globe beside the top-bar clock opens it. Each city shows its time,
// date, UTC offset relative to here and a 24-hour strip. Cities are saved as time-zone ids.

internal const val WORLD_CLOCK_PREF = "world_clock_zones"
internal val DEFAULT_WORLD_ZONES = listOf("America/Los_Angeles", "Europe/Paris", "Asia/Tokyo")
private val ZONE_REGIONS = setOf("Africa", "America", "Asia", "Atlantic", "Australia", "Europe", "Indian", "Pacific")

/** "America/New_York" -> "New York". */
internal fun cityName(zoneId: String): String = zoneId.substringAfterLast('/').replace('_', ' ')

private fun validZone(id: String): Boolean = try { ZoneId.of(id); true } catch (_: Exception) { false }

/** Saved cities. Nothing saved yet gives the defaults; an empty string means the user removed them all. */
internal fun parseZones(raw: String?): List<String> {
    if (raw == null) return DEFAULT_WORLD_ZONES
    return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() && validZone(it) }.distinct()
}

internal fun encodeZones(zones: List<String>): String = zones.joinToString(",")

/** Every city zone the picker can offer, sorted by city name. */
internal fun pickableZones(all: Set<String> = ZoneId.getAvailableZoneIds()): List<String> =
    all.filter { '/' in it && it.substringBefore('/') in ZONE_REGIONS }.sortedBy { cityName(it).lowercase(Locale.ROOT) }

/** Zones whose city or id contains [query] (case-insensitive), skipping [exclude]. */
internal fun searchZones(query: String, zones: List<String>, exclude: Collection<String>, limit: Int = 30): List<String> {
    val q = query.trim().lowercase(Locale.ROOT).replace('_', ' ')
    if (q.isEmpty()) return emptyList()
    return zones.asSequence()
        .filter { it !in exclude }
        .filter { cityName(it).lowercase(Locale.ROOT).contains(q) || it.lowercase(Locale.ROOT).replace('_', ' ').contains(q) }
        .sortedBy { !cityName(it).lowercase(Locale.ROOT).startsWith(q) }
        .take(limit).toList()
}

/** "+3.5h" / "-13.5h" / "+5.75h": how far [other] is ahead of or behind [here]. */
internal fun offsetLabel(here: ZonedDateTime, other: ZonedDateTime): String {
    val hours = (other.offset.totalSeconds - here.offset.totalSeconds) / 3600.0
    return String.format(Locale.US, "%+.2f", hours).trimEnd('0').trimEnd('.') + "h"
}

/** -1 yesterday, 0 same day, 1 tomorrow (relative to [here]'s date). */
internal fun dayShift(here: LocalDate, other: LocalDate): Int =
    ChronoUnit.DAYS.between(here, other).toInt().coerceIn(-1, 1)

/** Position along the 24-hour strip, 0..1. */
internal fun dayFraction(t: LocalTime): Float = (t.toSecondOfDay() / 86400f).coerceIn(0f, 1f)

internal fun isDaytime(t: LocalTime): Boolean = t.hour in 6..17

/** The big time and its small AM/PM suffix (empty in 24-hour mode). */
internal fun clockParts(t: LocalTime, is24: Boolean): Pair<String, String> =
    if (is24) Pair(String.format(Locale.US, "%02d:%02d", t.hour, t.minute), "")
    else Pair(
        String.format(Locale.US, "%d:%02d", if (t.hour % 12 == 0) 12 else t.hour % 12, t.minute),
        if (t.hour < 12) "AM" else "PM"
    )

internal fun MainActivity.showWorldClockPanel() {
    val p = pal
    val allZones by lazy { pickableZones() }
    var zones = parseZones(prefs.getString(WORLD_CLOCK_PREF, null))
    var picking = false
    val dayFmt = DateTimeFormatter.ofPattern("EEE MMM d", Locale.getDefault())
    val abbrFmt = DateTimeFormatter.ofPattern("zzz", Locale.getDefault())
    val dim = p.cDim and 0x00FFFFFF or (0x99 shl 24)

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = box(p.cBg or (0xFF shl 24), p.cBlue)
    }
    var dialog: androidx.appcompat.app.AlertDialog? = null

    fun save() = prefs.edit().putString(WORLD_CLOCK_PREF, encodeZones(zones)).apply()

    fun hideKeyboard() {
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(root.windowToken, 0)
    }

    lateinit var showList: () -> Unit

    fun showPicker() {
        picking = true
        root.removeAllViews()
        root.addView(txt(15f, p.cBlue, "~/add-city"))
        root.addView(txt(11f, p.cDim, "type a city, e.g. London or Sao Paulo").apply { setPadding(0, dp(4), 0, dp(10)) })
        val input = EditText(this).apply {
            hint = "city"
            textSize = 14f * uiScale * (uiTextSize / 13f)
            setTextColor(p.cFg)
            setHintTextColor(p.cDim)
            typeface = font
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE
            background = box(Color.TRANSPARENT, p.cDim)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        root.addView(input, LinearLayout.LayoutParams(-1, -2))
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(results) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, dp(190)).apply { topMargin = dp(8) })

        fun refresh() {
            results.removeAllViews()
            val q = input.text.toString()
            val hits = searchZones(q, allZones, zones)
            if (hits.isEmpty()) {
                results.addView(txt(11f, p.cDim, if (q.isBlank()) "start typing to search" else "no match").apply {
                    setPadding(dp(4), dp(8), dp(4), dp(8))
                })
            }
            for (id in hits) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), dp(9), dp(8), dp(9))
                    setOnClickListener {
                        zones = zones + id
                        save()
                        hideKeyboard()
                        showList()
                    }
                }
                row.addView(txt(13f, p.cFg, cityName(id)), LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(txt(10f, p.cDim, id.substringBefore('/')))
                results.addView(row)
            }
        }
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = refresh()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        refresh()
        root.addView(txt(13f, p.cDim, "BACK").apply {
            gravity = Gravity.END
            setPadding(dp(14), dp(10), dp(4), dp(4))
            setOnClickListener { hideKeyboard(); showList() }
        }, LinearLayout.LayoutParams(-1, -2))
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        input.requestFocus()
    }

    showList = list@{
        picking = false
        root.removeAllViews()
        val now = Instant.now()
        val here = now.atZone(ZoneId.systemDefault())
        val is24 = DateFormat.is24HourFormat(this)
        val hereParts = clockParts(here.toLocalTime(), is24)
        root.addView(txt(11f, p.cDim, "It's ${hereParts.first}${if (hereParts.second.isEmpty()) "" else " " + hereParts.second} here")
            .apply { gravity = Gravity.CENTER })
        val title = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(10))
        }
        title.addView(txt(17f, p.cFg, "World").apply { setTypeface(typeface, Typeface.BOLD) })
        title.addView(GlobeIconView(this, p.cFg), LinearLayout.LayoutParams(dp(30), dp(30)).apply {
            marginStart = dp(10); marginEnd = dp(10)
        })
        title.addView(txt(17f, p.cFg, "Clock").apply { setTypeface(typeface, Typeface.BOLD) })
        root.addView(title, LinearLayout.LayoutParams(-1, -2))

        val card = p.cFg and 0x00FFFFFF or (0x12 shl 24)
        for (id in zones) {
            val t = now.atZone(ZoneId.of(id))
            val shift = dayShift(here.toLocalDate(), t.toLocalDate())
            val parts = clockParts(t.toLocalTime(), is24)
            val dateText = t.format(dayFmt) + when (shift) { -1 -> " · Yesterday"; 1 -> " · Tomorrow"; else -> "" }
            val dateColor = if (shift == 0) dim else p.cFg

            val c = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(8))
                setBackgroundColor(card)
                setOnLongClickListener {
                    zones = zones - id
                    save()
                    Toast.makeText(this@showWorldClockPanel, "Removed ${cityName(id)}", Toast.LENGTH_SHORT).show()
                    showList()
                    true
                }
            }
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            top.addView(txt(15f, p.cFg, cityName(id)).apply { setTypeface(typeface, Typeface.BOLD); maxLines = 1 },
                LinearLayout.LayoutParams(0, -2, 1f))
            top.addView(txt(21f, p.cFg, parts.first).apply { setTypeface(typeface, Typeface.BOLD) })
            if (parts.second.isNotEmpty()) top.addView(txt(10f, p.cDim, " " + parts.second))
            c.addView(top)
            val sub = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            sub.addView(txt(11f, dateColor, dateText).apply { maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f))
            sub.addView(txt(11f, dim, t.format(abbrFmt) + " " + offsetLabel(here, t)))
            c.addView(sub)
            c.addView(DayTrackView(
                this, p.cDim and 0x00FFFFFF or (0x44 shl 24), p.cDim, p.cAccent, p.cFg and 0x00FFFFFF or (0xCC shl 24)
            ).apply {
                fraction = dayFraction(t.toLocalTime())
                daytime = isDaytime(t.toLocalTime())
            }, LinearLayout.LayoutParams(-1, dp(22)))
            root.addView(c, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        if (zones.isEmpty()) {
            root.addView(txt(11f, p.cDim, "no cities yet").apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(14)) },
                LinearLayout.LayoutParams(-1, -2))
        }
        root.addView(txt(13f, p.cFg, "+  Add a city").apply {
            gravity = Gravity.CENTER
            background = box(Color.TRANSPARENT, p.cDim)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { showPicker() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        if (zones.isNotEmpty()) {
            root.addView(txt(9f, p.cDim, "long-press a city to remove it").apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) },
                LinearLayout.LayoutParams(-1, -2))
        }
    }

    showList()
    val ticker = object : Runnable {
        override fun run() {
            if (dialog?.isShowing != true) return
            if (!picking) showList()
            handler.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L + 50L)
        }
    }
    dialog = showBarDialog(BarKind.WORLD_CLOCK, root, 400) { handler.removeCallbacks(ticker) }
    handler.postDelayed(ticker, 60_000L - System.currentTimeMillis() % 60_000L + 50L)
}
