package com.lightninglab

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.max

enum class Screen { HOME, RESULTS, ABOUT, PHOTO, VIDEO }
/** order = 0: not selected; 1 = base background; 2.. = overlays in the order the user tapped. */
data class Item(val event: LightningEvent, val thumb: Bitmap, val order: Int = 0)
data class Seg(val startMs: Long, val endMs: Long)

class MainVM(private val app: Application) : AndroidViewModel(app) {
    var screen by mutableStateOf(Screen.HOME)
    var uri by mutableStateOf<Uri?>(null)
    var busy by mutableStateOf(false)
    var progress by mutableStateOf(0f)
    var stage by mutableStateOf(R.string.stage_scan)
    var sensitivity by mutableStateOf(0.6f)
    var composite by mutableStateOf<Bitmap?>(null)
    var method by mutableStateOf(Composer.Method.ADVANCED)
    var intensity by mutableStateOf(1f)
    var infoRes by mutableStateOf<Int?>(null)
    var infoArgs by mutableStateOf<List<Any>>(emptyList())
    val items = mutableStateListOf<Item>()
    private var job: Job? = null

    // photo editor
    var photoSrc by mutableStateOf<Bitmap?>(null)
    var photoPrev by mutableStateOf<Bitmap?>(null)
    var params by mutableStateOf(PhotoParams())
    var watermark by mutableStateOf(true)
    var showOriginal by mutableStateOf(false)
    var align by mutableStateOf(true)
    var canUndo by mutableStateOf(false)
    var canRedo by mutableStateOf(false)
    private val hist = ArrayList<PhotoParams>(); private var hi = -1
    private fun histFlags() { canUndo = hi > 0; canRedo = hi < hist.size - 1 }
    private fun histReset() { hist.clear(); hist += params; hi = 0; histFlags() }
    /** Call after a finished edit gesture (slider released, button tapped). */
    fun commit() {
        if (hist.getOrNull(hi) == params) return
        while (hist.size > hi + 1) hist.removeAt(hist.size - 1)
        hist += params
        if (hist.size > 60) hist.removeAt(0)
        hi = hist.size - 1; histFlags()
    }
    fun undo() { if (hi > 0) { hi--; params = hist[hi]; histFlags() } }
    fun redo() { if (hi < hist.size - 1) { hi++; params = hist[hi]; histFlags() } }

    // video editor
    var videoUri by mutableStateOf<Uri?>(null)
    var videoDur by mutableStateOf(0L)
    val segments = mutableStateListOf<Seg>()
    var music by mutableStateOf<Uri?>(null)
    var muteOriginal by mutableStateOf(false)

    private fun info(res: Int, vararg a: Any) { infoRes = res; infoArgs = a.toList() }

    // ---------------- detection ----------------
    fun startDetect(u: Uri) {
        job?.cancel(); uri = u; items.clear(); composite = null; infoRes = null
        busy = true; progress = 0f; stage = R.string.stage_scan
        job = viewModelScope.launch {
            try {
                val det = Detector(app)
                withContext(Dispatchers.Default) {
                    val evs = det.detect(u, sensitivity, onProgress = { progress = it },
                        onStage = { stage = it }, cancelled = { !isActive })
                    stage = R.string.stage_thumbs
                    for (e in evs.sortedByDescending { it.score }.take(60).sortedBy { it.startMs }) {
                        det.frame(u, e.bestMs, 480)?.let { items += Item(e, it) }
                    }
                }
                if (items.isEmpty()) info(R.string.no_events) else screen = Screen.RESULTS
            } catch (c: CancellationException) { throw c
            } catch (e: Exception) { info(R.string.error_generic, e.message ?: e.javaClass.simpleName)
            } finally { busy = false }
        }
    }

    fun cancel() { job?.cancel(); busy = false }

    fun toggle(i: Int) {
        val it = items[i]
        if (it.order > 0) {
            val o = it.order
            for (k in items.indices) if (items[k].order > o) items[k] = items[k].copy(order = items[k].order - 1)
            items[i] = items[i].copy(order = 0)
        } else {
            items[i] = it.copy(order = (items.maxOfOrNull { x -> x.order } ?: 0) + 1)
        }
    }
    fun clearSel() { for (i in items.indices) items[i] = items[i].copy(order = 0) }
    fun selectAll() { for (i in items.indices) items[i] = items[i].copy(order = i + 1) }
    fun autoSelect() {
        clearSel()
        items.indices.sortedByDescending { items[it].event.score }.take(6).forEachIndexed { r, i ->
            items[i] = items[i].copy(order = r + 1)
        }
    }

    private fun selected() = items.filter { it.order > 0 }.sortedBy { it.order }

