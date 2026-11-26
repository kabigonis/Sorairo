package com.kabigonis.sorairo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CityDbTest {
    @Test fun databaseCoversEveryZoneAndManyCities() {
        val all = CityDb.all
        assertTrue("cities: ${all.size}", all.size > 450)
        assertTrue(all.map { it.zoneId }.toSet().size > 380)
    }

    @Test fun emptyQueryListsEverythingAlphabetically() {
        val list = CityDb.search("")
        assertTrue(list.size > 450)
        assertEquals(list.map { it.key }.sorted(), list.map { it.key })
    }

    @Test fun prefixBeatsContains() {
        val r = CityDb.search("tok")
        assertEquals("Tokyo", r.first().name)
        val york = CityDb.search("york").map { it.name }
        assertTrue("New York" in york)
    }

    @Test fun searchIgnoresCaseAndAccents() {
        assertTrue(CityDb.search("SÃO").any { it.name == "Sao Paulo" })
        assertEquals("sao paulo", normalizeCityKey("São_Paulo"))
    }

    @Test fun countriesAndZonesAreSearchable() {
        assertTrue(CityDb.search("japan").any { it.name == "Tokyo" })
        assertTrue(CityDb.search("kolkata").any { it.zoneId == "Asia/Kolkata" })
    }

    @Test fun largeCitiesShareTheirZone() {
        val mumbai = CityDb.search("mumbai").first()
        assertEquals("Asia/Kolkata", mumbai.zoneId)
        assertEquals("Los Angeles", CityDb.search("los angeles").first().name)
    }

    @Test fun nearestFindsTheCityOrNothing() {
        assertEquals("Paris", CityDb.nearest(48.9, 2.4, 300.0)?.name)
        assertEquals("Tokyo", CityDb.nearest(35.7, 139.7, 300.0)?.name)
        assertNull(CityDb.nearest(-45.0, -130.0, 800.0)) // empty South Pacific
        assertNotNull(CityDb.nearest(19.0, 72.8, 300.0))
    }

    @Test fun parserSkipsBadLines() {
        val blob = "\nGood|Nowhere|Europe/Paris|1.0|2.0\nBad|Zone|Not/AZone|1|2\nShort|line\nNum|X|Europe/Paris|a|b\n"
        val list = parseCityDb(blob)
        assertEquals(1, list.size)
        assertEquals("Good", list[0].name)
    }

    @Test fun utcOffsetFormatting() {
        assertEquals("UTC+4", formatUtcOffset(4 * 3600))
        assertEquals("UTC-7", formatUtcOffset(-7 * 3600))
        assertEquals("UTC+5:30", formatUtcOffset(5 * 3600 + 1800))
        assertEquals("UTC-9:30", formatUtcOffset(-(9 * 3600 + 1800)))
        assertEquals("UTC+0", formatUtcOffset(0))
    }

    @Test fun popularCitiesAreFlagged() {
        assertTrue(CityDb.popular.size > 200)
        assertTrue(CityDb.popular.any { it.name == "Mumbai" })
        assertTrue(CityDb.popular.any { it.name == "Lagos" })
        assertTrue(CityDb.all.any { it.name == "Majuro" && it.tier == 0 })
        assertTrue(CityDb.all.count { it.tier == 2 } in 40..120)
    }

    @Test fun tappingPrefersPopularCities() {
        assertEquals("Tokyo", CityDb.pick(35.68, 139.65)?.name)
        // a spot between Bangalore, Chennai and Hyderabad snaps to one of the popular three
        assertTrue(CityDb.pick(14.0, 78.0)?.tier ?: 0 >= 1)
        // a lone atoll still resolves to its own town when nothing popular is near
        assertEquals("Majuro", CityDb.pick(7.1, 171.3)?.name)
        assertNull(CityDb.pick(-45.0, -130.0))
    }

    @Test fun parserReadsOptionalTier() {
        val list = parseCityDb("A|X|Europe/Paris|1.0|2.0|2\nB|X|Europe/Paris|3.0|4.0")
        assertEquals(2, list.size)
        assertEquals(2, list.first { it.name == "A" }.tier)
        assertEquals(0, list.first { it.name == "B" }.tier)
    }
}
