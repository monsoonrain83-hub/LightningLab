package com.lightninglab

import java.util.Locale

/** Stable, offline evidence export. Scores are heuristic, not calibrated probabilities. */
object LightningEvidenceReport {
    const val SCHEMA_VERSION = 4
    private fun number(value: Float): String =
        if (value.isFinite()) String.format(Locale.US, "%.6f", value) else "null"

    private fun quote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""

    private fun csvField(value: String): String =
        "\"" + value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\""

    fun csv(events: List<LightningEvent>, predictions: Map<Int, List<Pair<String, Float>>> = emptyMap(), reviews: Map<Int, LightningReview> = emptyMap(), binary: Map<Int, Float> = emptyMap()): String = buildString {
        append("schema_version,id,start_ms,end_ms,best_ms,duration_ms,algorithm_score,visual_type,visual_evidence,spatial_spread,pulse_count,source,model_label,model_softmax_uncalibrated,human_review,binary_lightning_score_uncalibrated\n")
        for (e in events.sortedWith(compareBy<LightningEvent> { it.startMs }.thenBy { it.id })) {
            append(SCHEMA_VERSION).append(',').append(e.id).append(',')
                .append(e.startMs).append(',').append(e.endMs).append(',').append(e.bestMs).append(',')
                .append((e.endMs - e.startMs).coerceAtLeast(0L)).append(',')
                .append(number(e.score)).append(',').append(csvField(e.visualType.name)).append(',')
                .append(number(e.visualEvidence)).append(',').append(number(e.spatialSpread)).append(',')
                .append(e.pulseCount).append(",visual_heuristic,")
            val prediction = predictions[e.id]?.firstOrNull()
            if (prediction != null) {
                append(csvField(prediction.first)).append(',').append(number(prediction.second))
            } else {
                append(',')
            }
            append(',').append(csvField((reviews[e.id] ?: LightningReview.UNREVIEWED).name))
            append(',').append(binary[e.id]?.takeIf { it.isFinite() && it in 0f..1f }?.let(::number) ?: "")
            append('\n')
        }
    }

    fun json(events: List<LightningEvent>, predictions: Map<Int, List<Pair<String, Float>>> = emptyMap(), reviews: Map<Int, LightningReview> = emptyMap(), binary: Map<Int, Float> = emptyMap()): String = buildString {
        append("{\"schema_version\":").append(SCHEMA_VERSION)
            .append(",\"source\":\"visual_heuristic\"")
            .append(",\"scientific_caveat\":")
            .append(quote("Video RGB alone cannot establish CG/IC, polarity or electrical energy. Scores are not calibrated probabilities."))
            .append(",\"events\":[")
        events.sortedWith(compareBy<LightningEvent> { it.startMs }.thenBy { it.id }).forEachIndexed { i, e ->
            if (i > 0) append(',')
            append("{\"id\":").append(e.id)
                .append(",\"start_ms\":").append(e.startMs)
                .append(",\"end_ms\":").append(e.endMs)
                .append(",\"best_ms\":").append(e.bestMs)
                .append(",\"duration_ms\":").append((e.endMs - e.startMs).coerceAtLeast(0L))
                .append(",\"algorithm_score\":").append(number(e.score))
                .append(",\"visual_type\":").append(quote(e.visualType.name))
                .append(",\"visual_evidence\":").append(number(e.visualEvidence))
                .append(",\"spatial_spread\":").append(number(e.spatialSpread))
                .append(",\"pulse_count\":").append(e.pulseCount)
                .append(",\"human_review\":").append(quote((reviews[e.id] ?: LightningReview.UNREVIEWED).name))
            predictions[e.id]?.firstOrNull()?.let { prediction ->
                append(",\"model_label\":").append(quote(prediction.first))
                    .append(",\"model_softmax_uncalibrated\":").append(number(prediction.second))
            }
            binary[e.id]?.takeIf { it.isFinite() && it in 0f..1f }?.let {
                append(",\\\"binary_lightning_score_uncalibrated\\\":").append(number(it))
            }
            append('}')
        }
        append("]}")
    }
}
