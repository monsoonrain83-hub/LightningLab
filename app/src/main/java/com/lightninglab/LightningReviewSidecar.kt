package com.lightninglab

import android.content.Context
import androidx.core.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Human review decisions persisted ONLY in private app storage. Never stores
 * file paths, video content, or cloud credentials in the sidecar.
 *
 * Exact event timestamps are used rather than detector-assigned integers, so
 * decisions cannot silently transfer to a different event after rescanning.
 */
internal object LightningReviewKeys {
    fun event(startMs: Long, endMs: Long, bestMs: Long): String {
        require(startMs >= 0 && endMs >= startMs && bestMs >= startMs && bestMs <= endMs)
        return "$startMs:$endMs:$bestMs"
    }

    fun decode(decision: String): LightningReview? =
        LightningReview.values().firstOrNull { it.name == decision }
}

internal class LightningReviewSidecar(context: Context) {
    private val dir = File(context.filesDir, "lightning-human-reviews")
    private fun digest(source: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun file(source: String): AtomicFile {
        require(source.isNotBlank() && source.length <= 8192)
        return AtomicFile(File(dir, digest(source) + ".json"))
    }

    @Synchronized
    fun read(source: String): Map<String, LightningReview> {
        return try {
        val disk = file(source)
        if (!disk.baseFile.isFile || disk.baseFile.length() > 100_000L) return emptyMap()
        val doc = JSONObject(disk.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
        if (doc.optInt("version") != 1 || doc.optString("source_hash") != digest(source)) return emptyMap()
        val rows = doc.getJSONArray("reviews")
        if (rows.length() > 500) return emptyMap()
        val result = LinkedHashMap<String, LightningReview>()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val key = row.optString("event")
            val state = LightningReviewKeys.decode(row.optString("human_review"))
            if (Regex("[0-9]{1,19}:[0-9]{1,19}:[0-9]{1,19}").matches(key) &&
                state != null && state != LightningReview.UNREVIEWED) result[key] = state
        }
        result
        } catch (_: Exception) { emptyMap() }
    }

    @Synchronized
    fun save(source: String, decisions: Map<String, LightningReview>) {
        require(decisions.size <= 500)
        if (!dir.exists() && !dir.mkdirs()) error("Cannot create review sidecar folder")
        val sourceHash = digest(source)
        val rows = JSONArray()
        decisions.toSortedMap().forEach { (key, review) ->
            require(Regex("[0-9]{1,19}:[0-9]{1,19}:[0-9]{1,19}").matches(key))
            if (review != LightningReview.UNREVIEWED)
                rows.put(JSONObject().put("event", key).put("human_review", review.name))
        }
        val bytes = JSONObject().put("version", 1).put("source_hash", sourceHash)
            .put("reviews", rows).toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 100_000)
        val file = file(source)
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }
}
