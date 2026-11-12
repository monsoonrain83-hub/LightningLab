package com.lightninglab

import java.util.Locale

/** One photo analysis report, explicitly separating optical proxy measurements and ML. */
object SkyPhotoEvidence {
    const val VERSION = 1
    private fun quote(s: String) = "\"" + s.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\""
    private fun num(v: Float) = if (v.isFinite()) String.format(Locale.US, "%.5f", v) else ""

    fun csv(observation: CloudAnalysis.Result, candidates: List<Pair<String, Float>>): String = buildString {
        append("schema_version,cover_ratio_proxy,oktas_proxy,cloud_luma,texture,contrast,heuristic_kind,")
        append("model_genus_candidate,model_softmax_uncalibrated,assessment\n")
        val candidate = candidates.filter { it.second.isFinite() }.maxByOrNull { it.second }
        append(VERSION).append(',').append(num(observation.cover)).append(',')
            .append(observation.oktas).append(',').append(num(observation.cloudLuma)).append(',')
            .append(num(observation.texture)).append(',').append(num(observation.contrast))
            .append(',').append(quote(observation.kind.name)).append(',')
            .append(quote(candidate?.first ?: "")).append(',')
            .append(candidate?.second?.let(::num) ?: "").append(',')
            .append(quote("RGB sky heuristic; not WMO-confirmed, not measured precipitation")).append('\n')
    }
}
