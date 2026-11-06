package com.lightninglab

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Subject cut-out with ML Kit Subject Segmentation (Google, on-device). NOT bundled: the model is downloaded by Google
 * Play services the first time it is used (needs Play services and internet once; afterwards it works offline).
 * The result is an ARGB bitmap of the same size with a transparent background.
 */
object Cutout {
    suspend fun remove(src: Bitmap): Bitmap = suspendCancellableCoroutine { cont ->
        val seg = SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build())
        seg.process(InputImage.fromBitmap(src, 0))
            .addOnSuccessListener { r ->
                val fg = r.foregroundBitmap
                if (fg != null) cont.resume(fg) else cont.resumeWithException(IllegalStateException("no subject found"))
                seg.close()
            }
            .addOnFailureListener { e -> cont.resumeWithException(e); seg.close() }
        cont.invokeOnCancellation { seg.close() }
    }
}
