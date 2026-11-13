package com.lightninglab

import android.content.Context
import android.graphics.Bitmap
import androidx.core.util.AtomicFile
import java.io.File

/** Fail-closed overlay filenames; legacy r10.68 filenames stay readable. */
internal object VideoDraftAssets {
    fun legacy(id: Long): String = "overlay_${id}.png"
    fun revision(id: Long, token: Long): String {
        require(id >= 0 && token >= 0)
        return "overlay_${id}_${token}.png"
    }
    fun isSafe(id: Long, name: String): Boolean =
        id >= 0 && Regex("overlay_${id}(?:_[0-9]+)?\\.png").matches(name)

    fun obsolete(files: Collection<String>, referenced: Set<String>): List<String> =
        files.filter { it.startsWith("overlay_") && it.endsWith(".png") && it !in referenced }
}

/**
 * Crash-safe metadata publication with versioned PNG overlay assets.
 * Existing draft images are never overwritten before the new manifest commits.
 * Media URI targets are retained, not transcoded or duplicated.
 */
internal class VideoDraftStore(context: Context) {
    private val dir = File(context.filesDir, "video-autodraft")
    private val metadata = AtomicFile(File(dir, "draft.json"))

    fun exists(): Boolean = metadata.baseFile.isFile

    @Synchronized
    fun save(draft: VideoDraft, bitmaps: Map<Long, Bitmap>) {
        if (!dir.exists() && !dir.mkdirs()) error("Could not create draft folder")
        val token = System.nanoTime() and Long.MAX_VALUE
        val filenames = HashMap<Long, String>()
        for ((id, bitmap) in bitmaps) {
            require(id >= 0 && !bitmap.isRecycled)
            val name = VideoDraftAssets.revision(id, token)
            val target = AtomicFile(File(dir, name))
            val stream = target.startWrite()
            try {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    error("Could not encode overlay")
                target.finishWrite(stream)
                filenames[id] = name
            } catch (error: Exception) {
                target.failWrite(stream)
                throw error
            }
        }

        val revisedProject = draft.project.copy(overlayClips = draft.project.overlayClips.map { clip ->
            val requested = clip.assetFile
            if (requested == null) clip
            else {
                val name = filenames[clip.id] ?: requested
                require(VideoDraftAssets.isSafe(clip.id, name) && File(dir, name).isFile) {
                    "Missing draft image for layer ${clip.id}"
                }
                clip.copy(assetFile = name)
            }
        })
        val published = draft.copy(project = revisedProject)
        val body = VideoDraftCodec.encode(published).toByteArray(Charsets.UTF_8)
        require(body.size <= 2_000_000) { "Draft metadata too large" }

        // The old draft and its PNGs survive any failure before finishWrite.
        val stream = metadata.startWrite()
        try {
            stream.write(body)
            metadata.finishWrite(stream)
        } catch (error: Exception) {
            metadata.failWrite(stream)
            throw error
        }

        // Prune AFTER the new manifest is fully committed. An interruption here
        // leaves harmless orphaned PNGs rather than a broken recovery snapshot.
        val referenced = revisedProject.overlayClips.mapNotNull { it.assetFile }.toSet()
        val obsolete = VideoDraftAssets.obsolete(dir.listFiles()?.map { it.name }.orEmpty(), referenced)
        for (name in obsolete) File(dir, name).takeIf { it.isFile }?.delete()
    }

    @Synchronized
    fun read(): VideoDraft? = try {
        if (!exists() || metadata.baseFile.length() > 2_000_000L) null
        else metadata.openRead().use { VideoDraftCodec.decode(it.readBytes().toString(Charsets.UTF_8)) }
    } catch (_: Exception) { null }

    @Synchronized
    fun overlay(id: Long, assetFile: String? = null): File? {
        val name = assetFile ?: VideoDraftAssets.legacy(id)
        if (!VideoDraftAssets.isSafe(id, name)) return null
        return File(dir, name).takeIf { it.isFile && it.length() > 0 }
    }

    @Synchronized
    fun delete() {
        metadata.delete()
        dir.listFiles()?.forEach {
            if (it.isFile && it.name.startsWith("overlay_") && it.name.endsWith(".png")) it.delete()
        }
    }
}
