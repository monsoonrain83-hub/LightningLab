package com.lightninglab

import android.content.Context
import android.graphics.Bitmap
import androidx.core.util.AtomicFile
import java.io.File
import java.security.SecureRandom

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
    private val previous = AtomicFile(File(dir, "draft.previous.json"))
    private val nonce = SecureRandom()

    fun exists(): Boolean = read() != null

    private fun readSnapshot(file: AtomicFile): VideoDraft? = try {
        if (!file.baseFile.isFile || file.baseFile.length() !in 1L..2_000_000L) null
        else file.openRead().use { VideoDraftCodec.decode(it.readBytes().toString(Charsets.UTF_8)) }
    } catch (_: Exception) { null }

    private fun assetExists(name: String): Boolean =
        File(dir, name).let { it.isFile && it.length() > 0L }

    private fun writeAtomic(target: AtomicFile, body: ByteArray) {
        val stream = target.startWrite()
        try {
            stream.write(body)
            target.finishWrite(stream)
        } catch (error: Exception) {
            target.failWrite(stream)
            throw error
        }
    }

    private fun newToken(ids: Set<Long>): Long {
        repeat(16) {
            val token = nonce.nextLong() and Long.MAX_VALUE
            if (ids.none { File(dir, VideoDraftAssets.revision(it, token)).exists() }) return token
        }
        error("Could not allocate a unique overlay revision")
    }

    @Synchronized
    fun save(draft: VideoDraft, bitmaps: Map<Long, Bitmap>) {
        if (!dir.exists() && !dir.mkdirs()) error("Could not create draft folder")
        val token = newToken(bitmaps.keys)
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
        require(VideoDraftRecoveryPolicy.usable(published, ::assetExists)) {
            "Invalid video draft or missing overlay assets"
        }
        val body = VideoDraftCodec.encode(published).toByteArray(Charsets.UTF_8)
        require(body.size <= 2_000_000) { "Draft metadata too large" }

        // Preserve the last recoverable CURRENT manifest before publishing
        // its replacement. A failed backup write must NOT modify the current
        // draft, even when PNGs have already been staged on disk.
        val oldCurrent = readSnapshot(metadata).takeIf {
            VideoDraftRecoveryPolicy.usable(it, ::assetExists)
        }
        if (oldCurrent != null) {
            val oldBody = VideoDraftCodec.encode(oldCurrent).toByteArray(Charsets.UTF_8)
            writeAtomic(previous, oldBody)
        }
        // Current or independent previous manifest survives process death.
        writeAtomic(metadata, body)

        // Keep all PNGs needed by EITHER generation.
        val protected = VideoDraftRecoveryPolicy.protectedAssets(
            published, readSnapshot(previous))
        val obsolete = VideoDraftAssets.obsolete(
            dir.listFiles()?.map { it.name }.orEmpty(), protected)
        for (name in obsolete) File(dir, name).takeIf { it.isFile }?.delete()
    }

    @Synchronized
    fun read(): VideoDraft? = VideoDraftRecoveryPolicy.select(
        readSnapshot(metadata), readSnapshot(previous), ::assetExists)

    @Synchronized
    fun overlay(id: Long, assetFile: String? = null): File? {
        val name = assetFile ?: VideoDraftAssets.legacy(id)
        if (!VideoDraftAssets.isSafe(id, name)) return null
        return File(dir, name).takeIf { it.isFile && it.length() > 0 }
    }

    @Synchronized
    fun delete() {
        metadata.delete()
        previous.delete()
        dir.listFiles()?.forEach {
            if (it.isFile && it.name.startsWith("overlay_") && it.name.endsWith(".png")) it.delete()
        }
    }
}
