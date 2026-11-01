package com.lightninglab

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.text.SpannableString
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.Presentation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import com.google.common.collect.ImmutableList
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Effects
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
import kotlin.math.min

enum class Screen { HOME, RESULTS, ABOUT, PHOTO, VIDEO, WEATHER, PREPARE }
/** order = 0: not selected; 1 = base background; 2.. = overlays in the order the user tapped. */
data class Item(val event: LightningEvent, val thumb: Bitmap, val order: Int = 0)
data class Seg(val startMs: Long, val endMs: Long, val uri: Uri? = null)

class MainVM(private val app: Application) : AndroidViewModel(app) {
    var screen by mutableStateOf(Screen.HOME)
    var uri by mutableStateOf<Uri?>(null)
    var busy by mutableStateOf(false)
    var progress by mutableStateOf(0f)
    var stage by mutableStateOf(R.string.stage_scan)
    var sensitivity by mutableStateOf(0.7f)
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
    var muteOriginal by mutableStateOf(false)
    var vBrightness by mutableStateOf(0f)
    var vContrast by mutableStateOf(0f)
    var vRotate by mutableStateOf(0)
    var volOrig by mutableStateOf(1f)
    var volMusic by mutableStateOf(1f)
    var volVoice by mutableStateOf(1f)
    var videoW by mutableStateOf(0)
    var videoH by mutableStateOf(0)
    var vFilter by mutableStateOf(0)
    var vFlipH by mutableStateOf(false)
    var vFlipV by mutableStateOf(false)
    var vCrop by mutableStateOf(0f)              // 0 = original, else target width/height
    var selSeg by mutableStateOf(-1)

