package com.kabigonis.sorairo

import kotlin.math.pow

/**
 * Color-contrast helpers (WCAG relative luminance), so any theme, dark or light,
 * keeps its text readable. Colors are ARGB ints; the alpha channel is ignored.
 */
object Contrast {
    private val BLACK = 0xFF000000.toInt()
    private val WHITE = 0xFFFFFFFF.toInt()

    private fun channel(value: Int): Double {
        val s = value / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    fun luminance(color: Int): Double =
        0.2126 * channel((color shr 16) and 0xFF) +
            0.7152 * channel((color shr 8) and 0xFF) +
            0.0722 * channel(color and 0xFF)

    /** 1.0 (identical) to 21.0 (black on white). */
    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** `t` = 0 gives `a`, 1 gives `b`. */
    fun mix(a: Int, b: Int, t: Float): Int {
        fun part(shift: Int): Int {
            val x = (a shr shift) and 0xFF
            val y = (b shr shift) and 0xFF
            return (x + (y - x) * t).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (part(16) shl 16) or (part(8) shl 8) or part(0)
    }

    /**
     * `color` itself when it already reads on `against`; otherwise the closest shade of it
     * (moved toward white on a dark background, toward black on a light one) that does.
     */
    fun ensure(color: Int, against: Int, min: Double): Int {
        if (ratio(color, against) >= min) return color
        val target = if (luminance(against) > 0.5) BLACK else WHITE
        var t = 0.05f
        while (t < 1f) {
            val shade = mix(color, target, t)
            if (ratio(shade, against) >= min) return shade
            t += 0.05f
        }
        return target
    }

    /**
     * The color to draw text in on a filled surface: `preferred` when it reads on `on`,
     * else the first alternative that does, else whichever of black or white reads best.
     */
    fun readable(preferred: Int, on: Int, vararg alternatives: Int, min: Double = 4.5): Int {
        if (ratio(preferred, on) >= min) return preferred
        for (alternative in alternatives) {
            if (ratio(alternative, on) >= min) return alternative
        }
        return if (ratio(BLACK, on) >= ratio(WHITE, on)) BLACK else WHITE
    }

    fun parse(hex: String): Int = (0xFF shl 24) or hex.removePrefix("#").toInt(16)

    fun hex(color: Int): String = "#%06x".format(color and 0xFFFFFF)
}
