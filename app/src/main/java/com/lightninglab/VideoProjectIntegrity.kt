package com.lightninglab

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Integrity manifest for newly saved video projects.
 * Older projects without an integrity index remain readable, but all asset paths
 * must still resolve inside the selected project directory.
 */
internal object VideoProjectIntegrity {
    const val INDEX_NAME = "integrity.json"
    const val VERSION = 1
    private val safeFile = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")

    fun safeProjectId(id: String): Boolean =
        id.length in 2..128 && id.startsWith("p") && safeFile.matches(id) &&
            !id.contains("..")

    fun assetNames(project: ProjectData): Set<String> = buildSet {
        add(project.video)
        project.segs.mapNotNullTo(this) { it.clip }
        project.music?.let { add(it) }
        project.voice?.let { add(it) }
        project.overlayClips.mapNotNullTo(this) { it.assetFile }
    }

    fun assetFile(dir: File, name: String): File? {
        if (name.isEmpty() || !safeFile.matches(name) || name == "." || name == ".." ||
            name.startsWith(".") || name.contains("..")) return null
        val root = dir.canonicalFile
        val file = File(root, name).canonicalFile
        return file.takeIf { it.parentFile == root }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { stream ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun writeIndex(dir: File, project: ProjectData) {
        val names = (assetNames(project) + "project.json").sorted()
        val files = JSONArray()
        for (name in names) {
            val file = assetFile(dir, name) ?: throw IllegalArgumentException("Unsafe asset: $name")
            if (!file.isFile || file.length() == 0L) throw IllegalStateException("Missing or empty asset: $name")
            files.put(JSONObject().put("name", name).put("size", file.length())
                .put("sha256", sha256(file)))
        }
        val index = JSONObject().put("version", VERSION).put("files", files)
        val temp = File(dir, "$INDEX_NAME.tmp")
        temp.writeText(index.toString(), Charsets.UTF_8)
        if (!temp.renameTo(File(dir, INDEX_NAME)))
            throw IllegalStateException("Could not finalize project integrity index")
    }

    /**
     * Never mutate editor state until this passes. A missing index is permitted
     * only for legacy projects; an existing malformed index is always rejected.
     */
    fun verify(dir: File, project: ProjectData): Boolean {
        return try {
        if (!dir.isDirectory) return false
        val names = (assetNames(project) + "project.json").sorted()
        for (name in names) {
            val file = assetFile(dir, name) ?: return false
            if (!file.isFile || file.length() <= 0L) return false
        }
        val indexFile = File(dir, INDEX_NAME)
        if (!indexFile.exists()) return true // Legacy format: strict path/existence checks
        if (!indexFile.isFile || indexFile.length() > 256_000L) return false
        val index = JSONObject(indexFile.readText(Charsets.UTF_8))
        if (index.optInt("version") != VERSION) return false
        val entries = index.getJSONArray("files")
        if (entries.length() != names.size) return false
        val checked = HashSet<String>()
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            val name = entry.getString("name")
            if (name !in names || !checked.add(name)) return false
            val file = assetFile(dir, name) ?: return false
            val expected = entry.getString("sha256")
            if (!Regex("[a-f0-9]{64}").matches(expected) ||
                file.length() != entry.getLong("size") || sha256(file) != expected) return false
        }
        checked.size == names.size
        } catch (_: Exception) { false }
    }
}
