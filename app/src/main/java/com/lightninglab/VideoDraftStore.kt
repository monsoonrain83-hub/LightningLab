package com.lightninglab

import android.content.Context
import android.graphics.Bitmap
import androidx.core.util.AtomicFile
import java.io.File

/**
 * One compact autosave draft in internal app storage, with atomic metadata and
 * per-overlay atomic PNG writes. Video/audio are referenced, not duplicated.
 */
internal class VideoDraftStore(context: Context) {
    private val dir = File(context.filesDir, "video-autodraft")
    private val metadata = AtomicFile(File(dir, "draft.json"))

    fun exists(): Boolean = metadata.baseFile.isFile

    /** Extract overlay ids the current project still references, so stale PNGs can be pruned. */
    private fun referencedOverlayIds(draft: VideoDraft): Set<Long> =
        draft.project.overlayClips.mapNotNull { it.id.takeIf { id -> id >= 0 } }.toSet()

    fun save(draft: VideoDraft, bitmaps: Map<Long, Bitmap>) {
        if (!dir.exists() && !dir.mkdirs()) error("Could not create draft folder")
        // Write images before publishing the manifest; only reference completed files.
        for ((id, bitmap) in bitmaps) {
            require(id >= 0 && !bitmap.isRecycled)
            val target = AtomicFile(File(dir, "overlay_${id}.png"))
            val stream = target.startWrite()
            try {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    throw IllegalStateException("Could not encode overlay")
                target.finishWrite(stream)
            } catch (error: Exception) {
                target.failWrite(stream)
                throw error
            }
        }
        // Drop overlay PNGs the project no longer references so a restored draft
        // can never re-attach an image of a deleted layer (and storage does not leak).
        val referenced = referencedOverlayIds(draft)
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith("overlay_") && file.name.endsWith(".png")) {
                val id = file.name.removePrefix("overlay_").removeSuffix(".png").toLongOrNull()
                if (id == null || id !in referenced) file.delete()
            }
        }
        val body = VideoDraftCodec.encode(draft).toByteArray(Charsets.UTF_8)
        require(body.size <= 2_000_000) { "Draft metadata too large" }
        val stream = metadata.startWrite()
        try {
            stream.write(body)
            metadata.finishWrite(stream)
        } catch (error: Exception) {
            metadata.failWrite(stream)
            throw error
        }
    }

    fun read(): VideoDraft? = try {
        if (!exists() || metadata.baseFile.length() > 2_000_000L) null
        else metadata.openRead().use { VideoDraftCodec.decode(it.readBytes().toString(Charsets.UTF_8)) }
    } catch (_: Exception) { null }

    fun overlay(id: Long): File? {
        if (id < 0) return null
        return File(dir, "overlay_${id}.png").takeIf { it.isFile && it.length() > 0 }
    }

    fun delete() {
        metadata.delete()
        dir.listFiles()?.forEach { if (it.name.startsWith("overlay_") && it.name.endsWith(".png")) it.delete() }
    }
}
