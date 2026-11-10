package com.lightninglab

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.BlurMaskFilter

/** Brush mask strokes use normalized image coordinates; they remain editable until export. */
data class PhotoMaskStroke(val points: List<Pair<Float, Float>>, val radius: Float = 0.025f, val softness: Float = 0.35f)

/** Non-destructive, independently editable photo overlay layer. Coordinates are normalized to canvas size. */
data class PhotoTextLayer(
    val id: Long,
    val name: String,
    val text: String,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val size: Float = 0.075f,
    val color: Int = 0xFFFFFFFF.toInt(),
    val opacity: Float = 1f,
    val visible: Boolean = true,
    val bold: Boolean = true,
    val zIndex: Int = 0,
    val rotationDegrees: Float = 0f
)

data class PhotoImageLayer(
    val id: Long,
    val name: String,
    val bitmap: Bitmap,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val width: Float = 0.35f,
    val opacity: Float = 1f,
    val visible: Boolean = true,
    val zIndex: Int = 0,
    val rotationDegrees: Float = 0f
)

data class PhotoShapeLayer(
    val id: Long,
    val name: String,
    val shape: Shape = Shape.RECTANGLE,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val width: Float = 0.3f,
    val height: Float = 0.18f,
    val color: Int = 0xFFFFD43B.toInt(),
    val opacity: Float = 0.65f,
    val strokeWidth: Float = 0f,
    val visible: Boolean = true,
    val zIndex: Int = 0,
    val rotationDegrees: Float = 0f
) { enum class Shape { RECTANGLE, ELLIPSE } }


private sealed interface PhotoDrawEntry {
    val z: Int
    data class ShapeEntry(val value: PhotoShapeLayer) : PhotoDrawEntry { override val z get() = value.zIndex }
    data class ImageEntry(val value: PhotoImageLayer) : PhotoDrawEntry { override val z get() = value.zIndex }
    data class TextEntry(val value: PhotoTextLayer) : PhotoDrawEntry { override val z get() = value.zIndex }
}

