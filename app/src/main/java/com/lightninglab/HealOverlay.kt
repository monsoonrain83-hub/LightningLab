package com.lightninglab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.hypot

/** Paint-over layer: tap or drag to place removal marks (normalised coordinates; r relative to image width). */
@Composable
fun HealOverlay(marks: List<Heal>, brush: Float, onAdd: (Heal) -> Unit, modifier: Modifier = Modifier) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var last by remember { mutableStateOf<Offset?>(null) }
    Box(modifier.fillMaxSize().onSizeChanged { size = it }
        .pointerInput(brush) {
            detectTapGestures { o ->
                if (size.width > 0) onAdd(Heal(o.x / size.width, o.y / size.height, brush))
            }
        }
        .pointerInput(brush) {
            detectDragGestures(onDragStart = { last = null }) { change, _ ->
                val o = change.position
                val l = last
                if (size.width > 0 && (l == null || hypot(o.x - l.x, o.y - l.y) >= brush * size.width * 0.6f)) {
                    last = o
                    onAdd(Heal(o.x / size.width, o.y / size.height, brush))
                }
            }
        }) {
        Canvas(Modifier.fillMaxSize()) {
            marks.forEach {
                drawCircle(Color(0x66FF4081), radius = it.r * size.width,
                    center = Offset(it.x * size.width, it.y * size.height))
            }
        }
    }
}
