package com.kabigonis.sorairo

import org.junit.Assert.*
import org.junit.Test

class IslandCoreTest {
    private fun activity(
        id: String,
        kind: IslandKind = IslandKind.NOTIFICATION,
        title: String = id,
        ttlMs: Long = 0L,
        progress: Float? = null,
        subtitle: String = ""
    ) = IslandActivity(id, kind, title, subtitle, progress, ttlMs)

    @Test fun priorityOrderSelectsHighestPriority() {
        val core = IslandCore()
        core.submit(activity("media", IslandKind.MEDIA), 0)
        core.submit(activity("alarm", IslandKind.ALARM), 1)
        core.submit(activity("timer", IslandKind.TIMER), 2)
        assertEquals("alarm", core.snapshot(2).current?.id)
    }

    @Test fun mostRecentlyUpdatedWinsWithinSamePriority() {
        val core = IslandCore()
        core.submit(activity("one", IslandKind.MEDIA), 0)
        core.submit(activity("two", IslandKind.MEDIA), 1)
        assertEquals("two", core.snapshot(1).current?.id)
        core.submit(activity("one", IslandKind.MEDIA, title = "updated"), 2)
        assertEquals("one", core.snapshot(2).current?.id)
    }

    @Test fun sameIdReplacesWithoutIncreasingCountOrRestartingPeek() {
        val core = IslandCore()
        core.submit(activity("timer", IslandKind.TIMER, title = "First"), 100)
        core.submit(activity("timer", IslandKind.TIMER, title = "Second"), 500)
        val snapshot = core.snapshot(500)
        assertEquals("Second", snapshot.current?.title)
        assertEquals(0, snapshot.queuedCount)
        assertEquals(2_600L, snapshot.nextWakeAt)
    }

    @Test fun nonStickyActivityExpiresAtTtlBoundary() {
        val core = IslandCore()
        core.submit(activity("short", ttlMs = 100), 1_000)
        assertNotNull(core.snapshot(1_099).current)
        assertNull(core.snapshot(1_100).current)
    }

    @Test fun eligibleNewActivityPeeksThenCollapses() {
        val core = IslandCore()
        core.submit(activity("hud", IslandKind.HUD), 10)
        assertEquals(IslandState.PEEK, core.snapshot(10).state)
        assertEquals(IslandState.PEEK, core.snapshot(2_509).state)
        assertEquals(IslandState.COLLAPSED, core.snapshot(2_510).state)
    }

    @Test fun userExpansionPreventsAutomaticCollapse() {
        val core = IslandCore()
        core.submit(activity("alarm", IslandKind.ALARM), 0)
        core.tap(100)
        assertEquals(IslandState.EXPANDED, core.snapshot(10_000).state)
        core.collapse(10_001)
        assertEquals(IslandState.COLLAPSED, core.snapshot(10_001).state)
    }

    @Test fun tapTogglesExpandedAndCollapsed() {
        val core = IslandCore()
        core.submit(activity("sticky"), 0)
        core.tap(1)
        assertEquals(IslandState.EXPANDED, core.snapshot(1).state)
        core.tap(2)
        assertEquals(IslandState.COLLAPSED, core.snapshot(2).state)
    }

    @Test fun capacityIsEightAndReplacementStillWorksAtCapacity() {
        val core = IslandCore()
        repeat(8) { assertTrue(core.submit(activity("n$it"), it.toLong()).accepted) }
        val rejected = core.submit(activity("ninth"), 9)
        assertFalse(rejected.accepted)
        assertNotNull(rejected.error)
        assertTrue(core.submit(activity("n0", title = "replaced"), 10).accepted)
        assertEquals(7, core.snapshot(10).queuedCount)
    }

    @Test fun invalidFieldsAreReportedWithoutThrowing() {
        val core = IslandCore()
        val invalid = listOf(
            activity("UPPER"),
            activity("x".repeat(33)),
            activity("long-title", title = "t".repeat(61)),
            activity("long-subtitle", subtitle = "s".repeat(81)),
            activity("control", title = "bad\nvalue"),
            activity("bad-progress", progress = 1.1f),
            activity("nan-progress", progress = Float.NaN),
            activity("negative-ttl", ttlMs = -1)
        )
        invalid.forEach { value ->
            val result = core.submit(value, 0)
            assertFalse("Expected rejection for ${value.id}", result.accepted)
            assertFalse(result.error.isNullOrBlank())
        }
        assertNull(core.snapshot(0).current)
    }

    @Test fun stickyActivityDoesNotExpire() {
        val core = IslandCore()
        core.submit(activity("sticky", ttlMs = 0), 0)
        core.tick(Long.MAX_VALUE)
        assertEquals("sticky", core.snapshot(Long.MAX_VALUE).current?.id)
        assertNull(core.snapshot(Long.MAX_VALUE).nextWakeAt)
    }

    @Test fun queuedCountAndNextWakeAtIncludeExpiriesAndPeekDeadline() {
        val core = IslandCore()
        core.submit(activity("sticky", IslandKind.MEDIA), 0)
        core.submit(activity("short", IslandKind.NOTIFICATION, ttlMs = 700), 10)
        assertEquals(1, core.snapshot(10).queuedCount)
        assertEquals(710L, core.snapshot(10).nextWakeAt)

        val peeking = IslandCore()
        peeking.submit(activity("hud", IslandKind.HUD, ttlMs = 5_000), 100)
        assertEquals(2_600L, peeking.snapshot(100).nextWakeAt)
    }

    @Test fun mediaAndNotificationOnlyPeekWhenTitleChanges() {
        val core = IslandCore()
        core.submit(activity("media", IslandKind.MEDIA, title = "Track A"), 0)
        assertEquals(IslandState.COLLAPSED, core.snapshot(0).state)
        core.submit(activity("media", IslandKind.MEDIA, title = "Track B"), 100)
        assertEquals(IslandState.PEEK, core.snapshot(100).state)

        val notifications = IslandCore()
        notifications.submit(activity("notice", IslandKind.NOTIFICATION, title = "One"), 0)
        notifications.submit(activity("notice", IslandKind.NOTIFICATION, title = "Two"), 10)
        assertEquals(IslandState.PEEK, notifications.snapshot(10).state)
    }

    @Test fun removeClearsActivity() {
        val core = IslandCore()
        core.submit(activity("remove-me"), 0)
        assertTrue(core.remove("remove-me"))
        assertFalse(core.remove("remove-me"))
        assertNull(core.snapshot(1).current)
    }
}
