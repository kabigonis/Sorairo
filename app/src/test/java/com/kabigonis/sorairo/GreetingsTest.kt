package com.kabigonis.sorairo

import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingsTest {
    @Test fun dayPartsFollowTheLocalHour() {
        assertEquals(DayPart.NIGHT, dayPartFor(3))
        assertEquals(DayPart.MORNING, dayPartFor(5))
        assertEquals(DayPart.MORNING, dayPartFor(11))
        assertEquals(DayPart.AFTERNOON, dayPartFor(12))
        assertEquals(DayPart.AFTERNOON, dayPartFor(16))
        assertEquals(DayPart.EVENING, dayPartFor(17))
        assertEquals(DayPart.EVENING, dayPartFor(21))
        assertEquals(DayPart.NIGHT, dayPartFor(22))
        assertEquals(DayPart.NIGHT, dayPartFor(0))
    }

    @Test fun japaneseGreetingsMatchTheReference() {
        assertEquals("ohayou", greetingFor("Japan", 8))
        assertEquals("konnichiwa", greetingFor("Japan", 14))
        assertEquals("konbanwa", greetingFor("Japan", 21))
        assertEquals("oyasumi", greetingFor("Japan", 23))
    }

    @Test fun otherLanguages() {
        assertEquals("bonjour", greetingFor("France", 10))
        assertEquals("bonsoir", greetingFor("France", 19))
        assertEquals("dobryy vecher", greetingFor("Russia", 19))
        assertEquals("namaste", greetingFor("India", 13))
        assertEquals("buenas tardes", greetingFor("Mexico", 15))
        assertEquals("guten morgen", greetingFor("Germany", 7))
    }

    @Test fun unknownPlacesFallBackToEnglish() {
        assertEquals("good morning", greetingFor("United States", 9))
        assertEquals("good evening", greetingFor(null, 18))
        assertEquals("good night", greetingFor("Nowhereland", 2))
    }

    @Test fun capitalising() {
        assertEquals("Konbanwa", capitalizeFirst("konbanwa"))
        assertEquals("", capitalizeFirst(""))
    }
}
