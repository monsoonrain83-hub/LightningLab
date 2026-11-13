package com.lightninglab

import java.util.Locale

/** Auditable sky-video frames with optional RGB-quality cautions. */
object SkyVideoEvidence {
    const val VERSION = 2
    fun csv(report: CloudVideoReport): String = csvFrames(report.frameResults)

    fun csvFrames(frames: List<CloudVideoFrameResult>): String = buildString {
        append("schema_version,elapsed_ms,cover_percent,oktas,texture,kind_label,model_genus_candidate,model_softmax_uncalibrated,")
        append("clipped_percent,dark_percent,warm_red_percent,rgb_quality_flags\n")
        frames.sortedBy { it.elapsedMillis }.forEach { f ->
            append(VERSION).append(',').append(f.elapsedMillis).append(',')
                .append(f.coverPercent).append(',').append(f.oktas).append(',')
                .append(String.format(Locale.US, "%.5f", f.texture)).append(',')
                .append(quote(f.kindLabel)).append(',').append(quote(f.genusCode ?: ""))
                .append(',').append(if (f.score?.isFinite() == true) String.format(Locale.US, "%.5f", f.score) else "")
                .append(',').append(f.quality?.clippedPercent ?: "")
                .append(',').append(f.quality?.darkPercent ?: "")
                .append(',').append(f.quality?.warmRedPercent ?: "")
                .append(',').append(quote(f.quality?.flags?.joinToString("|") { it.name } ?: ""))
                .append('\n')
        }
    }
    private fun quote(s: String): String = "\"" + s.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\""
}
