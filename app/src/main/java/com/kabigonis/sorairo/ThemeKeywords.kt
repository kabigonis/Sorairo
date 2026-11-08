package com.kabigonis.sorairo

/** Search aliases for recognizable bundled themes. */
internal object ThemeKeywords {
    private val aliases = mapOf(
        "eva01" to "evangelion eva-01",
        "everforest" to "everforest forest",
        "hackerman" to "hacker terminal",
        "kanagawa" to "kanagawa japan",
        "last-horizon" to "last horizon",
        "lumon" to "severance lumon",
        "miasma" to "miasma dark",
        "osaka-jade" to "osaka jade",
        "ristretto" to "ristretto coffee",
        "rose-pine" to "rose pine",
        "tokyo-night" to "tokyo night"
    )
    fun forId(id: String): String = aliases[id.lowercase()].orEmpty()
}
