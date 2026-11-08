package com.kabigonis.sorairo

import android.content.Context

/**
 * Omarchy themes, read from their colors.toml and mapped onto Sorairo's seven
 * theme colors. The bundled palettes (assets/omarchy-themes.txt) come from
 * Omarchy (MIT, Copyright (c) David Heinemeier Hansson); the way missing colors
 * are filled in for older themes follows Omarchy's own cascade, as also done by
 * Omalauncher (MIT, Copyright (c) 2026 Simon Schubert and contributors).
 * See THIRD_PARTY.md.
 */
object OmarchyThemes {
    /** A theme in Sorairo's terms. Every color is "#rrggbb". */
    class Palette(
        val id: String,
        val name: String,
        val bg: String,
        val fg: String,
        val dim: String,
        val blue: String,
        val green: String,
        val accent: String,
        val sel: String,
        val isLight: Boolean,
        val ansiColors: List<String>
    )

    private const val BUNDLE = "omarchy-themes.txt"
    private val LINE = Regex("""^\s*([A-Za-z0-9_]+)\s*=\s*"([^"]*)"""")
    private val HEX = Regex("^#[0-9a-fA-F]{6}$")

    /** The themes shipped inside the app. A broken block is skipped, never fatal. */
    fun loadBundled(context: Context): List<Palette> {
        val text = runCatching {
            context.assets.open(BUNDLE).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return emptyList()
        return splitBundle(text).mapNotNull { (id, toml) ->
            runCatching { fromColorsToml(id, toml) }.getOrNull()
        }
    }

    /** "#### name" starts a block; everything up to the next one is that theme's colors.toml. */
    internal fun splitBundle(text: String): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        var id: String? = null
        val body = StringBuilder()
        fun flush() {
            id?.let { out += it to body.toString() }
            body.setLength(0)
        }
        for (line in text.lineSequence()) {
            if (line.startsWith("#### ")) {
                flush()
                id = line.removePrefix("#### ").trim()
            } else if (id != null) {
                body.append(line).append('\n')
            }
        }
        flush()
        return out
    }

    /** Null when the file lacks the colors Sorairo needs or any of them isn't a plain hex color. */
    fun fromColorsToml(id: String, text: String): Palette? {
        val raw = text.lineSequence()
            .mapNotNull { LINE.find(it)?.destructured?.let { (k, v) -> k to v } }
            .toMap()
        val c = resolve(raw)
        fun hex(key: String): String? = c[key]?.takeIf { HEX.matches(it) }?.lowercase()

        val bg = hex("background") ?: return null
        val fg = hex("bright_foreground") ?: hex("foreground") ?: return null
        val dim = hex("dark_foreground") ?: hex("muted") ?: return null
        val blue = hex("blue") ?: hex("accent") ?: return null
        val green = hex("green") ?: return null
        val accent = hex("yellow") ?: hex("accent") ?: return null
        val sel = hex("selection") ?: hex("selection_background") ?: dim
        val ansiKeys = listOf(
            "background", "red", "green", "yellow", "blue", "magenta", "cyan", "foreground",
            "dark_foreground", "bright_red", "bright_green", "bright_yellow", "bright_blue",
            "bright_magenta", "bright_cyan", "bright_foreground"
        )
        val ansiFallbacks = listOf(bg, "#f7768e", green, accent, blue, "#bb9af7", "#7dcfff", fg,
            dim, "#ff899d", "#b9f27c", "#ffda8b", "#8db0ff", "#c7a0ff", "#8de6ff", fg)
        val ansiColors = ansiKeys.mapIndexed { index, key ->
            hex(key) ?: ansiFallbacks[index]
        }
        val light = when (c["mode"]) {
            "light" -> true
            "dark" -> false
            else -> luminance(bg) > 0.5
        }
        val bgInt = Contrast.parse(bg)
        fun readable(color: String, min: Double) =
            Contrast.hex(Contrast.ensure(Contrast.parse(color), bgInt, min))
        val readableAnsi = ansiColors.mapIndexed { index, color ->
            when (index) {
                0 -> color
                7, 15 -> readable(color, 4.5)
                else -> readable(color, 3.0)
            }
        }
        return Palette(
            id, titleOf(id), bg,
            readable(fg, 4.5), readable(dim, 3.0),
            readable(blue, 4.0), readable(green, 4.0), readable(accent, 4.0),
            sel, light, readableAnsi
        )
    }

    /** Fills in what a theme leaves out, falling back to the older ANSI colorN names. */
    internal fun resolve(raw: Map<String, String>): Map<String, String> {
        val c = raw.toMutableMap()
        fun default(key: String, value: () -> String?) {
            if (c[key].isNullOrEmpty()) value()?.let { c[key] = it }
        }
        default("background") { c["color0"] }
        default("foreground") { c["color7"] }
        default("green") { c["color2"] }
        default("yellow") { c["color3"] }
        default("blue") { c["color4"] }
        default("red") { c["color1"] }
        default("magenta") { c["color5"] }
        default("cyan") { c["color6"] }
        default("bright_red") { c["color9"] }
        default("bright_green") { c["color10"] }
        default("bright_yellow") { c["color11"] }
        default("bright_blue") { c["color12"] }
        default("bright_magenta") { c["color13"] }
        default("bright_cyan") { c["color14"] }
        default("bright_foreground") { c["color15"] ?: c["foreground"] }
        default("dark_foreground") { c["color8"] ?: c["foreground"] }
        default("muted") { c["color8"] ?: c["dark_foreground"] }
        default("selection") {
            c["selection_background"] ?: c["color8"] ?: c["color0"] ?: c["background"]
        }
        default("mode") { c["theme_type"] }
        return c
    }

    private fun luminance(hex: String): Double {
        val v = hex.removePrefix("#")
        val r = v.substring(0, 2).toInt(16)
        val g = v.substring(2, 4).toInt(16)
        val b = v.substring(4, 6).toInt(16)
        return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
    }

    /** "osaka-jade" -> "Osaka Jade" */
    internal fun titleOf(id: String): String =
        id.split('-', '_').filter { it.isNotEmpty() }
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
}