object PhotoLayers {
    /** Returns a new bitmap; input remains owned by caller. */
    fun render(base: Bitmap, layers: List<PhotoTextLayer>, shapes: List<PhotoShapeLayer> = emptyList(), images: List<PhotoImageLayer> = emptyList(), masks: List<PhotoMaskStroke> = emptyList(), invertMask: Boolean = false): Bitmap {
        if (layers.none { it.visible && it.text.isNotBlank() } && shapes.none { it.visible } && images.none { it.visible && !it.bitmap.isRecycled } && masks.isEmpty()) return base
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        if (masks.isNotEmpty()) {
            // Build an alpha mask separately so feathering and inversion are non-destructive.
            val mask = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
            val maskCanvas = Canvas(mask)
            val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
            masks.forEach { stroke ->
                if (stroke.points.isNotEmpty()) {
                    val path = Path()
                    stroke.points.forEachIndexed { index, point ->
                        val x = point.first.coerceIn(0f, 1f) * base.width
                        val y = point.second.coerceIn(0f, 1f) * base.height
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    val radius = (stroke.radius.coerceIn(0.003f, 0.18f) * base.width).coerceAtLeast(2f)
                    white.strokeWidth = radius * 2f
                    val blur = (stroke.softness.coerceIn(0f, 1f) * radius * 0.9f)
                    white.maskFilter = if (blur > 0.5f) BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) else null
                    if (stroke.points.size == 1) {
                        white.style = Paint.Style.FILL
                        maskCanvas.drawCircle(stroke.points[0].first.coerceIn(0f, 1f) * base.width, stroke.points[0].second.coerceIn(0f, 1f) * base.height, radius, white)
                        white.style = Paint.Style.STROKE
                    } else maskCanvas.drawPath(path, white)
                }
            }
            if (invertMask) {
                val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
                // Turn the painted region into its complement without touching the source bitmap.
                val inverted = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
                val invCanvas = Canvas(inverted)
                invCanvas.drawColor(android.graphics.Color.WHITE)
                invCanvas.drawBitmap(mask, 0f, 0f, clear)
                mask.recycle()
                canvas.drawBitmap(inverted, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) })
                inverted.recycle()
            } else {
                canvas.drawBitmap(mask, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) })
                mask.recycle()
            }
        }
        // All overlay types share one global z-order. This allows a sticker to sit
        // between a shape and a text layer instead of being forced into a fixed category.
        val ordered = buildList<PhotoDrawEntry> {
            shapes.filter { it.visible }.forEach { add(PhotoDrawEntry.ShapeEntry(it)) }
            images.filter { it.visible && !it.bitmap.isRecycled }.forEach { add(PhotoDrawEntry.ImageEntry(it)) }
            layers.filter { it.visible && it.text.isNotBlank() }.forEach { add(PhotoDrawEntry.TextEntry(it)) }
        }.sortedBy { it.z }
        ordered.forEach { entry -> when (entry) {
            is PhotoDrawEntry.ShapeEntry -> {
                val layer = entry.value
                val w = base.width * layer.width.coerceIn(0.02f, 0.95f)
                val h = base.height * layer.height.coerceIn(0.02f, 0.95f)
                val cx = layer.x.coerceIn(0f, 1f) * base.width
                val cy = layer.y.coerceIn(0f, 1f) * base.height
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = layer.color; alpha = (255 * layer.opacity.coerceIn(0f, 1f)).toInt()
                    style = if (layer.strokeWidth > 0f) Paint.Style.STROKE else Paint.Style.FILL
                    strokeWidth = (base.width * layer.strokeWidth.coerceIn(0f, 0.04f)).coerceAtLeast(1f)
                }
                val rect = RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
                canvas.save()
                canvas.rotate(layer.rotationDegrees, cx, cy)
                when (layer.shape) {
                    PhotoShapeLayer.Shape.RECTANGLE -> canvas.drawRect(rect, paint)
                    PhotoShapeLayer.Shape.ELLIPSE -> canvas.drawOval(rect, paint)
                }
                canvas.restore()
            }
            is PhotoDrawEntry.ImageEntry -> {
                val layer = entry.value; val image = layer.bitmap
                val targetW = base.width * layer.width.coerceIn(0.03f, 0.95f)
                val targetH = targetW * image.height.toFloat() / image.width.coerceAtLeast(1)
                val cx = layer.x.coerceIn(0f, 1f) * base.width; val cy = layer.y.coerceIn(0f, 1f) * base.height
                val dst = RectF(cx - targetW / 2f, cy - targetH / 2f, cx + targetW / 2f, cy + targetH / 2f)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = (255 * layer.opacity.coerceIn(0f, 1f)).toInt() }
                canvas.save()
                canvas.rotate(layer.rotationDegrees, cx, cy)
                canvas.drawBitmap(image, null, dst, paint)
                canvas.restore()
            }
            is PhotoDrawEntry.TextEntry -> {
                val layer = entry.value
                val sizePx = (base.width * layer.size).coerceIn(12f, base.width * 0.3f)
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                    color = layer.color; alpha = (255 * layer.opacity.coerceIn(0f, 1f)).toInt(); textSize = sizePx
                    typeface = if (layer.bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
                    setShadowLayer(sizePx * 0.12f, 0f, sizePx * 0.06f, 0x99000000.toInt())
                }
                val layout = StaticLayout.Builder.obtain(layer.text, 0, layer.text.length, paint, base.width)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
                    .setIncludePad(false).setLineSpacing(sizePx * 0.12f, 1f).build()
                val x = layer.x.coerceIn(0f, 1f) * base.width; val y = layer.y.coerceIn(0f, 1f) * base.height
                canvas.save()
                canvas.rotate(layer.rotationDegrees, x, y)
                canvas.translate(x - base.width / 2f, y - layout.height / 2f)
                layout.draw(canvas)
                canvas.restore()
            }
        } }
        return out
    }
}