    // ---- timeline (all times on the OUTPUT timeline, see Timeline.kt) ----
    var playheadMs by mutableStateOf(0L)
    val textClips = mutableStateListOf<TextClip>()
    val stickerClips = mutableStateListOf<StickerClip>()
    val audioClips = mutableStateListOf<AudioClip>()
    val zoomKeys = mutableStateListOf<ZoomKey>()
    /** What is selected on the timeline: 0 none, 1 text, 2 sticker, 3 audio, 4 zoom marker. */
    var selKind by mutableStateOf(0)
    var selId by mutableStateOf(-1)
    var exportHeight by mutableStateOf(0)          // 0 = keep source size, else 360/480/720/1080/2160
    var exportQuality by mutableStateOf(1)         // 0 low size, 1 balanced, 2 high
    val fonts = mutableStateListOf<String>().also { l -> File(app.filesDir, "fonts").listFiles()?.sortedBy { it.name }?.forEach { l += it.absolutePath } }
    private var nextId = 1
    fun newId() = nextId++
    /** Copies a user-chosen .ttf/.otf into app storage and makes it available to text clips. */
    fun importFont(u: Uri) {
        viewModelScope.launch {
            try {
                val f = withContext(Dispatchers.IO) {
                    val dir = File(app.filesDir, "fonts").also { it.mkdirs() }
                    val name = (app.contentResolver.query(u, null, null, null, null)?.use { c ->
                        val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if (c.moveToFirst() && i >= 0) c.getString(i) else null
                    } ?: ("font_" + System.currentTimeMillis() + ".ttf")).replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val out = File(dir, name)
                    (app.contentResolver.openInputStream(u) ?: throw java.io.IOException("cannot open")).use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                    android.graphics.Typeface.createFromFile(out)   // throws if the file is not a font
                    out.absolutePath
                }
                if (f !in fonts) fonts += f
                val sel = textClips.firstOrNull { selKind == 1 && it.id == selId }
                if (sel != null) updateText(sel.id) { it.copy(font = f) }
                info(R.string.ve_font_added)
            } catch (e: Exception) { info(R.string.ve_font_bad) }
        }
    }
    /** Length of the output timeline in ms (trimmed/joined source divided by speed). */
    val totalMs: Long get() = (((if (segments.isEmpty()) videoDur else segments.sumOf { it.endMs - it.startMs }) / vSpeed)).toLong()
    fun select(kind: Int, id: Int) { selKind = kind; selId = id }
    private fun clampHere(len: Long): LongArray {
        val start = playheadMs.coerceAtMost(max(0L, totalMs - TimelineMath.MIN_CLIP_MS))
        return TimelineMath.clampRange(start, start + len, totalMs)
    }
    fun addText(text: String) {
        val r = clampHere(3000)
        val t = TextClip(newId(), text, 0xFFFFFFFF.toInt(), 1, 2, false, r[0], r[1], fonts.lastOrNull())
        textClips += t; select(1, t.id)
    }
    fun addSticker(emoji: String) {
        val r = clampHere(3000)
        val t = StickerClip(newId(), emoji, 2, 1, r[0], r[1]); stickerClips += t; select(2, t.id)
    }
    fun addAudio(u: Uri, label: String, kind: Int, atMs: Long = playheadMs) {
        viewModelScope.launch {
            val d = withContext(Dispatchers.IO) { audioDur(u, 0L) }
            if (d <= 0L) { info(R.string.error_generic, "audio"); return@launch }
            val c = AudioClip(newId(), u.toString(), label, kind, atMs.coerceIn(0L, max(0L, totalMs - 200L)), d)
            audioClips += c; select(3, c.id)
        }
    }
    fun addZoom(scale: Float) {
        val t = playheadMs.coerceIn(0L, totalMs)
        zoomKeys.removeAll { kotlin.math.abs(it.timeMs - t) < 100 }
        val k = ZoomKey(newId(), t, scale); zoomKeys += k; select(4, k.id)
    }
    fun updateText(id: Int, f: (TextClip) -> TextClip) { val i = textClips.indexOfFirst { it.id == id }; if (i >= 0) textClips[i] = f(textClips[i]) }
    fun updateSticker(id: Int, f: (StickerClip) -> StickerClip) { val i = stickerClips.indexOfFirst { it.id == id }; if (i >= 0) stickerClips[i] = f(stickerClips[i]) }
    fun updateAudio(id: Int, f: (AudioClip) -> AudioClip) { val i = audioClips.indexOfFirst { it.id == id }; if (i >= 0) audioClips[i] = f(audioClips[i]) }
    fun updateZoom(id: Int, f: (ZoomKey) -> ZoomKey) { val i = zoomKeys.indexOfFirst { it.id == id }; if (i >= 0) zoomKeys[i] = f(zoomKeys[i]) }
    fun deleteSelected() {
        when (selKind) {
            1 -> textClips.removeAll { it.id == selId }
            2 -> stickerClips.removeAll { it.id == selId }
            3 -> audioClips.removeAll { it.id == selId }
            4 -> zoomKeys.removeAll { it.id == selId }
        }
        selKind = 0; selId = -1
    }
    /** Moves the selected clip (text/sticker/audio) by dMs on the timeline; zoom markers move too. */
    fun moveSelected(dMs: Long) {
        when (selKind) {
            1 -> updateText(selId) { val r = TimelineMath.clampRange(it.startMs + dMs, it.endMs + dMs, totalMs); it.copy(startMs = r[0], endMs = r[1]) }
            2 -> updateSticker(selId) { val r = TimelineMath.clampRange(it.startMs + dMs, it.endMs + dMs, totalMs); it.copy(startMs = r[0], endMs = r[1]) }
            3 -> updateAudio(selId) { it.copy(startMs = (it.startMs + dMs).coerceIn(0L, max(0L, totalMs - 200L))) }
            4 -> updateZoom(selId) { it.copy(timeMs = (it.timeMs + dMs).coerceIn(0L, totalMs)) }
        }
    }
    /** Changes the length of the selected text/sticker clip (audio keeps its natural length). */
    fun resizeSelected(dMs: Long) {
        when (selKind) {
            1 -> updateText(selId) { val r = TimelineMath.clampRange(it.startMs, it.endMs + dMs, totalMs); it.copy(startMs = r[0], endMs = r[1]) }
            2 -> updateSticker(selId) { val r = TimelineMath.clampRange(it.startMs, it.endMs + dMs, totalMs); it.copy(startMs = r[0], endMs = r[1]) }
        }
    }
    /** Splits the segment under the playhead in two. */
    fun splitAtPlayhead() {
        val segs = if (segments.isEmpty()) listOf(Seg(0, videoDur)) else segments.toList()
        val (i, off) = TimelineMath.locate(segs.map { it.endMs - it.startMs }, vSpeed, playheadMs)
        val sg = segs.getOrNull(i) ?: return
        val pr = TimelineMath.split(sg.startMs, sg.endMs, sg.startMs + off) ?: run { info(R.string.split_here_impossible); return }
        if (segments.isEmpty()) segments += Seg(0, videoDur)
        segments[i] = Seg(pr.first[0], pr.first[1], sg.uri)
        segments.add(i + 1, Seg(pr.second[0], pr.second[1], sg.uri))
        segPush(); selSeg = i + 1
    }
    /** Trims the selected segment's source range by (dStart, dEnd) ms. */
    fun trimSelected(dStart: Long, dEnd: Long) {
        val i = selSeg; val sg = segments.getOrNull(i) ?: return
        val maxEnd = if (sg.uri == null) videoDur else Long.MAX_VALUE
        val ns = (sg.startMs + dStart).coerceAtLeast(0L); val ne = (sg.endMs + dEnd).coerceAtMost(maxEnd)
        if (ne - ns < TimelineMath.MIN_CLIP_MS) return
        segments[i] = sg.copy(startMs = ns, endMs = ne); segPush()
    }
    fun duplicateSeg(i: Int) {
        if (i !in segments.indices) return
        segments.add(i + 1, segments[i].copy()); segPush(); selSeg = i + 1
    }
    var vSpeed by mutableStateOf(1f)

