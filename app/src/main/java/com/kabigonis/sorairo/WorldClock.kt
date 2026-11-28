package com.kabigonis.sorairo

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan
import org.json.JSONObject

// World clock: city list, time maths, sunrise/sunset, weather and city search.
// Everything above the "network" divider is pure and unit tested.

internal const val WORLD_MAX_CITIES = 8

internal data class WorldCity(
    val name: String,
    val zoneId: String,
    val lat: Double,
    val lon: Double,
    /** Free text the user attaches to a clock: names, @handles, anything. */
    val note: String = ""
) {
    val zone: ZoneId? get() = runCatching { ZoneId.of(zoneId) }.getOrNull()
}

internal val DEFAULT_WORLD_CITIES = listOf(
    WorldCity("Los Angeles", "America/Los_Angeles", 34.0522, -118.2437),
    WorldCity("Paris", "Europe/Paris", 48.8566, 2.3522),
    WorldCity("Tokyo", "Asia/Tokyo", 35.6762, 139.6503)
)

// ---- persistence (tab separated, one city per line) ----

internal fun encodeWorldCities(cities: List<WorldCity>): String =
    cities.joinToString("\n") { c ->
        listOf(
            c.name.replace(Regex("[\t\r\n]"), " "),
            c.zoneId,
            c.lat.toString(),
            c.lon.toString(),
            c.note.replace(Regex("[\t\r\n]"), " ")
        ).joinToString("\t")
    }

/** null means "never saved" and gives the defaults; an empty string is a deliberately empty list. */
internal fun decodeWorldCities(raw: String?): List<WorldCity> {
    if (raw == null) return DEFAULT_WORLD_CITIES
    return raw.split('\n').mapNotNull { line ->
        val f = line.split('\t')
        if (f.size != 4 && f.size != 5) return@mapNotNull null
        val lat = f[2].toDoubleOrNull() ?: return@mapNotNull null
        val lon = f[3].toDoubleOrNull() ?: return@mapNotNull null
        val zoneOk = runCatching { ZoneId.of(f[1]) }.isSuccess
        if (!zoneOk || f[0].isBlank()) null else WorldCity(f[0], f[1], lat, lon, f.getOrElse(4) { "" })
    }.take(WORLD_MAX_CITIES)
}

// ---- time maths ----

/** "11:02" + "PM" in 12-hour mode, "23:02" + "" in 24-hour mode. */
internal fun worldTimeParts(t: ZonedDateTime, is24: Boolean): Pair<String, String> =
    if (is24) {
        Pair(String.format(Locale.US, "%02d:%02d", t.hour, t.minute), "")
    } else {
        val h = if (t.hour % 12 == 0) 12 else t.hour % 12
        Pair(String.format(Locale.US, "%d:%02d", h, t.minute), if (t.hour < 12) "AM" else "PM")
    }

/** Hours the city is ahead of (+) or behind (-) the home zone right now. */
internal fun offsetBetweenHours(zone: ZoneId, home: ZoneId, at: Instant): Double =
    (zone.rules.getOffset(at).totalSeconds - home.rules.getOffset(at).totalSeconds) / 3600.0

/** "-13.5h", "+3.5h", "+2h", "+0h". */
internal fun formatOffsetHours(h: Double): String {
    val sign = if (h < 0) "-" else "+"
    val body = String.format(Locale.US, "%.2f", abs(h)).trimEnd('0').trimEnd('.')
    return "$sign${body}h"
}

internal fun relativeDayLabel(cityDate: LocalDate, homeDate: LocalDate): String? =
    when (ChronoUnit.DAYS.between(homeDate, cityDate)) {
        -1L -> "Yesterday"
        1L -> "Tomorrow"
        else -> null
    }

/** Position of the time within its day, 0..1. */
internal fun dayFraction(t: ZonedDateTime): Double =
    (t.hour * 3600 + t.minute * 60 + t.second) / 86400.0

