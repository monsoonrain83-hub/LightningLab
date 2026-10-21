package com.lightninglab

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File

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

    fun saveImage(ctx: Context, bmp: Bitmap, png: Boolean, quality: Int, withWatermark: Boolean, name: String): Uri? {
        val b = if (withWatermark) watermark(bmp) else bmp
        val ext = if (png) "png" else "jpg"
        val cv = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.$ext")
            put(MediaStore.Images.Media.MIME_TYPE, if (png) "image/png" else "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LightningLab")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val res = ctx.contentResolver
        val uri = res.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: return null
        res.openOutputStream(uri)?.use {
            b.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, if (png) 100 else quality, it)
        }
        cv.clear(); cv.put(MediaStore.Images.Media.IS_PENDING, 0)
        res.update(uri, cv, null, null)
        return uri
    }

    fun saveVideo(ctx: Context, file: File): Uri? {
        val cv = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "LightningLab_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/LightningLab")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val res = ctx.contentResolver
        val uri = res.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv) ?: return null
        res.openOutputStream(uri)?.use { os -> file.inputStream().use { it.copyTo(os) } }
        cv.clear(); cv.put(MediaStore.Video.Media.IS_PENDING, 0)
        res.update(uri, cv, null, null)
        return uri
    }
}
