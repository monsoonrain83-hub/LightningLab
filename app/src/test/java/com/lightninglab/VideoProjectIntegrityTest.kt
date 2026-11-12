package com.lightninglab

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class VideoProjectIntegrityTest {
    private fun project(video: String = "main.mp4") = ProjectData(
        name = "Test", video = video, videoDur = 1000L,
        segs = listOf(SegData(0L, 1000L, null)),
        music = "music.audio", voice = null, mute = false,
        filter = 0, speed = 1f, brightness = 0f, contrast = 0f,
        rotate = 0, flipH = false, flipV = false, crop = 1f,
        text = "", textColor = -1, textSize = 1, textPos = 1, textBg = false,
        sticker = null, stickerSize = 1, stickerPos = 1
    )

    private inline fun inTemp(action: (File) -> Unit) {
        val dir = Files.createTempDirectory("lightning-project-integrity").toFile()
        try { action(dir) } finally { dir.deleteRecursively() }
    }

    private fun createFiles(dir: File, p: ProjectData) {
        File(dir, "main.mp4").writeText("video-contents")
        File(dir, "music.audio").writeText("music-contents")
        File(dir, "project.json").writeText(ProjectCodec.encode(p))
    }

    @Test fun validProjectPassesAndDetectsCorruption() = inTemp { dir ->
        val p = project()
        createFiles(dir, p)
        VideoProjectIntegrity.writeIndex(dir, p)
        assertTrue(VideoProjectIntegrity.verify(dir, p))
        File(dir, "music.audio").writeText("tampered but same length!")
        assertFalse(VideoProjectIntegrity.verify(dir, p))
    }

    @Test fun missingMediaFailsEvenForLegacyProjects() = inTemp { dir ->
        val p = project()
        File(dir, "project.json").writeText(ProjectCodec.encode(p))
        assertFalse(VideoProjectIntegrity.verify(dir, p))
        File(dir, "main.mp4").writeText("video")
        File(dir, "music.audio").writeText("music")
        assertTrue(VideoProjectIntegrity.verify(dir, p))
    }

    @Test fun pathTraversalIsRejectedForAllVersions() = inTemp { dir ->
        val p = project("../private.mp4")
        File(dir, "project.json").writeText(ProjectCodec.encode(p))
        File(dir, "music.audio").writeText("music")
        assertFalse(VideoProjectIntegrity.verify(dir, p))
        assertNull(VideoProjectIntegrity.assetFile(dir, "../private.mp4"))
        assertNull(VideoProjectIntegrity.assetFile(dir, "/tmp/private.mp4"))
        assertNull(VideoProjectIntegrity.assetFile(dir, "sub/folder.mp4"))
    }

    @Test fun rejectTamperedIntegrityManifest() = inTemp { dir ->
        val p = project()
        createFiles(dir, p)
        VideoProjectIntegrity.writeIndex(dir, p)
        File(dir, VideoProjectIntegrity.INDEX_NAME).writeText("{broken")
        assertFalse(VideoProjectIntegrity.verify(dir, p))
    }

    @Test fun strictProjectIdentifiersProtectDeletion() {
        assertTrue(VideoProjectIntegrity.safeProjectId("p1734291000000"))
        assertFalse(VideoProjectIntegrity.safeProjectId("../"))
        assertFalse(VideoProjectIntegrity.safeProjectId("p../../"))
        assertFalse(VideoProjectIntegrity.safeProjectId("other"))
    }
}
