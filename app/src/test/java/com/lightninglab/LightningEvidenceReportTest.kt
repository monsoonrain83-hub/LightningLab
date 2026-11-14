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
        assertTrue(lines[1].startsWith("4,1,10,45,30,35,"))
        assertTrue(lines[2].startsWith("4,2,120,180,160,60,"))
        assertTrue(lines[2].contains("visual_heuristic"))
    }

    @Test fun jsonParsesAndPreservesScientificCaveat() {
        val result = JSONObject(LightningEvidenceReport.json(events))
        assertEquals(4, result.getInt("schema_version"))
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

    @Test fun humanReviewIsExplicitAndIndependentOfModel() {
        val decisions = mapOf(1 to LightningReview.REJECTED, 2 to LightningReview.CONFIRMED)
        val obj = JSONObject(LightningEvidenceReport.json(events, emptyMap(), decisions))
        val rows = obj.getJSONArray("events")
        assertEquals("REJECTED", rows.getJSONObject(0).getString("human_review"))
        assertEquals("CONFIRMED", rows.getJSONObject(1).getString("human_review"))
        assertFalse(rows.getJSONObject(0).has("model_label"))
        val csv = LightningEvidenceReport.csv(events, emptyMap(), decisions)
        assertTrue(csv.contains("human_review"))
        assertTrue(csv.contains("\"REJECTED\""))
    }

    @Test fun binaryModelIsIndependentOfHumanDecisionAndBrixton() {
        val decisions = mapOf(1 to LightningReview.REJECTED)
        val evidence = LightningEvidenceReport.json(
            events, emptyMap(), decisions, mapOf(1 to 0.93f))
        val result = JSONObject(evidence)
        val first = result.getJSONArray("events").getJSONObject(0)
        assertEquals("REJECTED", first.getString("human_review"))
        assertEquals(0.93, first.getDouble("binary_lightning_score_uncalibrated"), 0.0001)
        assertFalse(first.has("model_label"))
        assertFalse(result.getJSONArray("events").getJSONObject(1)
            .has("binary_lightning_score_uncalibrated"))
        val csv = LightningEvidenceReport.csv(events, emptyMap(), decisions, mapOf(1 to 0.93f))
        assertTrue(csv.contains("binary_lightning_score_uncalibrated"))
        assertTrue(csv.contains("0.930000"))
    }

    @Test fun invalidBinaryScoreDoesNotEnterEvidence() {
        val result = JSONObject(LightningEvidenceReport.json(
            events, emptyMap(), emptyMap(), mapOf(1 to Float.NaN)))
        assertFalse(result.getJSONArray("events").getJSONObject(0)
            .has("binary_lightning_score_uncalibrated"))
    }

    @Test fun emptyReportIsWellFormed() {
        assertEquals(0, JSONObject(LightningEvidenceReport.json(emptyList()))
            .getJSONArray("events").length())
        assertEquals(1, LightningEvidenceReport.csv(emptyList()).lines().count { it.isNotEmpty() })
    }
}
