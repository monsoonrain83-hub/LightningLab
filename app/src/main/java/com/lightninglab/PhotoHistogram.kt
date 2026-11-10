package com.lightninglab

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.max

data class PhotoHistogramData(
    val red: FloatArray,
    val green: FloatArray,
    val blue: FloatArray,
    val luma: FloatArray
)

object PhotoHistogram {
    fun compute(bitmap: Bitmap, bins: Int = 64): PhotoHistogramData {
        val step = max(1, max(bitmap.width, bitmap.height) / 256)
        val pixels = IntArray(((bitmap.width + step - 1) / step) * ((bitmap.height + step - 1) / step))
        var index = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                pixels[index++] = bitmap.getPixel(x, y)
                x += step
            }
            y += step
        }
        return fromPixels(pixels.copyOf(index), bins)
    }

    internal fun fromPixels(pixels: IntArray, bins: Int = 64): PhotoHistogramData {
        val count = bins.coerceIn(16, 256)
        val r = IntArray(count); val g = IntArray(count); val b = IntArray(count); val y = IntArray(count)
        pixels.forEach { pixel ->
            val red = (pixel shr 16) and 255
            val green = (pixel shr 8) and 255
            val blue = pixel and 255
            r[red * count / 256]++
            g[green * count / 256]++
            b[blue * count / 256]++
            val luma = (0.2126f * red + 0.7152f * green + 0.0722f * blue).toInt().coerceIn(0, 255)
            y[luma * count / 256]++
        }
        fun normalize(values: IntArray): FloatArray {
            val peak = values.maxOrNull()?.coerceAtLeast(1) ?: 1
            return FloatArray(count) { values[it].toFloat() / peak }
        }
        return PhotoHistogramData(normalize(r), normalize(g), normalize(b), normalize(y))
    }
}

@Composable
fun PhotoHistogramMini(data: PhotoHistogramData, modifier: Modifier = Modifier) {
    Canvas(modifier.size(164.dp, 72.dp).clip(RoundedCornerShape(12.dp))
        .background(Color(0xCC0A1420))
        .border(1.dp, Color(0x557EC8FF), RoundedCornerShape(12.dp))) {
        val grid = Color.White.copy(alpha = 0.14f)
        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(grid, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1.dp.toPx())
        }
        fun line(values: FloatArray, color: Color, width: Float) {
            if (values.isEmpty()) return
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = if (values.size == 1) 0f else size.width * index / (values.size - 1f)
                val y = size.height * (1f - value.coerceIn(0f, 1f))
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width))
        }
        line(data.luma, Color.White.copy(alpha = 0.72f), 1.2.dp.toPx())
        line(data.red, Color(0xFFFF6B6B).copy(alpha = 0.8f), 1.dp.toPx())
        line(data.green, Color(0xFF55D6A4).copy(alpha = 0.8f), 1.dp.toPx())
        line(data.blue, Color(0xFF6BA9FF).copy(alpha = 0.8f), 1.dp.toPx())
    }
}