private val ZONE_ABBR: Map<String, Pair<String, String>> = HashMap<String, Pair<String, String>>().apply {
    fun put(std: String, dst: String, vararg ids: String) {
        for (id in ids) this[id] = Pair(std, dst)
    }
    put(
        "CET", "CEST", "Europe/Paris", "Europe/Berlin", "Europe/Madrid", "Europe/Rome", "Europe/Amsterdam",
        "Europe/Brussels", "Europe/Vienna", "Europe/Zurich", "Europe/Stockholm", "Europe/Oslo",
        "Europe/Copenhagen", "Europe/Prague", "Europe/Warsaw", "Europe/Budapest"
    )
    put("EET", "EEST", "Europe/Athens", "Europe/Helsinki", "Europe/Kiev", "Europe/Kyiv", "Europe/Bucharest", "Europe/Sofia")
    put("GMT", "BST", "Europe/London")
    put("WET", "WEST", "Europe/Lisbon")
    put("MSK", "MSK", "Europe/Moscow", "Europe/Minsk")
    put("JST", "JST", "Asia/Tokyo")
    put("KST", "KST", "Asia/Seoul")
    put("IST", "IST", "Asia/Kolkata", "Asia/Calcutta")
    put("CST", "CST", "Asia/Shanghai", "Asia/Taipei")
    put("HKT", "HKT", "Asia/Hong_Kong")
    put("SGT", "SGT", "Asia/Singapore")
    put("ICT", "ICT", "Asia/Bangkok", "Asia/Ho_Chi_Minh", "Asia/Saigon")
    put("WIB", "WIB", "Asia/Jakarta")
    put("PHT", "PHT", "Asia/Manila")
    put("PKT", "PKT", "Asia/Karachi")
    put("GST", "GST", "Asia/Dubai")
    put("AEST", "AEDT", "Australia/Sydney", "Australia/Melbourne", "Australia/Canberra")
    put("AEST", "AEST", "Australia/Brisbane")
    put("ACST", "ACDT", "Australia/Adelaide")
    put("AWST", "AWST", "Australia/Perth")
    put("NZST", "NZDT", "Pacific/Auckland")
    put("SAST", "SAST", "Africa/Johannesburg")
    put("WAT", "WAT", "Africa/Lagos")
    put("EAT", "EAT", "Africa/Nairobi")
    put("EET", "EEST", "Africa/Cairo")
    put("BRT", "BRT", "America/Sao_Paulo")
    put("ART", "ART", "America/Argentina/Buenos_Aires", "America/Buenos_Aires")
    put("CST", "CDT", "America/Mexico_City")
}

/** Short zone name (PST, CET, JST). Falls back to UTC+5:30 style when nothing better is known. */
internal fun zoneAbbrev(zone: ZoneId, at: Instant): String {
    val daylight = zone.rules.isDaylightSavings(at)
    val name = TimeZone.getTimeZone(zone.id).getDisplayName(daylight, TimeZone.SHORT, Locale.US)
    if (name.isNotEmpty() && !name.startsWith("GMT") && !name.startsWith("UTC")) return name
    ZONE_ABBR[zone.id]?.let { return if (daylight) it.second else it.first }
    val secs = zone.rules.getOffset(at).totalSeconds
    val h = abs(secs) / 3600
    val m = (abs(secs) % 3600) / 60
    val sign = if (secs < 0) "-" else "+"
    return if (m == 0) "UTC$sign$h" else String.format(Locale.US, "UTC%s%d:%02d", sign, h, m)
}

/** "UTC+4", "UTC-7", "UTC+5:30". */
internal fun formatUtcOffset(totalSeconds: Int): String {
    val sign = if (totalSeconds < 0) "-" else "+"
    val a = abs(totalSeconds)
    val h = a / 3600
    val m = (a % 3600) / 60
    return if (m == 0) "UTC$sign$h" else String.format(Locale.US, "UTC%s%d:%02d", sign, h, m)
}

/** "+3h 15m", "-45m", "+2h": how far the clocks have been scrubbed from now. */
internal fun formatShift(ms: Long): String {
    val sign = if (ms < 0) "-" else "+"
    val mins = abs(ms) / 60000L
    val h = mins / 60L
    val m = mins % 60L
    return when {
        h == 0L -> "$sign${m}m"
        m == 0L -> "$sign${h}h"
        else -> "$sign${h}h ${m}m"
    }
}

/** Where the sun is straight overhead at [at], as (latitude, longitude) in degrees. */
internal fun subsolarPoint(at: Instant): Pair<Double, Double> {
    val t = at.atZone(ZoneId.of("UTC"))
    val hour = t.hour + t.minute / 60.0 + t.second / 3600.0
    val g = 2.0 * Math.PI / 365.0 * (t.dayOfYear - 1 + (hour - 12.0) / 24.0)
    val eq = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) -
        0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
    val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) +
        0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
    var lon = -((hour * 60.0 + eq) - 720.0) / 4.0
    while (lon > 180.0) lon -= 360.0
    while (lon < -180.0) lon += 360.0
    return Pair(Math.toDegrees(decl), lon)
}

// ---- daylight ----

/** Local hours (0..24) between sunrise and sunset. rise == set means polar night, 0..24 means polar day. */
internal data class SunWindow(val rise: Double, val set: Double) {
    fun isUp(hour: Double): Boolean = when {
        rise == set -> false
        rise < set -> hour >= rise && hour < set
        else -> hour >= rise || hour < set
    }

    /** Pieces of the day to draw as daylight, each as (from, to) in hours. */
    fun segments(): List<Pair<Double, Double>> = when {
        rise == set -> emptyList()
        rise < set -> listOf(Pair(rise, set))
        else -> listOf(Pair(rise, 24.0), Pair(0.0, set))
    }

    companion object {
        val POLAR_DAY = SunWindow(0.0, 24.0)
        val POLAR_NIGHT = SunWindow(0.0, 0.0)
    }
}

