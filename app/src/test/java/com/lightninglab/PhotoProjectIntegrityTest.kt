package com.lightninglab

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PhotoProjectIntegrityTest {
    private fun project(source: String = "source.png", image: String = "layer_3.png") =
        PhotoProjectData("Photo", source, PhotoParams(), emptyList(), emptyList(),
            listOf(PhotoImageData(3L, "overlay", image, .5f, .5f, .5f, 1f, true, 1)),
            emptyList(), false)

    private inline fun withFiles(block: (File, PhotoProjectData) -> Unit) {
        val dir = Files.createTempDirectory("lightning-photo").toFile()
        val p = project()
        try {
            File(dir, "source.png").writeBytes(byteArrayOf(1, 2, 3))
            File(dir, "layer_3.png").writeBytes(byteArrayOf(4, 5, 6))
            File(dir, "project.json").writeText(PhotoProjectCodec.encode(p))
            block(dir, p)
        } finally { dir.deleteRecursively() }
    }

    @Test fun versionedIndexDetectsByteTampering() = withFiles { dir, p ->
        PhotoProjectIntegrity.write(dir, p)
        assertTrue(PhotoProjectIntegrity.verify(dir, p))
        File(dir, "layer_3.png").writeBytes(byteArrayOf(4, 5, 7))
        assertFalse(PhotoProjectIntegrity.verify(dir, p))
    }

    @Test fun legacyProjectLoadsOnlyWhenAssetsExist() = withFiles { dir, p ->
        assertTrue(PhotoProjectIntegrity.verify(dir, p))
        File(dir, "layer_3.png").delete()
        assertFalse(PhotoProjectIntegrity.verify(dir, p))
    }

    @Test fun traversalPathsAreRejected() = withFiles { dir, _ ->
        assertNull(PhotoProjectIntegrity.asset(dir, "../outside.png"))
        assertNull(PhotoProjectIntegrity.asset(dir, "/tmp/photo.png"))
        assertFalse(PhotoProjectIntegrity.verify(dir, project("../source.png")))
    }

    @Test fun malformedIndexNeverDisablesVerification() = withFiles { dir, p ->
        PhotoProjectIntegrity.write(dir, p)
        File(dir, PhotoProjectIntegrity.INDEX).writeText("{broken")
        assertFalse(PhotoProjectIntegrity.verify(dir, p))
    }

    @Test fun unsafeProjectIdentifiersCannotResolveOutsideRoot() = withFiles { dir, _ ->
        assertEquals(File(dir, "photo_123").canonicalFile, PhotoProjectIntegrity.directory(dir, "photo_123"))
        assertNull(PhotoProjectIntegrity.directory(dir, "../photo_123"))
        assertNull(PhotoProjectIntegrity.directory(dir, "photo_../outside"))
        assertNull(PhotoProjectIntegrity.directory(dir, "notphoto_123"))
    }
}
