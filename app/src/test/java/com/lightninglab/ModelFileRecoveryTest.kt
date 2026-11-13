package com.lightninglab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ModelFileRecoveryTest {
    private fun tempDir(): File = Files.createTempDirectory("ll-model-recovery-").toFile()

    @Test fun keepsValidCurrentModelAndRemovesStaleBackup() {
        val dir = tempDir()
        try {
            val target = File(dir, "model.tflite").apply { writeText("valid-new") }
            val backup = File(dir, "model.tflite.previous").apply { writeText("valid-old") }
            val loaded = ModelFileRecovery.loadOrRestore(target, backup, {}, { target.readText().startsWith("valid") })
            assertTrue(loaded)
            assertEquals("valid-new", target.readText())
            assertFalse(backup.exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun restoresBackupWhenCurrentModelIsInvalid() {
        val dir = tempDir()
        try {
            val target = File(dir, "model.tflite").apply { writeText("broken") }
            val backup = File(dir, "model.tflite.previous").apply { writeText("valid-old") }
            var attempts = 0
            val loaded = ModelFileRecovery.loadOrRestore(target, backup, {}, {
                attempts++
                target.readText().startsWith("valid")
            })
            assertTrue(loaded)
            assertEquals(2, attempts)
            assertEquals("valid-old", target.readText())
            assertFalse(backup.exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun restoresBackupWhenProcessWasInterruptedBeforeNewFileWasMovedIntoPlace() {
        val dir = tempDir()
        try {
            val target = File(dir, "model.tflite")
            val backup = File(dir, "model.tflite.previous").apply { writeText("valid-old") }
            val loaded = ModelFileRecovery.loadOrRestore(target, backup, {}, { target.readText() == "valid-old" })
            assertTrue(loaded)
            assertEquals("valid-old", target.readText())
            assertFalse(backup.exists())
        } finally { dir.deleteRecursively() }
    }

    /**
     * Regression (r10.70): prepareForImport used to delete the .previous backup even
     * when the active target could not be loaded or restored. Here the target cannot
     * be removed (non-empty directory), so the restore aborts and the last
     * known-good backup must survive for manual recovery.
     */
    @Test fun prepareForImportKeepsBackupWhenRestoreCannotProceed() {
        val dir = tempDir()
        try {
            val target = File(dir, "model.tflite").apply {
                mkdirs()
                File(this, "child.txt").writeText("occupied")
            }
            val backup = File(dir, "model.tflite.previous").apply { writeText("valid-old") }
            val prepared = ModelFileRecovery.prepareForImport(target, backup, {}, { false })
            assertFalse(prepared)
            assertTrue(backup.exists())
            assertEquals("valid-old", backup.readText())
        } finally { dir.deleteRecursively() }
    }

    /**
     * A backup that restores cleanly is consumed: it becomes the active target and
     * no stale .previous copy is left behind.
     */
    @Test fun prepareForImportConsumesBackupOnSuccessfulRestore() {
        val dir = tempDir()
        try {
            val target = File(dir, "model.tflite").apply { writeText("broken") }
            val backup = File(dir, "model.tflite.previous").apply { writeText("valid-old") }
            val prepared = ModelFileRecovery.prepareForImport(target, backup, {}, { target.readText() == "valid-old" })
            assertTrue(prepared)
            assertEquals("valid-old", target.readText())
            assertFalse(backup.exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun prepareForImportRemovesBackupOnlyAfterSuccessfulValidation() {
        val dir = tempDir()
        try {
            val target = File(dir, "model.tflite").apply { writeText("valid-new") }
            val backup = File(dir, "model.tflite.previous").apply { writeText("valid-old") }
            val prepared = ModelFileRecovery.prepareForImport(target, backup, {}, { true })
            assertTrue(prepared)
            assertFalse(backup.exists())
            assertEquals("valid-new", target.readText())
        } finally { dir.deleteRecursively() }
    }
}
