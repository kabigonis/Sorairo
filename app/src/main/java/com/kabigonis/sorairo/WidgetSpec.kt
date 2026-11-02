package com.kabigonis.sorairo

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Collections

/** Immutable, data-only model for Sorairo Widget Format v1. */
data class WidgetSpec(
    val schema: Int,
    val id: String,
    val name: String,
    val author: String,
    val license: String,
    val version: Int,
    val description: String,
    val size: WidgetSize,
    val providers: List<String>,
    val refreshMs: Int,
    val root: WidgetNode,
) {
    data class WidgetSize(val cols: Int, val rows: Int)

    sealed interface WidgetNode {
        data class Container(
            val type: ContainerType,
            val children: List<WidgetNode>,
            val gap: Int,
            val align: Align,
        ) : WidgetNode
        data class Text(val text: String, val size: TextSize, val color: String?, val maxLines: Int) : WidgetNode
        data class Bar(val value: Double, val max: Double, val color: String?) : WidgetNode
        data class Spark(val series: String, val color: String?) : WidgetNode
        data object Divider : WidgetNode
        data class Spacer(val size: Int) : WidgetNode
    }

    enum class ContainerType { COLUMN, ROW }
    enum class Align { START, CENTER, END }
    enum class TextSize { SMALL, NORMAL, LARGE }

    sealed interface ParseResult {
        data class Success(val spec: WidgetSpec) : ParseResult
        data class Failure(val errors: List<String>) : ParseResult
    }

    companion object {
        private const val MAX_INPUT_BYTES = 16 * 1024
        private const val MAX_NODES = 60
        private const val MAX_DEPTH = 6
        private val colorTokens = setOf("fg", "dim", "accent", "blue", "green", "red", "yellow")
        private val providerFields = mapOf(
            "time" to setOf("hh", "mm", "ss", "date", "weekday"),
            "battery" to setOf("pct", "charging", "tempC"),
            "memory" to setOf("usedPct", "usedGb", "totalGb"),
            "storage" to setOf("diskPct", "sdPct", "diskUsedGb", "diskTotalGb"),
            "network" to setOf("down", "up", "downBps", "upBps", "type"),
            "uptime" to setOf("text"),
            "nowplaying" to setOf("title", "artist", "state", "positionPct"),
            "theme" to setOf("name"),
            "workspace" to setOf("index"),
        )
        private val placeholder = Regex("\\{([a-z][a-z0-9]*)\\.([A-Za-z][A-Za-z0-9]*)}")

        fun parse(json: String): ParseResult {
            if (json.toByteArray(Charsets.UTF_8).size > MAX_INPUT_BYTES) {
                return ParseResult.Failure(listOf("Input exceeds 16 KB"))
            }
            val errors = mutableListOf<String>()
            val obj = try {
                val tokener = JSONTokener(json)
                val value = tokener.nextValue()
                if (tokener.nextClean() != '\u0000') {
                    return ParseResult.Failure(listOf("Input contains trailing data"))
                }
                value as? JSONObject
                    ?: return ParseResult.Failure(listOf("Top-level JSON value must be an object"))
            } catch (e: JSONException) {
                return ParseResult.Failure(listOf("Invalid JSON: ${e.message ?: "malformed input"}"))
            } catch (_: RuntimeException) {
                return ParseResult.Failure(listOf("Invalid JSON"))
            }

            scanControls(obj, "$", errors)
            rejectKeys(obj, setOf(
                "schema", "id", "name", "author", "license", "version",
                "description", "size", "providers", "refreshMs", "root",
            ), "$", errors)

            val schema = int(obj, "schema", "$", errors)
            if (schema != null && schema != 1) errors += "$.schema must equal 1"
            val id = string(obj, "id", "$", 3, 40, errors)
            if (id != null && !Regex("[a-z0-9-]{3,40}").matches(id)) {
                errors += "$.id must match [a-z0-9-] and be 3..40 characters"
            }
            val name = string(obj, "name", "$", 1, 40, errors)
            val author = string(obj, "author", "$", 1, 40, errors)
            val license = string(obj, "license", "$", 1, 30, errors)
            val version = int(obj, "version", "$", errors)?.also {
                if (it < 1) errors += "$.version must be a positive integer"
            }
            val description = string(obj, "description", "$", 0, 140, errors)
            val refreshMs = int(obj, "refreshMs", "$", errors)?.also {
                if (it !in 1_000..60_000) errors += "$.refreshMs must be 1000..60000"
            }

            val sizeObj = objectField(obj, "size", "$", errors)
            var size: WidgetSize? = null
            if (sizeObj != null) {
                rejectKeys(sizeObj, setOf("cols", "rows"), "$.size", errors)
                val cols = int(sizeObj, "cols", "$.size", errors)?.also {
                    if (it !in 1..6) errors += "$.size.cols must be 1..6"
                }
                val rows = int(sizeObj, "rows", "$.size", errors)?.also {
                    if (it !in 1..6) errors += "$.size.rows must be 1..6"
                }
                if (cols != null && rows != null) size = WidgetSize(cols, rows)
            }

            val providers = providers(obj, errors)
            val rootObj = objectField(obj, "root", "$", errors)
            val nodeCount = intArrayOf(0)
            val root = rootObj?.let { node(it, "$.root", 1, providers ?: emptySet(), nodeCount, errors) }

            if (errors.isNotEmpty()) return ParseResult.Failure(Collections.unmodifiableList(errors.toList()))
            return ParseResult.Success(
                WidgetSpec(
                    schema = schema!!,
                    id = id!!,
                    name = name!!,
                    author = author!!,
                    license = license!!,
                    version = version!!,
                    description = description!!,
                    size = size!!,
                    providers = Collections.unmodifiableList(providers!!.toList()),
                    refreshMs = refreshMs!!,
                    root = root!!,
                ),
            )
        }

        /** Replaces {provider.field}; unresolved values are rendered as an en dash. */
        fun resolveText(template: String, values: Map<String, Map<String, String>>): String =
            placeholder.replace(template) { match ->
                values[match.groupValues[1]]?.get(match.groupValues[2]) ?: "–"
            }

        private fun providers(obj: JSONObject, errors: MutableList<String>): Set<String>? {
            if (!obj.has("providers")) {
                errors += "$.providers is required"
                return null
            }
            val array = obj.optJSONArray("providers")
            if (array == null) {
                errors += "$.providers must be an array"
                return null
            }
            val result = linkedSetOf<String>()
            for (i in 0 until array.length()) {
                val value = array.opt(i)
                if (value !is String) {
                    errors += "$.providers[$i] must be a string"
                } else if (value !in providerFields) {
                    errors += "$.providers[$i] is not an allowed provider"
                } else if (!result.add(value)) {
                    errors += "$.providers[$i] duplicates provider '$value'"
                }
            }
            return result
        }

        private fun node(
            obj: JSONObject,
            path: String,
            depth: Int,
            declared: Set<String>,
            count: IntArray,
            errors: MutableList<String>,
        ): WidgetNode? {
            count[0]++
            if (count[0] > MAX_NODES) {
                if (count[0] == MAX_NODES + 1) errors += "Widget contains more than 60 nodes"
                return null
            }
            if (depth > MAX_DEPTH) {
                errors += "$path exceeds maximum node depth of 6"
                return null
            }
            val typeValue = obj.opt("type")
            if (typeValue !is String) {
                errors += "$path.type must be a string"
                return null
            }
            return when (typeValue) {
                "column", "row" -> {
                    rejectKeys(obj, setOf("type", "children", "gap", "align"), path, errors)
                    val array = obj.optJSONArray("children")
                    if (array == null) errors += "$path.children must be an array"
                    val children = mutableListOf<WidgetNode>()
                    if (array != null) {
                        if (array.length() == 0) errors += "$path.children must not be empty"
                        for (i in 0 until array.length()) {
                            val child = array.optJSONObject(i)
                            if (child == null) errors += "$path.children[$i] must be a node object"
                            else node(child, "$path.children[$i]", depth + 1, declared, count, errors)?.let(children::add)
                        }
                    }
                    val gap = optionalInt(obj, "gap", 0, path, errors)?.also {
                        if (it !in 0..16) errors += "$path.gap must be 0..16"
                    }
                    val alignValue = optionalString(obj, "align", "start", path, errors)
                    val align = when (alignValue) {
                        "start" -> Align.START
                        "center" -> Align.CENTER
                        "end" -> Align.END
                        else -> null.also { errors += "$path.align must be start, center or end" }
                    }
                    if (gap == null || align == null) null else WidgetNode.Container(
                        if (typeValue == "column") ContainerType.COLUMN else ContainerType.ROW,
                        Collections.unmodifiableList(children.toList()), gap, align,
                    )
                }
                "text" -> {
                    rejectKeys(obj, setOf("type", "text", "size", "color", "maxLines"), path, errors)
                    val text = string(obj, "text", path, 0, 500, errors)
                    if (text != null) validateTemplate(text, declared, path, errors)
                    val sizeValue = optionalString(obj, "size", "normal", path, errors)
                    val size = when (sizeValue) {
                        "small" -> TextSize.SMALL
                        "normal" -> TextSize.NORMAL
                        "large" -> TextSize.LARGE
                        else -> null.also { errors += "$path.size must be small, normal or large" }
                    }
                    val color = color(obj, "color", path, errors)
                    val lines = optionalInt(obj, "maxLines", 1, path, errors)?.also {
                        if (it !in 1..4) errors += "$path.maxLines must be 1..4"
                    }
                    if (text == null || size == null || lines == null) null
                    else WidgetNode.Text(text, size, color, lines)
                }
                "bar" -> {
                    rejectKeys(obj, setOf("type", "value", "max", "color"), path, errors)
                    val value = number(obj, "value", path, errors)
                    val max = number(obj, "max", path, errors)
                    if (value != null && value < 0) errors += "$path.value must be non-negative"
                    if (max != null && max <= 0) errors += "$path.max must be greater than zero"
                    if (value != null && max != null && value > max) errors += "$path.value must not exceed max"
                    val color = color(obj, "color", path, errors)
                    if (value == null || max == null || value < 0 || max <= 0 || value > max) null
                    else WidgetNode.Bar(value, max, color)
                }
                "spark" -> {
                    rejectKeys(obj, setOf("type", "series", "color"), path, errors)
                    val series = string(obj, "series", path, 1, 40, errors)
                    val allowed = setOf("network.down", "network.up", "memory.usedPct")
                    if (series != null && series !in allowed) errors += "$path.series is not allowed"
                    val color = color(obj, "color", path, errors)
                    if (series == null || series !in allowed) null else WidgetNode.Spark(series, color)
                }
                "divider" -> {
                    rejectKeys(obj, setOf("type"), path, errors)
                    WidgetNode.Divider
                }
                "spacer" -> {
                    rejectKeys(obj, setOf("type", "size"), path, errors)
                    val size = int(obj, "size", path, errors)?.also {
                        if (it !in 0..32) errors += "$path.size must be 0..32"
                    }
                    size?.takeIf { it in 0..32 }?.let { WidgetNode.Spacer(it) }
                }
                else -> {
                    errors += "$path.type '$typeValue' is not an allowed node type"
                    null
                }
            }
        }

        private fun validateTemplate(text: String, declared: Set<String>, path: String, errors: MutableList<String>) {
            val matches = placeholder.findAll(text).toList()
            val remainder = placeholder.replace(text, "")
            if ('{' in remainder || '}' in remainder) {
                errors += "$path.text has an invalid placeholder; only {provider.field} is allowed"
            }
            for (match in matches) {
                val provider = match.groupValues[1]
                val field = match.groupValues[2]
                if (provider !in providerFields) errors += "$path.text uses unsupported provider '$provider'"
                else if (provider !in declared) errors += "$path.text uses undeclared provider '$provider'"
                else if (field !in providerFields.getValue(provider)) errors += "$path.text uses unknown field '$provider.$field'"
            }
        }

        private fun color(obj: JSONObject, key: String, path: String, errors: MutableList<String>): String? {
            if (!obj.has(key)) return null
            val value = obj.opt(key)
            if (value !is String || value !in colorTokens) {
                errors += "$path.$key must be a theme color token (fg, dim, accent, blue, green, red, yellow)"
                return null
            }
            return value
        }

        private fun scanControls(value: Any?, path: String, errors: MutableList<String>) {
            when (value) {
                is JSONObject -> {
                    val keys = value.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (key.any { it.isISOControl() }) errors += "$path contains a control character in a key"
                        scanControls(value.opt(key), "$path.$key", errors)
                    }
                }
                is JSONArray -> for (i in 0 until value.length()) scanControls(value.opt(i), "$path[$i]", errors)
                is String -> if (value.any { it.isISOControl() }) errors += "$path contains a control character"
            }
        }

        private fun rejectKeys(obj: JSONObject, allowed: Set<String>, path: String, errors: MutableList<String>) {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key !in allowed) errors += "$path contains unknown key '$key'"
            }
        }

        private fun string(
            obj: JSONObject, key: String, path: String, min: Int, max: Int, errors: MutableList<String>,
        ): String? {
            if (!obj.has(key)) {
                errors += "$path.$key is required"
                return null
            }
            val value = obj.opt(key)
            if (value !is String) {
                errors += "$path.$key must be a string"
                return null
            }
            if (value.length !in min..max) errors += "$path.$key must contain $min..$max characters"
            return value
        }

        private fun optionalString(
            obj: JSONObject, key: String, default: String, path: String, errors: MutableList<String>,
        ): String? {
            if (!obj.has(key)) return default
            val value = obj.opt(key)
            if (value !is String) {
                errors += "$path.$key must be a string"
                return null
            }
            return value
        }

        private fun int(obj: JSONObject, key: String, path: String, errors: MutableList<String>): Int? {
            if (!obj.has(key)) {
                errors += "$path.$key is required"
                return null
            }
            val value = obj.opt(key)
            if (value !is Number) {
                errors += "$path.$key must be an integer"
                return null
            }
            val number = value.toDouble()
            if (!number.isFinite() || number % 1.0 != 0.0 || number < Int.MIN_VALUE || number > Int.MAX_VALUE) {
                errors += "$path.$key must be an integer"
                return null
            }
            return number.toInt()
        }

        private fun optionalInt(
            obj: JSONObject, key: String, default: Int, path: String, errors: MutableList<String>,
        ): Int? = if (obj.has(key)) int(obj, key, path, errors) else default

        private fun number(obj: JSONObject, key: String, path: String, errors: MutableList<String>): Double? {
            if (!obj.has(key)) {
                errors += "$path.$key is required"
                return null
            }
            val value = obj.opt(key)
            if (value !is Number) {
                errors += "$path.$key must be a number"
                return null
            }
            val number = value.toDouble()
            if (!number.isFinite()) errors += "$path.$key must be finite"
            return number.takeIf { it.isFinite() }
        }

        private fun objectField(
            obj: JSONObject, key: String, path: String, errors: MutableList<String>,
        ): JSONObject? {
            if (!obj.has(key)) {
                errors += "$path.$key is required"
                return null
            }
            val value = obj.opt(key)
            if (value !is JSONObject) {
                errors += "$path.$key must be an object"
                return null
            }
            return value
        }
    }
}
