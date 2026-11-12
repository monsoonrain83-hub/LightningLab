package com.lightninglab

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Validate that every photo project asset is a plain file inside its project
 * directory. New projects record SHA-256; legacy projects remain loadable if
 * all referenced assets exist and have safe relative names.
 */
internal object PhotoProjectIntegrity {
    const val INDEX = "integrity.json"
    private val safeId = Regex("photo_[0-9]{1,19}")
    private val safeName = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")

    fun directory(parent: File, id: String): File? {
        if (!safeId.matches(id)) return null
        val root = parent.canonicalFile
        val child = File(root, id).canonicalFile
        return child.takeIf { it.parentFile == root }
    }

    fun asset(dir: File, name: String): File? {
        if (!safeName.matches(name) || name.startsWith(".") || name.contains("..")) return null
        val root = dir.canonicalFile
        return File(root, name).canonicalFile.takeIf { it.parentFile == root }
    }

    fun assets(project: PhotoProjectData): Set<String> =
        (listOf(project.source, "project.json") + project.images.map { it.file }).toSet()

    private fun digest(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                if (n > 0) md.update(buffer, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun write(dir: File, project: PhotoProjectData) {
        val rows = JSONArray()
        for (name in assets(project).sorted()) {
            val file = asset(dir, name) ?: error("Unsafe photo asset: $name")
            check(file.isFile && file.length() > 0L) { "Photo asset absent: $name" }
            rows.put(JSONObject().put("name", name).put("bytes", file.length()).put("sha256", digest(file)))
        }
        val temp = File(dir, "$INDEX.tmp")
        temp.writeText(JSONObject().put("version", 1).put("files", rows).toString(), Charsets.UTF_8)
        if (!temp.renameTo(File(dir, INDEX))) error("Photo project integrity index not published")
    }

    fun verify(dir: File, project: PhotoProjectData): Boolean {
        return try {
            if (!dir.isDirectory) return false
            val names = assets(project)
            for (name in names) {
                val f = asset(dir, name) ?: return false
                if (!f.isFile || f.length() <= 0) return false
            }
            val indexFile = File(dir, INDEX)
            if (!indexFile.exists()) return true // backward-compatible project
            if (!indexFile.isFile || indexFile.length() > 200_000) return false
            val doc = JSONObject(indexFile.readText(Charsets.UTF_8))
            if (doc.optInt("version") != 1) return false
            val rows = doc.getJSONArray("files")
            if (rows.length() != names.size) return false
            val seen = HashSet<String>()
            for (i in 0 until rows.length()) {
                val item = rows.getJSONObject(i)
                val name = item.getString("name")
                if (name !in names || !seen.add(name)) return false
                val f = asset(dir, name) ?: return false
                val sha = item.getString("sha256")
                if (!Regex("[a-f0-9]{64}").matches(sha) ||
                    f.length() != item.getLong("bytes") || digest(f) != sha) return false
            }
            seen.size == names.size
        } catch (_: Exception) { false }
    }
}
