package com.lightninglab

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LightningEvidenceReportTest {
    private val events = listOf(
        LightningEvent(2, 120L, 180L, 160L, 0.4f, LightningVisualType.LOCALIZED_FLASH, 0.5f, 0.2f, 2),
        LightningEvent(1, 10L, 45L, 30L, 0.9f, LightningVisualType.VISIBLE_CHANNEL, 0.8f, 0.1f, 1)
    )

    @Test fun csvHasDeterministicChronologicalOrderAndDuration() {
        val csv = LightningEvidenceReport.csv(events)
        val lines = csv.lines().filter { it.isNotEmpty() }
        assertEquals(3, lines.size)
        assertTrue(lines[0].contains("duration_ms"))
        assertTrue(lines[1].startsWith("1,1,10,45,30,35,"))
        assertTrue(lines[2].startsWith("1,2,120,180,160,60,"))
        assertTrue(lines[2].contains("visual_heuristic"))
    }

    @Test fun jsonParsesAndPreservesScientificCaveat() {
        val result = JSONObject(LightningEvidenceReport.json(events))
        assertEquals(1, result.getInt("schema_version"))
        assertEquals("visual_heuristic", result.getString("source"))
        assertTrue(result.getString("scientific_caveat").contains("CG/IC"))
        val rows = result.getJSONArray("events")
        assertEquals(2, rows.length())
        assertEquals(1, rows.getJSONObject(0).getInt("id"))
        assertEquals(60L, rows.getJSONObject(1).getLong("duration_ms"))
    }

    @Test fun modelPredictionsAppearOnlyWhenPresent() {
        val pred = mapOf(1 to listOf("close" to 0.75f))
        val obj = JSONObject(LightningEvidenceReport.json(events, pred))
        val first = obj.getJSONArray("events").getJSONObject(0)
        val second = obj.getJSONArray("events").getJSONObject(1)
        assertEquals("close", first.getString("model_label"))
        assertEquals(0.75, first.getDouble("model_softmax_uncalibrated"), 0.0001)
        assertFalse(second.has("model_label"))
        val csv = LightningEvidenceReport.csv(events, pred)
        assertTrue(csv.lines()[0].contains("model_softmax_uncalibrated"))
        assertTrue(csv.contains("\"close\",0.750000"))
    }

    @Test fun emptyReportIsWellFormed() {
        assertEquals(0, JSONObject(LightningEvidenceReport.json(emptyList()))
            .getJSONArray("events").length())
        assertEquals(1, LightningEvidenceReport.csv(emptyList()).lines().count { it.isNotEmpty() })
    }
}
