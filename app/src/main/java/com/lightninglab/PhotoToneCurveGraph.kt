package com.lightninglab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Interactive five-node RGB tone curve; horizontal node positions are fixed, vertical values are editable. */
@Composable
fun PhotoToneCurveGraph(
    values: List<Float>,
    modifier: Modifier = Modifier,
    onPointChange: (index: Int, value: Float) -> Unit,
    onGestureFinished: () -> Unit
) {
    val latestValues = rememberUpdatedState(values)
    val latestCallback = rememberUpdatedState(onPointChange)
    val latestFinished = rememberUpdatedState(onGestureFinished)
    var activePoint by remember { mutableStateOf(-1) }
    Canvas(
        modifier.fillMaxWidth().height(164.dp).pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { position ->
                    val index = (0 until PhotoToneCurves.POINTS).minByOrNull {
                        abs(position.x - it.toFloat() / (PhotoToneCurves.POINTS - 1) * size.width)
                    } ?: -1
                    val nodeX = index.toFloat() / (PhotoToneCurves.POINTS - 1) * size.width
                    activePoint = if (index >= 0 && abs(position.x - nodeX) <= 28.dp.toPx()) index else -1
                    if (activePoint >= 0) {
                        val y = (1f - position.y / size.height).coerceIn(0f, 1f)
                        latestCallback.value(activePoint, y)
                    }
                },
                onDrag = { change, _ ->
                    if (activePoint in 0 until PhotoToneCurves.POINTS) {
                        val y = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                        latestCallback.value(activePoint, y)
                        change.consume()
                    }
                },
                onDragEnd = { activePoint = -1; latestFinished.value() },
                onDragCancel = { activePoint = -1; latestFinished.value() }
            )
        }
    ) {
        drawRoundRect(Color(0xFF0D1928), cornerRadius = CornerRadius(14.dp.toPx()))
        for (step in 0..4) {
            val x = size.width * step / 4f
            val y = size.height * step / 4f
            drawLine(Color(0x334D789C), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            drawLine(Color(0x334D789C), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        val points = (0 until PhotoToneCurves.POINTS).map { index ->
            val value = latestValues.value.getOrElse(index) { index / 4f }.coerceIn(0f, 1f)
            Offset(size.width * index / 4f, size.height * (1f - value))
        }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, Color(0xFF75C9FF), style = Stroke(width = 3.dp.toPx()))
        points.forEachIndexed { index, point ->
            drawCircle(Color(0xFF0D1928), radius = if (activePoint == index) 10.dp.toPx() else 8.dp.toPx(), center = point)
            drawCircle(Color(0xFFB8E4FF), radius = if (activePoint == index) 7.dp.toPx() else 5.dp.toPx(), center = point)
        }
    }
}
