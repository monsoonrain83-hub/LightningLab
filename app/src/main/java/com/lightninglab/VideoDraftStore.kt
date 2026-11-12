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
