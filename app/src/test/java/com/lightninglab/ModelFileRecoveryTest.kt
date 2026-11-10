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
}
