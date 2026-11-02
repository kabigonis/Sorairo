package com.kabigonis.sorairo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSpecTest {
    private fun valid(
        id: String = "ram-bar",
        name: String = "RAM",
        author: String = "Manas",
        license: String = "MIT",
        version: String = "1",
        description: String = "Memory usage",
        size: String = """{"cols":3,"rows":2}""",
        providers: String = """["memory"]""",
        refresh: String = "5000",
        root: String = """{"type":"column","gap":4,"align":"start","children":[{"type":"text","text":"RAM {memory.usedPct}%","size":"normal","color":"fg","maxLines":1},{"type":"bar","value":45,"max":100,"color":"accent"},{"type":"spark","series":"memory.usedPct","color":"green"},{"type":"divider"},{"type":"spacer","size":8}]}""",
    ) = """{"schema":1,"id":"$id","name":"$name","author":"$author","license":"$license","version":$version,"description":"$description","size":$size,"providers":$providers,"refreshMs":$refresh,"root":$root}"""

    private fun errors(json: String): List<String> {
        val result = WidgetSpec.parse(json)
        assertTrue("Expected failure, got $result", result is WidgetSpec.ParseResult.Failure)
        return (result as WidgetSpec.ParseResult.Failure).errors
    }

    @Test fun acceptsAValidWidget() {
        val result = WidgetSpec.parse(valid())
        assertTrue("Expected success, got $result", result is WidgetSpec.ParseResult.Success)
        val spec = (result as WidgetSpec.ParseResult.Success).spec
        assertEquals("ram-bar", spec.id)
        assertEquals(3, spec.size.cols)
        assertEquals(2, spec.size.rows)
        assertEquals(listOf("memory"), spec.providers)
        assertEquals(WidgetSpec.ContainerType.COLUMN, (spec.root as WidgetSpec.WidgetNode.Container).type)
    }

    @Test fun rejectsNonStandardLenientJsonSyntax() {
        assertTrue(errors(valid().replace("\\\"schema\\\":1", "'schema':1")).any { it.contains("strict JSON") })
        assertTrue(errors(valid().replace("\\\"schema\\\":1", "schema:1")).any { it.contains("strict JSON") })
        assertTrue(errors(valid().replace("\\\"version\\\":1", "\\\"version\\\":01")).any { it.contains("strict JSON") })
    }

    @Test fun rejectsMalformedJsonAndTrailingData() {
        assertTrue(errors("{").any { it.startsWith("Invalid JSON") })
        assertTrue(errors(valid() + " false").any { it.contains("trailing data") })
        assertTrue(errors("""["not","an","object"]""").isNotEmpty())
    }

    @Test fun rejectsUnknownKeysAtTopAndNestedLevels() {
        assertTrue(errors(valid().replace("\"schema\":1", "\"schema\":1,\"extra\":true")).any { it.contains("unknown key") })
        assertTrue(errors(valid(size = """{"cols":3,"rows":2,"extra":1}""")).any { it.contains("unknown key") })
        assertTrue(errors(valid(root = """{"type":"text","text":"ok","extra":1}""")).any { it.contains("unknown key") })
    }

    @Test fun validatesSchemaAndMetadata() {
        assertTrue(errors(valid().replace("\"schema\":1", "\"schema\":2")).any { it.contains("schema must equal 1") })
        assertTrue(errors(valid(id = "AB")).isNotEmpty())
        assertTrue(errors(valid(id = "a".repeat(41))).isNotEmpty())
        assertTrue(errors(valid(id = "bad_id")).isNotEmpty())
        assertTrue(errors(valid(name = "")).isNotEmpty())
        assertTrue(errors(valid(name = "n".repeat(41))).isNotEmpty())
        assertTrue(errors(valid(author = "")).isNotEmpty())
        assertTrue(errors(valid(author = "a".repeat(41))).isNotEmpty())
        assertTrue(errors(valid(license = "")).isNotEmpty())
        assertTrue(errors(valid(license = "l".repeat(31))).isNotEmpty())
        assertTrue(errors(valid(description = "d".repeat(141))).isNotEmpty())
        assertTrue(errors(valid(version = "0")).isNotEmpty())
        assertTrue(errors(valid(version = "1.5")).isNotEmpty())
        assertTrue(errors(valid().replace("\"name\":\"RAM\"", "\"name\":42")).isNotEmpty())
        assertTrue(errors(valid().replace(",\"license\":\"MIT\"", "")).any { it.contains("license is required") })
    }

    @Test fun validatesSizeAndRefreshRanges() {
        assertTrue(errors(valid(size = """{"cols":0,"rows":2}""")).isNotEmpty())
        assertTrue(errors(valid(size = """{"cols":7,"rows":2}""")).isNotEmpty())
        assertTrue(errors(valid(size = """{"cols":2,"rows":0}""")).isNotEmpty())
        assertTrue(errors(valid(size = """{"cols":2.5,"rows":2}""")).isNotEmpty())
        assertTrue(errors(valid(refresh = "999")).isNotEmpty())
        assertTrue(errors(valid(refresh = "60001")).isNotEmpty())
        assertTrue(errors(valid(refresh = "1000.5")).isNotEmpty())
    }

    @Test fun validatesProviderAllowlistAndTypes() {
        assertTrue(errors(valid(providers = """["filesystem"]""")).any { it.contains("not an allowed provider") })
        assertTrue(errors(valid(providers = """["memory","memory"]""")).any { it.contains("duplicates") })
        assertTrue(errors(valid(providers = """["memory",4]""")).any { it.contains("must be a string") })
        assertTrue(errors(valid().replace("\"providers\":[\"memory\"]", "\"providers\":\"memory\"")).isNotEmpty())
        assertTrue(errors(valid().replace(",\"providers\":[\"memory\"]", "")).any { it.contains("providers is required") })
    }

    @Test fun rejectsUnknownNodeTypesAndNodeKeys() {
        assertTrue(errors(valid(root = """{"type":"javascript","code":"alert(1)"}""")).any { it.contains("not an allowed node type") })
        assertTrue(errors(valid(root = """{"type":"divider","color":"red"}""")).any { it.contains("unknown key") })
        assertTrue(errors(valid(root = """{"type":4}""")).any { it.contains("type must be a string") })
    }

    @Test fun validatesContainerTextAndColors() {
        assertTrue(errors(valid(root = """{"type":"column","children":[]}""")).isNotEmpty())
        assertTrue(errors(valid(root = """{"type":"row","gap":17,"children":[{"type":"divider"}]}""")).isNotEmpty())
        assertTrue(errors(valid(root = """{"type":"row","align":"baseline","children":[{"type":"divider"}]}""")).isNotEmpty())
        assertTrue(errors(valid(root = """{"type":"text","text":"ok","size":"huge"}""")).isNotEmpty())
        assertTrue(errors(valid(root = """{"type":"text","text":"ok","maxLines":5}""")).isNotEmpty())
        assertTrue(errors(valid(root = """{"type":"text","text":"ok","color":"#ffffff"}""")).isNotEmpty())
        assertTrue(errors(valid(root = """{"type":"text","text":"${"x".repeat(501)}"}""")).isNotEmpty())
    }

    @Test fun validatesBarSparkAndSpacer() {
        assertTrue(errors(valid(root = """{"type":"bar","value":-1,"max":100}""")).isNotEmpty())
        assertTrue(errors(valid(root = """{"type":"bar","value":101,"max":100}""")).isNotEmpty())
        assertTrue(errors(valid(root = """{"type":"bar","value":1,"max":0}""")).isNotEmpty())
        assertTrue(errors(valid(root = """{"type":"bar","value":1,"max":2,"color":"purple"}""")).isNotEmpty())
        assertTrue(errors(valid(root = """{"type":"spark","series":"cpu.load"}""")).isNotEmpty())
        assertTrue(errors(valid(root = """{"type":"spacer","size":33}""")).isNotEmpty())
        assertTrue(errors(valid(root = """{"type":"spacer","size":"4"}""")).isNotEmpty())
    }

    @Test fun rejectsControlCharactersAndInputOver16Kb() {
        assertTrue(errors(valid(name = "bad\\nname")).any { it.contains("control character") })
        assertTrue(errors(" ".repeat(16 * 1024 + 1)).any { it.contains("16 KB") })
    }

    @Test fun rejectsDepthOverSixAndMoreThanSixtyNodes() {
        var deep = """{"type":"divider"}"""
        repeat(6) { deep = """{"type":"column","children":[$deep]}""" }
        assertTrue(errors(valid(root = deep)).any { it.contains("depth") })

        val sixtyChildren = (1..60).joinToString(",") { """{"type":"divider"}""" }
        assertTrue(errors(valid(root = """{"type":"column","children":[$sixtyChildren]}""")).any { it.contains("more than 60 nodes") })
    }

    @Test fun rejectsUndeclaredProviderAndInvalidPlaceholderSyntax() {
        assertTrue(errors(valid(root = """{"type":"text","text":"{battery.pct}"}""")).any { it.contains("undeclared provider") })
        assertTrue(errors(valid(providers = """["battery"]""", root = """{"type":"text","text":"{battery.unknown}"}""")).any { it.contains("unknown field") })
        assertTrue(errors(valid(providers = """["battery"]""", root = """{"type":"text","text":"{battery.pct + 1}"}""")).any { it.contains("invalid placeholder") })
        assertTrue(errors(valid(providers = """["battery"]""", root = """{"type":"text","text":"{device.pct}"}""")).any { it.contains("unsupported provider") })
    }

    @Test fun resolvesTextAndUsesDashForMissingValues() {
        assertEquals(
            "Battery 84% –",
            WidgetSpec.resolveText(
                "Battery {battery.pct}% {battery.tempC}",
                mapOf("battery" to mapOf("pct" to "84")),
            ),
        )
    }
}
