package com.kabigonis.sorairo

/** The visual importance used to select the activity shown by the island. */
enum class IslandKind(val priority: Int) {
    ALARM(100),
    TIMER(90),
    HUD(80),
    MEDIA(60),
    POWER(50),
    NOTIFICATION(40)
}

/** Immutable, renderer-independent description of one island activity. */
data class IslandActivity(
    val id: String,
    val kind: IslandKind,
    val title: String,
    val subtitle: String = "",
    val progress: Float? = null,
    /** Zero means that this activity remains until explicitly removed. */
    val ttlMs: Long
)

enum class IslandState { COLLAPSED, PEEK, EXPANDED }

data class IslandSnapshot(
    val state: IslandState,
    val current: IslandActivity?,
    val queuedCount: Int,
    val nextWakeAt: Long?
)

data class IslandSubmitResult(
    val accepted: Boolean,
    val error: String? = null
)

/** Pure Kotlin state machine. Every time-dependent operation receives its clock value. */
class IslandCore {
    private data class Entry(val activity: IslandActivity, val updatedOrder: Long, val expiresAt: Long?)

    private val entries = LinkedHashMap<String, Entry>()
    private var updateOrder = 0L
    private var peekUntil: Long? = null
    private var userExpanded = false

    /** Adds or replaces an activity. Invalid values and capacity overflow are reported, not thrown. */
    fun submit(activity: IslandActivity, now: Long): IslandSubmitResult {
        val validationError = validate(activity)
        if (validationError != null) return IslandSubmitResult(false, validationError)

        val previous = entries[activity.id]
        if (previous == null && entries.size >= MAX_ACTIVITIES) {
            return IslandSubmitResult(false, "Island already contains the maximum of $MAX_ACTIVITIES activities")
        }

        val titleChanged = previous != null && previous.activity.title != activity.title
        updateOrder += 1
        entries[activity.id] = Entry(
            activity = activity,
            updatedOrder = updateOrder,
            expiresAt = expiryAt(now, activity.ttlMs)
        )

        // Replacing an id must not restart an existing peek. A changed MEDIA/NOTIFICATION
        // title is the sole replacement case that can initiate a new peek.
        if (previous == null && activity.kind in PEEK_ON_NEW_KINDS) {
            startPeek(now)
        } else if (previous != null && titleChanged &&
            activity.kind in TITLE_CHANGE_PEEK_KINDS && !userExpanded
        ) {
            startPeek(now)
        }

        return IslandSubmitResult(true)
    }

    fun remove(id: String): Boolean = entries.remove(id) != null

    /** Expires timed activities and ends a peek when its deadline is reached. */
    fun tick(now: Long) {
        val expiredIds = entries.values
            .filter { it.expiresAt != null && now >= it.expiresAt }
            .map { it.activity.id }
        expiredIds.forEach(entries::remove)

        val deadline = peekUntil
        if (!userExpanded && deadline != null && now >= deadline) {
            peekUntil = null
        }
    }

    /** A tap expands the island, or collapses it if the user had expanded it. */
    fun tap(now: Long) {
        tick(now)
        if (entries.isEmpty()) {
            userExpanded = false
            peekUntil = null
            return
        }
        if (userExpanded) {
            collapse(now)
        } else {
            userExpanded = true
            peekUntil = null
        }
    }

    /** Explicitly collapses the island and clears any pending peek. */
    fun collapse(now: Long) {
        tick(now)
        userExpanded = false
        peekUntil = null
    }

    /** Returns a point-in-time view and applies any expirations due at [now]. */
    fun snapshot(now: Long): IslandSnapshot {
        tick(now)
        val current = entries.values.maxWithOrNull(
            compareBy<Entry> { it.activity.kind.priority }.thenBy { it.updatedOrder }
        )?.activity
        val state = when {
            userExpanded && current != null -> IslandState.EXPANDED
            current != null && peekUntil != null && now < peekUntil!! -> IslandState.PEEK
            else -> IslandState.COLLAPSED
        }
        val nextExpiry = entries.values.mapNotNull { it.expiresAt }.minOrNull()
        val nextWakeAt = listOfNotNull(
            nextExpiry,
            if (state == IslandState.PEEK) peekUntil else null
        ).minOrNull()
        return IslandSnapshot(
            state = state,
            current = current,
            queuedCount = (entries.size - if (current == null) 0 else 1).coerceAtLeast(0),
            nextWakeAt = nextWakeAt
        )
    }

    private fun startPeek(now: Long) {
        if (!userExpanded) peekUntil = expiryAt(now, PEEK_DURATION_MS)
    }

    private fun validate(activity: IslandActivity): String? {
        if (!ID_PATTERN.matches(activity.id)) return "id must be 1–32 characters: lowercase letters, digits, ':', '_' or '-'"
        if (activity.title.length > MAX_TITLE_LENGTH) return "title must be at most $MAX_TITLE_LENGTH characters"
        if (activity.subtitle.length > MAX_SUBTITLE_LENGTH) return "subtitle must be at most $MAX_SUBTITLE_LENGTH characters"
        if (containsControlCharacters(activity.title)) return "title must not contain control characters"
        if (containsControlCharacters(activity.subtitle)) return "subtitle must not contain control characters"
        if (activity.progress != null &&
            (!activity.progress.isFinite() || activity.progress < 0f || activity.progress > 1f)
        ) return "progress must be a finite value between 0 and 1"
        if (activity.ttlMs < 0L) return "ttlMs must be 0 (sticky) or greater"
        return null
    }

    private fun containsControlCharacters(value: String): Boolean =
        value.any { Character.isISOControl(it) }

    private fun expiryAt(now: Long, durationMs: Long): Long? {
        if (durationMs == 0L) return null
        return if (now > Long.MAX_VALUE - durationMs) Long.MAX_VALUE else now + durationMs
    }

    companion object {
        const val MAX_ACTIVITIES = 8
        const val PEEK_DURATION_MS = 2_500L
        private const val MAX_TITLE_LENGTH = 60
        private const val MAX_SUBTITLE_LENGTH = 80
        private val ID_PATTERN = Regex("[a-z0-9:_-]{1,32}")
        private val PEEK_ON_NEW_KINDS = setOf(
            IslandKind.ALARM, IslandKind.TIMER, IslandKind.HUD, IslandKind.POWER
        )
        private val TITLE_CHANGE_PEEK_KINDS = setOf(IslandKind.MEDIA, IslandKind.NOTIFICATION)
    }
}
