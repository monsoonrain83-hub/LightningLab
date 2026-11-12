package com.lightninglab

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Intent
import android.graphics.Matrix
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.text.SpannableString
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Effect
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.Crop
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import com.google.common.collect.ImmutableList
import com.lightninglab.atmospheric.knowledge.AtmosphericEngine
import com.lightninglab.atmospheric.knowledge.CloudVisualEvidence
import com.lightninglab.atmospheric.knowledge.CloudFrameObservation
import com.lightninglab.atmospheric.knowledge.CloudEvolutionAnalyzer
import com.lightninglab.atmospheric.knowledge.CloudEvolutionReport
import com.lightninglab.atmospheric.knowledge.CloudVideoSamplingPlan
import com.lightninglab.atmospheric.knowledge.MorphologyAssessment
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Effects
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

enum class Screen { HOME, RESULTS, ABOUT, PHOTO, VIDEO, WEATHER, PREPARE, CLOUD_ATLAS }
/** order = 0: not selected; 1 = base background; 2.. = overlays in the order the user tapped. */
data class Item(val event: LightningEvent, val thumb: Bitmap, val order: Int = 0)
data class Seg(val startMs: Long, val endMs: Long, val uri: Uri? = null)
data class VideoEffectClip(val id: Long, val filterIndex: Int, val startMs: Long, val endMs: Long, val locked: Boolean = false)
data class VideoOverlayKeyframe(val timeMs: Long, val alpha: Float = 1f, val position: Int = 1)
data class VideoOverlayClip(
    val id: Long, val kind: String, val content: String, val startMs: Long, val endMs: Long,
    val color: Int = 0xFFFFFFFF.toInt(), val size: Int = 1, val position: Int = 1, val background: Boolean = false,
    val keyframes: List<VideoOverlayKeyframe> = emptyList(), val assetFile: String? = null, val locked: Boolean = false
)
data class CloudVideoFrameResult(
    val elapsedMillis: Long,
    val genusCode: String?,
    val score: Float?,
    val coverPercent: Int,
    val oktas: Int,
    val texture: Float,
    val kindLabel: String,
    /** Optional multi-label structural feature scores from the separately imported TFLite model. */
    val structuralFeatures: List<Pair<String, Float>>? = null
)
data class CloudVideoReport(
    val durationMillis: Long,
    val sampledFrames: Int,
    val frameResults: List<CloudVideoFrameResult>,
    val evolution: CloudEvolutionReport,
    val modelWasAvailable: Boolean,
    val temporalConsensus: CloudTemporalConsensus,
    val cloudCoverTrend: CloudCoverTrend,
    val structuralFeatureTrend: CloudFeatureTemporalReport
)

private data class PhotoEditorSnapshot(
    val params: PhotoParams,
    val texts: List<PhotoTextLayer>,
    val shapes: List<PhotoShapeLayer>,
    val images: List<PhotoImageLayer>,
    val masks: List<PhotoMaskStroke>,
    val maskInverted: Boolean
)

/** One coherent undo unit for the video editor. Bitmap references are retained by history
 * snapshots so deleting an image overlay can be undone without decoding or losing its asset. */
private data class VideoEditorSnapshot(
    val segments: List<Seg>, val overlays: List<VideoOverlayClip>, val effects: List<VideoEffectClip>,
    val markers: List<Long>, val overlayBitmaps: Map<Long, Bitmap>, val selectedOverlay: Long?, val selectedEffect: Long?,
    val muteOriginal: Boolean, val brightness: Float, val contrast: Float, val rotate: Int,
    val flipH: Boolean, val flipV: Boolean, val crop: Float, val speed: Float,
    val filter: Int, val music: Uri?, val voice: Uri?, val volOrig: Float, val volMusic: Float, val volVoice: Float,
    val fadeInMusicMs: Long, val fadeOutMusicMs: Long, val fadeInVoiceMs: Long, val fadeOutVoiceMs: Long,
    val muteMusic: Boolean, val muteVoice: Boolean, val soloAudioTrack: String,
    val overlayText: String, val textColor: Int, val textSize: Int, val textPos: Int, val textBg: Boolean,
    val sticker: String?, val stickerSize: Int, val stickerPos: Int
)

class MainVM(private val app: Application) : AndroidViewModel(app) {
    /** Decode through the explicit listener overload so Kotlin does not have to infer an overloaded SAM. */
    private fun decodeSoftwareBitmap(uri: Uri, maxDimension: Int): Bitmap {
        val source = ImageDecoder.createSource(app.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source, ImageDecoder.OnHeaderDecodedListener { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            var sample = 1
            val largest = maxOf(info.size.width, info.size.height)
            while (largest / sample > maxDimension.coerceAtLeast(1)) sample *= 2
            decoder.setTargetSampleSize(sample)
        })
    }
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
    val photoLayers = mutableStateListOf<PhotoTextLayer>()
    val photoShapeLayers = mutableStateListOf<PhotoShapeLayer>()
    val photoImageLayers = mutableStateListOf<PhotoImageLayer>()
    val photoMaskStrokes = mutableStateListOf<PhotoMaskStroke>()
    var photoMaskInverted by mutableStateOf(false)
    fun addPhotoMaskStroke(stroke: PhotoMaskStroke) { if (stroke.points.isNotEmpty()) { photoMaskStrokes.add(stroke); commit() } }
    fun undoPhotoMaskStroke() { if (photoMaskStrokes.isNotEmpty()) { photoMaskStrokes.removeAt(photoMaskStrokes.lastIndex); commit() } }
    fun clearPhotoMask() { if (photoMaskStrokes.isNotEmpty()) { photoMaskStrokes.clear(); commit() } }
    fun togglePhotoMaskInvert() { photoMaskInverted = !photoMaskInverted; commit() }
    private var nextPhotoLayerId = 1L
    fun addPhotoTextLayer(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        photoLayers.add(PhotoTextLayer(nextPhotoLayerId++, "Text ${photoLayers.size + 1}", clean, zIndex = nextPhotoZ())); commit()
    }
    fun duplicatePhotoTextLayer(id: Long): Long? {
        val old = photoLayers.firstOrNull { it.id == id } ?: return null
        val idNew = nextPhotoLayerId++
        photoLayers.add(old.copy(id = idNew, name = "${old.name} copy", x = (old.x + 0.025f).coerceAtMost(0.98f),
            y = (old.y + 0.025f).coerceAtMost(0.98f), zIndex = nextPhotoZ()))
        commit(); return idNew
    }

    fun updatePhotoLayer(id: Long, transform: (PhotoTextLayer) -> PhotoTextLayer) {
        val index = photoLayers.indexOfFirst { it.id == id }
        if (index >= 0) { photoLayers[index] = transform(photoLayers[index]); commit() }
    }
    fun movePhotoLayer(id: Long, delta: Int) {
        val from = photoLayers.indexOfFirst { it.id == id }; val to = (from + delta).coerceIn(0, photoLayers.lastIndex)
        if (from >= 0 && to in photoLayers.indices && from != to) { val item = photoLayers.removeAt(from); photoLayers.add(to, item); commit() }
    }
    fun removePhotoLayer(id: Long) { if (photoLayers.removeAll { it.id == id }) commit() }
    fun addPhotoImageLayer(u: Uri) {
        viewModelScope.launch {
            try {
                val decoded = withContext(Dispatchers.IO) {
                    decodeSoftwareBitmap(u, 2048)
                }
                val id = nextPhotoLayerId++
                photoImageLayers.add(PhotoImageLayer(id, "تصویر $id", decoded, zIndex = nextPhotoZ())); commit()
            } catch (e: Exception) { info(R.string.error_generic, e.message ?: "overlay image") }
        }
    }
    fun duplicatePhotoImageLayer(id: Long): Long? {
        val old = photoImageLayers.firstOrNull { it.id == id } ?: return null
        val copy = try { old.bitmap.copy(Bitmap.Config.ARGB_8888, true) } catch (_: Exception) { null } ?: return null
        val idNew = nextPhotoLayerId++
        photoImageLayers.add(old.copy(id = idNew, name = "${old.name} copy", bitmap = copy,
            x = (old.x + 0.025f).coerceAtMost(0.98f), y = (old.y + 0.025f).coerceAtMost(0.98f), zIndex = nextPhotoZ()))
        commit(); return idNew
    }

    fun updatePhotoImage(id: Long, transform: (PhotoImageLayer) -> PhotoImageLayer) {
        val index = photoImageLayers.indexOfFirst { it.id == id }
        if (index >= 0) { photoImageLayers[index] = transform(photoImageLayers[index]); commit() }
    }
    fun movePhotoImage(id: Long, delta: Int) {
        val from = photoImageLayers.indexOfFirst { it.id == id }; if (from < 0) return
        val to = (from + delta).coerceIn(0, photoImageLayers.lastIndex)
        if (to != from) { val item = photoImageLayers.removeAt(from); photoImageLayers.add(to, item); commit() }
    }
    fun removePhotoImage(id: Long) {
        val item = photoImageLayers.firstOrNull { it.id == id } ?: return
        photoImageLayers.remove(item)
        // Bitmap stays alive while an undo snapshot can still reference it.
        commit()
    }
    fun addPhotoShape(shape: PhotoShapeLayer.Shape) {
        val id = nextPhotoLayerId++
        photoShapeLayers.add(PhotoShapeLayer(id, if (shape == PhotoShapeLayer.Shape.RECTANGLE) "مستطیل $id" else "بیضی $id", shape, zIndex = nextPhotoZ())); commit()
    }
    fun duplicatePhotoShapeLayer(id: Long): Long? {
        val old = photoShapeLayers.firstOrNull { it.id == id } ?: return null
        val idNew = nextPhotoLayerId++
        photoShapeLayers.add(old.copy(id = idNew, name = "${old.name} copy", x = (old.x + 0.025f).coerceAtMost(0.98f),
            y = (old.y + 0.025f).coerceAtMost(0.98f), zIndex = nextPhotoZ()))
        commit(); return idNew
    }

