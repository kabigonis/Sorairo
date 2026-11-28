package com.kabigonis.sorairo

import java.text.Normalizer
import java.time.ZoneId
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

// Offline city / time zone list used by the world clock search and by tapping the globe.
// The data (CityDbData.kt) covers every IANA zone plus a few hundred large cities.

/** Lower-case, accent-free, underscores as spaces: "São_Paulo" -> "sao paulo". */
internal fun normalizeCityKey(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace('_', ' ')
        .trim()

internal class DbCity(
    val name: String,
    val country: String,
    val zoneId: String,
    val lat: Double,
    val lon: Double,
    /** 2 = big city labelled on the globe, 1 = popular city shown as a dot, 0 = just a zone's town. */
    val tier: Int = 0
) {
    val key: String = normalizeCityKey(name)
    val countryKey: String = normalizeCityKey(country)
    val zoneKey: String = normalizeCityKey(zoneId)
    val latR: Double = Math.toRadians(lat)
    val lonR: Double = Math.toRadians(lon)

    fun toCity() = WorldCity(name, zoneId, lat, lon)
}

/** Newer tz names fall back to their older spelling on devices that have not got them yet. */
internal fun resolveZoneId(id: String): String? {
    if (runCatching { ZoneId.of(id) }.isSuccess) return id
    val alt = when (id) {
        "Europe/Kyiv" -> "Europe/Kiev"
        "Europe/Kiev" -> "Europe/Kyiv"
        else -> null
    }
    return if (alt != null && runCatching { ZoneId.of(alt) }.isSuccess) alt else null
}

/** One city per line: name|country|zoneId|lat|lon[|tier]. Bad lines and unknown zones are skipped. */
internal fun parseCityDb(blob: String): List<DbCity> {
    val out = ArrayList<DbCity>()
    for (line in blob.lines()) {
        val f = line.split('|')
        if (f.size != 5 && f.size != 6) continue
        val lat = f[3].toDoubleOrNull() ?: continue
        val lon = f[4].toDoubleOrNull() ?: continue
        val zone = resolveZoneId(f[2]) ?: continue
        out.add(DbCity(f[0], f[1], zone, lat, lon, f.getOrNull(5)?.toIntOrNull() ?: 0))
    }
    return out.sortedBy { it.key }
}

/** Lower is better, -1 means no match. */
internal fun scoreCity(c: DbCity, q: String): Int = when {
    c.key.startsWith(q) -> 0
    c.key.split(' ').any { it.startsWith(q) } -> 1
    c.key.contains(q) -> 2
    c.countryKey.startsWith(q) -> 3
    c.zoneKey.contains(q) -> 4
    else -> -1
}

internal object CityDb {
    val all: List<DbCity> by lazy { parseCityDb(CITY_DB_ALL) }

    /** The well-known cities (tier 1 and 2), drawn on the globe and preferred when tapping it. */
    val popular: List<DbCity> by lazy { all.filter { it.tier >= 1 } }

    /** The cities that are named on the globe (the ones in the Omarchy reference views). */
    val globeCities: List<DbCity> by lazy { all.filter { it.tier >= 2 } }

    /** What a tap on the map means: a popular city nearby, else any listed town very close by. */
    fun pick(lat: Double, lon: Double): DbCity? =
        nearest(lat, lon, 1200.0, popular) ?: nearest(lat, lon, 500.0, all)

    /** Country of a saved clock, used to choose its greeting. */
    fun countryOf(name: String, zoneId: String): String? =
        all.firstOrNull { it.zoneId == zoneId && it.name.equals(name, ignoreCase = true) }?.country
            ?: all.firstOrNull { it.zoneId == zoneId }?.country

    /** Empty query returns everything alphabetically. */
    fun search(query: String, limit: Int = 800, source: List<DbCity> = all): List<DbCity> {
        val q = normalizeCityKey(query)
        if (q.isEmpty()) return source.take(limit)
        val scored = ArrayList<Pair<Int, DbCity>>()
        for (c in source) {
            val s = scoreCity(c, q)
            if (s >= 0) scored.add(Pair(s, c))
        }
        scored.sortWith(compareBy<Pair<Int, DbCity>>({ it.first }, { it.second.key }))
        return scored.take(limit).map { it.second }
    }

    /** Closest listed city to a point on the map, or null if nothing is within [maxKm]. */
    fun nearest(lat: Double, lon: Double, maxKm: Double, source: List<DbCity> = all): DbCity? {
        val la = Math.toRadians(lat)
        val lo = Math.toRadians(lon)
        var best: DbCity? = null
        var bestCos = -2.0
        for (c in source) {
            val v = sin(la) * sin(c.latR) + cos(la) * cos(c.latR) * cos(lo - c.lonR)
            if (v > bestCos) {
                bestCos = v
                best = c
            }
        }
        val km = 6371.0 * acos(bestCos.coerceIn(-1.0, 1.0))
        return if (km <= maxKm) best else null
    }
}
