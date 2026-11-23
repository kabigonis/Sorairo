package com.kabigonis.sorairo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryPanelTest {
    @Test fun powerFromMicroamps() {
        assertEquals(5.4f, batteryPowerW(4000, -1_350_000L)!!, 0.01f)
    }

    @Test fun powerFromMilliampsIsScaled() {
        assertEquals(5.4f, batteryPowerW(4000, 1350L)!!, 0.01f)
    }

    @Test fun powerMissingWhenUnavailable() {
        assertNull(batteryPowerW(4000, 0L))
        assertNull(batteryPowerW(0, 1_000_000L))
        assertNull(batteryPowerW(4000, Int.MIN_VALUE.toLong()))
    }

    @Test fun batterySizeFromChargeCounter() {
        assertEquals(25f, batterySizeWh(2_812_500L, 45, 4000)!!, 0.1f)
        assertNull(batterySizeWh(0L, 45, 4000))
        assertNull(batterySizeWh(1_000_000L, 2, 4000))
    }

    @Test fun dischargeTime() {
        // 2.8125 Ah left at 1.35 A is about 2h05m.
        assertEquals(125, dischargeMinutes(2_812_500L, -1_350_000L))
        assertNull(dischargeMinutes(0L, -1_350_000L))
        assertNotNull(dischargeMinutes(1_000_000L, 500_000L))
    }

    @Test fun durationFormatting() {
        assertEquals("1h 42m", formatMinutes(102))
        assertEquals("59m", formatMinutes(59))
        assertEquals("2h 0m", formatMinutes(120))
    }

    @Test fun displayedTimeLeftSnapsToFiveMinutes() {
        assertEquals(125, roundMinutesForDisplay(127))
        assertEquals(130, roundMinutesForDisplay(128))
        assertEquals(10, roundMinutesForDisplay(10))
    }

    @Test fun smoothingDampsSpikesButFollowsTrends() {
        // first sample passes through
        assertEquals(1000f, smoothValue(null, 1000f, 2000L), 0.001f)
        // one big spike for 2 s moves the average only a little
        val afterSpike = smoothValue(1000f, 3000f, 2000L)
        assertEquals(true, afterSpike > 1000f && afterSpike < 1200f)
        // a sustained new level is mostly reached after several time constants
        var v = 1000f
        repeat(150) { v = smoothValue(v, 2000f, 2000L) }
        assertEquals(2000f, v, 20f)
    }

    @Test fun stateLabels() {
        assertEquals("SPENDING JOULES", batteryStateLabel(3, false))   // discharging
        assertEquals("CHARGING", batteryStateLabel(2, true))
        assertEquals("PLUGGED IN", batteryStateLabel(4, true))         // plugged but "not charging"
        assertEquals("PLUGGED IN", batteryStateLabel(3, true))
        assertEquals("FULL", batteryStateLabel(5, true))
    }
}
