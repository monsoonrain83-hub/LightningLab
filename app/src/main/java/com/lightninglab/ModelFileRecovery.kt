package com.lightninglab

import java.io.File

/**
 * Transactional TFLite imports: a .previous recovery copy survives until the
 * candidate has passed the actual model loader's tensor-contract checks.
 */
internal object ModelFileRecovery {
    fun loadOrRestore(
        target: File, backup: File, close: () -> Unit, load: () -> Boolean
    ): Boolean {
        if (safelyLoad(load)) {
            backup.takeIf { it.isFile }?.delete()
            return true
        }
        closeSafely(close)
        if (!backup.isFile) return false
        return try {
            // Never MOVE the only recovery copy before the restored model loads.
            if (target.exists() && !target.delete()) return false
            backup.copyTo(target, overwrite = true)
            val restored = safelyLoad(load)
            if (restored) backup.delete()
            restored
        } catch (_: Throwable) { false }
    }

    fun prepareForImport(
        target: File, backup: File, close: () -> Unit, load: () -> Boolean
    ): Boolean = loadOrRestore(target, backup, close, load)

    /**
     * Staging must contain a complete, non-empty candidate in the same directory.
     * A corrupt candidate cannot destroy the previous model, even if restore fails.
     */
    fun installCandidate(
        target: File, backup: File, staging: File, close: () -> Unit, load: () -> Boolean
    ): Boolean {
        if (!staging.isFile || staging.length() <= 0L || staging.parentFile != target.parentFile ||
            backup.parentFile != target.parentFile || staging == target || staging == backup) return false
        val hadOld = target.isFile
        // A prior interrupted transaction must be resolved before overwriting .previous.
        if (backup.isFile && !loadOrRestore(target, backup, close, load)) return false
        val existing = target.isFile
        closeSafely(close)
        return try {
            if (existing) {
                if (backup.exists() && !backup.delete()) return false
                // Preserve the source file; do not rename the only valid copy.
                target.copyTo(backup, overwrite = true)
                if (backup.length() != target.length()) error("Incomplete model backup")
            }
            if (target.exists() && !target.delete()) error("Cannot replace existing model")
            if (!staging.renameTo(target)) error("Cannot publish staged model")
            if (!safelyLoad(load)) error("New model failed interpreter validation")
            backup.delete() // only after the new active model loaded successfully
            true
        } catch (_: Throwable) {
            closeSafely(close)
            if (backup.isFile) {
                try {
                    if (target.exists()) target.delete()
                    backup.copyTo(target, overwrite = true)
                    if (safelyLoad(load)) backup.delete()
                } catch (_: Throwable) { /* leave last recovery backup intact */ }
            } else if (!hadOld && target.exists()) {
                target.delete()
            }
            false
        }
    }

    private fun safelyLoad(load: () -> Boolean): Boolean =
        try { load() } catch (_: Throwable) { false }

    private fun closeSafely(close: () -> Unit) {
        try { close() } catch (_: Throwable) { }
    }
}
