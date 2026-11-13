package com.lightninglab

import java.io.File

/**
 * Recovers an interrupted TensorFlow Lite model replacement.
 *
 * Imports use a sibling `.previous` file as a rollback copy. If Android kills the
 * process between moving the old model aside and validating the new one, this
 * helper restores the last known-good model instead of silently discarding it.
 */
internal object ModelFileRecovery {
    /**
     * Load [target]. If it is invalid and [backup] exists, restore the backup and
     * retry. The caller owns interpreter lifecycle through [close] and [load].
     */
    fun loadOrRestore(
        target: File,
        backup: File,
        close: () -> Unit,
        load: () -> Boolean
    ): Boolean {
        if (!target.exists() && backup.isFile) moveOrCopy(backup, target)

        val currentWorks = safelyLoad(load)
        if (currentWorks) {
            if (backup.exists()) backup.delete()
            return true
        }

        closeSafely(close)
        if (!backup.isFile) return false

        if (target.exists() && !target.delete()) return false
        return try {
            moveOrCopy(backup, target)
            val restoredWorks = safelyLoad(load)
            if (restoredWorks && backup.exists()) backup.delete()
            restoredWorks
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Resolve a previous interrupted transaction before starting a new import.
     * Returns true when the active target is loaded and validated. The backup is
     * only removed after that success, so a failed restore keeps the last
     * known-good copy available for manual recovery.
     */
    fun prepareForImport(
        target: File,
        backup: File,
        close: () -> Unit,
        load: () -> Boolean
    ): Boolean {
        // Preserve a backup until the active target has been validated.
        val restored = loadOrRestore(target, backup, close, load)
        if (restored && target.exists() && backup.exists()) backup.delete()
        return restored
    }

    private fun moveOrCopy(from: File, to: File) {
        if (from.renameTo(to)) return
        from.copyTo(to, overwrite = true)
        if (!from.delete()) throw IllegalStateException("Could not remove recovery backup")
    }

    private fun safelyLoad(load: () -> Boolean): Boolean = try {
        load()
    } catch (_: Throwable) {
        false
    }

    private fun closeSafely(close: () -> Unit) {
        try { close() } catch (_: Throwable) { /* recovery must continue */ }
    }
}
