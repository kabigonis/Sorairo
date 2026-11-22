package com.kabigonis.sorairo

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarPopupTest {
    @Test fun sundayStartGridBeginsOnTheFirstWhenItIsASunday() {
        // Nov 1, 2026 is a Sunday.
        assertEquals(LocalDate.of(2026, 11, 1), calendarGridStart(YearMonth.of(2026, 11), DayOfWeek.SUNDAY))
    }

    @Test fun mondayStartGridBacksUpToThePreviousMonday() {
        assertEquals(LocalDate.of(2026, 10, 26), calendarGridStart(YearMonth.of(2026, 11), DayOfWeek.MONDAY))
    }

    @Test fun yearProgressMatchesTheReference() {
        assertEquals(89, yearProgressPercent(LocalDate.of(2026, 11, 22)))
        assertEquals(0, yearProgressPercent(LocalDate.of(2026, 1, 1)))
        assertEquals(100, yearProgressPercent(LocalDate.of(2026, 12, 31)))
    }

    @Test fun sundayStartWeekNumbersMatchTheReference() {
        val wf = WeekFields.of(DayOfWeek.SUNDAY, 1)
        assertEquals(48, LocalDate.of(2026, 11, 22).get(wf.weekOfYear()))
        assertEquals(45, LocalDate.of(2026, 11, 1).get(wf.weekOfYear()))
    }
}
