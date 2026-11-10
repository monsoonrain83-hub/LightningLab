package com.lightninglab

import android.graphics.Bitmap
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlaySettings

/** Static bitmap layer for one already time-sliced output interval. */
class StaticBitmapOverlay(
    private val bitmap: Bitmap,
    position: Int,
    alpha: Float = 1f
) : BitmapOverlay() {
    private val settings: OverlaySettings
    init {
        val backgroundY = when (position) { 0 -> 0.88f; 2 -> -0.88f; else -> 0f }
        val overlayY = when (position) { 0 -> 1f; 2 -> -1f; else -> 0f }
        settings = OverlaySettings.Builder()
            .setBackgroundFrameAnchor(0f, backgroundY)
            .setOverlayFrameAnchor(0f, overlayY)
            .setAlphaScale(alpha.coerceIn(0f, 1f))
            .build()
    }
    override fun getBitmap(presentationTimeUs: Long): Bitmap = bitmap
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = settings
}