    fun compose() {
        val sel = selected(); val u = uri ?: return
        if (sel.isEmpty() || busy) return
        busy = true; progress = 0f; stage = R.string.stage_compose; infoRes = null
        job = viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.Default) {
                    val det = Detector(app)
                    val base = det.frame(u, sel[0].event.bestMs, 2048) ?: return@withContext null
                    val others = sel.drop(1).asSequence().map { det.frame(u, it.event.bestMs, 2048) }
                    Composer.compose(base, others, method, intensity, sensitivity, align)
                }
                if (r != null) {
                    composite = r.bitmap
                    info(R.string.composed_info, r.used, r.skipped, String.format(Locale.US, "%.1f", r.clippedPct), r.shifted, r.uncertain)
                }
            } catch (c: CancellationException) { throw c
            } catch (e: Throwable) { info(R.string.error_generic, e.message ?: e.javaClass.simpleName)
            } finally { busy = false }
        }
    }

    /** Save every selected frame, full resolution, as its own PNG (no watermark) before any stacking. */
    fun saveFrames() {
        val sel = selected(); val u = uri ?: return
        if (sel.isEmpty() || busy) return
        busy = true; progress = 0f; stage = R.string.stage_save; infoRes = null
        job = viewModelScope.launch {
            try {
                var ok = 0
                withContext(Dispatchers.IO) {
                    val det = Detector(app)
                    sel.forEachIndexed { k, it ->
                        det.frame(u, it.event.bestMs, 100000)?.let { b ->
                            Exporter.saveImage(app, b, true, 100, false, "LightningLab_frame_${it.order}_${it.event.bestMs}ms")
                            b.recycle(); ok++
                        }
                        progress = (k + 1f) / sel.size
                    }
                }
                info(R.string.frames_saved, ok)
            } catch (c: CancellationException) { throw c
            } catch (e: Throwable) { info(R.string.error_generic, e.message ?: e.javaClass.simpleName)
            } finally { busy = false }
        }
    }

    // ---------------- photo editor ----------------
    fun setPhoto(b: Bitmap) {
        photoSrc = b; params = PhotoParams(); histReset(); showOriginal = false
        val s = 1280f / max(b.width, b.height)
        photoPrev = if (s < 1f) Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true) else b
        screen = Screen.PHOTO
    }

    fun loadPhoto(u: Uri) {
        viewModelScope.launch {
            try {
                val b = withContext(Dispatchers.IO) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(app.contentResolver, u)) { dec, inf, _ ->
                        dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        var k = 1; var m = max(inf.size.width, inf.size.height)
                        while (m / k > 4096) k *= 2
                        dec.setTargetSampleSize(k)
                    }
                }
                setPhoto(b)
            } catch (e: Exception) { info(R.string.error_generic, e.message ?: "image") }
        }
    }

    fun savePhoto(png: Boolean) {
        val s = photoSrc ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) {
                    val out = PhotoEdit.apply(s, params)
                    Exporter.saveImage(app, out, png, 95, watermark, "LightningLab_${System.currentTimeMillis()}")
                }
                info(R.string.saved)
            } catch (e: Throwable) { info(R.string.error_generic, e.message ?: "save")
            } finally { busy = false }
        }
    }

    // ---------------- video editor ----------------
    fun loadVideo(u: Uri) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(app, u)
            videoDur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            videoUri = u; segments.clear(); infoRes = null
        } catch (e: Exception) { info(R.string.error_generic, e.message ?: "video") } finally { r.release() }
    }

    /** Multi-segment trim, optional mute, optional music (mixed with or replacing original), H.264/AAC MP4. */
    fun exportVideo() {
        val u = videoUri ?: return
        if (busy) return
        busy = true; progress = 0f; stage = R.string.exporting; infoRes = null
        job = viewModelScope.launch {
            val out = File(app.cacheDir, "export_${System.currentTimeMillis()}.mp4")
            var tr: Transformer? = null
            try {
                val segs = if (segments.isEmpty()) listOf(Seg(0, videoDur)) else segments.toList()
                val items = segs.map { s ->
                    val mi = MediaItem.Builder().setUri(u).setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(s.startMs).setEndPositionMs(s.endMs).build()).build()
                    EditedMediaItem.Builder(mi).setRemoveAudio(muteOriginal).build()
                }
                val seqs = ArrayList<EditedMediaItemSequence>()
                seqs += EditedMediaItemSequence(items)
                val total = segs.sumOf { it.endMs - it.startMs }
                music?.let { mu ->
                    val mi = MediaItem.Builder().setUri(mu).setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder().setStartPositionMs(0).setEndPositionMs(total).build()).build()
                    seqs += EditedMediaItemSequence(listOf(EditedMediaItem.Builder(mi).setRemoveVideo(true).build()))
                }
                val done = CompletableDeferred<Throwable?>()
                val t = Transformer.Builder(app)
                    .setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) { done.complete(null) }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            done.complete(exportException)
                        }
                    }).build()
                tr = t
                t.start(Composition.Builder(seqs).build(), out.absolutePath)
                val ph = ProgressHolder()
                while (!done.isCompleted) {
                    if (t.getProgress(ph) != Transformer.PROGRESS_STATE_NOT_STARTED) progress = ph.progress / 100f
                    delay(400)
                }
                val err = done.await()
                if (err != null) throw err
                withContext(Dispatchers.IO) { Exporter.saveVideo(app, out) }
                info(R.string.video_saved)
            } catch (c: CancellationException) { tr?.cancel(); throw c
            } catch (e: Throwable) { info(R.string.error_generic, e.message ?: e.javaClass.simpleName)
            } finally { out.delete(); busy = false }
        }
    }
}