/** NOAA sunrise/sunset approximation, good to a few minutes. */
internal fun sunWindow(lat: Double, lon: Double, date: LocalDate, zone: ZoneId): SunWindow {
    val noon = date.atTime(12, 0).atZone(zone).toInstant()
    val offsetH = zone.rules.getOffset(noon).totalSeconds / 3600.0
    val g = 2.0 * Math.PI / 365.0 * (date.dayOfYear - 1)
    val eq = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) -
        0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
    val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) +
        0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
    val latR = Math.toRadians(lat.coerceIn(-89.9, 89.9))
    val cosHa = cos(Math.toRadians(90.833)) / (cos(latR) * cos(decl)) - tan(latR) * tan(decl)
    if (cosHa > 1.0) return SunWindow.POLAR_NIGHT
    if (cosHa < -1.0) return SunWindow.POLAR_DAY
    val ha = Math.toDegrees(acos(cosHa))
    fun local(utcMinutes: Double): Double {
        val h = (utcMinutes / 60.0 + offsetH) % 24.0
        return if (h < 0) h + 24.0 else h
    }
    return SunWindow(local(720.0 - 4.0 * (lon + ha) - eq), local(720.0 - 4.0 * (lon - ha) - eq))
}

// ---- weather ----

internal enum class WeatherKind { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

/** WMO weather interpretation codes, as returned by Open-Meteo. */
internal fun weatherKindFor(code: Int): WeatherKind = when (code) {
    0, 1 -> WeatherKind.CLEAR
    2 -> WeatherKind.PARTLY
    3 -> WeatherKind.CLOUDY
    45, 48 -> WeatherKind.FOG
    in 51..57 -> WeatherKind.DRIZZLE
    in 61..67, in 80..82 -> WeatherKind.RAIN
    in 71..77, 85, 86 -> WeatherKind.SNOW
    in 95..99 -> WeatherKind.STORM
    else -> WeatherKind.CLOUDY
}

internal fun usesFahrenheit(locale: Locale): Boolean =
    locale.country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")

internal fun formatTemp(celsius: Double, fahrenheit: Boolean): String {
    val v = if (fahrenheit) celsius * 9.0 / 5.0 + 32.0 else celsius
    return "${v.roundToInt()}°${if (fahrenheit) "F" else "C"}"
}

internal data class CityWeather(val tempC: Double, val code: Int, val isDay: Boolean, val fetchedAtMs: Long)

internal data class CitySuggestion(
    val name: String,
    val detail: String,
    val zoneId: String,
    val lat: Double,
    val lon: Double
) {
    fun toCity() = WorldCity(name, zoneId, lat, lon)
}

// ---- network (Open-Meteo, no key needed) ----

internal fun parseWeatherJson(body: String, nowMs: Long): CityWeather? = try {
    val cur = JSONObject(body).getJSONObject("current")
    CityWeather(
        cur.getDouble("temperature_2m"),
        cur.getInt("weather_code"),
        cur.optInt("is_day", 1) == 1,
        nowMs
    )
} catch (_: Exception) {
    null
}

internal fun parseGeocodeJson(body: String): List<CitySuggestion> = try {
    val arr = JSONObject(body).optJSONArray("results")
    val out = ArrayList<CitySuggestion>()
    if (arr != null) {
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val tz = o.optString("timezone", "")
            if (tz.isEmpty() || runCatching { ZoneId.of(tz) }.isFailure) continue
            val detail = listOf(o.optString("admin1", ""), o.optString("country", ""))
                .filter { it.isNotBlank() }.joinToString(", ")
            out.add(CitySuggestion(o.getString("name"), detail, tz, o.getDouble("latitude"), o.getDouble("longitude")))
        }
    }
    out
} catch (_: Exception) {
    emptyList()
}

private fun httpGetText(url: String): String? {
    val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
        connectTimeout = 8000
        readTimeout = 8000
        requestMethod = "GET"
        setRequestProperty("User-Agent", "Sorairo/1.0")
        setRequestProperty("Accept", "application/json")
    }
    return try {
        if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
    } catch (_: Exception) {
        null
    } finally {
        conn.disconnect()
    }
}

internal object WorldWeather {
    private const val TTL_MS = 15L * 60L * 1000L
    private val cache = HashMap<String, CityWeather>()

    private fun key(c: WorldCity) = String.format(Locale.US, "%.2f,%.2f", c.lat, c.lon)

    fun cached(c: WorldCity): CityWeather? = synchronized(cache) { cache[key(c)] }

    fun isFresh(c: WorldCity, nowMs: Long): Boolean =
        cached(c)?.let { nowMs - it.fetchedAtMs < TTL_MS } ?: false

    /** Blocking. Call off the main thread. */
    fun fetch(c: WorldCity, nowMs: Long): CityWeather? {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day",
            c.lat, c.lon
        )
        val w = httpGetText(url)?.let { parseWeatherJson(it, nowMs) } ?: return null
        synchronized(cache) { cache[key(c)] = w }
        return w
    }
}

/** Blocking. Call off the main thread. */
internal fun searchCities(query: String): List<CitySuggestion> {
    val q = query.trim()
    if (q.length < 2) return emptyList()
    val url = "https://geocoding-api.open-meteo.com/v1/search?name=" +
        java.net.URLEncoder.encode(q, "UTF-8") + "&count=6&language=en&format=json"
    return httpGetText(url)?.let { parseGeocodeJson(it) } ?: emptyList()
}
