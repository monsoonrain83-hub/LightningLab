package com.lightninglab

/** Strict limits for an untrusted/corrupt on-device auto-draft before decoding images. */
internal object VideoDraftRestoreRules {
    const val MAX_IMAGE_PIXELS = 16_000_000L
    const val MAX_TOTAL_IMAGE_PIXELS = 48_000_000L

    fun validate(draft: VideoDraft): Boolean {
        val p = draft.project
        if (draft.videoUri != p.video || p.videoDur < 0 || p.videoDur > 7L * 24L * 60L * 60L * 1000L) return false
        if (p.overlayClips.size > 128 || p.effectClips.size > 128 || p.segs.size > 256 || p.markers.size > 5000) return false
        if (p.overlayClips.any { it.id < 0 || it.id == Long.MAX_VALUE || it.endMs < it.startMs || it.startMs < 0 ||
                    it.keyframes.size > 512 || it.keyframes.any { k -> k.timeMs < 0 || !k.alpha.isFinite() } }) return false
        if (p.effectClips.any { it.id < 0 || it.id == Long.MAX_VALUE || it.startMs < 0 || it.endMs < it.startMs }) return false
        if (p.segs.any { it.startMs < 0 || it.endMs < it.startMs } || p.markers.any { it < 0 }) return false
        if (p.overlayClips.map { it.id }.toSet().size != p.overlayClips.size) return false
        if (p.effectClips.map { it.id }.toSet().size != p.effectClips.size) return false
        if (p.overlayClips.any { it.assetFile != null && !VideoDraftAssets.isSafe(it.id, it.assetFile) }) return false
        return listOf(p.speed, p.brightness, p.contrast, p.crop, p.volOrig, p.volMusic, p.volVoice)
            .all { it.isFinite() }
    }

    fun permitsBitmap(width: Int, height: Int, usedPixels: Long): Boolean {
        if (width <= 0 || height <= 0 || usedPixels < 0) return false
        val pixels = width.toLong() * height.toLong()
        return pixels <= MAX_IMAGE_PIXELS && pixels <= MAX_TOTAL_IMAGE_PIXELS - usedPixels
    }
}