    /** Renders a built-in effect to a WAV in the cache and uses it as the added audio track. */
    fun useFx(k: SoundFx.Kind, nameRes: Int) {
        viewModelScope.launch {
            try {
                val f = withContext(Dispatchers.Default) {
                    val file = File(app.cacheDir, "fx_${k.name.lowercase()}.wav")
                    if (!file.exists()) SoundFx.writeWav(file, SoundFx.render(k))
                    file
                }
                addAudio(Uri.fromFile(f), app.getString(nameRes), 1)
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
            } catch (e: Throwable) { info(R.string.error_generic, e.message ?: "heal") } finally { busy = false }
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
            } catch (e: Throwable) { info(R.string.error_generic, e.message ?: "infographic") } finally { busy = false }
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
    fun addSeg(sg: Seg) { segments += sg; segPush() }
    fun removeSeg(i: Int) { if (i in segments.indices) { segments.removeAt(i); segPush() } }
    fun clearSegs() { if (segments.isNotEmpty()) { segments.clear(); segPush() } }
    fun undoSeg() { if (segHi > 0) { segHi--; segRestore() } }
    fun redoSeg() { if (segHi < segHist.size - 1) { segHi++; segRestore() } }

    // ---- optional TFLite cloud model + observer tags ----
    private val cloudModel by lazy { CloudClassifier(File(app.filesDir, "cloud_model.tflite")) }
    var modelReady by mutableStateOf(false)
    var topK by mutableStateOf<List<Pair<String, Float>>>(emptyList())
    var genus by mutableStateOf<String?>(null)
    val features = mutableStateListOf<String>()
    fun initModel() { viewModelScope.launch { modelReady = withContext(Dispatchers.IO) { cloudModel.load() } } }
    fun importModel(u: Uri) {
        viewModelScope.launch {
            val previousReady = modelReady
            val result = withContext(Dispatchers.IO) {
                val target = File(app.filesDir, "cloud_model.tflite")
                val temp = File(app.filesDir, "cloud_model.importing")
                val backup = File(app.filesDir, "cloud_model.previous")
                var oldModelClosed = false
                try {
                    temp.delete()
                    val input = app.contentResolver.openInputStream(u) ?: throw java.io.IOException("Cannot read selected model")
                    input.use { src -> temp.outputStream().use { dst -> src.copyTo(dst) } }
                    // Validate the candidate before touching the currently working model.
                    val candidate = CloudClassifier(temp)
                    val valid = candidate.load()
                    candidate.close()
                    if (!valid) throw java.io.IOException("Model tensor contract is incompatible")

                    cloudModel.close()
                    oldModelClosed = true
                    backup.delete()
                    if (target.exists() && !target.renameTo(backup)) throw java.io.IOException("Cannot preserve previous model")
                    if (!temp.renameTo(target)) throw java.io.IOException("Cannot activate imported model")
                    if (!cloudModel.load()) throw java.io.IOException("Imported model failed final validation")
                    backup.delete()
                    Pair(true, true)
                } catch (_: Throwable) {
                    temp.delete()
                    if (target.exists() && backup.exists()) target.delete()
                    if (!target.exists() && backup.exists()) backup.renameTo(target)
                    val restored = if (oldModelClosed && target.exists()) cloudModel.load() else previousReady
                    Pair(false, restored)
                }
            }
            modelReady = result.second
            if (result.first) info(R.string.model_loaded) else info(R.string.model_invalid)
        }
    }
    fun toggleFeature(f: String) { if (f in features) features.remove(f) else features.add(f) }

    var wxBitmap by mutableStateOf<Bitmap?>(null)
    var wx by mutableStateOf<CloudAnalysis.Result?>(null)
    fun analyzeSky(u: Uri) {
        viewModelScope.launch {
            try {
                val (bmp, res) = withContext(Dispatchers.Default) {
                    val b = ImageDecoder.decodeBitmap(ImageDecoder.createSource(app.contentResolver, u)) { dec, inf, _ ->
                        dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        var k = 1; var m = max(inf.size.width, inf.size.height)
                        while (m / k > 1024) k *= 2
                        dec.setTargetSampleSize(k)
                    }
                    val px = IntArray(b.width * b.height); b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
                    b to CloudAnalysis.analyze(px, b.width, b.height)
                }
                val ranked = withContext(Dispatchers.Default) { cloudModel.classify(bmp) }
                topK = ranked?.take(3) ?: emptyList(); genus = ranked?.firstOrNull()?.first
                features.clear()
                wxBitmap = bmp; wx = res; infoRes = null
            } catch (e: Exception) { info(R.string.error_generic, e.message ?: "image") }
        }
    }

    private fun info(res: Int, vararg a: Any) { infoRes = res; infoArgs = a.toList() }

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
        job?.cancel(); uri = u; items.clear(); composite = null; infoRes = null
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
        // Keep the actual ranked indices. The previous implementation ranked indices but
        // then wrote to items[rank], which selected the first N cards instead of the best N.
        val rankedIndices = items.indices
            .sortedWith(compareByDescending<Int> { items[it].event.score }
                .thenBy { items[it].event.bestMs })
            .take(6)
        rankedIndices.forEachIndexed { rank, itemIndex ->
            items[itemIndex] = items[itemIndex].copy(order = rank + 1)
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
            val vw = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val vh = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { videoW = vh; videoH = vw } else { videoW = vw; videoH = vh }
            videoUri = u; segments.clear(); segReset(); selSeg = -1; infoRes = null
            segments += Seg(0, videoDur); segPush(); selSeg = 0
            textClips.clear(); stickerClips.clear(); audioClips.clear(); zoomKeys.clear(); selKind = 0; selId = -1; playheadMs = 0L
        } catch (e: Exception) { info(R.string.error_generic, e.message ?: "video") } finally { r.release() }
    }

