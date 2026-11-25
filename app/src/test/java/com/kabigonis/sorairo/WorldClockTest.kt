package com.kabigonis.sorairo

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldClockTest {
    // 2026-11-22 12:32 in India (UTC+5:30), the reference screenshot's "here".
    private val here = ZonedDateTime.of(2026, 11, 22, 12, 32, 0, 0, ZoneId.of("Asia/Kolkata"))
    private fun at(zone: String) = here.withZoneSameInstant(ZoneId.of(zone))

    @Test fun offsetsMatchTheReference() {
        assertEquals("-13.5h", offsetLabel(here, at("America/Los_Angeles")))
        assertEquals("-4.5h", offsetLabel(here, at("Europe/Paris")))
        assertEquals("+3.5h", offsetLabel(here, at("Asia/Tokyo")))
        assertEquals("+0h", offsetLabel(here, here))
    }

    @Test fun kathmanduIsFifteenMinutesAheadOfIndia() {
        assertEquals("+0.25h", offsetLabel(here, at("Asia/Kathmandu")))
    }

    @Test fun losAngelesIsYesterdayParisAndTokyoAreToday() {
        assertEquals(-1, dayShift(here.toLocalDate(), at("America/Los_Angeles").toLocalDate()))
        assertEquals(0, dayShift(here.toLocalDate(), at("Europe/Paris").toLocalDate()))
        assertEquals(1, dayShift(LocalDate.of(2026, 11, 22), LocalDate.of(2026, 11, 23)))
    }

    @Test fun clockPartsHandle12And24Hour() {
        assertEquals(Pair("11:02", "PM"), clockParts(LocalTime.of(23, 2), false))
        assertEquals(Pair("12:05", "AM"), clockParts(LocalTime.of(0, 5), false))
        assertEquals(Pair("12:30", "PM"), clockParts(LocalTime.of(12, 30), false))
        assertEquals(Pair("08:02", ""), clockParts(LocalTime.of(8, 2), true))
    }

    @Test fun dayStripPositionAndDaylight() {
        assertEquals(0.5f, dayFraction(LocalTime.NOON), 0.0001f)
        assertTrue(isDaytime(LocalTime.of(16, 2)))
        assertFalse(isDaytime(LocalTime.of(23, 2)))
        assertFalse(isDaytime(LocalTime.of(5, 59)))
    }

    @Test fun savedCitiesRoundTripAndDropBadIds() {
        assertEquals(DEFAULT_WORLD_ZONES, parseZones(null))
        assertEquals(emptyList<String>(), parseZones(""))
        assertEquals(listOf("Asia/Tokyo"), parseZones("Asia/Tokyo, Nope/Nowhere,Asia/Tokyo"))
        assertEquals("Europe/Paris,Asia/Tokyo", encodeZones(listOf("Europe/Paris", "Asia/Tokyo")))
    }

    @Test fun citySearchMatchesNamesAndSkipsAdded() {
        val zones = listOf("Europe/London", "America/New_York", "America/Argentina/Buenos_Aires", "Asia/Tokyo")
        assertEquals("New York", cityName("America/New_York"))
        assertEquals("Buenos Aires", cityName("America/Argentina/Buenos_Aires"))
        assertEquals(listOf("America/New_York"), searchZones("new york", zones, emptyList()))
        assertEquals(listOf("America/Argentina/Buenos_Aires"), searchZones("buenos", zones, emptyList()))
        assertEquals(emptyList<String>(), searchZones("tokyo", zones, listOf("Asia/Tokyo")))
        assertEquals(emptyList<String>(), searchZones("  ", zones, emptyList()))
    }
}
