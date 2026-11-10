package com.lightninglab

/** A parsed SubRip subtitle cue in output-timeline milliseconds. */
data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

/** Defensive, dependency-free SRT parser. Invalid blocks are ignored; valid cues are sorted by time. */
object SubRip {
    private val timeLine = Regex(
        """^\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})(?:\s+.*)?$"""
    )

    fun parse(source: String): List<SubtitleCue> {
        val normalized = source.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val cues = ArrayList<SubtitleCue>()
        for (block in normalized.split(Regex("\n[ \t]*\n+"))) {
            val lines = block.lines()
            val timeIndex = lines.indexOfFirst { timeLine.matches(it.trim()) }
            if (timeIndex < 0) continue
            val match = timeLine.matchEntire(lines[timeIndex].trim()) ?: continue
            val start = timestamp(match.groupValues, 1) ?: continue
            val end = timestamp(match.groupValues, 5) ?: continue
            if (end <= start) continue
            val text = lines.drop(timeIndex + 1).joinToString("\n").trim()
                .replace(Regex("<[^>]*>"), "")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'")
            if (text.isNotBlank()) cues += SubtitleCue(start, end, text)
        }
        return cues.sortedWith(compareBy<SubtitleCue> { it.startMs }.thenBy { it.endMs })
    }

    /** Serialize editable text overlays back to standard SubRip. */
    fun serialize(cues: List<SubtitleCue>): String = cues
        .filter { it.endMs > it.startMs && it.text.isNotBlank() }
        .sortedWith(compareBy<SubtitleCue> { it.startMs }.thenBy { it.endMs })
        .mapIndexed { index, cue ->
            "${index + 1}\n${formatTimestamp(cue.startMs)} --> ${formatTimestamp(cue.endMs)}\n${cue.text.replace("\r\n", "\n").replace('\r', '\n')}"
        }.joinToString("\n\n").let { if (it.isEmpty()) "" else "$it\n" }

    private fun formatTimestamp(value: Long): String {
        val ms = value.coerceAtLeast(0L)
        val hours = ms / 3_600_000L
        val minutes = (ms / 60_000L) % 60L
        val seconds = (ms / 1_000L) % 60L
        val millis = ms % 1_000L
        return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')},${millis.toString().padStart(3, '0')}"
    }

    private fun timestamp(parts: List<String>, offset: Int): Long? {
        val hours = parts[offset].toLongOrNull() ?: return null
        val minutes = parts[offset + 1].toLongOrNull() ?: return null
        val seconds = parts[offset + 2].toLongOrNull() ?: return null
        val millisText = parts[offset + 3].padEnd(3, '0')
        val millis = millisText.toLongOrNull() ?: return null
        if (minutes !in 0..59 || seconds !in 0..59 || millis !in 0..999) return null
        if (hours > (Long.MAX_VALUE - 3_599_999L) / 3_600_000L) return null
        return hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L + millis
    }
}