    fun updatePhotoShape(id: Long, transform: (PhotoShapeLayer) -> PhotoShapeLayer) {
        val index = photoShapeLayers.indexOfFirst { it.id == id }
        if (index >= 0) { photoShapeLayers[index] = transform(photoShapeLayers[index]); commit() }
    }
    fun movePhotoShape(id: Long, delta: Int) {
        val from = photoShapeLayers.indexOfFirst { it.id == id }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, photoShapeLayers.lastIndex)
        if (to != from) { val item = photoShapeLayers.removeAt(from); photoShapeLayers.add(to, item); commit() }
    }
    fun removePhotoShape(id: Long) { if (photoShapeLayers.removeAll { it.id == id }) commit() }

    private fun nextPhotoZ(): Int {
        val allZ = photoShapeLayers.map { it.zIndex } + photoImageLayers.map { it.zIndex } + photoLayers.map { it.zIndex }
        // Normalize legacy layers that all used zIndex=0 before assigning a new top layer.
        if (allZ.distinct().size != allZ.size) {
            var z = 0
            photoShapeLayers.indices.forEach { i -> photoShapeLayers[i] = photoShapeLayers[i].copy(zIndex = z++) }
            photoImageLayers.indices.forEach { i -> photoImageLayers[i] = photoImageLayers[i].copy(zIndex = z++) }
            photoLayers.indices.forEach { i -> photoLayers[i] = photoLayers[i].copy(zIndex = z++) }
        }
        return ((photoShapeLayers.maxOfOrNull { it.zIndex } ?: -1)
            .coerceAtLeast(photoImageLayers.maxOfOrNull { it.zIndex } ?: -1)
            .coerceAtLeast(photoLayers.maxOfOrNull { it.zIndex } ?: -1)) + 1
    }

    /** Reorders text, shape and image overlays in one shared stack (positive delta = forward). */
    fun movePhotoLayerGlobal(kind: String, id: Long, delta: Int) {
        data class Ref(val kind: String, val id: Long, val z: Int)
        val all = buildList {
            photoLayers.forEach { add(Ref("TEXT", it.id, it.zIndex)) }
            photoShapeLayers.forEach { add(Ref("SHAPE", it.id, it.zIndex)) }
            photoImageLayers.forEach { add(Ref("IMAGE", it.id, it.zIndex)) }
        }.sortedWith(compareBy<Ref> { it.z }.thenBy { when (it.kind) { "SHAPE" -> 0; "IMAGE" -> 1; else -> 2 } })
        val from = all.indexOfFirst { it.kind == kind && it.id == id }
        if (from < 0 || all.isEmpty()) return
        val to = (from + delta).coerceIn(0, all.lastIndex)
        if (to == from) return
        val reordered = all.toMutableList(); val item = reordered.removeAt(from); reordered.add(to, item)
        reordered.forEachIndexed { index, ref -> when (ref.kind) {
            "TEXT" -> photoLayers.indexOfFirst { it.id == ref.id }.takeIf { it >= 0 }?.let { i -> photoLayers[i] = photoLayers[i].copy(zIndex = index) }
            "SHAPE" -> photoShapeLayers.indexOfFirst { it.id == ref.id }.takeIf { it >= 0 }?.let { i -> photoShapeLayers[i] = photoShapeLayers[i].copy(zIndex = index) }
            "IMAGE" -> photoImageLayers.indexOfFirst { it.id == ref.id }.takeIf { it >= 0 }?.let { i -> photoImageLayers[i] = photoImageLayers[i].copy(zIndex = index) }
        } }
        commit()
    }
    /** Fast gesture updates are not added to history until finishPhotoLayerGesture(). */
    fun translatePhotoLayer(kind: String, id: Long, dx: Float, dy: Float) {
        when (kind) {
            "TEXT" -> photoLayers.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { i ->
                val v = photoLayers[i]; photoLayers[i] = v.copy(x = (v.x + dx).coerceIn(0f, 1f), y = (v.y + dy).coerceIn(0f, 1f))
            }
            "SHAPE" -> photoShapeLayers.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { i ->
                val v = photoShapeLayers[i]; photoShapeLayers[i] = v.copy(x = (v.x + dx).coerceIn(0f, 1f), y = (v.y + dy).coerceIn(0f, 1f))
            }
            "IMAGE" -> photoImageLayers.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { i ->
                val v = photoImageLayers[i]; photoImageLayers[i] = v.copy(x = (v.x + dx).coerceIn(0f, 1f), y = (v.y + dy).coerceIn(0f, 1f))
            }
        }
    }
    fun finishPhotoLayerGesture() = commit()

    var showOriginal by mutableStateOf(false)
    var align by mutableStateOf(true)
    var canUndo by mutableStateOf(false)
    var canRedo by mutableStateOf(false)
    private val hist = ArrayList<PhotoEditorSnapshot>(); private var hi = -1
    private fun photoSnapshot() = PhotoEditorSnapshot(params, photoLayers.toList(), photoShapeLayers.toList(), photoImageLayers.toList(), photoMaskStrokes.toList(), photoMaskInverted)
    private fun restorePhotoSnapshot(s: PhotoEditorSnapshot) {
        params = s.params
        photoLayers.clear(); photoLayers.addAll(s.texts)
        photoShapeLayers.clear(); photoShapeLayers.addAll(s.shapes)
        photoImageLayers.clear(); photoImageLayers.addAll(s.images)
        photoMaskStrokes.clear(); photoMaskStrokes.addAll(s.masks)
        photoMaskInverted = s.maskInverted
    }
    private fun histFlags() { canUndo = hi > 0; canRedo = hi < hist.size - 1 }
    private fun histReset() { hist.clear(); hist += photoSnapshot(); hi = 0; histFlags() }
    /** One shared, non-destructive history for adjustments, masks and all overlay-layer edits. */
    fun commit() {
        val current = photoSnapshot()
        if (hist.getOrNull(hi) == current) return
        while (hist.size > hi + 1) hist.removeAt(hist.size - 1)
        hist += current
        if (hist.size > 80) hist.removeAt(0)
        hi = hist.size - 1; histFlags()
    }
    fun undo() { if (hi > 0) { hi--; restorePhotoSnapshot(hist[hi]); histFlags() } }
    fun redo() { if (hi < hist.size - 1) { hi++; restorePhotoSnapshot(hist[hi]); histFlags() } }

    // video editor
    var videoUri by mutableStateOf<Uri?>(null)
    var videoDur by mutableStateOf(0L)
    var videoFrameDurationMs by mutableStateOf(33L)
    var playheadMs by mutableLongStateOf(0L)
    fun videoOverlayBitmap(id: Long): Bitmap? = videoOverlayBitmaps[id]?.takeUnless { it.isRecycled }
    val segments = mutableStateListOf<Seg>()
    val videoMarkers = mutableStateListOf<Long>()
    val videoOverlayClips = mutableStateListOf<VideoOverlayClip>()
    val videoEffectClips = mutableStateListOf<VideoEffectClip>()
    private var nextVideoEffectId = 1L
    var selectedVideoEffectId by mutableStateOf<Long?>(null)
    var selectedOverlayClipId by mutableStateOf<Long?>(null)
    var selectedOverlayClipIds by mutableStateOf<Set<Long>>(emptySet())
    var timelineSnapEnabled by mutableStateOf(true)
    private var nextVideoOverlayId = 1L
    private val videoOverlayBitmaps = LinkedHashMap<Long, Bitmap>()
    private val videoBitmapPool = LinkedHashSet<Bitmap>()
    private val retiredVideoOverlayBitmaps = ArrayList<Bitmap>()
    private fun releaseRetiredVideoImages() { if (!busy) { retiredVideoOverlayBitmaps.forEach { if (!it.isRecycled) it.recycle() }; retiredVideoOverlayBitmaps.clear() } }
    private val videoHistory = ArrayList<VideoEditorSnapshot>()
    private var videoHistoryIndex = -1
    private var restoringVideoHistory = false
    private var timelineGestureDirty = false
    private fun captureVideoSnapshot() = VideoEditorSnapshot(
        segments.toList(), videoOverlayClips.toList(), videoEffectClips.toList(), videoMarkers.toList(), videoOverlayBitmaps.toMap(),
        selectedOverlayClipId, selectedVideoEffectId, muteOriginal, vBrightness, vContrast, vRotate,
        vFlipH, vFlipV, vCrop, vSpeed, vFilter, music, voice, volOrig, volMusic, volVoice,
        fadeInMusicMs, fadeOutMusicMs, fadeInVoiceMs, fadeOutVoiceMs,
        muteMusic, muteVoice, soloAudioTrack,
        overlayText, textColor, textSize, textPos, textBg, sticker, stickerSize, stickerPos
    )
    private fun updateVideoHistoryFlags() {
        canUndoSeg = videoHistoryIndex > 0
        canRedoSeg = videoHistoryIndex in 0 until videoHistory.lastIndex
    }
    private fun cleanupVideoBitmaps() {
        val referenced = LinkedHashSet<Bitmap>()
        referenced.addAll(videoOverlayBitmaps.values)
        videoHistory.forEach { referenced.addAll(it.overlayBitmaps.values) }
        videoBitmapPool.toList().filterNot { it in referenced }.forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
            videoBitmapPool.remove(bitmap)
        }
    }
    private fun resetVideoHistory() {
        videoHistory.clear(); videoHistory += captureVideoSnapshot(); videoHistoryIndex = 0
        updateVideoHistoryFlags(); cleanupVideoBitmaps()
    }
    private fun pushVideoHistory() {
        if (restoringVideoHistory) return
        if (videoHistoryIndex < 0) { resetVideoHistory(); return }
        while (videoHistory.size > videoHistoryIndex + 1) videoHistory.removeAt(videoHistory.lastIndex)
        videoHistory += captureVideoSnapshot()
        if (videoHistory.size > 60) videoHistory.removeAt(0)
        videoHistoryIndex = videoHistory.lastIndex
        updateVideoHistoryFlags(); cleanupVideoBitmaps()
    }
    private fun restoreVideoSnapshot(s: VideoEditorSnapshot) {
        restoringVideoHistory = true
        try {
            segments.clear(); segments.addAll(s.segments)
            videoOverlayClips.clear(); videoOverlayClips.addAll(s.overlays)
            videoEffectClips.clear(); videoEffectClips.addAll(s.effects)
            videoMarkers.clear(); videoMarkers.addAll(s.markers)
            videoOverlayBitmaps.clear(); videoOverlayBitmaps.putAll(s.overlayBitmaps)
            selectedOverlayClipId = s.selectedOverlay; selectedVideoEffectId = s.selectedEffect
            muteOriginal = s.muteOriginal; vBrightness = s.brightness; vContrast = s.contrast; vRotate = s.rotate
            vFlipH = s.flipH; vFlipV = s.flipV; vCrop = s.crop; vSpeed = s.speed; vFilter = s.filter
            if (music != s.music) { music = s.music; s.music?.let { analyzeWaveform(it, false) } ?: run { musicWaveform = emptyList() } }
            if (voice != s.voice) { voice = s.voice; s.voice?.let { analyzeWaveform(it, true) } ?: run { voiceWaveform = emptyList() } }
            volOrig = s.volOrig; volMusic = s.volMusic; volVoice = s.volVoice
            fadeInMusicMs = s.fadeInMusicMs; fadeOutMusicMs = s.fadeOutMusicMs
            fadeInVoiceMs = s.fadeInVoiceMs; fadeOutVoiceMs = s.fadeOutVoiceMs
            muteMusic = s.muteMusic; muteVoice = s.muteVoice; soloAudioTrack = s.soloAudioTrack
            overlayText = s.overlayText; textColor = s.textColor; textSize = s.textSize; textPos = s.textPos; textBg = s.textBg
            sticker = s.sticker; stickerSize = s.stickerSize; stickerPos = s.stickerPos
            segReset(); segPush(); selSeg = -1
        } finally { restoringVideoHistory = false }
    }
    fun undoVideoEdit() {
        if (videoHistoryIndex > 0) { videoHistoryIndex--; restoreVideoSnapshot(videoHistory[videoHistoryIndex]); updateVideoHistoryFlags() }
    }
    fun redoVideoEdit() {
        if (videoHistoryIndex < videoHistory.lastIndex) { videoHistoryIndex++; restoreVideoSnapshot(videoHistory[videoHistoryIndex]); updateVideoHistoryFlags() }
    }
    /** Commit one user-finished settings gesture as a single undo step. */
    fun commitVideoSettings() = pushVideoHistory()
    var musicWaveform by mutableStateOf<List<Float>>(emptyList())
    var voiceWaveform by mutableStateOf<List<Float>>(emptyList())
    var waveformBusy by mutableStateOf(false)
    private var musicWaveformJob: Job? = null
    private var voiceWaveformJob: Job? = null
    private var waveformTasks = 0
    fun updateMusicSource(u: Uri?) {
        music = u; pushVideoHistory()
        if (u == null) { musicWaveformJob?.cancel(); musicWaveform = emptyList(); musicName = null }
        else analyzeWaveform(u, false)
    }
    fun updateVoiceSource(u: Uri?) {
        voice = u; pushVideoHistory()
        if (u == null) { voiceWaveformJob?.cancel(); voiceWaveform = emptyList() }
        else analyzeWaveform(u, true)
    }
    private fun analyzeWaveform(u: Uri, isVoice: Boolean) {
        if (isVoice) voiceWaveformJob?.cancel() else musicWaveformJob?.cancel()
        val task = viewModelScope.launch {
            waveformTasks++; waveformBusy = true
            try {
                val data = withContext(Dispatchers.IO) { AudioWaveform.decode(app, u) }
                if (isVoice) voiceWaveform = data else musicWaveform = data
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (isVoice) voiceWaveform = emptyList() else musicWaveform = emptyList() }
            finally { waveformTasks = (waveformTasks - 1).coerceAtLeast(0); waveformBusy = waveformTasks > 0 }
        }
        if (isVoice) voiceWaveformJob = task else musicWaveformJob = task
    }
    fun selectOverlayClip(id: Long) { selectedOverlayClipId = id }

    fun toggleOverlaySelection(id: Long) {
        selectedOverlayClipIds = if (id in selectedOverlayClipIds) selectedOverlayClipIds - id else selectedOverlayClipIds + id
        selectedOverlayClipId = id
    }

    fun clearOverlaySelection() { selectedOverlayClipIds = emptySet() }

    fun removeSelectedOverlayClips() {
        val ids = selectedOverlayClipIds
        if (ids.size < 2 || videoOverlayClips.any { it.id in ids && it.locked }) return
        videoOverlayClips.removeAll { it.id in ids }
        ids.forEach { videoOverlayBitmaps.remove(it) }
        selectedOverlayClipIds = emptySet()
        selectedOverlayClipId = null
        pushVideoHistory()
    }

    fun moveOverlayGroup(id: Long, deltaMs: Long, recordHistory: Boolean = true) {
        val ids = if (id in selectedOverlayClipIds && selectedOverlayClipIds.size > 1) selectedOverlayClipIds else setOf(id)
        val selected = videoOverlayClips.filter { it.id in ids }
        if (selected.isEmpty() || selected.any { it.locked }) return
        val total = ((if (segments.isEmpty()) videoDur else segments.sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }) / vSpeed.coerceAtLeast(0.05f)).toLong().coerceAtLeast(1L)
        val minStart = selected.minOf { it.startMs }
        val maxEnd = selected.maxOf { it.endMs }
        val anchors = if (timelineSnapEnabled) buildList {
            add(0L); add(total); addAll(videoMarkers)
            var boundary = 0L
            segments.forEach { segment ->
                boundary += (segment.endMs - segment.startMs).coerceAtLeast(0L)
                add((boundary / vSpeed.coerceAtLeast(0.05f)).toLong())
            }
            videoEffectClips.forEach { add(it.startMs); add(it.endMs) }
            videoOverlayClips.filter { it.id !in ids }.forEach { add(it.startMs); add(it.endMs) }
        } else emptyList()
        val snappedStart = TimelineMath.snapTime(minStart + deltaMs, anchors)
        val safeDelta = (snappedStart - minStart).coerceIn(-minStart, (total - maxEnd).coerceAtLeast(-minStart))
        if (safeDelta == 0L) return
        selected.forEach { clip ->
            val index = videoOverlayClips.indexOfFirst { it.id == clip.id }
            if (index >= 0) videoOverlayClips[index] = clip.copy(startMs = clip.startMs + safeDelta, endMs = clip.endMs + safeDelta)
        }
        if (recordHistory) pushVideoHistory() else timelineGestureDirty = true
    }

    fun setOverlayClipLocked(id: Long, locked: Boolean) {
        val i = videoOverlayClips.indexOfFirst { it.id == id }; if (i < 0) return
        val old = videoOverlayClips[i]
        if (old.locked != locked) { videoOverlayClips[i] = old.copy(locked = locked); pushVideoHistory() }
    }

    fun addOverlayKeyframe(id: Long, timeMs: Long, alpha: Float, position: Int) {
        val i = videoOverlayClips.indexOfFirst { it.id == id }; if (i < 0 || videoOverlayClips[i].locked) return
        val clip = videoOverlayClips[i]
        val t = timeMs.coerceIn(clip.startMs, clip.endMs)
        val frames = (clip.keyframes.filterNot { it.timeMs == t } + VideoOverlayKeyframe(t, alpha.coerceIn(0f, 1f), position.coerceIn(0, 2))).sortedBy { it.timeMs }
        videoOverlayClips[i] = clip.copy(keyframes = frames); pushVideoHistory()
    }
    fun removeOverlayKeyframe(id: Long, timeMs: Long) {
        val i = videoOverlayClips.indexOfFirst { it.id == id }; if (i < 0 || videoOverlayClips[i].locked) return
        val updated = videoOverlayClips[i].copy(keyframes = videoOverlayClips[i].keyframes.filterNot { it.timeMs == timeMs })
        if (updated != videoOverlayClips[i]) { videoOverlayClips[i] = updated; pushVideoHistory() }
    }
    fun addEffectClip() {
        val total = ((if (segments.isEmpty()) videoDur else segments.sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }) / vSpeed.coerceAtLeast(0.05f)).toLong()
        if (total <= 0L) return
        val clip = VideoEffectClip(nextVideoEffectId++, vFilter, 0L, total)
        videoEffectClips.add(clip); selectedVideoEffectId = clip.id; pushVideoHistory()
    }
    fun updateEffectClip(id: Long, start: Long, end: Long, recordHistory: Boolean = true) {
        val i = videoEffectClips.indexOfFirst { it.id == id }; if (i < 0 || videoEffectClips[i].locked) return
        val total = ((if (segments.isEmpty()) videoDur else segments.sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }) / vSpeed.coerceAtLeast(0.05f)).toLong().coerceAtLeast(1L)
        val anchors = if (timelineSnapEnabled) buildList {
            add(0L); add(total)
            addAll(videoMarkers)
            var boundary = 0L
            segments.forEach { segment ->
                boundary += (segment.endMs - segment.startMs).coerceAtLeast(0L)
                add((boundary / vSpeed.coerceAtLeast(0.05f)).toLong())
            }
            videoEffectClips.filterIndexed { index, _ -> index != i }.forEach { add(it.startMs); add(it.endMs) }
            videoOverlayClips.forEach { add(it.startMs); add(it.endMs) }
        } else emptyList()
        val snappedStart = TimelineMath.snapTime(start, anchors)
        val minimum = min(200L, total)
        val a = snappedStart.coerceIn(0L, total - minimum)
        val snappedEnd = TimelineMath.snapTime(end, anchors)
        val b = snappedEnd.coerceIn(a + minimum, total)
        val updated = videoEffectClips[i].copy(startMs = a, endMs = b)
        if (updated != videoEffectClips[i]) {
            videoEffectClips[i] = updated
            if (recordHistory) pushVideoHistory() else timelineGestureDirty = true
        }
    }
    fun removeEffectClip(id: Long) {
        if (videoEffectClips.any { it.id == id && it.locked }) return
        if (videoEffectClips.removeAll { it.id == id }) { if (selectedVideoEffectId == id) selectedVideoEffectId = null; pushVideoHistory() }
    }

    fun setEffectClipLocked(id: Long, locked: Boolean) {
        val i = videoEffectClips.indexOfFirst { it.id == id }; if (i < 0) return
        val old = videoEffectClips[i]
        if (old.locked != locked) { videoEffectClips[i] = old.copy(locked = locked); pushVideoHistory() }
    }

    private fun defaultOverlayRange(totalMs: Long): Pair<Long, Long> {
        val total = totalMs.coerceAtLeast(1L)
        val start = playheadMs.coerceIn(0L, total - 1L)
        val end = (start + 5_000L).coerceAtMost(total).coerceAtLeast(start + 1L)
        return start to end
    }


    /** Exports all current text overlay clips as a standard .srt sidecar file. */
    fun exportSubRip(uri: Uri) {
        viewModelScope.launch {
            try {
                val cues = videoOverlayClips.filter { it.kind == "TEXT" }
                    .map { SubtitleCue(it.startMs, it.endMs, it.content) }
                if (cues.isEmpty()) {
                    subtitleImportStatus = "هیچ لایهٔ متنی برای خروجی SRT وجود ندارد."
                    return@launch
                }
                val srt = SubRip.serialize(cues)
                withContext(Dispatchers.IO) {
                    val output = app.contentResolver.openOutputStream(uri, "wt")
                        ?: throw java.io.IOException("subtitle output cannot be opened")
                    output.bufferedWriter(Charsets.UTF_8).use { it.write(srt) }
                }
                subtitleImportStatus = "${cues.size} زیرنویس در فایل SRT ذخیره شد."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val reason = e.message ?: "SRT"
                subtitleImportStatus = "خطا در ذخیرهٔ SRT: $reason"
            }
        }
    }

    /** Imports timed SubRip captions as ordinary editable/exportable text overlay clips. */
    var subtitleImportStatus by mutableStateOf("")
    fun importSubRip(uri: Uri) {
        if (busy) return
        viewModelScope.launch {
            try {
                val cues = withContext(Dispatchers.IO) {
                    val stream = app.contentResolver.openInputStream(uri)
                        ?: throw java.io.IOException("subtitle file cannot be opened")
                    stream.bufferedReader(Charsets.UTF_8).use { SubRip.parse(it.readText()) }
                }
                val total = ((if (segments.isEmpty()) videoDur else
                    segments.sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }) /
                    vSpeed.coerceAtLeast(0.05f)).toLong().coerceAtLeast(1L)
                val imported = cues.mapNotNull { cue ->
                    if (cue.startMs >= total) return@mapNotNull null
                    val start = cue.startMs.coerceAtLeast(0L)
                    val end = cue.endMs.coerceAtMost(total)
                    if (end - start < TimelineMath.MIN_CLIP_MS) return@mapNotNull null
                    VideoOverlayClip(
                        nextVideoOverlayId++, "TEXT", cue.text, start, end,
                        0xFFFFFFFF.toInt(), 1, 2, true,
                        listOf(VideoOverlayKeyframe(start, 1f, 2), VideoOverlayKeyframe(end, 1f, 2))
                    )
                }
                if (imported.isEmpty()) {
                    subtitleImportStatus = "زیرنویس معتبر و هم‌زمان با طول ویدئو پیدا نشد."
                    return@launch
                }
                videoOverlayClips.addAll(imported)
                selectedOverlayClipId = imported.last().id
                subtitleImportStatus = "${imported.size} زیرنویس وارد تایم‌لاین شد."
                pushVideoHistory()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                subtitleImportStatus = "خطا در خواندن زیرنویس: ${e.message ?: "SRT"}"
            }
        }
    }

    fun addTextClip() {
        val value = overlayText.trim(); if (value.isEmpty()) return
        val total = (if (segments.isEmpty()) videoDur else segments.sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }) / vSpeed.coerceAtLeast(0.05f)
        if (total <= 0L) return
        val (startMs, endMs) = defaultOverlayRange(total.toLong())
        val clip = VideoOverlayClip(nextVideoOverlayId++, "TEXT", value, startMs, endMs, textColor, textSize, textPos, textBg,
            listOf(VideoOverlayKeyframe(startMs, 1f, textPos), VideoOverlayKeyframe(endMs, 1f, textPos)))
        videoOverlayClips.add(clip); selectedOverlayClipId = clip.id; pushVideoHistory()
    }
    fun addStickerClip() {
        val value = sticker ?: return
        val total = (if (segments.isEmpty()) videoDur else segments.sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }) / vSpeed.coerceAtLeast(0.05f)
        if (total <= 0L) return
        val (startMs, endMs) = defaultOverlayRange(total.toLong())
        val clip = VideoOverlayClip(nextVideoOverlayId++, "STICKER", value, startMs, endMs, 0xFFFFFFFF.toInt(), stickerSize, stickerPos, false,
            listOf(VideoOverlayKeyframe(startMs, 1f, stickerPos), VideoOverlayKeyframe(endMs, 1f, stickerPos)))
        videoOverlayClips.add(clip); selectedOverlayClipId = clip.id; pushVideoHistory()
    }
    fun addVideoImageClip(uri: Uri) {
        if (busy) return
        val total = (if (segments.isEmpty()) videoDur else segments.sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }) / vSpeed.coerceAtLeast(0.05f)
        if (total <= 0L) return
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val decoded = decodeSoftwareBitmap(uri, 1200)
                    val targetW = (videoW.coerceAtLeast(decoded.width) * 0.38f).toInt().coerceAtLeast(64)
                    if (decoded.width > targetW) {
                        val targetH = (decoded.height * targetW.toFloat() / decoded.width).toInt().coerceAtLeast(1)
                        Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also { if (it !== decoded) decoded.recycle() }
                    } else decoded
                }
                val id = nextVideoOverlayId++
                val (startMs, endMs) = defaultOverlayRange(total.toLong())
                videoOverlayBitmaps[id] = bitmap; videoBitmapPool.add(bitmap)
                videoOverlayClips.add(VideoOverlayClip(id, "IMAGE", "تصویر $id", startMs, endMs, 0xFFFFFFFF.toInt(), 1, 1, false,
                    listOf(VideoOverlayKeyframe(startMs, 1f, 1), VideoOverlayKeyframe(endMs, 1f, 1))))
                selectedOverlayClipId = id; pushVideoHistory()
            } catch (e: Exception) { info(R.string.error_generic, e.message ?: "video image overlay") }
        }
    }

    fun updateOverlayClip(id: Long, start: Long, end: Long, recordHistory: Boolean = true) {
        val i = videoOverlayClips.indexOfFirst { it.id == id }; if (i < 0 || videoOverlayClips[i].locked) return
        val limit = ((if (segments.isEmpty()) videoDur else segments.sumOf { (it.endMs-it.startMs).coerceAtLeast(0L) }) / vSpeed.coerceAtLeast(0.05f)).toLong()
        if (limit < 1L) return
        val anchors = if (timelineSnapEnabled) buildList {
            add(0L); add(limit)
            addAll(videoMarkers)
            var boundary = 0L
            segments.forEach { segment ->
                boundary += (segment.endMs - segment.startMs).coerceAtLeast(0L)
                add((boundary / vSpeed.coerceAtLeast(0.05f)).toLong())
            }
            videoOverlayClips.filterIndexed { index, _ -> index != i }.forEach { add(it.startMs); add(it.endMs) }
            videoEffectClips.forEach { add(it.startMs); add(it.endMs) }
        } else emptyList()
        val snappedStart = TimelineMath.snapTime(start, anchors)
        val a = snappedStart.coerceIn(0L, limit - 1L)
        val snappedEnd = TimelineMath.snapTime(end, anchors)
        val b = snappedEnd.coerceIn(a + 1L, limit)
        val updated = videoOverlayClips[i].copy(startMs = a, endMs = b, keyframes = videoOverlayClips[i].keyframes.map { it.copy(timeMs = it.timeMs.coerceIn(a, b)) }.distinctBy { it.timeMs }.sortedBy { it.timeMs })
        if (updated != videoOverlayClips[i]) {
            videoOverlayClips[i] = updated
            if (recordHistory) pushVideoHistory() else timelineGestureDirty = true
        }
    }
    fun finishVideoTimelineGesture() {
        if (timelineGestureDirty) {
            timelineGestureDirty = false
            pushVideoHistory()
        }
    }
    fun updateSelectedOverlayFromControls() {
        val id = selectedOverlayClipId ?: return
        val i = videoOverlayClips.indexOfFirst { it.id == id }; if (i < 0 || videoOverlayClips[i].locked) return
        val old = videoOverlayClips[i]
        if (old.kind == "IMAGE") return
        videoOverlayClips[i] = if (old.kind == "TEXT") old.copy(content = overlayText.trim().ifEmpty { old.content },
            color = textColor, size = textSize, position = textPos, background = textBg)
        else old.copy(content = sticker ?: old.content, size = stickerSize, position = stickerPos)
        pushVideoHistory()
    }
    fun removeOverlayClip(id: Long) {
        if (videoOverlayClips.any { it.id == id && it.locked }) return
        videoOverlayClips.removeAll { it.id == id }
        // Keep the bitmap alive while an undo snapshot can still reference it.
        videoOverlayBitmaps.remove(id)
        if (selectedOverlayClipId == id) selectedOverlayClipId = null
        pushVideoHistory()
    }
    var music by mutableStateOf<Uri?>(null)
    var muteOriginal by mutableStateOf(false)
    var vBrightness by mutableStateOf(0f)
    var vContrast by mutableStateOf(0f)
    var vRotate by mutableStateOf(0)
    var overlayText by mutableStateOf("")
    var volOrig by mutableStateOf(1f)
    var volMusic by mutableStateOf(1f)
    var volVoice by mutableStateOf(1f)
    var fadeInMusicMs by mutableStateOf(0L)
    var fadeOutMusicMs by mutableStateOf(0L)
    var fadeInVoiceMs by mutableStateOf(0L)
    var fadeOutVoiceMs by mutableStateOf(0L)
    var muteMusic by mutableStateOf(false)
    var muteVoice by mutableStateOf(false)
    var soloAudioTrack by mutableStateOf(AudioTrackRouting.NONE)
    var videoW by mutableStateOf(0)
    var videoH by mutableStateOf(0)
    var vFilter by mutableStateOf(0)
    var vFlipH by mutableStateOf(false)
    var vFlipV by mutableStateOf(false)
    var vCrop by mutableStateOf(0f)              // 0 = original, else target width/height
    var textColor by mutableStateOf(0xFFFFFFFF.toInt())
    var textSize by mutableStateOf(1)
    var textPos by mutableStateOf(1)
    var textBg by mutableStateOf(false)
    var sticker by mutableStateOf<String?>(null)
    var stickerSize by mutableStateOf(1)
    var stickerPos by mutableStateOf(1)
    var selSeg by mutableStateOf(-1)
    var selectedSegmentIndices by mutableStateOf<Set<Int>>(emptySet())
    fun toggleSegmentSelection(index: Int) {
        if (index !in segments.indices) return
        selectedSegmentIndices = if (index in selectedSegmentIndices) selectedSegmentIndices - index else selectedSegmentIndices + index
    }
    fun clearSegmentSelection() { selectedSegmentIndices = emptySet() }
    fun moveSelectedSegmentGroup(direction: Int): Boolean {
        val selected = selectedSegmentIndices
        val ordered = selected.sorted()
        val first = ordered.firstOrNull() ?: return false
        val last = ordered.last()
        val reordered = TimelineMath.reorderContiguousGroup(segments.toList(), selected, direction) ?: return false
        val previousSelection = selSeg
        segments.clear(); segments.addAll(reordered)
        selectedSegmentIndices = selected.map { it + direction }.toSet()
        selSeg = when {
            previousSelection in selected -> previousSelection + direction
            direction < 0 && previousSelection == first - 1 -> last
            direction > 0 && previousSelection == last + 1 -> first
            else -> previousSelection
        }.coerceIn(-1, segments.lastIndex)
        segPush(); pushVideoHistory()
        return true
    }
    fun deleteSelectedSegments(): Boolean {
        if (selectedSegmentIndices.isEmpty() || selectedSegmentIndices.any { it !in segments.indices }) return false
        val removedBeforeSelection = selectedSegmentIndices.count { it < selSeg }
        selectedSegmentIndices.sortedDescending().forEach { segments.removeAt(it) }
        selectedSegmentIndices = emptySet()
        selSeg = when {
            segments.isEmpty() -> -1
            selSeg < 0 -> -1
            else -> (selSeg - removedBeforeSelection).coerceIn(0, segments.lastIndex)
        }
        segPush(); pushVideoHistory()
        return true
    }
    fun duplicateSeg(i: Int) {
        clearSegmentSelection()
        if (i !in segments.indices) return
        segments.add(i + 1, segments[i].copy()); segPush(); selSeg = i + 1; pushVideoHistory()
    }
    /** Reorder two clips in the output sequence without changing their source in/out points. */
    fun moveSegment(from: Int, to: Int): Boolean {
        clearSegmentSelection()
        val destination = TimelineMath.reorderIndex(segments.size, from, to) ?: return false
        val item = segments.removeAt(from)
        segments.add(destination, item)
        selSeg = destination
        segPush(); pushVideoHistory()
        return true
    }
    /** Update a selected clip's source in/out points during a trim gesture; commit once on release. */
    fun updateSegmentRange(index: Int, requestedStart: Long, requestedEnd: Long): Boolean {
        val old = segments.getOrNull(index) ?: return false
        val sourceLimit = if (old.uri == null) videoDur.coerceAtLeast(old.endMs) else old.endMs
        val range = TimelineMath.trimSegmentRange(requestedStart, requestedEnd, sourceLimit) ?: return false
        val updated = old.copy(startMs = range[0], endMs = range[1])
        if (updated == old) return false
        segments[index] = updated
        return true
    }
    /** Split the selected source segment at the current output playhead. */
    fun splitSelectedSegmentAtPlayhead(): Boolean {
        clearSegmentSelection()
        val i = selSeg
        if (i !in segments.indices) return false
        val speed = vSpeed.coerceAtLeast(0.05f)
        val beforeOutput = segments.take(i).sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) } / speed
        val localSource = ((playheadMs - beforeOutput.toLong()).coerceAtLeast(0L) * speed).toLong()
        val segment = segments[i]
        val cut = (segment.startMs + localSource).coerceIn(segment.startMs, segment.endMs)
        val split = TimelineMath.split(segment.startMs, segment.endMs, cut) ?: return false
        segments[i] = segment.copy(startMs = split.first[0], endMs = split.first[1])
        segments.add(i + 1, segment.copy(startMs = split.second[0], endMs = split.second[1]))
        segPush(); selSeg = i + 1; pushVideoHistory()
        return true
    }
    var vSpeed by mutableStateOf(1f)
    var musicName by mutableStateOf<Int?>(null)

    /** Renders a built-in effect to a WAV in the cache and uses it as the added audio track. */
    fun useFx(k: SoundFx.Kind, nameRes: Int) {
        viewModelScope.launch {
            try {
                val f = withContext(Dispatchers.Default) {
                    val file = File(app.cacheDir, "fx_${k.name.lowercase()}.wav")
                    if (!file.exists()) SoundFx.writeWav(file, SoundFx.render(k))
                    file
                }
                updateMusicSource(Uri.fromFile(f)); musicName = nameRes
            } catch (e: Exception) { info(R.string.error_generic, e.message ?: "fx") }
        }
    }

    /** Appends a whole extra video as its own section (joined after the others on export). */
    fun addClip(c: Uri) {
        viewModelScope.launch {
            try {
                val d = withContext(Dispatchers.IO) {
                    val r = MediaMetadataRetriever()
                    try {
                        r.setDataSource(app, c)
                        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    } finally { r.release() }
                }
                if (d <= 0L) { info(R.string.error_generic, "video"); return@launch }
                if (segments.isEmpty()) addSeg(Seg(0, videoDur))
                addSeg(Seg(0, d, c))
            } catch (e: Exception) { info(R.string.error_generic, e.message ?: "video") }
        }
    }

    // ---- object removal (destructive on the working source; one-level undo) ----
    val healCircles = mutableStateListOf<Heal>()
    var canUndoHeal by mutableStateOf(false)
    private var healBackup: Bitmap? = null
    fun addHeal(h: Heal) { if (healCircles.size < 400) healCircles += h }
    private fun replaceSource(b: Bitmap) {
        photoSrc = b
        val s = 1280f / max(b.width, b.height)
        photoPrev = if (s < 1f) Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true) else b
    }
    fun applyHeal() {
        val s = photoSrc ?: return
        if (healCircles.isEmpty() || busy) return
        busy = true
        val marks = healCircles.toList()
        viewModelScope.launch {
            try {
                val n = withContext(Dispatchers.Default) { PhotoEdit.heal(s, marks) }
                healBackup = s; replaceSource(n); healCircles.clear(); canUndoHeal = true
            } catch (e: Throwable) { info(R.string.error_generic, e.message ?: "heal") } finally { busy = false; releaseRetiredVideoImages() }
        }
    }
    /** Replaces the working photo by its subject on a transparent background (<= 2048 px long side). */
    fun cutoutBackground() {
        val s = photoSrc ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val n = withContext(Dispatchers.Default) {
                    val k = 2048f / max(s.width, s.height)
                    val small = if (k < 1f) Bitmap.createScaledBitmap(s, (s.width * k).toInt(), (s.height * k).toInt(), true) else s
                    Cutout.remove(small)
                }
                healBackup = s; replaceSource(n); healCircles.clear(); canUndoHeal = true
                info(R.string.cutout_done)
            } catch (e: Throwable) { info(R.string.error_generic, e.message ?: "cutout") } finally { busy = false; releaseRetiredVideoImages() }
        }
    }
    fun undoHeal() { healBackup?.let { replaceSource(it); healBackup = null; canUndoHeal = false } }

    // ---- photo presets (adjustment sliders only) ----
    private val prefs get() = app.getSharedPreferences("lightninglab", android.content.Context.MODE_PRIVATE)
    fun savePreset() {
        val p = params
        prefs.edit().putString("photo_preset", listOf(p.exposure, p.brightness, p.contrast, p.saturation, p.warmth,
            p.highlights, p.shadows, p.sharpness, p.scurve, p.denoise, p.vibrance).joinToString(",")).apply()
        info(R.string.preset_saved)
    }
    fun loadPreset() {
        val v = prefs.getString("photo_preset", null)?.split(",")?.mapNotNull { it.toFloatOrNull() }
        if (v == null || v.size < 11) { info(R.string.preset_none); return }
        params = params.copy(exposure = v[0], brightness = v[1], contrast = v[2], saturation = v[3], warmth = v[4],
            highlights = v[5], shadows = v[6], sharpness = v[7], scurve = v[8], denoise = v[9], vibrance = v[10])
        commit()
    }

    // ---- infographic ----
    fun saveInfographic() {
        val b = wxBitmap ?: return; val r = wx ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val kindRes = when (r.kind) {
                    CloudAnalysis.Kind.CLEAR -> R.string.kind_clear
                    CloudAnalysis.Kind.THIN_STREAKS -> R.string.kind_thin
                    CloudAnalysis.Kind.CUMULIFORM -> R.string.kind_cumuliform
                    CloudAnalysis.Kind.UNIFORM_LAYER -> R.string.kind_layer
                    CloudAnalysis.Kind.DARK_OVERCAST -> R.string.kind_dark
                    CloudAnalysis.Kind.MIXED -> R.string.kind_mixed
                }
                withContext(Dispatchers.Default) {
                    val txt = app.getString(R.string.wx_report, Math.round(r.cover * 100), r.oktas,
                        String.format(Locale.US, "%.1f", r.texture), Math.round(r.cloudLuma), app.getString(kindRes))
                    val img = Infographic.render(b, app.getString(R.string.weather_title),
                        txt.split("\n") +
                        listOfNotNull(genus?.let { app.getString(R.string.wx_genus, CloudInfo.genusName(it)) },
                            if (features.isEmpty()) null else app.getString(R.string.wx_features, features.joinToString(", "))) +
                        app.getString(R.string.wx_disclaimer), "LightningLab")
                    Exporter.saveImage(app, img, false, 92, false, "LightningLab_sky_${System.currentTimeMillis()}")
                }
                info(R.string.saved)
            } catch (e: Throwable) { info(R.string.error_generic, e.message ?: "infographic") } finally { busy = false; releaseRetiredVideoImages() }
        }
    }
    var canUndoSeg by mutableStateOf(false)
    var canRedoSeg by mutableStateOf(false)
    private val segHist = ArrayList<List<Seg>>(); private var segHi = 0
    private fun segFlags() { canUndoSeg = segHi > 0; canRedoSeg = segHi < segHist.size - 1 }
    private fun segReset() { segHist.clear(); segHist += emptyList<Seg>(); segHi = 0; segFlags() }
    private fun segPush() {
        while (segHist.size > segHi + 1) segHist.removeAt(segHist.size - 1)
        segHist += segments.toList(); segHi = segHist.size - 1; segFlags()
    }
    private fun segRestore() { segments.clear(); segments.addAll(segHist[segHi]); segFlags() }
    fun addSeg(sg: Seg) { clearSegmentSelection(); segments += sg; segPush(); pushVideoHistory() }
    fun removeSeg(i: Int) { if (i in segments.indices) { clearSegmentSelection(); segments.removeAt(i); segPush(); pushVideoHistory() } }
    fun addVideoMarker(timeMs: Long = playheadMs) {
        val total = ((if (segments.isEmpty()) videoDur else segments.sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }) / vSpeed.coerceAtLeast(0.05f)).toLong()
        val marker = timeMs.coerceIn(0L, total.coerceAtLeast(0L))
        if (marker !in videoMarkers) { videoMarkers.add(marker); videoMarkers.sort(); pushVideoHistory() }
    }
    fun removeVideoMarker(timeMs: Long) { if (videoMarkers.remove(timeMs)) pushVideoHistory() }
    /** Remove a segment and close its output-time gap across overlays, effects and markers. */
    fun rippleDeleteSegment(index: Int): Boolean {
        val removed = segments.getOrNull(index) ?: return false
        val speed = vSpeed.coerceAtLeast(0.05f)
        val cutStart = (segments.take(index).sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) } / speed).toLong()
        val cutDuration = ((removed.endMs - removed.startMs).coerceAtLeast(0L) / speed).toLong()
        if (cutDuration <= 0L) return false
        clearSegmentSelection()
        segments.removeAt(index)
        fun shift(t: Long) = TimelineMath.rippleShift(t, cutStart, cutDuration)
        val overlays = videoOverlayClips.mapNotNull { clip ->
            val start = shift(clip.startMs); val end = shift(clip.endMs)
            if (end <= start) null else clip.copy(startMs = start, endMs = end,
                keyframes = clip.keyframes.map { it.copy(timeMs = shift(it.timeMs)) }
                    .filter { it.timeMs in start..end }.distinctBy { it.timeMs }.sortedBy { it.timeMs })
        }
        videoOverlayClips.clear(); videoOverlayClips.addAll(overlays)
        val effects = videoEffectClips.mapNotNull { effect ->
            val start = shift(effect.startMs); val end = shift(effect.endMs)
            if (end <= start) null else effect.copy(startMs = start, endMs = end)
        }
        videoEffectClips.clear(); videoEffectClips.addAll(effects)
        val markers = videoMarkers.map { shift(it) }.distinct().sorted()
        videoMarkers.clear(); videoMarkers.addAll(markers)
        selectedOverlayClipId?.let { id -> if (videoOverlayClips.none { it.id == id }) selectedOverlayClipId = null }
        selectedVideoEffectId?.let { id -> if (videoEffectClips.none { it.id == id }) selectedVideoEffectId = null }
        selSeg = when { segments.isEmpty() -> -1; index >= segments.size -> segments.lastIndex; else -> index }
        segPush(); pushVideoHistory()
        return true
    }
    fun clearSegs() { if (segments.isNotEmpty()) { clearSegmentSelection(); segments.clear(); selSeg = -1; segPush(); pushVideoHistory() } }
    fun undoSeg() = undoVideoEdit()
    fun redoSeg() = redoVideoEdit()

    private val temporalLightningModel by lazy {
        LightningTemporalModel(File(app.filesDir, "lightning_temporal.tflite"))
    }
    var lightningTemporalReady by mutableStateOf(false)
    val temporalScores = androidx.compose.runtime.mutableStateMapOf<Int, List<Pair<String, Float>>>()

    /** Imports an externally trained model, retaining the last known-good model on failure. */
    fun importLightningTemporalModel(u: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val target = File(app.filesDir, "lightning_temporal.tflite")
                val backup = File(app.filesDir, "lightning_temporal.tflite.previous")
                val staging = File(app.filesDir, "lightning_temporal.tflite.importing")
                try {
                    ModelFileRecovery.prepareForImport(target, backup, temporalLightningModel::close, temporalLightningModel::load)
                    staging.delete()
                    val input = app.contentResolver.openInputStream(u) ?: throw java.io.IOException("Cannot open model")
                    input.use { source ->
                        staging.outputStream().use { out ->
                            val chunk = ByteArray(64 * 1024)
                            var size = 0L
                            while (true) {
                                val n = source.read(chunk)
                                if (n < 0) break
                                size += n
                                if (size > 150L * 1024 * 1024) throw java.io.IOException("Model exceeds 150 MB limit")
                                out.write(chunk, 0, n)
                            }
                        }
                    }
                    if (staging.length() == 0L) throw java.io.IOException("Empty model")
                    temporalLightningModel.close()
                    backup.delete()
                    val hadOld = target.exists()
                    if (hadOld && !target.renameTo(backup)) throw java.io.IOException("Backup failed")
                    if (!staging.renameTo(target)) throw java.io.IOException("Model replacement failed")
                    if (!temporalLightningModel.load()) throw java.io.IOException("Incompatible TFLite contract")
                    backup.delete()
                    true
                } catch (_: Throwable) {
                    staging.delete()
                    temporalLightningModel.close()
                    if (backup.exists()) {
                        target.delete()
                        backup.renameTo(target)
                    }
                    temporalLightningModel.load()
                    false
                }
            }
            lightningTemporalReady = withContext(Dispatchers.IO) { temporalLightningModel.load() }
            temporalScores.clear()
            info(if (ok) R.string.model_loaded else R.string.model_invalid)
        }
    }

    /** Optional inference; never changes the independent heuristic detector's decisions. */
    fun classifyLightningEvents() {
        val source = uri ?: return
        if (!lightningTemporalReady || busy || items.isEmpty()) return
        busy = true
        job = viewModelScope.launch {
            try {
                val selected = items.toList()
                progress = 0f
                val results = withContext(Dispatchers.Default) {
                    val detector = Detector(app)
                    val duration = if (prepDur > 0L) prepDur else Long.MAX_VALUE / 2
                    val found = mutableMapOf<Int, List<Pair<String, Float>>>()
                    val runningJob = coroutineContext[Job]
                    for ((index, item) in selected.withIndex()) {
                        ensureActive()
                        val times = LightningTemporalModel.sampleTimes(
                            item.event.startMs, item.event.endMs, duration
                        )
                        val bitmaps = detector.temporalFrames(source, times, LightningTemporalModel.SIDE) {
                            runningJob?.isActive == false
                        }
                        try {
                            ensureActive()
                            if (bitmaps.size == LightningTemporalModel.FRAMES) {
                                temporalLightningModel.classify(bitmaps)?.let { found[item.event.id] = it }
                            }
                        } finally {
                            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
                        }
                        progress = (index + 1).toFloat() / selected.size
                    }
                    found
                }
                temporalScores.clear()
                temporalScores.putAll(results)
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                info(R.string.error_generic, e.message ?: e.javaClass.simpleName)
            } finally {
                busy = false
            }
        }
    }

    // ---- optional TFLite cloud model + observer tags ----
    private val cloudModel by lazy { CloudClassifier(File(app.filesDir, "cloud_model.tflite")) }
    private val cloudSynopticModel by lazy { CloudSynopticClassifier(File(app.filesDir, "cloud_synoptic_model.tflite")) }
    var synopticModelReady by mutableStateOf(false)
    var synopticPredictions by mutableStateOf<CloudSynopticPredictions?>(null)
    private val cloudFeatureModel by lazy { CloudFeatureClassifier(File(app.filesDir, "cloud_features.tflite")) }
    private val cloudSegmentationModel by lazy { CloudSegmentationModel(File(app.filesDir, "cloud_segmentation.tflite")) }
    private val atmosphericSegmentationModel by lazy { AtmosphericSegmentationModel(File(app.filesDir, "atmosphere_multiclass.tflite")) }
    var modelReady by mutableStateOf(false)
    var featureModelReady by mutableStateOf(false)
    var segmentationModelReady by mutableStateOf(false)
    var segmentationResult by mutableStateOf<CloudSegmentationResult?>(null)
    var atmosphericSegmentationReady by mutableStateOf(false)
    var atmosphericSegmentationResult by mutableStateOf<AtmosphericSegmentationResult?>(null)
    var featurePredictions by mutableStateOf<List<Pair<String, Float>>>(emptyList())
    var multiScaleReport by mutableStateOf<CloudMultiScaleReport?>(null)
    var topK by mutableStateOf<List<Pair<String, Float>>>(emptyList())
    var genus by mutableStateOf<String?>(null)
    val features = mutableStateListOf<String>()
    val morphologyCues = mutableStateListOf<String>()
    var morphologyAssessment by mutableStateOf<MorphologyAssessment?>(null)

    fun toggleMorphologyCue(code: String) {
        if (code in morphologyCues) morphologyCues.remove(code) else morphologyCues.add(code)
        morphologyAssessment = null
    }

    fun assessMorphology() {
        val selectedGenus = genus ?: topK.firstOrNull()?.first ?: return
        val selectedCues = morphologyCues.toSet()
        viewModelScope.launch(Dispatchers.IO) {
        fun has(code: String) = code in selectedCues
        val evidence = CloudVisualEvidence(
            genusCode = selectedGenus,
            hookedOrCurvedFibres = has("hooked"), straightOrCurvedFilaments = has("filaments"), denseFibrousVeil = has("fibrous"),
            distinctAnvil = has("anvil"), smoothRoundedTowerTops = has("smooth_tops"),
            cauliflowerTowers = has("cauliflower"), shallowSmallPuffs = has("shallow"),
            moderateVerticalGrowth = has("moderate_growth"), strongVerticalGrowth = has("strong_growth"),
            raggedFragments = has("ragged"), lensShape = has("lens"), horizontalRoll = has("roll"),
            layerOrSheet = has("layer"), smallDetachedTufts = has("tufts"), uniformHazyAppearance = has("hazy"),
            hangingPouches = has("mamma"), precipitationStreaks = has("streaks"),
            precipitationReachesGround = has("reaches_ground"), smoothCapAboveTower = has("pileus"),
            horizontalBannerOrShelf = has("arcus"), funnelOrHangingColumn = has("funnel"),
            waveLikeUnderside = has("asperitas"), wallCloudAppearance = has("murus"),
            tailCloudBand = has("cauda"), holeInCloudLayer = has("cavum"), waveBreakingTop = has("fluctus"),
            sunOrMoonVisibleThroughCloud = has("translucent"), clearGapsBetweenCloudElements = has("gaps"),
            multipleCloudLayers = has("multiple_layers"), visibleWavePattern = has("waves"),
            radiatingOrConvergingBands = has("radiating"), regularCloudHoles = has("regular_holes"),
            vertebraLikeArrangement = has("vertebra"), sheetAroundTower = has("velum"),
            raggedFragmentsUnderMainCloud = has("pannus"), inflowBandToStorm = has("flumen"),
            intertwinedFibres = has("intortus")
        )
        val result = AtmosphericEngine(app).assessCloudMorphology(evidence)
        withContext(Dispatchers.Main) { morphologyAssessment = result }
        }
    }
    fun initModel() { viewModelScope.launch {
        modelReady = withContext(Dispatchers.IO) {
            ModelFileRecovery.loadOrRestore(File(app.filesDir, "cloud_model.tflite"), File(app.filesDir, "cloud_model.tflite.previous"), cloudModel::close, cloudModel::load)
        }
        featureModelReady = withContext(Dispatchers.IO) {
            ModelFileRecovery.loadOrRestore(File(app.filesDir, "cloud_features.tflite"), File(app.filesDir, "cloud_features.tflite.previous"), cloudFeatureModel::close, cloudFeatureModel::load)
        }
        segmentationModelReady = withContext(Dispatchers.IO) {
            ModelFileRecovery.loadOrRestore(File(app.filesDir, "cloud_segmentation.tflite"), File(app.filesDir, "cloud_segmentation.tflite.previous"), cloudSegmentationModel::close, cloudSegmentationModel::load)
        }
        atmosphericSegmentationReady = withContext(Dispatchers.IO) {
            ModelFileRecovery.loadOrRestore(File(app.filesDir, "atmosphere_multiclass.tflite"), File(app.filesDir, "atmosphere_multiclass.tflite.previous"), atmosphericSegmentationModel::close, atmosphericSegmentationModel::load)
        }
        lightningTemporalReady = withContext(Dispatchers.IO) {
            ModelFileRecovery.loadOrRestore(File(app.filesDir, "lightning_temporal.tflite"),
                File(app.filesDir, "lightning_temporal.tflite.previous"),
                temporalLightningModel::close, temporalLightningModel::load)
        }
        synopticModelReady = withContext(Dispatchers.IO) {
            ModelFileRecovery.loadOrRestore(File(app.filesDir, "cloud_synoptic_model.tflite"), File(app.filesDir, "cloud_synoptic_model.tflite.previous"), cloudSynopticModel::close, cloudSynopticModel::load)
        }
    } }
    fun importModel(u: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val target = File(app.filesDir, "cloud_model.tflite")
                val temp = File(app.filesDir, "cloud_model.tflite.importing")
                val backup = File(app.filesDir, "cloud_model.tflite.previous")
                try {
                    ModelFileRecovery.prepareForImport(target, backup, cloudModel::close, cloudModel::load)
                    temp.delete()
                    backup.delete()
                    val copied = app.contentResolver.openInputStream(u)?.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                        temp.isFile && temp.length() > 0L
                    } ?: false
                    if (!copied) {
                        temp.delete()
                        false
                    } else {
                        // Keep the last working model until the candidate has passed the
                        // interpreter's tensor-contract checks. A bad import must never
                        // silently replace a previously usable classifier.
                        cloudModel.close()
                        val hadPrevious = target.exists()
                        if (hadPrevious && !target.renameTo(backup)) {
                            temp.delete()
                            cloudModel.load()
                            false
                        } else if (!temp.renameTo(target)) {
                            temp.delete()
                            if (hadPrevious) { backup.renameTo(target) } else { Unit }
                            cloudModel.load()
                            false
                        } else {
                            val valid = cloudModel.load()
                            if (valid) {
                                backup.delete()
                                true
                            } else {
                                target.delete()
                                if (hadPrevious) { backup.renameTo(target) } else { Unit }
                                cloudModel.load()
                                false
                            }
                        }
                    }
                } catch (_: Throwable) {
                    temp.delete()
                    if (backup.exists()) {
                        target.delete()
                        backup.renameTo(target)
                    }
                    cloudModel.load()
                    false
                }
            }
            modelReady = ok
            if (ok) info(R.string.model_loaded) else info(R.string.model_invalid)
        }
    }
    fun importSynopticModel(u: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val target = File(app.filesDir, "cloud_synoptic_model.tflite")
                val temp = File(app.filesDir, "cloud_synoptic_model.tflite.importing")
                val backup = File(app.filesDir, "cloud_synoptic_model.tflite.previous")
                try {
                    ModelFileRecovery.prepareForImport(target, backup, cloudSynopticModel::close, cloudSynopticModel::load)
                    temp.delete(); backup.delete()
                    val copied = app.contentResolver.openInputStream(u)?.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                        temp.isFile && temp.length() > 0L
                    } ?: false
                    if (!copied) {
                        temp.delete()
                        false
                    } else {
                        cloudSynopticModel.close()
                        val hadPrevious = target.exists()
                        if (hadPrevious && !target.renameTo(backup)) {
                            temp.delete(); cloudSynopticModel.load(); false
                        } else if (!temp.renameTo(target)) {
                            temp.delete()
                            if (hadPrevious) backup.renameTo(target)
                            cloudSynopticModel.load()
                            false
                        } else if (cloudSynopticModel.load()) {
                            backup.delete()
                            true
                        } else {
                            target.delete()
                            if (hadPrevious) backup.renameTo(target)
                            cloudSynopticModel.load()
                            false
                        }
                    }
                } catch (_: Throwable) {
                    temp.delete()
                    if (backup.exists()) { target.delete(); backup.renameTo(target) }
                    cloudSynopticModel.load()
                    false
                }
            }
            synopticModelReady = if (ok) true else withContext(Dispatchers.IO) { cloudSynopticModel.load() }
            if (ok) info(R.string.model_loaded) else info(R.string.model_invalid)
        }
    }

    fun importFeatureModel(u: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val target = File(app.filesDir, "cloud_features.tflite")
                val temp = File(app.filesDir, "cloud_features.tflite.importing")
                val backup = File(app.filesDir, "cloud_features.tflite.previous")
                try {
                    ModelFileRecovery.prepareForImport(target, backup, cloudFeatureModel::close, cloudFeatureModel::load)
                    temp.delete(); backup.delete()
                    val copied = app.contentResolver.openInputStream(u)?.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                        temp.exists() && temp.length() > 0L
                    } ?: false
                    if (!copied) {
                        temp.delete(); false
                    } else {
                        cloudFeatureModel.close()
                        val hadOld = target.exists()
                        if (hadOld && !target.renameTo(backup)) {
                            temp.delete(); cloudFeatureModel.load(); false
                        } else if (!temp.renameTo(target)) {
                            temp.delete()
                            if (hadOld) { backup.renameTo(target) } else { Unit }
                            cloudFeatureModel.load(); false
                        } else {
                            val valid = cloudFeatureModel.load()
                            if (valid) {
                                backup.delete()
                                true
                            } else {
                                target.delete()
                                if (hadOld) { backup.renameTo(target) } else { Unit }
                                cloudFeatureModel.load()
                                false
                            }
                        }
                    }
                } catch (_: Throwable) {
                    temp.delete()
                    if (backup.exists() && !target.exists()) { backup.renameTo(target) } else { Unit }
                    cloudFeatureModel.load()
                    false
                }
            }
            featureModelReady = ok
            if (ok) info(R.string.model_loaded) else info(R.string.model_invalid)
        }
    }

    fun importSegmentationModel(u: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val target = File(app.filesDir, "cloud_segmentation.tflite")
                val temp = File(app.filesDir, "cloud_segmentation.tflite.importing")
                val backup = File(app.filesDir, "cloud_segmentation.tflite.previous")
                try {
                    ModelFileRecovery.prepareForImport(target, backup, cloudSegmentationModel::close, cloudSegmentationModel::load)
                    temp.delete(); backup.delete()
                    val copied = app.contentResolver.openInputStream(u)?.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                        temp.length() > 0L
                    } ?: false
                    if (!copied) { temp.delete(); false } else {
                        cloudSegmentationModel.close()
                        val hadOld = target.exists()
                        if (hadOld && !target.renameTo(backup)) { temp.delete(); cloudSegmentationModel.load(); false }
                        else if (!temp.renameTo(target)) {
                            temp.delete(); if (hadOld) { backup.renameTo(target) } else { Unit }
                            cloudSegmentationModel.load(); false
                        } else {
                            val valid = cloudSegmentationModel.load()
                            if (valid) backup.delete()
                            else {
                                target.delete()
                                if (hadOld) { backup.renameTo(target) } else { Unit }
                                cloudSegmentationModel.load()
                            }
                            valid
                        }
                    }
                } catch (_: Throwable) {
                    temp.delete()
                    if (backup.exists() && !target.exists()) { backup.renameTo(target) } else { Unit }
                    cloudSegmentationModel.load()
                    false
                }
            }
            segmentationModelReady = ok
            segmentationResult = null
            if (ok) info(R.string.model_loaded) else info(R.string.model_invalid)
        }
    }

    fun importAtmosphericSegmentationModel(u: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val target = File(app.filesDir, "atmosphere_multiclass.tflite")
                val temp = File(app.filesDir, "atmosphere_multiclass.tflite.importing")
                val backup = File(app.filesDir, "atmosphere_multiclass.tflite.previous")
                try {
                    ModelFileRecovery.prepareForImport(target, backup, atmosphericSegmentationModel::close, atmosphericSegmentationModel::load)
                    temp.delete(); backup.delete()
                    val copied = app.contentResolver.openInputStream(u)?.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                        temp.length() > 0L
                    } ?: false
                    if (!copied) { temp.delete(); false } else {
                        atmosphericSegmentationModel.close()
                        val hadOld = target.exists()
                        if (hadOld && !target.renameTo(backup)) { temp.delete(); atmosphericSegmentationModel.load(); false }
                        else if (!temp.renameTo(target)) {
                            temp.delete(); if (hadOld) { backup.renameTo(target) } else { Unit }
                            atmosphericSegmentationModel.load(); false
                        } else {
                            val valid = atmosphericSegmentationModel.load()
                            if (valid) backup.delete() else {
                                target.delete(); if (hadOld) { backup.renameTo(target) } else { Unit }
                                atmosphericSegmentationModel.load()
                            }
                            valid
                        }
                    }
                } catch (_: Throwable) {
                    temp.delete(); if (backup.exists() && !target.exists()) { backup.renameTo(target) } else { Unit }
                    atmosphericSegmentationModel.load(); false
                }
            }
            atmosphericSegmentationReady = ok
            atmosphericSegmentationResult = null
            if (ok) info(R.string.model_loaded) else info(R.string.model_invalid)
        }
    }

    fun toggleFeature(f: String) { if (f in features) features.remove(f) else features.add(f) }

    var wxBitmap by mutableStateOf<Bitmap?>(null)
    var wx by mutableStateOf<CloudAnalysis.Result?>(null)
    fun analyzeSky(u: Uri) {
        viewModelScope.launch {
            try {
                val (bmp, res) = withContext(Dispatchers.Default) {
                    val b = decodeSoftwareBitmap(u, 1024)
                    val px = IntArray(b.width * b.height); b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
                    b to CloudAnalysis.analyze(px, b.width, b.height)
                }
                val ranked = withContext(Dispatchers.Default) { cloudModel.classify(bmp) }
                val synoptic = if (synopticModelReady) withContext(Dispatchers.Default) { cloudSynopticModel.classify(bmp) } else null
                val featureRanked = withContext(Dispatchers.Default) { cloudFeatureModel.classify(bmp) }
                val regional = withContext(Dispatchers.Default) { CloudMultiScaleAnalyzer(cloudModel, cloudFeatureModel).analyze(bmp) }
                val segmented = if (segmentationModelReady) withContext(Dispatchers.Default) { cloudSegmentationModel.segment(bmp) } else null
                val atmosphericSegmented = if (atmosphericSegmentationReady) withContext(Dispatchers.Default) { atmosphericSegmentationModel.segment(bmp) } else null
                // Keep model output as candidate evidence only. Its scores are not calibrated,
                // so never silently convert the highest score into a confirmed WMO genus.
                topK = ranked?.take(3) ?: emptyList(); genus = null
                synopticPredictions = synoptic
                featurePredictions = featureRanked?.filter { it.second >= 0.50f } ?: emptyList()
                multiScaleReport = regional
                segmentationResult = segmented
                atmosphericSegmentationResult = atmosphericSegmented
                features.clear(); morphologyCues.clear(); morphologyAssessment = null
                wxBitmap = bmp; wx = res; infoRes = null
            } catch (e: Exception) { info(R.string.error_generic, e.message ?: "image") }
        }
    }

    // ---------------- offline cloud video analysis ----------------
    var cloudVideoBusy by mutableStateOf(false)
    var cloudVideoProgress by mutableStateOf(0f)
    var cloudVideoReport by mutableStateOf<CloudVideoReport?>(null)

    fun analyzeCloudVideo(uri: Uri) {
        if (cloudVideoBusy) return
        cloudVideoBusy = true
        cloudVideoProgress = 0f
        cloudVideoReport = null
        viewModelScope.launch {
            try {
                val report = withContext(Dispatchers.Default) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(app, uri)
                        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                        require(duration >= 1000L) { "ویدئو باید حداقل یک ثانیه باشد." }
                        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                            ?.toIntOrNull() ?: 0
                        val selectedTimes = CloudVideoSamplingPlan.timestamps(duration, maxFrames = 30)
                        val results = mutableListOf<CloudVideoFrameResult>()
                        val observations = mutableListOf<CloudFrameObservation>()
                        selectedTimes.forEachIndexed { index, ms ->
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val extractedFrame = retriever.getScaledFrameAtTime(
                                ms * 1000L,
                                MediaMetadataRetriever.OPTION_CLOSEST,
                                512,
                                512
                            )
                            if (extractedFrame != null) {
                                var frame: Bitmap = extractedFrame
                                try {
                                    if (rotation == 90 || rotation == 180 || rotation == 270) {
                                        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                                        val rotated = Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, matrix, true)
                                        if (rotated !== frame) frame.recycle()
                                        frame = rotated
                                    }
                                    val pixels = IntArray(frame.width * frame.height)
                                    frame.getPixels(pixels, 0, frame.width, 0, 0, frame.width, frame.height)
                                    val sky = CloudAnalysis.analyze(pixels, frame.width, frame.height)
                                    val ranked = cloudModel.classify(frame)
                                    val structuralFeatures = cloudFeatureModel.classify(frame)
                                    val top: Pair<String, Float>? = ranked?.firstOrNull()
                                    val label = when (sky.kind) {
                                        CloudAnalysis.Kind.CLEAR -> "آسمان صاف یا کم‌ابر"
                                        CloudAnalysis.Kind.THIN_STREAKS -> "رگه‌های نازک احتمالی"
                                        CloudAnalysis.Kind.CUMULIFORM -> "بافت کومه‌ای احتمالی"
                                        CloudAnalysis.Kind.UNIFORM_LAYER -> "پوشش لایه‌ای احتمالی"
                                        CloudAnalysis.Kind.DARK_OVERCAST -> "پوشش تیره و گسترده"
                                        CloudAnalysis.Kind.MIXED -> "الگوی ترکیبی یا نامشخص"
                                    }
                                    results += CloudVideoFrameResult(
                                        elapsedMillis = ms,
                                        genusCode = top?.first,
                                        score = top?.second,
                                        coverPercent = (sky.cover * 100f).toInt().coerceIn(0, 100),
                                        oktas = sky.oktas,
                                        texture = sky.texture,
                                        kindLabel = label,
                                        structuralFeatures = structuralFeatures
                                    )
                                    observations += CloudFrameObservation(
                                        elapsedMillis = ms,
                                        genusCode = top?.first,
                                        genusLabelPersian = top?.first?.let { CloudInfo.genusName(it) },
                                        genusScore = top?.second
                                    )
                                } finally { frame.recycle() }
                            }
                            withContext(Dispatchers.Main) {
                                cloudVideoProgress = (index + 1).toFloat() / selectedTimes.size.coerceAtLeast(1)
                            }
                        }
                        require(results.size >= 2) { "از این ویدئو دست‌کم دو فریم قابل استخراج نبود." }
                        val evolution = CloudEvolutionAnalyzer().analyze(observations)
                        val consensus = CloudTemporalConsensusAnalyzer.analyze(results)
                        CloudVideoReport(
                            duration, results.size, results, evolution,
                            results.any { it.genusCode != null }, consensus,
                            CloudCoverTrendAnalyzer.analyze(results),
                            CloudFeatureTemporalAnalyzer.analyze(results)
                        )
                    } finally { retriever.release() }
                }
                cloudVideoReport = report
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                info(R.string.error_generic, e.message ?: "cloud video")
            } finally {
                cloudVideoBusy = false
            }
        }
    }

    fun shareCloudVideoReport() {
        val report = cloudVideoReport ?: return
        val lines = mutableListOf<String>()
        lines += "گزارش تحلیل آفلاین ویدئوی ابر — Lightning Lab"
        lines += "مدت ویدئو: ${report.durationMillis / 1000} ثانیه | فریم‌های بررسی‌شده: ${report.sampledFrames}"
        lines += "روند پوشش ابریِ برآوردشده: ${report.cloudCoverTrend.labelPersian}"
        lines += "ابتدا: ${report.cloudCoverTrend.firstMeanPercent}٪ | انتها: ${report.cloudCoverTrend.lastMeanPercent}٪ | تغییر: ${report.cloudCoverTrend.changePercentagePoints} واحد درصد"
        lines += "جمع‌بندی جنس ابر: ${report.temporalConsensus.candidates.joinToString("، ") { CloudInfo.genusName(it.genusCode) + " (${it.frameSharePercent}٪ فریم‌ها)" }.ifBlank { "خروجی مدل جنس در دسترس نیست" }}"
        lines += "ویژگی‌های ساختاری: ${report.structuralFeatureTrend.candidates.joinToString("، ") { CloudFeatureTemporalAnalyzer.displayName(it.code) + " (${it.frameSharePercent}٪ فریم‌ها)" }.ifBlank { "مدل ویژگی ساختاری خروجی قابل گزارش ندارد" }}"
        lines += report.structuralFeatureTrend.notePersian
        lines += "یادآوری: نتایج ماشینی و تخمینی‌اند؛ امتیاز مدل احتمال کالیبره‌شده نیست و جایگزین مشاهده یا ارزیابی هواشناسی نمی‌شود."
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "گزارش تحلیل ابر — Lightning Lab")
            putExtra(Intent.EXTRA_TEXT, lines.joinToString("\n"))
        }
        app.startActivity(Intent.createChooser(send, "اشتراک گزارش تحلیل ابر").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun info(res: Int, vararg a: Any) { infoRes = res; infoArgs = a.toList() }

    // ---------------- detection ----------------
    // ---- pre-detect trim ----
    var prepUri by mutableStateOf<Uri?>(null)
    var prepDur by mutableStateOf(0L)
    var prepStart by mutableStateOf(0L)
    var prepEnd by mutableStateOf(0L)
    fun prepare(u: Uri) {
        viewModelScope.launch {
            val d = withContext(Dispatchers.IO) {
                val r = MediaMetadataRetriever()
                try { r.setDataSource(app, u); r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L }
                catch (e: Exception) { 0L } finally { r.release() }
            }
            prepUri = u; prepDur = d; prepStart = 0L; prepEnd = d; infoRes = null
            screen = Screen.PREPARE
        }
    }

    fun startDetect(u: Uri, startMs: Long = 0L, endMs: Long = Long.MAX_VALUE) {
        job?.cancel(); uri = u; items.clear(); temporalScores.clear(); composite = null; infoRes = null
        busy = true; progress = 0f; stage = R.string.stage_scan
        job = viewModelScope.launch {
            try {
                val det = Detector(app)
                withContext(Dispatchers.Default) {
                    val evs = det.detect(u, sensitivity, startMs = startMs, endMs = endMs, onProgress = { progress = it },
                        onStage = { stage = it }, cancelled = { !isActive })
                    stage = R.string.stage_thumbs
                    for (e in evs.sortedByDescending { it.score }.take(60).sortedBy { it.startMs }) {
                        det.frame(u, e.bestMs, 480)?.let { items += Item(e, it) }
                    }
                }
                if (items.isEmpty()) info(R.string.no_events) else screen = Screen.RESULTS
            } catch (c: CancellationException) { throw c
            } catch (e: Exception) { info(R.string.error_generic, e.message ?: e.javaClass.simpleName)
            } finally { busy = false; releaseRetiredVideoImages() }
        }
    }

    /** Save an auditable evidence report to an Android user-selected document URI. */
    fun exportLightningEvidence(destination: Uri, json: Boolean) {
        val snapshot = items.map { it.event }
        if (snapshot.isEmpty()) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val payload = if (json) LightningEvidenceReport.json(snapshot) else LightningEvidenceReport.csv(snapshot)
                    app.contentResolver.openOutputStream(destination, "wt")?.use {
                        it.write(payload.toByteArray(Charsets.UTF_8))
                        it.flush()
                    } ?: throw java.io.IOException("Cannot write selected document")
                }
                infoRes = null
            } catch (e: Exception) {
                info(R.string.error_generic, e.message ?: e.javaClass.simpleName)
            }
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
            } finally { busy = false; releaseRetiredVideoImages() }
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
            } finally { busy = false; releaseRetiredVideoImages() }
        }
    }

    // ---------------- photo editor ----------------
    fun setPhoto(b: Bitmap) {
        // Previous snapshots may still be rendering/exporting; drop references without recycling their bitmaps here.
        photoSrc = b; params = PhotoParams(); photoLayers.clear(); photoShapeLayers.clear(); photoMaskStrokes.clear(); photoMaskInverted = false; photoImageLayers.clear(); nextPhotoLayerId = 1L; histReset()
        showOriginal = false
        val s = 1280f / max(b.width, b.height)
        photoPrev = if (s < 1f) Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true) else b
        screen = Screen.PHOTO
    }

    fun loadPhoto(u: Uri) {
        viewModelScope.launch {
            try {
                val b = withContext(Dispatchers.IO) {
                    decodeSoftwareBitmap(u, 4096)
                }
                setPhoto(b)
            } catch (e: Exception) { info(R.string.error_generic, e.message ?: "image") }
        }
    }

    fun savePhoto(png: Boolean) {
        val s = photoSrc ?: return
        if (busy) return
        busy = true
        val paramsSnapshot = params
        val layersSnapshot = photoLayers.toList()
        val shapesSnapshot = photoShapeLayers.toList()
        val imagesSnapshot = photoImageLayers.toList()
        val masksSnapshot = photoMaskStrokes.toList()
        val invertMaskSnapshot = photoMaskInverted
        val watermarkSnapshot = watermark
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) {
                    val adjusted = PhotoEdit.apply(s, paramsSnapshot)
                    val out = PhotoLayers.render(adjusted, layersSnapshot, shapesSnapshot, imagesSnapshot, masksSnapshot, invertMaskSnapshot)
                    if (out !== adjusted && !adjusted.isRecycled) adjusted.recycle()
                    Exporter.saveImage(app, out, png, 95, watermarkSnapshot, "LightningLab_${System.currentTimeMillis()}")
                    if (!out.isRecycled) out.recycle()
                }
                info(R.string.saved)
            } catch (e: Throwable) { info(R.string.error_generic, e.message ?: "save")
            } finally { busy = false; releaseRetiredVideoImages() }
        }
    }

    // ---------------- video editor ----------------
    fun loadVideo(u: Uri) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(app, u)
            videoDur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val fps = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()?.takeIf { it in 1f..240f } ?: 30f
            videoFrameDurationMs = (1000f / fps).toLong().coerceAtLeast(1L)
            val vw = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val vh = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { videoW = vh; videoH = vw } else { videoW = vw; videoH = vh }
            videoUri = u; playheadMs = 0L; clearSegmentSelection(); segments.clear(); videoMarkers.clear(); videoOverlayClips.clear(); videoEffectClips.clear(); selectedVideoEffectId = null; nextVideoEffectId = 1L; videoOverlayBitmaps.values.forEach { if (busy) retiredVideoOverlayBitmaps.add(it) else if (!it.isRecycled) it.recycle() }; videoOverlayBitmaps.clear(); selectedOverlayClipId = null; nextVideoOverlayId = 1L; segReset(); selSeg = -1; infoRes = null; resetVideoHistory()
        } catch (e: Exception) { info(R.string.error_generic, e.message ?: "video") } finally { r.release() }
    }

    // ---- voice-over recording ----
    var voice by mutableStateOf<Uri?>(null)
    var recording by mutableStateOf(false)
    private var recorder: MediaRecorder? = null
    private var recFile: File? = null
    fun micDenied() = info(R.string.rec_denied)
    fun startRecording() {
        if (recording) return
        try {
            val f = File(app.cacheDir, "voice_" + System.currentTimeMillis() + ".m4a")
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(app) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(128000); r.setAudioSamplingRate(44100)
            r.setOutputFile(f.absolutePath)
            r.prepare(); r.start()
            recorder = r; recFile = f; recording = true
        } catch (e: Exception) {
            try { recorder?.release() } catch (x: Exception) { }
            recorder = null; recording = false
            info(R.string.error_generic, e.message ?: "mic")
        }
    }
    fun stopRecording() {
        val r = recorder ?: return
        var ok = true
        try { r.stop() } catch (e: RuntimeException) { ok = false }
        try { r.release() } catch (e: Exception) { }
        recorder = null; recording = false
        val f = recFile; recFile = null
        if (ok && f != null && f.exists() && f.length() > 1000) { updateVoiceSource(Uri.fromFile(f)); info(R.string.rec_added) }
        else { f?.delete(); info(R.string.rec_too_short) }
    }
    override fun onCleared() {
        try { recorder?.release() } catch (e: Exception) { }
        photoImageLayers.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        (videoBitmapPool + videoOverlayBitmaps.values + videoHistory.flatMap { it.overlayBitmaps.values }).distinct().forEach { if (!it.isRecycled) it.recycle() }
        retiredVideoOverlayBitmaps.forEach { if (!it.isRecycled) it.recycle() }
        try { cloudModel.close(); cloudFeatureModel.close(); cloudSegmentationModel.close(); atmosphericSegmentationModel.close(); cloudSynopticModel.close(); temporalLightningModel.close() } catch (_: Exception) { }
        super.onCleared()
    }

    // ---- editable photo projects: source, adjustments, masks and all overlay assets remain editable ----
    class PhotoProjectInfo(val id: String, val name: String, val modified: Long)
    private val photoProjectsDir get() = File(app.filesDir, "photo-projects")
    fun listPhotoProjects(): List<PhotoProjectInfo> = photoProjectsDir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") && File(it, "project.json").exists() }
        ?.mapNotNull { d -> PhotoProjectCodec.decode(File(d, "project.json").readText())?.let { PhotoProjectInfo(d.name, it.name, File(d, "project.json").lastModified()) } }
        ?.sortedByDescending { it.modified } ?: emptyList()

    fun savePhotoProject() {
        val source = photoSrc ?: return
        if (busy) return
        busy = true
        val p = params; val texts = photoLayers.toList(); val shapes = photoShapeLayers.toList(); val images = photoImageLayers.toList()
        val masks = photoMaskStrokes.toList(); val inverted = photoMaskInverted
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val finalDir = File(photoProjectsDir, "photo_" + System.currentTimeMillis())
                    val dir = File(photoProjectsDir, ".${finalDir.name}.tmp")
                    dir.deleteRecursively(); dir.mkdirs()
                    fun saveBitmap(bitmap: Bitmap, file: File) {
                        if (bitmap.isRecycled) throw java.io.IOException("photo layer bitmap was released")
                        file.outputStream().buffered().use { stream ->
                            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw java.io.IOException("PNG encode failed")
                            stream.flush()
                        }
                    }
                    saveBitmap(source, File(dir, "source.png"))
                    val imageData = images.map { layer ->
                        val file = "layer_${layer.id}.png"; saveBitmap(layer.bitmap, File(dir, file))
                        PhotoImageData(layer.id, layer.name, file, layer.x, layer.y, layer.width, layer.opacity, layer.visible, layer.zIndex, layer.rotationDegrees)
                    }
                    val name = "Photo " + java.text.SimpleDateFormat("MM-dd HH:mm", Locale.US).format(java.util.Date())
                    val data = PhotoProjectData(name, "source.png", p,
                        texts.map { PhotoTextData(it.id, it.name, it.text, it.x, it.y, it.size, it.color, it.opacity, it.visible, it.bold, it.zIndex, it.rotationDegrees) },
                        shapes.map { PhotoShapeData(it.id, it.name, it.shape.name, it.x, it.y, it.width, it.height, it.color, it.opacity, it.strokeWidth, it.visible, it.zIndex, it.rotationDegrees) },
                        imageData, masks.map { PhotoMaskData(it.points, it.radius, it.softness) }, inverted)
                    val manifestTemp = File(dir, "project.json.tmp")
                    manifestTemp.writeText(PhotoProjectCodec.encode(data), Charsets.UTF_8)
                    if (!manifestTemp.renameTo(File(dir, "project.json"))) throw java.io.IOException("cannot finalize photo project manifest")
                    if (!dir.renameTo(finalDir)) throw java.io.IOException("cannot finalize photo project folder")
                }
                info(R.string.project_saved)
            } catch (e: Exception) { info(R.string.error_generic, e.message ?: "photo project") }
            finally { busy = false; releaseRetiredVideoImages() }
        }
    }

    fun loadPhotoProject(id: String) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val dir = File(photoProjectsDir, id)
                    val data = PhotoProjectCodec.decode(File(dir, "project.json").readText()) ?: throw java.io.IOException("invalid photo project")
                    val source = BitmapFactory.decodeFile(File(dir, data.source).absolutePath) ?: throw java.io.IOException("missing source image")
                    val images = data.images.map { item ->
                        val bmp = BitmapFactory.decodeFile(File(dir, item.file).absolutePath) ?: throw java.io.IOException("missing layer image ${item.file}")
                        item to bmp
                    }
                    Triple(data, source, images)
                }
                val (data, source, images) = loaded
                setPhoto(source)
                params = data.params
                photoLayers.clear(); photoLayers.addAll(data.texts.map { PhotoTextLayer(it.id, it.name, it.text, it.x, it.y, it.size, it.color, it.opacity, it.visible, it.bold, it.z, it.rotation) })
                photoShapeLayers.clear(); photoShapeLayers.addAll(data.shapes.map { PhotoShapeLayer(it.id, it.name,
                    runCatching { PhotoShapeLayer.Shape.valueOf(it.shape) }.getOrDefault(PhotoShapeLayer.Shape.RECTANGLE), it.x, it.y, it.width, it.height,
                    it.color, it.opacity, it.stroke, it.visible, it.z, it.rotation) })
                photoImageLayers.clear(); photoImageLayers.addAll(images.map { (item, bmp) -> PhotoImageLayer(item.id, item.name, bmp, item.x, item.y, item.width, item.opacity, item.visible, item.z, item.rotation) })
                photoMaskStrokes.clear(); photoMaskStrokes.addAll(data.masks.map { PhotoMaskStroke(it.points, it.radius, it.softness) })
                photoMaskInverted = data.maskInverted
                nextPhotoLayerId = ((photoLayers.maxOfOrNull { it.id } ?: 0L).coerceAtLeast(photoShapeLayers.maxOfOrNull { it.id } ?: 0L)
                    .coerceAtLeast(photoImageLayers.maxOfOrNull { it.id } ?: 0L)) + 1L
                histReset()
            } catch (e: Exception) { info(R.string.error_generic, e.message ?: "load photo project") }
            finally { busy = false; releaseRetiredVideoImages() }
        }
    }

    fun deletePhotoProject(id: String) { File(photoProjectsDir, id).deleteRecursively() }

    // ---- video projects (the source files are COPIED into app storage: picker URIs do not survive restarts) ----
    class ProjectInfo(val id: String, val name: String, val modified: Long)
    private val projectsDir get() = File(app.filesDir, "projects")

    fun listProjects(): List<ProjectInfo> {
        projectsDir.listFiles()?.filter { it.isDirectory && it.name.startsWith(".") && it.name.endsWith(".tmp") && System.currentTimeMillis() - it.lastModified() > 86_400_000L }
            ?.forEach { it.deleteRecursively() }
        return projectsDir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") && File(it, "project.json").exists() }?.mapNotNull { d ->
            ProjectCodec.decode(File(d, "project.json").readText())?.let { ProjectInfo(d.name, it.name, File(d, "project.json").lastModified()) }
        }?.sortedByDescending { it.modified } ?: emptyList()
    }

    private fun copyTo(u: Uri, dest: File) {
        if (u.scheme == "file") File(u.path!!).copyTo(dest, overwrite = true)
        else (app.contentResolver.openInputStream(u) ?: throw java.io.IOException("cannot open")).use { i ->
            dest.outputStream().use { o -> i.copyTo(o) }
        }
    }

    fun saveProject() {
        val main = videoUri ?: return
        if (busy) return
        busy = true; stage = R.string.saving_project; progress = 0f
        val overlayClipsSnapshot = videoOverlayClips.toList()
        val effectClipsSnapshot = videoEffectClips.toList()
        val markerSnapshot = videoMarkers.toList()
        val overlayBitmapsSnapshot = videoOverlayBitmaps.toMap()
        val fadeInMusicSnapshot = fadeInMusicMs
        val fadeOutMusicSnapshot = fadeOutMusicMs
        val fadeInVoiceSnapshot = fadeInVoiceMs
        val fadeOutVoiceSnapshot = fadeOutVoiceMs
        val muteMusicSnapshot = muteMusic
        val muteVoiceSnapshot = muteVoice
        val soloAudioTrackSnapshot = soloAudioTrack
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    projectsDir.mkdirs()
                    val finalDir = File(projectsDir, "p" + System.currentTimeMillis())
                    val dir = File(projectsDir, ".${finalDir.name}.tmp")
                    dir.deleteRecursively()
                    if (!dir.mkdirs()) throw java.io.IOException("cannot create project staging directory")
                    copyTo(main, File(dir, "main.mp4"))
                    val clips = LinkedHashMap<String, String>()
                    val segs = segments.map { sg ->
                        val c = sg.uri?.let { cu ->
                            clips.getOrPut(cu.toString()) { val n = "clip_" + (clips.size + 1) + ".mp4"; copyTo(cu, File(dir, n)); n }
                        }
                        SegData(sg.startMs, sg.endMs, c)
                    }
                    val mu = music?.let { m ->
                        val n = if (m.path?.endsWith(".wav") == true) "music.wav" else "music.audio"; copyTo(m, File(dir, n)); n
                    }
                    val vo = voice?.let { copyTo(it, File(dir, "voice.m4a")); "voice.m4a" }
                    val name = "Project " + java.text.SimpleDateFormat("MM-dd HH:mm", Locale.US).format(java.util.Date())
                    val overlayData = overlayClipsSnapshot.map { clip ->
                        val asset = overlayBitmapsSnapshot[clip.id]?.let { bitmap ->
                            val file = "overlay_${clip.id}.png"
                            File(dir, file).outputStream().use { stream -> if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw java.io.IOException("overlay PNG encode failed") }
                            file
                        } ?: clip.assetFile
                        OverlayClipData(clip.id, clip.kind, clip.content, clip.startMs, clip.endMs, clip.color, clip.size, clip.position, clip.background,
                            clip.keyframes.map { k -> OverlayKeyframeData(k.timeMs, k.alpha, k.position) }, asset, clip.locked)
                    }
                    val manifest = File(dir, "project.json.tmp")
                    manifest.writeText(ProjectCodec.encode(ProjectData(
                        name, "main.mp4", videoDur, segs, mu, vo, muteOriginal, vFilter, vSpeed, vBrightness, vContrast,
                        vRotate, vFlipH, vFlipV, vCrop, overlayText, textColor, textSize, textPos, textBg,
                        sticker, stickerSize, stickerPos, volOrig, volMusic, volVoice,
                        overlayData, effectClipsSnapshot.map { EffectClipData(it.id, it.filterIndex, it.startMs, it.endMs, it.locked) }, markerSnapshot,
                        fadeInMusicSnapshot, fadeOutMusicSnapshot, fadeInVoiceSnapshot, fadeOutVoiceSnapshot,
                        muteMusicSnapshot, muteVoiceSnapshot, soloAudioTrackSnapshot)), Charsets.UTF_8)
                    if (!manifest.renameTo(File(dir, "project.json"))) throw java.io.IOException("cannot finalize project manifest")
                    // Publish only a complete project. A crash before this point leaves a hidden staging folder.
                    if (!dir.renameTo(finalDir)) throw java.io.IOException("cannot publish completed project")
                }
                info(R.string.project_saved)
            } catch (e: Exception) {
                projectsDir.listFiles()?.filter { it.isDirectory && it.name.startsWith(".") && it.name.endsWith(".tmp") }?.forEach { it.deleteRecursively() }
                info(R.string.error_generic, e.message ?: "save")
            } finally { busy = false; releaseRetiredVideoImages() }
        }
    }

    fun loadProject(id: String) {
        try {
            val dir = File(projectsDir, id)
            val p = ProjectCodec.decode(File(dir, "project.json").readText()) ?: run { info(R.string.error_generic, "project"); return }
            loadVideo(Uri.fromFile(File(dir, p.video)))
            if (videoUri == null) return
            clearSegmentSelection()
            for (sg in p.segs) segments.add(Seg(sg.startMs, sg.endMs, sg.clip?.let { Uri.fromFile(File(dir, it)) }))
            videoMarkers.clear(); videoMarkers.addAll(p.markers)
            segReset(); segPush(); selSeg = -1
            updateMusicSource(p.music?.let { Uri.fromFile(File(dir, it)) }); musicName = null
            updateVoiceSource(p.voice?.let { Uri.fromFile(File(dir, it)) })
            muteOriginal = p.mute; vFilter = p.filter; vSpeed = p.speed; vBrightness = p.brightness; vContrast = p.contrast
            vRotate = p.rotate; vFlipH = p.flipH; vFlipV = p.flipV; vCrop = p.crop
            overlayText = p.text; textColor = p.textColor; textSize = p.textSize; textPos = p.textPos; textBg = p.textBg
            sticker = p.sticker; stickerSize = p.stickerSize; stickerPos = p.stickerPos
            videoOverlayBitmaps.values.forEach { if (busy) retiredVideoOverlayBitmaps.add(it) else if (!it.isRecycled) it.recycle() }; videoOverlayBitmaps.clear()
            videoOverlayClips.clear(); videoOverlayClips.addAll(p.overlayClips.map { item ->
                item.assetFile?.let { asset -> BitmapFactory.decodeFile(File(dir, asset).absolutePath)?.let { videoOverlayBitmaps[item.id] = it } }
                VideoOverlayClip(item.id, item.kind, item.content, item.startMs, item.endMs, item.color, item.size, item.position, item.background,
                    item.keyframes.map { k -> VideoOverlayKeyframe(k.timeMs, k.alpha, k.position) }, item.assetFile, item.locked)
            })
            nextVideoOverlayId = (videoOverlayClips.maxOfOrNull { it.id } ?: 0L) + 1L
            videoEffectClips.clear(); videoEffectClips.addAll(p.effectClips.map { VideoEffectClip(it.id, it.filterIndex, it.startMs, it.endMs, it.locked) })
            nextVideoEffectId = (videoEffectClips.maxOfOrNull { it.id } ?: 0L) + 1L
            volOrig = p.volOrig; volMusic = p.volMusic; volVoice = p.volVoice
            fadeInMusicMs = p.fadeInMusicMs; fadeOutMusicMs = p.fadeOutMusicMs
            fadeInVoiceMs = p.fadeInVoiceMs; fadeOutVoiceMs = p.fadeOutVoiceMs
            muteMusic = p.muteMusic; muteVoice = p.muteVoice
            soloAudioTrack = AudioTrackRouting.normalize(p.soloAudioTrack)
            screen = Screen.VIDEO
            resetVideoHistory()
        } catch (e: Exception) { info(R.string.error_generic, e.message ?: "project") }
    }

    fun deleteProject(id: String) { File(projectsDir, id).deleteRecursively() }

    /** Duration of an audio file in ms, or `fallback` if unreadable. */
    private fun audioDur(u: Uri, fallback: Long): Long {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(app, u)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 } ?: fallback
        } catch (e: Exception) { fallback } finally { r.release() }
    }

    private val exportPrefs by lazy { app.getSharedPreferences("video_export", 0) }
    var exportProfile by mutableStateOf(VideoExportProfile.fromId(exportPrefs.getString("profile", "SOURCE")))
        private set
    fun chooseExportProfile(profile: VideoExportProfile) {
        if (exportProfile != profile) {
            exportProfile = profile
            exportPrefs.edit().putString("profile", profile.name).apply()
            exportPreflightIssues = emptyList()
        }
    }

    var exportPreflightIssues by mutableStateOf<List<String>>(emptyList())

    /** Checks the project before expensive rendering and reports actionable issues in Persian. */
    fun runVideoExportPreflight(): Boolean {
        val issues = ArrayList<String>()
        if (videoUri == null) issues += "فایل ویدئوی اصلی انتخاب نشده است."
        if (videoDur <= 0L) issues += "مدت‌زمان ویدئو قابل تشخیص نیست؛ فایل دیگری انتخاب کنید."
        if (videoW <= 0 || videoH <= 0) issues += "ابعاد تصویر ویدئو قابل خواندن نیست."
        val sourceSegments = if (segments.isEmpty()) listOf(Seg(0L, videoDur, videoUri)) else segments.toList()
        sourceSegments.forEachIndexed { index, seg ->
            if (seg.startMs < 0L || (seg.uri == null && seg.endMs > videoDur) || seg.endMs - seg.startMs < 100L)
                issues += "بازهٔ قطعهٔ ${index + 1} نامعتبر است؛ ابتدا و انتهای برش را بررسی کنید."
            if (seg.uri != null && seg.uri.scheme == "file" && !File(seg.uri.path.orEmpty()).isFile)
                issues += "فایل منبع قطعهٔ ${index + 1} پیدا نشد."
        }
        val outputDuration = (sourceSegments.sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) } / vSpeed.coerceAtLeast(0.05f)).toLong()
        if (outputDuration <= 0L) issues += "مدت‌زمان خروجی صفر است؛ حداقل یک قطعهٔ معتبر لازم است."
        videoOverlayClips.forEach { clip ->
            if (clip.startMs < 0L || clip.endMs <= clip.startMs || clip.endMs > outputDuration)
                issues += "زمان‌بندی لایهٔ ${clip.kind} خارج از محدودهٔ ویدئو است."
            if (clip.kind == "IMAGE" && videoOverlayBitmaps[clip.id]?.isRecycled != false && clip.assetFile == null)
                issues += "تصویر یکی از لایه‌های ویدئو در دسترس نیست."
        }
        videoEffectClips.forEach { clip ->
            if (clip.startMs < 0L || clip.endMs <= clip.startMs || clip.endMs > outputDuration)
                issues += "بازهٔ یکی از افکت‌ها خارج از محدودهٔ ویدئو است."
        }
        if (app.cacheDir.usableSpace < 150L * 1024L * 1024L)
            issues += "فضای خالی حافظه کمتر از ۱۵۰ مگابایت است؛ پیش از خروجی فضا آزاد کنید."
        exportPreflightIssues = issues.distinct()
        return exportPreflightIssues.isEmpty()
    }

    /** Multi-segment trim, optional mute, optional music (mixed with or replacing original), H.264/AAC MP4. */
    fun exportVideo() {
        val u = videoUri ?: return
        if (busy) return
        if (exportPreflightIssues.isNotEmpty()) return
        busy = true; progress = 0f; stage = R.string.exporting; infoRes = null
        val overlaySnapshot = videoOverlayClips.toList()
        val effectSnapshot = videoEffectClips.toList()
        val overlayBitmapSnapshot = videoOverlayBitmaps.toMap()
        job = viewModelScope.launch {
            val out = File(app.cacheDir, "export_${System.currentTimeMillis()}.mp4")
            var tr: Transformer? = null
            try {
                val segs = if (segments.isEmpty()) listOf(Seg(0, videoDur)) else segments.toList()
                val fx = ArrayList<Effect>()
                VideoFx.cropBounds(videoW, videoH, vCrop)?.let { fx += Crop(it[0], it[1], it[2], it[3]) }
                if (vBrightness != 0f) fx += Brightness(vBrightness)
                if (vContrast != 0f) fx += Contrast(vContrast)
                if (vSpeed != 1f) fx += SpeedChangeEffect(vSpeed)
                if (vRotate != 0 || vFlipH || vFlipV) fx += ScaleAndRotateTransformation.Builder()
                    .setScale(if (vFlipH) -1f else 1f, if (vFlipV) -1f else 1f)
                    .setRotationDegrees(vRotate.toFloat()).build()
                // Split each source segment at overlay boundaries. Every generated media item
                // receives only the overlays active during that exact output-time interval.
                // Legacy single text/sticker settings remain exportable for older projects.
                val timedClips = if (overlaySnapshot.isEmpty()) buildList {
                    val legacyTotal = (segs.sumOf { (it.endMs-it.startMs).coerceAtLeast(0L) } / vSpeed.coerceAtLeast(0.05f)).toLong()
                    if (overlayText.isNotBlank() && legacyTotal > 0L) add(VideoOverlayClip(-1L, "TEXT", overlayText.trim(), 0L, legacyTotal, textColor, textSize, textPos, textBg))
                    sticker?.let { if (legacyTotal > 0L) add(VideoOverlayClip(-2L, "STICKER", it, 0L, legacyTotal, 0xFFFFFFFF.toInt(), stickerSize, stickerPos, false)) }
                } else overlaySnapshot
                val items = ArrayList<EditedMediaItem>()
                var timelineCursor = 0L
                for (segment in segs) {
                    val sourceLength = (segment.endMs - segment.startMs).coerceAtLeast(1L)
                    val outputLength = (sourceLength / vSpeed.coerceAtLeast(0.05f)).toLong().coerceAtLeast(1L)
                    val animationCuts = timedClips.flatMap { clip ->
                        buildList {
                            add(clip.startMs); add(clip.endMs)
                            if (clip.keyframes.size > 1 && (clip.keyframes.map { it.alpha }.distinct().size > 1 || clip.keyframes.map { it.position }.distinct().size > 1)) {
                                val span = (clip.endMs - clip.startMs).coerceAtLeast(1L)
                                val step = (span / 1600L).coerceAtLeast(100L)
                                var t = clip.startMs + step
                                while (t < clip.endMs) { add(t); t += step }
                                clip.keyframes.forEach { add(it.timeMs) }
                            }
                        }
                    }
                    val effectCuts = effectSnapshot.flatMap { listOf(it.startMs, it.endMs) }
                    val cuts = (listOf(0L, outputLength) + animationCuts.map { it - timelineCursor } + effectCuts.map { it - timelineCursor }
                        .filter { it > 0L && it < outputLength }).distinct().sorted()
                    for (j in 0 until cuts.lastIndex) {
                        val outA = cuts[j]; val outB = cuts[j + 1]
                        if (outB <= outA) continue
                        val sourceA = (segment.startMs + outA * vSpeed).toLong().coerceAtMost(segment.endMs - 1L)
                        val sourceB = (segment.startMs + outB * vSpeed).toLong().coerceIn(sourceA + 1L, segment.endMs)
                        val active = timedClips.filter { it.startMs < timelineCursor + outB && it.endMs > timelineCursor + outA }
                        val itemFx = ArrayList<Effect>(); itemFx.addAll(fx)
                        val activeEffects = effectSnapshot.filter { it.startMs < timelineCursor + outB && it.endMs > timelineCursor + outA }
                        if (activeEffects.isEmpty()) itemFx.addAll(VideoFx.filterEffects(vFilter))
                        else activeEffects.sortedBy { it.id }.forEach { effect -> itemFx.addAll(VideoFx.filterEffects(effect.filterIndex)) }
                        val overlays: List<TextureOverlay> = active.mapNotNull { clip ->
                            val state = VideoFx.overlayStateAt(clip, timelineCursor + (outA + outB) / 2L)
                            if (clip.kind == "IMAGE") {
                                overlayBitmapSnapshot[clip.id]?.takeUnless { it.isRecycled }?.let { StaticBitmapOverlay(it, state.position, state.alpha) }
                            } else {
                                val styled = if (clip.kind == "STICKER") VideoFx.styled(clip.content, clip.color, clip.size, state.position, false, 2.2f, state.alpha)
                                    else VideoFx.styled(clip.content, clip.color, clip.size, state.position, clip.background, alpha = state.alpha)
                                TextOverlay.createStaticTextOverlay(styled)
                            }
                        }
                        if (overlays.isNotEmpty()) itemFx += OverlayEffect(ImmutableList.copyOf(overlays))
                        val exportHeight = exportProfile.effectiveHeight(videoW, videoH)
                        if (exportHeight < videoH) itemFx += Presentation.createForHeight(exportHeight)
                        val mi = MediaItem.Builder().setUri(segment.uri ?: u).setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder().setStartPositionMs(sourceA).setEndPositionMs(sourceB).build()).build()
                        items += EditedMediaItem.Builder(mi).setRemoveAudio(muteOriginal || !AudioTrackRouting.include(AudioTrackRouting.ORIGINAL, false, soloAudioTrack) || vSpeed != 1f)
                            .setEffects(Effects(if (volOrig != 1f) listOf<AudioProcessor>(VolumeProcessor(volOrig)) else emptyList(), itemFx)).build()
                    }
                    timelineCursor += outputLength
                }
                val seqs = ArrayList<EditedMediaItemSequence>()
                seqs += EditedMediaItemSequence(items)
                val total = (segs.sumOf { it.endMs - it.startMs } / vSpeed).toLong()
                val audioTracks = listOfNotNull(
                    music?.takeIf { AudioTrackRouting.include(AudioTrackRouting.MUSIC, muteMusic, soloAudioTrack) }
                        ?.let { Triple(it, volMusic, fadeInMusicMs to fadeOutMusicMs) },
                    voice?.takeIf { AudioTrackRouting.include(AudioTrackRouting.VOICE, muteVoice, soloAudioTrack) }
                        ?.let { Triple(it, volVoice, fadeInVoiceMs to fadeOutVoiceMs) }
                )
                for ((au, vol, fades) in audioTracks) {
                    val end = min(total, audioDur(au, total)).coerceAtLeast(1L)
                    val mi = MediaItem.Builder().setUri(au).setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder().setStartPositionMs(0).setEndPositionMs(end).build()).build()
                    val needsEnvelope = vol != 1f || fades.first > 0L || fades.second > 0L
                    val processors = if (needsEnvelope) listOf<AudioProcessor>(
                        FadeVolumeProcessor(vol, end, fades.first.coerceAtMost(end), fades.second.coerceAtMost(end))
                    ) else emptyList()
                    seqs += EditedMediaItemSequence(listOf(EditedMediaItem.Builder(mi).setRemoveVideo(true)
                        .setEffects(Effects(processors, emptyList())).build()))
                }
                val done = CompletableDeferred<Throwable?>()
                val requestedVideoSettings = VideoEncoderSettings.Builder()
                    .setBitrate(exportProfile.targetBitrate(videoW, videoH))
                    .build()
                val encoderFactory = DefaultEncoderFactory.Builder(app)
                    .setRequestedVideoEncoderSettings(requestedVideoSettings)
                    .build()
                val t = Transformer.Builder(app)
                    .setEncoderFactory(encoderFactory)
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
            } finally { out.delete(); busy = false; releaseRetiredVideoImages() }
        }
    }
}
