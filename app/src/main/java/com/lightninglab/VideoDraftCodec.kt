package com.lightninglab

import org.json.JSONObject

/**
 * A small recoverable editor draft. Media bytes are NOT copied to avoid
 * exhausting storage or battery. Recoverability depends on live URI grants.
 */
internal data class VideoDraft(val videoUri: String, val project: ProjectData, val savedAtMs: Long)

internal object VideoDraftCodec {
    const val VERSION = 1
    fun encode(draft: VideoDraft): String {
        require(draft.videoUri.isNotBlank() && draft.videoUri.length <= 8192)
        require(draft.savedAtMs >= 0)
        return JSONObject()
            .put("version", VERSION)
            .put("source_uri", draft.videoUri)
            .put("saved_at_ms", draft.savedAtMs)
            .put("project", JSONObject(ProjectCodec.encode(draft.project)))
            .toString()
    }

    fun decode(data: String): VideoDraft? {
        return try {
        if (data.length > 2_000_000) return null
        val doc = JSONObject(data)
        if (doc.optInt("version") != VERSION) return null
        val uri = doc.optString("source_uri")
        val saved = doc.optLong("saved_at_ms", -1L)
        if (uri.isBlank() || uri.length > 8192 || saved < 0) return null
        val project = ProjectCodec.decode(doc.getJSONObject("project").toString()) ?: return null
        // Draft references may be content://, file://, but never remote/network URIs.
        if (!uri.startsWith("content://") && !uri.startsWith("file://")) return null
        VideoDraft(uri, project, saved)
        } catch (_: Exception) { null }
    }
}
