package com.lightninglab

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

object Exporter {
    private fun watermark(src: Bitmap): Bitmap {
        val b = src.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt(); alpha = 140
            textSize = b.height * 0.018f; textAlign = Paint.Align.RIGHT
            setShadowLayer(3f, 0f, 0f, 0xFF000000.toInt())
        }
        c.drawText("LightningLab", b.width - b.height * 0.02f, b.height - b.height * 0.02f, p)
        return b
    }

    fun savePng(ctx: Context, bmp: Bitmap, withWatermark: Boolean): Uri? {
        val b = if (withWatermark) watermark(bmp) else bmp
        val cv = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "LightningLab_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LightningLab")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val res = ctx.contentResolver
        val uri = res.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: return null
        res.openOutputStream(uri)?.use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        cv.clear(); cv.put(MediaStore.Images.Media.IS_PENDING, 0)
        res.update(uri, cv, null, null)
        return uri
    }
}
