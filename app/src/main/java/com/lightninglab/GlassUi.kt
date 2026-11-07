package com.lightninglab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

/**
 * Lightweight, fully offline atmospheric backdrop.
 * This is a layered glassmorphism treatment, not a backdrop-blur implementation.
 * It intentionally uses Compose primitives so it remains compatible with the
 * project's Kotlin 1.9.24 / Compose compiler 1.5.14 toolchain and minSdk 29.
 */
@Composable
fun GlassAtmosphereBackground(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(
            Brush.verticalGradient(
                listOf(Color(0xFF070C16), Color(0xFF0B1423), Color(0xFF080B12))
            )
        )
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0x553B83C8), Color(0x183B83C8), Color.Transparent),
                center = Offset(size.width * 0.88f, size.height * 0.08f),
                radius = size.maxDimension * 0.72f
            ),
            radius = size.maxDimension * 0.72f,
            center = Offset(size.width * 0.88f, size.height * 0.08f)
        )
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0x332F6F9E), Color.Transparent),
                center = Offset(size.width * 0.04f, size.height * 0.58f),
                radius = size.maxDimension * 0.58f
            ),
            radius = size.maxDimension * 0.58f,
            center = Offset(size.width * 0.04f, size.height * 0.58f)
        )
    }
}

/** Reusable frosted-glass panel with a subtle top highlight and cool rim. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    androidx.compose.material3.Surface(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xCC1B2B40), Color(0xB3131F30), Color(0xCC101A29))
                ), shape
            ),
        shape = shape,
        color = Color.Transparent,
        shadowElevation = 14.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(Color(0x887EC8FF), Color(0x335D83AE), Color(0x6677A9D8))
            )
        )
    ) {
        androidx.compose.foundation.layout.Column(
            Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x20FFFFFF), Color.Transparent, Color(0x08000000))
                    )
                )
                .then(Modifier),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}