    // ---- voice-over recording ----
    var recording by mutableStateOf(false)
    private var recorder: MediaRecorder? = null
    private var recFile: File? = null
    private var recStartMs = 0L
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
            recorder = r; recFile = f; recording = true; recStartMs = playheadMs
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
        if (ok && f != null && f.exists() && f.length() > 1000) { addAudio(Uri.fromFile(f), app.getString(R.string.rec_label), 2, recStartMs); info(R.string.rec_added) }
        else { f?.delete(); info(R.string.rec_too_short) }
    }
    override fun onCleared() {
        try { recorder?.release() } catch (e: Exception) { }
        super.onCleared()
    }

    // ---- video projects (the source files are COPIED into app storage: picker URIs do not survive restarts) ----
    class ProjectInfo(val id: String, val name: String, val modified: Long)
    private val projectsDir get() = File(app.filesDir, "projects")

    fun listProjects(): List<ProjectInfo> =
        projectsDir.listFiles()?.filter { it.isDirectory && File(it, "project.json").exists() }?.mapNotNull { d ->
            ProjectCodec.decode(File(d, "project.json").readText())?.let { ProjectInfo(d.name, it.name, File(d, "project.json").lastModified()) }
        }?.sortedByDescending { it.modified } ?: emptyList()

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
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val dir = File(projectsDir, "p" + System.currentTimeMillis()); dir.mkdirs()
                    copyTo(main, File(dir, "main.mp4"))
                    val clips = LinkedHashMap<String, String>()
                    val segs = segments.map { sg ->
                        val c = sg.uri?.let { cu ->
                            clips.getOrPut(cu.toString()) { val n = "clip_" + (clips.size + 1) + ".mp4"; copyTo(cu, File(dir, n)); n }
                        }
                        SegData(sg.startMs, sg.endMs, c)
                    }
                    val audios = audioClips.map { c ->
                        val n = "audio_" + c.id + ".bin"; copyTo(Uri.parse(c.uri), File(dir, n)); c.copy(uri = n)
                    }
                    val name = "Project " + java.text.SimpleDateFormat("MM-dd HH:mm", Locale.US).format(java.util.Date())
                    File(dir, "project.json").writeText(ProjectCodec.encode(ProjectData(
                        name, "main.mp4", videoDur, segs, null, null, muteOriginal, vFilter, vSpeed, vBrightness, vContrast,
                        vRotate, vFlipH, vFlipV, vCrop, "", 0xFFFFFFFF.toInt(), 1, 1, false,
                        null, 1, 1, volOrig, volMusic, volVoice,
                        textClips.toList(), stickerClips.toList(), audios, zoomKeys.toList())))
                }
                info(R.string.project_saved)
            } catch (e: Exception) { info(R.string.error_generic, e.message ?: "save") } finally { busy = false }
        }
    }

    fun loadProject(id: String) {
        try {
            val dir = File(projectsDir, id)
            val p = ProjectCodec.decode(File(dir, "project.json").readText()) ?: run { info(R.string.error_generic, "project"); return }
            loadVideo(Uri.fromFile(File(dir, p.video)))
            if (videoUri == null) return
            if (p.segs.isNotEmpty()) segments.clear()
            for (sg in p.segs) segments.add(Seg(sg.startMs, sg.endMs, sg.clip?.let { Uri.fromFile(File(dir, it)) }))
            segReset(); segPush(); selSeg = -1
            muteOriginal = p.mute; vFilter = p.filter; vSpeed = p.speed; vBrightness = p.brightness; vContrast = p.contrast
            vRotate = p.rotate; vFlipH = p.flipH; vFlipV = p.flipV; vCrop = p.crop
            volOrig = p.volOrig; volMusic = p.volMusic; volVoice = p.volVoice
            textClips.addAll(p.texts); stickerClips.addAll(p.stickers); zoomKeys.addAll(p.zooms)
            audioClips.addAll(p.audios.map { it.copy(uri = Uri.fromFile(File(dir, it.uri)).toString()) })
            nextId = (textClips.map { it.id } + stickerClips.map { it.id } + audioClips.map { it.id } + zoomKeys.map { it.id }).maxOrNull()?.plus(1) ?: 1
            // projects saved before the timeline existed: one whole-video text/sticker, music and voice track
            if (p.texts.isEmpty() && p.text.isNotBlank()) textClips += TextClip(newId(), p.text, p.textColor, p.textSize, p.textPos, p.textBg, 0L, max(500L, totalMs))
            if (p.stickers.isEmpty() && p.sticker != null) stickerClips += StickerClip(newId(), p.sticker, p.stickerSize, p.stickerPos, 0L, max(500L, totalMs))
            p.music?.let { addAudio(Uri.fromFile(File(dir, it)), app.getString(R.string.tool_music), 0, 0L) }
            p.voice?.let { addAudio(Uri.fromFile(File(dir, it)), app.getString(R.string.rec_label), 2, 0L) }
            screen = Screen.VIDEO
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

    /** Exports the timeline: trimmed/joined segments, colour/crop/speed, timed text+stickers, zoom markers, timed audio clips. */
    fun exportVideo() {
        val u = videoUri ?: return
        if (busy) return
        busy = true; progress = 0f; stage = R.string.exporting; infoRes = null
        job = viewModelScope.launch {
            val out = File(app.cacheDir, "export_${System.currentTimeMillis()}.mp4")
            var tr: Transformer? = null
            try {
                val segs = if (segments.isEmpty()) listOf(Seg(0, videoDur)) else segments.toList()
                val fx = ArrayList<Effect>()
                VideoFx.cropBounds(videoW, videoH, vCrop)?.let { fx += Crop(it[0], it[1], it[2], it[3]) }
                fx += VideoFx.filterEffects(vFilter)
                if (vBrightness != 0f) fx += Brightness(vBrightness)
                if (vContrast != 0f) fx += Contrast(vContrast)
                if (vSpeed != 1f) fx += SpeedChangeEffect(vSpeed)
                if (vRotate != 0 || vFlipH || vFlipV) fx += ScaleAndRotateTransformation.Builder()
                    .setScale(if (vFlipH) -1f else 1f, if (vFlipV) -1f else 1f)
                    .setRotationDegrees(vRotate.toFloat()).build()
                val items = segs.map { sg ->
                    val mi = MediaItem.Builder().setUri(sg.uri ?: u).setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(sg.startMs).setEndPositionMs(sg.endMs).build()).build()
                    EditedMediaItem.Builder(mi).setRemoveAudio(muteOriginal || vSpeed != 1f)
                        .setEffects(Effects(if (volOrig != 1f) listOf<AudioProcessor>(VolumeProcessor(volOrig)) else emptyList(), fx)).build()
                }
                val total = (segs.sumOf { it.endMs - it.startMs } / vSpeed).toLong()
                val seqs = ArrayList<EditedMediaItemSequence>()
                seqs += EditedMediaItemSequence(items)
                // audio clips: each is its own sequence, delayed by a gap
                for (c in audioClips) {
                    if (c.startMs >= total - 100) continue
                    val len = min(c.durMs, total - c.startMs)
                    val mi = MediaItem.Builder().setUri(Uri.parse(c.uri)).setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder().setStartPositionMs(0).setEndPositionMs(len).build()).build()
                    val vol = c.vol * (if (c.kind == 2) volVoice else volMusic)
                    val ei = EditedMediaItem.Builder(mi).setRemoveVideo(true)
                        .setEffects(Effects(if (vol != 1f) listOf<AudioProcessor>(VolumeProcessor(vol)) else emptyList(), emptyList())).build()
                    // Media3 1.3.1 has no sequence gaps: delay the clip with a generated silent WAV (44.1 kHz mono, 16-bit)
                    val items2 = ArrayList<EditedMediaItem>()
                    if (c.startMs > 0) {
                        val sil = File(app.cacheDir, "silence_${c.startMs}.wav")
                        if (!sil.exists()) SoundFx.writeWav(sil, ShortArray((c.startMs * SoundFx.RATE / 1000).toInt()))
                        items2 += EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(sil))).setRemoveVideo(true).build()
                    }
                    items2 += ei
                    seqs += EditedMediaItemSequence(items2)
                }
                // composition-level effects run on the OUTPUT timeline: zoom markers, then timed text/stickers on top
                val cfx = ArrayList<Effect>()
                if (zoomKeys.isNotEmpty()) {
                    val keys = zoomKeys.toList()
                    cfx += MatrixTransformation { t ->
                        val z = TimelineMath.zoomAt(keys, t / 1000)
                        android.graphics.Matrix().also { it.postScale(z, z) }
                    }
                }
                val (rw, rh) = if (vRotate % 180 != 0) videoH to videoW else videoW to videoH
                val ow = if (rw > 0) rw else 1080; val oh = if (rh > 0) rh else 1920
                val ov = ArrayList<TextureOverlay>()
                for (t in textClips) {
                    if (t.text.isBlank()) continue
                    val tf = t.font?.let { try { android.graphics.Typeface.createFromFile(it) } catch (e: Exception) { null } }
                    ov += TimedOverlay(OverlayRender.render(t.text, t.color, ow, oh, t.size, t.bg, true, tf), t.startMs * 1000, t.endMs * 1000, t.pos)
                }
                for (k in stickerClips) {
                    ov += TimedOverlay(OverlayRender.render(k.emoji, 0xFFFFFFFF.toInt(), ow, oh, k.size, false, false, null, 2f), k.startMs * 1000, k.endMs * 1000, k.pos)
                }
                if (ov.isNotEmpty()) cfx += OverlayEffect(ImmutableList.copyOf(ov))
                if (exportHeight > 0) cfx += Presentation.createForHeight(exportHeight)
                val done = CompletableDeferred<Throwable?>()
                val t = Transformer.Builder(app)
                    .setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(androidx.media3.transformer.DefaultEncoderFactory.Builder(app)
                        .setRequestedVideoEncoderSettings(
                            androidx.media3.transformer.VideoEncoderSettings.Builder()
                                .setBitrateMode(android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                                .also { b -> when (exportQuality) { 0 -> b.setBitrate(2_000_000); 2 -> b.setBitrate(16_000_000); else -> b.setBitrate(8_000_000) } }
                                .build())
                        .build())
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) { done.complete(null) }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            done.complete(exportException)
                        }
                    }).build()
                tr = t
                t.start(Composition.Builder(seqs).setEffects(Effects(emptyList(), cfx)).build(), out.absolutePath)
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
