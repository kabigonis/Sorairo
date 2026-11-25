package com.kabigonis.sorairo

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldClockTest {
    private val kolkata = ZoneId.of("Asia/Kolkata")

    @Test fun offsetsMatchTheReference() {
        // 12:32 PM in India on Nov 22, 2026 (no US/EU DST change that day).
        val at = ZonedDateTime.of(2026, 11, 22, 12, 32, 0, 0, kolkata).toInstant()
        assertEquals(-13.5, offsetBetweenHours(ZoneId.of("America/Los_Angeles"), kolkata, at), 0.001)
        assertEquals(-4.5, offsetBetweenHours(ZoneId.of("Europe/Paris"), kolkata, at), 0.001)
        assertEquals(3.5, offsetBetweenHours(ZoneId.of("Asia/Tokyo"), kolkata, at), 0.001)
    }

    @Test fun offsetFormatting() {
        assertEquals("-13.5h", formatOffsetHours(-13.5))
        assertEquals("-4.5h", formatOffsetHours(-4.5))
        assertEquals("+3.5h", formatOffsetHours(3.5))
        assertEquals("+2h", formatOffsetHours(2.0))
        assertEquals("+0h", formatOffsetHours(0.0))
        assertEquals("+5.75h", formatOffsetHours(5.75))
        assertEquals("-10h", formatOffsetHours(-10.0))
    }

    @Test fun timeParts() {
        val la = ZonedDateTime.of(2026, 11, 21, 23, 2, 0, 0, ZoneId.of("America/Los_Angeles"))
        val paris = ZonedDateTime.of(2026, 11, 22, 8, 2, 0, 0, ZoneId.of("Europe/Paris"))
        val noon = ZonedDateTime.of(2026, 11, 22, 12, 5, 0, 0, ZoneId.of("UTC"))
        val midnight = ZonedDateTime.of(2026, 11, 22, 0, 9, 0, 0, ZoneId.of("UTC"))
        assertEquals(Pair("11:02", "PM"), worldTimeParts(la, false))
        assertEquals(Pair("8:02", "AM"), worldTimeParts(paris, false))
        assertEquals(Pair("12:05", "PM"), worldTimeParts(noon, false))
        assertEquals(Pair("12:09", "AM"), worldTimeParts(midnight, false))
        assertEquals(Pair("23:02", ""), worldTimeParts(la, true))
        assertEquals(Pair("08:02", ""), worldTimeParts(paris, true))
    }

    @Test fun relativeDayLabels() {
        val home = LocalDate.of(2026, 11, 22)
        assertEquals("Yesterday", relativeDayLabel(LocalDate.of(2026, 11, 21), home))
        assertEquals("Tomorrow", relativeDayLabel(LocalDate.of(2026, 11, 23), home))
        assertNull(relativeDayLabel(home, home))
    }

    @Test fun dayFractionIsTimeOfDay() {
        assertEquals(0.5, dayFraction(ZonedDateTime.of(2026, 11, 22, 12, 0, 0, 0, ZoneId.of("UTC"))), 1e-9)
        assertEquals(23.0 / 24.0, dayFraction(ZonedDateTime.of(2026, 11, 22, 23, 0, 0, 0, ZoneId.of("UTC"))), 1e-9)
    }

    @Test fun sunTimesAreSensible() {
        val d = LocalDate.of(2026, 11, 22)
        val la = sunWindow(34.0522, -118.2437, d, ZoneId.of("America/Los_Angeles"))
        assertEquals(6.5, la.rise, 0.4)
        assertEquals(16.8, la.set, 0.4)
        val paris = sunWindow(48.8566, 2.3522, d, ZoneId.of("Europe/Paris"))
        assertEquals(8.2, paris.rise, 0.4)
        assertEquals(17.1, paris.set, 0.4)
        val tokyo = sunWindow(35.6762, 139.6503, d, ZoneId.of("Asia/Tokyo"))
        assertEquals(6.4, tokyo.rise, 0.4)
        assertEquals(16.5, tokyo.set, 0.4)
        assertTrue(tokyo.isUp(12.0))
        assertFalse(tokyo.isUp(2.0))
        assertFalse(tokyo.isUp(20.0))
    }

    @Test fun polarDayAndNight() {
        val zone = ZoneId.of("Europe/Oslo")
        assertEquals(SunWindow.POLAR_DAY, sunWindow(69.65, 18.96, LocalDate.of(2026, 6, 21), zone))
        assertEquals(SunWindow.POLAR_NIGHT, sunWindow(69.65, 18.96, LocalDate.of(2026, 12, 21), zone))
        assertTrue(SunWindow.POLAR_DAY.isUp(3.0))
        assertFalse(SunWindow.POLAR_NIGHT.isUp(12.0))
        assertTrue(SunWindow.POLAR_NIGHT.segments().isEmpty())
    }

    @Test fun wrappedDaylightIsSplit() {
        val w = SunWindow(22.0, 6.0)
        assertTrue(w.isUp(23.0))
        assertTrue(w.isUp(2.0))
        assertFalse(w.isUp(12.0))
        assertEquals(listOf(Pair(22.0, 24.0), Pair(0.0, 6.0)), w.segments())
    }

    @Test fun weatherCodes() {
        assertEquals(WeatherKind.CLEAR, weatherKindFor(0))
        assertEquals(WeatherKind.PARTLY, weatherKindFor(2))
        assertEquals(WeatherKind.CLOUDY, weatherKindFor(3))
        assertEquals(WeatherKind.FOG, weatherKindFor(45))
        assertEquals(WeatherKind.DRIZZLE, weatherKindFor(53))
        assertEquals(WeatherKind.RAIN, weatherKindFor(63))
        assertEquals(WeatherKind.RAIN, weatherKindFor(81))
        assertEquals(WeatherKind.SNOW, weatherKindFor(73))
        assertEquals(WeatherKind.SNOW, weatherKindFor(86))
        assertEquals(WeatherKind.STORM, weatherKindFor(95))
        assertEquals(WeatherKind.CLOUDY, weatherKindFor(1234))
    }

    @Test fun temperatureFormatting() {
        assertEquals("53°F", formatTemp(11.7, true))
        assertEquals("12°C", formatTemp(11.7, false))
        assertEquals("32°F", formatTemp(0.0, true))
        assertEquals("-4°C", formatTemp(-4.2, false))
        assertTrue(usesFahrenheit(Locale.US))
        assertFalse(usesFahrenheit(Locale.UK))
    }

    @Test fun cityPersistenceRoundTrips() {
        val cities = listOf(
            WorldCity("São Paulo", "America/Sao_Paulo", -23.55, -46.63),
            WorldCity("Odd\tName", "Asia/Kolkata", 28.61, 77.21)
        )
        val back = decodeWorldCities(encodeWorldCities(cities))
        assertEquals(2, back.size)
        assertEquals("São Paulo", back[0].name)
        assertEquals(-46.63, back[0].lon, 1e-9)
        assertEquals("Odd Name", back[1].name)
    }

    @Test fun neverSavedGivesDefaultsButEmptyStaysEmpty() {
        assertEquals(DEFAULT_WORLD_CITIES, decodeWorldCities(null))
        assertTrue(decodeWorldCities("").isEmpty())
    }

    @Test fun badLinesAreSkipped() {
        val raw = "Good\tEurope/Paris\t48.8\t2.3\nBad zone\tNope/Nowhere\t1\t2\nShort\tline\nNaN\tEurope/Paris\tx\ty"
        val cities = decodeWorldCities(raw)
        assertEquals(1, cities.size)
        assertEquals("Good", cities[0].name)
    }

    @Test fun abbreviationsHaveAFallback() {
        val at = Instant.parse("2026-11-22T12:00:00Z")
        assertEquals("CET", zoneAbbrev(ZoneId.of("Europe/Paris"), at))
        assertEquals("JST", zoneAbbrev(ZoneId.of("Asia/Tokyo"), at))
    }
}
