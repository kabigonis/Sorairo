package com.kabigonis.sorairo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarPanelsTest {
    @Test fun ratesUseTheOmarchyStyle() {
        assertEquals("183 B/s", formatRate(183))
        assertEquals("11.2 KB/s", formatRate(11_200))
        assertEquals("2.5 MB/s", formatRate(2_500_000))
    }

    @Test fun byteTotals() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("9.0 MB", formatBytes(9L * 1024 * 1024))
        assertEquals("n/a", formatBytes(-1))
    }

    @Test fun pingStatsAverageSuccessesAndCountLoss() {
        val (avg, loss) = pingStats(listOf(20L, 40L, -1L, -1L))
        assertEquals(30L, avg)
        assertEquals(50, loss)
        assertNull(pingStats(emptyList()).first)
        assertNull(pingStats(listOf(-1L, -1L)).first)
        assertEquals(100, pingStats(listOf(-1L, -1L)).second)
    }

    @Test fun dnsProviderDetection() {
        assertEquals(0, dnsProviderFor("opportunistic", null))
        assertEquals(0, dnsProviderFor("off", "dns.google"))
        assertEquals(1, dnsProviderFor("hostname", "one.one.one.one"))
        assertEquals(2, dnsProviderFor("hostname", "DNS.GOOGLE"))
        assertEquals(3, dnsProviderFor("hostname", "dns.quad9.net"))
    }

    @Test fun wifiArcsFollowSignalStrength() {
        assertEquals(3, wifiArcs(-45))
        assertEquals(2, wifiArcs(-65))
        assertEquals(1, wifiArcs(-85))
    }

    @Test fun scaleLabels() {
        assertEquals("1x", formatScale(1f))
        assertEquals("1.25x", formatScale(1.25f))
        assertEquals("2x", formatScale(2f))
    }

    @Test fun dnsHostnamesAreCleanedAndChecked() {
        assertEquals("dns.adguard-dns.com", normalizeDnsHost("  https://dns.adguard-dns.com/ "))
        assertEquals("dns.quad9.net", normalizeDnsHost("dns.quad9.net"))
        assertNull(normalizeDnsHost("9.9.9.9"))
        assertNull(normalizeDnsHost("not a host"))
        assertNull(normalizeDnsHost(""))
    }
}
