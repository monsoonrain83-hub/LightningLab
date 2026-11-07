package com.lightninglab

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private enum class VTab(val label: Int) {
    MEDIA(R.string.ve_tab_media), EDIT(R.string.ve_tab_edit), AUDIO(R.string.ve_tab_audio), TEXT(R.string.ve_tab_text),
    EFFECTS(R.string.ve_tab_effects), FILTERS(R.string.ve_tab_filters), AI(R.string.ve_tab_ai),
    TEMPLATES(R.string.ve_tab_templates), EXPORT(R.string.ve_tab_export)
}

private val tColors = listOf(0xFFFFFFFF.toInt(), 0xFFFFD43B.toInt(), 0xFF6FB4FF.toInt(), 0xFF000000.toInt(), 0xFFFF5252.toInt(), 0xFF7CFC00.toInt())
private val emojis = listOf("⚡", "☁", "🌧", "⛈", "🌪", "❄", "🌈", "☀", "🌙", "⭐", "🔥", "💧", "👍", "❤", "😮", "📍")
private val cTrack = Color(0xFF7E57C2); private val cFx = Color(0xFFD1558A); private val cVoice = Color(0xFFE0584F)
private val cText = Color(0xFF8DA85A); private val cSticker = Color(0xFFC9B23A); private val cVideo = Color(0xFF3B6EA8); private val cZoom = Color(0xFF4DD0E1)

@Composable
private fun Chips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, l -> FilterChip(selected == i, { onSelect(i) }, { Text(l) }) }
    }
}

/** label  [-1s][-0.1s]  value  [+0.1s][+1s] */
@Composable
private fun Stepper(label: String, value: String, onStep: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Storm.muted, modifier = Modifier.width(64.dp))
        TextButton(onClick = { onStep(-1000) }, contentPadding = PaddingValues(6.dp), modifier = Modifier.defaultMinSize(minWidth = 40.dp)) { Text("−1s") }
        TextButton(onClick = { onStep(-100) }, contentPadding = PaddingValues(6.dp), modifier = Modifier.defaultMinSize(minWidth = 40.dp)) { Text("−.1") }
        Text(value, style = MaterialTheme.typography.labelLarge, color = Storm.bolt, modifier = Modifier.widthIn(min = 52.dp), textAlign = TextAlign.Center)
        TextButton(onClick = { onStep(100) }, contentPadding = PaddingValues(6.dp), modifier = Modifier.defaultMinSize(minWidth = 40.dp)) { Text("+.1") }
        TextButton(onClick = { onStep(1000) }, contentPadding = PaddingValues(6.dp), modifier = Modifier.defaultMinSize(minWidth = 40.dp)) { Text("+1s") }
    }
}

private fun previewMatrix(vm: MainVM): ColorMatrixColorFilter {
    val f = VideoFx.filters.getOrNull(vm.vFilter) ?: VideoFx.filters[0]
    val k = (1f + f.contrast) * (1f + vm.vContrast); val off = 255f * (0.5f * (1f - k) + vm.vBrightness)
    val m = ColorMatrix(floatArrayOf(k * f.r, 0f, 0f, 0f, off, 0f, k * f.g, 0f, 0f, off, 0f, 0f, k * f.b, 0f, off, 0f, 0f, 0f, 1f, 0f))
    return ColorMatrixColorFilter(m)
}

private class Pill(val kind: Int, val id: Int, val startMs: Long, val lenMs: Long, val label: String, val color: Color)

/** Greedy lane assignment so overlapping clips are drawn on separate rows. */
private fun lanes(p: List<Pill>): List<List<Pill>> {
    val out = ArrayList<MutableList<Pill>>()
    for (c in p.sortedBy { it.startMs }) {
        val lane = out.firstOrNull { l -> l.last().startMs + l.last().lenMs <= c.startMs } ?: ArrayList<Pill>().also { out += it }
        lane += c
    }
    return out
}

@Composable
fun VideoEditor(vm: MainVM) {
    val ctx = LocalContext.current
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u -> if (u != null) vm.loadVideo(u) }
    val pickClip = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u -> if (u != null) vm.addClip(u) }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) vm.addAudio(u, ctx.getString(R.string.tool_music), 0)
    }
    val pickFont = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> if (u != null) vm.importFont(u) }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g -> if (g) vm.startRecording() else vm.micDenied() }
    var showProjects by remember { mutableStateOf(false) }
    if (showProjects) ProjectsDialog(vm) { showProjects = false }
    var tab by remember { mutableStateOf(VTab.MEDIA) }
    var tool by remember { mutableStateOf(0) }
    val pickReq = { pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.nav_video), onBack = { vm.screen = Screen.HOME }) {
            TextButton(onClick = { showProjects = true }) { Text(stringResource(R.string.projects_title)) }
            IconAction(Ic.VIDEO, stringResource(R.string.pick_video), true) { pickReq() }
        }
        val u = vm.videoUri
        if (u == null) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                AppIcon(Ic.VIDEO, Storm.muted, Modifier.size(64.dp)); Spacer(Modifier.height(16.dp))
                Button(onClick = { pickReq() }, modifier = Modifier.heightIn(min = 52.dp)) { Text(stringResource(R.string.pick_video)) }
            }
            return@Column
        }

        // ---------------- player (preview of the trimmed/joined timeline) ----------------
        val player = remember(u) { ExoPlayer.Builder(ctx).build() }
        var playing by remember { mutableStateOf(false) }
        DisposableEffect(player) { onDispose { player.release() } }
        val segsKey = vm.segments.map { Triple(it.startMs, it.endMs, it.uri) }
        val segMs = (if (vm.segments.isEmpty()) listOf(vm.videoDur) else vm.segments.map { it.endMs - it.startMs })
        fun seekOut(ms: Long) {
            val (i, off) = TimelineMath.locate(segMs, vm.vSpeed, ms.coerceIn(0L, vm.totalMs))
            if (i < player.mediaItemCount) player.seekTo(i, off)
        }
        LaunchedEffect(segsKey, u) {
            val src = if (vm.segments.isEmpty()) listOf(Seg(0, vm.videoDur)) else vm.segments.toList()
            val was = player.isPlaying
            player.setMediaItems(src.map { sg ->
                MediaItem.Builder().setUri(sg.uri ?: u).setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setStartPositionMs(sg.startMs).setEndPositionMs(sg.endMs).build()).build()
            })
            player.prepare(); seekOut(vm.playheadMs); player.playWhenReady = was
        }
        LaunchedEffect(vm.vSpeed) { player.setPlaybackSpeed(vm.vSpeed) }
        LaunchedEffect(vm.muteOriginal, vm.volOrig) { player.volume = if (vm.muteOriginal) 0f else min(1f, vm.volOrig) }
        LaunchedEffect(player) {
            while (true) {
                playing = player.isPlaying
                if (player.isPlaying) vm.playheadMs = TimelineMath.outPosition(segMs, vm.vSpeed, player.currentMediaItemIndex, player.currentPosition)
                else if (player.playbackState == androidx.media3.common.Player.STATE_ENDED && vm.playheadMs < vm.totalMs - 50) vm.playheadMs = vm.totalMs
                delay(33)
            }
        }
        // added audio clips are previewed by one small player each
        val audioPlayers = remember { HashMap<Int, ExoPlayer>() }
        DisposableEffect(Unit) { onDispose { audioPlayers.values.forEach { it.release() }; audioPlayers.clear() } }
        LaunchedEffect(Unit) {
            while (true) {
                val ids = vm.audioClips.map { it.id }.toSet()
                audioPlayers.keys.filter { it !in ids }.forEach { audioPlayers.remove(it)?.release() }
                for (c in vm.audioClips) {
                    val ap = audioPlayers.getOrPut(c.id) { ExoPlayer.Builder(ctx).build().also { it.setMediaItem(MediaItem.fromUri(c.uri)); it.prepare() } }
                    ap.volume = min(1f, c.vol * (if (c.kind == 2) vm.volVoice else vm.volMusic))
                    val rel = vm.playheadMs - c.startMs
                    val active = player.isPlaying && rel in 0 until c.durMs
                    if (active) {
                        if (!ap.isPlaying || abs(ap.currentPosition - rel) > 400) ap.seekTo(rel)
                        ap.playWhenReady = true
                    } else if (ap.isPlaying) ap.pause()
                }
                delay(100)
            }
        }

        val zoom = TimelineMath.zoomAt(vm.zoomKeys, vm.playheadMs)
        val ar = if (vm.videoW > 0 && vm.videoH > 0) vm.videoW.toFloat() / vm.videoH else 16f / 9f
        val paint = remember(vm.vFilter, vm.vBrightness, vm.vContrast) { Paint().also { it.colorFilter = previewMatrix(vm) } }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
            val boxAr = maxWidth.value / maxHeight.value
            val pw = if (ar > boxAr) maxWidth else maxHeight * ar
            val ph = if (ar > boxAr) maxWidth / ar else maxHeight
            Box(Modifier.size(pw, ph).clip(RoundedCornerShape(2.dp))) {
                AndroidView(factory = { c -> TextureView(c).also { player.setVideoTextureView(it) } },
                    update = { tv -> tv.setLayerPaint(paint) },
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = zoom * (if (vm.vFlipH) -1f else 1f); scaleY = zoom * (if (vm.vFlipV) -1f else 1f)
                    })
                val dens = LocalDensity.current
                val minSidePx = with(dens) { min(pw.toPx(), ph.toPx()) }
                val t = vm.playheadMs
                vm.textClips.filter { t in it.startMs until it.endMs }.forEach { c ->
                    val fam = c.font?.let { f -> try { FontFamily(android.graphics.Typeface.createFromFile(f)) } catch (e: Exception) { null } }
                    Box(Modifier.fillMaxSize().padding(vertical = 8.dp), contentAlignment = when (c.pos) { 0 -> Alignment.TopCenter; 2 -> Alignment.BottomCenter; else -> Alignment.Center }) {
                        Text(c.text, color = Color(c.color), fontFamily = fam, textAlign = TextAlign.Center,
                            fontSize = with(dens) { (minSidePx * OverlayRender.sizeFactor[c.size.coerceIn(0, 2)]).toSp() },
                            modifier = Modifier.then(if (c.bg) Modifier.background(Color(0xAA000000)) else Modifier))
                    }
                }
                vm.stickerClips.filter { t in it.startMs until it.endMs }.forEach { c ->
                    Box(Modifier.fillMaxSize().padding(vertical = 8.dp), contentAlignment = when (c.pos) { 0 -> Alignment.TopCenter; 2 -> Alignment.BottomCenter; else -> Alignment.Center }) {
                        Text(c.emoji, fontSize = with(dens) { (minSidePx * OverlayRender.sizeFactor[c.size.coerceIn(0, 2)] * 2f).toSp() })
                    }
                }
            }
        }

        // ---------------- transport ----------------
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(fmt(vm.playheadMs) + " / " + fmt(vm.totalMs), style = MaterialTheme.typography.labelLarge, color = Storm.bolt, modifier = Modifier.weight(1f))
            Text("${vm.videoW}×${vm.videoH}", style = MaterialTheme.typography.labelSmall, color = Storm.muted)
            Spacer(Modifier.width(8.dp))
            IconAction(Ic.UNDO, stringResource(R.string.undo), vm.canUndoSeg) { vm.undoSeg() }
            IconAction(Ic.REDO, stringResource(R.string.redo), vm.canRedoSeg) { vm.redoSeg() }
            FilledTonalButton(onClick = {
                if (player.isPlaying) player.pause() else {
                    if (vm.playheadMs >= vm.totalMs - 50) seekOut(0)
                    player.play()
                }
            }, modifier = Modifier.heightIn(min = 40.dp)) { Text(stringResource(if (playing) R.string.pause else R.string.play)) }
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                TextButton(onClick = {
                    val w = vm.videoW.coerceAtLeast(16); val h = vm.videoH.coerceAtLeast(9)
                    try {
                        (ctx as? android.app.Activity)?.enterPictureInPictureMode(android.app.PictureInPictureParams.Builder()
                            .setAspectRatio(android.util.Rational(w, h).let { r ->
                                val mx = 2.39f; val mn = 1f / 2.39f
                                if (r.toFloat() > mx) android.util.Rational(239, 100) else if (r.toFloat() < mn) android.util.Rational(100, 239) else r
                            }).build())
                    } catch (_: Exception) { }
                }) { Text(stringResource(R.string.pip_mode)) }
            }
        }

        // ---------------- timeline ----------------
        var dpPerSec by remember { mutableStateOf(48f) }
        Timeline(vm, player, dpPerSec, playing, seek = { ms -> vm.playheadMs = ms; seekOut(ms) }, onUserScroll = { player.pause() })
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ve_hint_timeline), style = MaterialTheme.typography.labelSmall, color = Storm.muted, modifier = Modifier.weight(1f))
            TextButton(onClick = { dpPerSec = max(12f, dpPerSec / 1.5f) }) { Text("−") }
            TextButton(onClick = { dpPerSec = min(300f, dpPerSec * 1.5f) }) { Text("+") }
        }

        // ---------------- tabs + tool panel ----------------
        Surface(Modifier.fillMaxWidth(), color = Storm.surfaceHi, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            border = BorderStroke(1.dp, Storm.hairline)) {
            Column(Modifier.padding(top = 6.dp)) {
                if (vm.busy) LinearProgressIndicator(progress = { vm.progress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
                Column(Modifier.heightIn(max = 190.dp).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TabContent(vm, tab, tool, { tool = it }, pickClip = { pickClip.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                        pickVideo = pickReq, pickAudio = { pickAudio.launch("audio/*") }, pickFont = { pickFont.launch("*/*") },
                        projects = { showProjects = true },
                        record = {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) ==
                                android.content.pm.PackageManager.PERMISSION_GRANTED) vm.startRecording()
                            else micPerm.launch(android.Manifest.permission.RECORD_AUDIO)
                        })
                    Box { InfoText(vm) }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Storm.hairline))
                Tabs(VTab.values().map { stringResource(it.label) }, tab.ordinal) { tab = VTab.values()[it]; tool = 0 }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun Timeline(vm: MainVM, player: ExoPlayer, dpPerSec: Float, playing: Boolean, seek: (Long) -> Unit, onUserScroll: () -> Unit) {
    val dens = LocalDensity.current
    val pxPerMs = with(dens) { dpPerSec.dp.toPx() } / 1000f
    val dpPerMs = dpPerSec / 1000f
    val scroll = rememberScrollState()
    var programmatic by remember { mutableStateOf(false) }
    // user drags the timeline -> move the playhead
    LaunchedEffect(scroll, pxPerMs) {
        snapshotFlow { scroll.value to scroll.isScrollInProgress }.collect { (v, inProg) ->
            if (inProg && !programmatic) { onUserScroll(); seek((v / pxPerMs).toLong().coerceIn(0L, vm.totalMs)) }
        }
    }
    // playhead moved by playback or buttons -> move the timeline
    LaunchedEffect(vm.playheadMs, pxPerMs) {
        val target = (vm.playheadMs * pxPerMs).toInt()
        if (abs(scroll.value - target) > 1 && (!scroll.isScrollInProgress || programmatic)) {
            programmatic = true
            try { scroll.scrollTo(target) } finally { programmatic = false }
        }
    }
    val pills = ArrayList<Pill>()
    val textP = vm.textClips.map { Pill(1, it.id, it.startMs, it.endMs - it.startMs, it.text, cText) }
    val stickP = vm.stickerClips.map { Pill(2, it.id, it.startMs, it.endMs - it.startMs, it.emoji, cSticker) }
    val audioP = vm.audioClips.map { Pill(3, it.id, it.startMs, it.durMs, it.label, when (it.kind) { 1 -> cFx; 2 -> cVoice; else -> cTrack }) }
    val overlayLanes = lanes(textP + stickP)
    val audioLanes = lanes(audioP)
    val totalDp = (vm.totalMs * dpPerMs).dp
    BoxWithConstraints(Modifier.fillMaxWidth().height(150.dp).background(Color(0xFF05070B))) {
        val half = maxWidth / 2
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(Modifier.horizontalScroll(scroll)) {
                Spacer(Modifier.width(half))
                Column(Modifier.width(totalDp + 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    // ruler
                    Box(Modifier.fillMaxWidth().height(16.dp)) {
                        val step = if (dpPerSec >= 90) 1 else if (dpPerSec >= 36) 2 else if (dpPerSec >= 18) 5 else 10
                        var s = 0
                        while (s * 1000L <= vm.totalMs) {
                            Text(fmt(s * 1000L).substringBefore('.'), fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp),
                                color = Storm.muted, modifier = Modifier.offset(x = (s * 1000 * dpPerMs).dp))
                            s += step
                        }
                    }
                    // zoom markers
                    Box(Modifier.fillMaxWidth().height(20.dp)) {
                        vm.zoomKeys.forEach { k ->
                            val sel = vm.selKind == 4 && vm.selId == k.id
                            Box(Modifier.offset(x = (k.timeMs * dpPerMs).dp - 9.dp).size(18.dp).clip(RoundedCornerShape(4.dp))
                                .background(if (sel) Color.White else cZoom).clickable { vm.select(4, k.id); seek(k.timeMs) }, contentAlignment = Alignment.Center) {
                                Text(String.format(java.util.Locale.US, "%.1f", k.scale), fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp), color = Color.Black)
                            }
                        }
                    }
                    (overlayLanes + audioLanes).forEach { lane -> PillRow(vm, lane, dpPerMs) }
                    // video segments
                    Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        val segs = if (vm.segments.isEmpty()) listOf(Seg(0, vm.videoDur)) else vm.segments.toList()
                        segs.forEachIndexed { i, sg ->
                            val w = ((sg.endMs - sg.startMs) / vm.vSpeed * dpPerMs).dp
                            val sel = vm.selSeg == i && vm.selKind == 0
                            Box(Modifier.width(maxOf(6.dp, w - 2.dp)).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(cVideo)
                                .border(if (sel) 2.dp else 0.dp, if (sel) Color.White else Color.Transparent, RoundedCornerShape(6.dp))
                                .clickable { vm.selSeg = i; vm.selKind = 0 }, contentAlignment = Alignment.CenterStart) {
                                Text((i + 1).toString() + (if (sg.uri != null) "+" else "") + "  " + fmt(sg.endMs - sg.startMs).substringBefore('.') + "s",
                                    style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(start = 6.dp), maxLines = 1)
                            }
                        }
                    }
                }
                Spacer(Modifier.width(half))
            }
        }
        // fixed playhead
        Box(Modifier.align(Alignment.TopCenter).fillMaxHeight().width(2.dp).background(Color.White))
    }
}

@Composable
private fun PillRow(vm: MainVM, lane: List<Pill>, dpPerMs: Float) {
    Box(Modifier.fillMaxWidth().height(24.dp)) {
        lane.forEach { p ->
            val sel = vm.selKind == p.kind && vm.selId == p.id
            var rem by remember(p.id) { mutableStateOf(0f) }
            Box(Modifier.offset(x = (p.startMs * dpPerMs).dp).width(maxOf(14.dp, (p.lenMs * dpPerMs).dp)).fillMaxHeight()
                .clip(RoundedCornerShape(7.dp)).background(p.color)
                .border(if (sel) 2.dp else 0.dp, if (sel) Color.White else Color.Transparent, RoundedCornerShape(7.dp))
                .clickable { vm.select(p.kind, p.id) }
                .pointerInput(p.id, dpPerMs) {
                    detectDragGestures(onDragStart = { vm.select(p.kind, p.id) }) { change, drag ->
                        change.consume()
                        rem += drag.x / this.density / dpPerMs
                        val whole = rem.toLong(); if (whole != 0L) { vm.moveSelected(whole); rem -= whole }
                    }
                }, contentAlignment = Alignment.CenterStart) {
                Text(p.label, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp))
            }
        }
    }
}

@Composable
private fun ToolRow(content: @Composable RowScope.() -> Unit) =
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp), content = content)

@Composable
private fun Status(items: List<Pair<Int, Int>>) {
    items.forEach { (n, need) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(n), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.45f))
            Text(stringResource(need), style = MaterialTheme.typography.bodySmall, color = Storm.muted, modifier = Modifier.weight(0.55f))
        }
    }
}

private val aiStatus = listOf(R.string.ve_ai_captions to R.string.ve_need_model, R.string.ve_ai_cutout to R.string.ve_need_model,
    R.string.ve_ai_tracking to R.string.ve_need_model, R.string.ve_ai_enhance to R.string.ve_need_model,
    R.string.ve_ai_stabilize to R.string.ve_need_stabilize, R.string.ve_ai_beat to R.string.ve_need_audio_analysis,
    R.string.ve_ai_reframe to R.string.ve_need_model)
private val fxStatus = listOf(R.string.ve_fx_glitch to R.string.ve_need_shader, R.string.ve_fx_blur to R.string.ve_need_shader,
    R.string.ve_fx_grain to R.string.ve_need_shader, R.string.ve_fx_transitions to R.string.ve_need_compositor,
    R.string.ve_fx_pip to R.string.ve_need_compositor, R.string.ve_fx_chroma to R.string.ve_need_shader,
    R.string.ve_fx_reverse to R.string.ve_need_buffer, R.string.ve_fx_freeze to R.string.ve_need_buffer)

@Composable
private fun TabContent(vm: MainVM, tab: VTab, tool: Int, setTool: (Int) -> Unit, pickClip: () -> Unit, pickVideo: () -> Unit,
                       pickAudio: () -> Unit, pickFont: () -> Unit, projects: () -> Unit, record: () -> Unit) {
    when (tab) {
        VTab.MEDIA -> {
            ToolRow {
                ToolButton(Ic.VIDEO, stringResource(R.string.add_clip), false) { pickClip() }
                ToolButton(Ic.PHOTO, stringResource(R.string.ve_replace_main), false) { pickVideo() }
                ToolButton(Ic.EXPORT, stringResource(R.string.projects_title), false) { projects() }
            }
            Text(stringResource(R.string.ve_media_note), style = MaterialTheme.typography.bodySmall, color = Storm.muted)
        }
        VTab.EDIT -> {
            ToolRow {
                ToolButton(Ic.SCISSORS, stringResource(R.string.ve_split), false) { vm.splitAtPlayhead() }
                ToolButton(Ic.CROP, stringResource(R.string.ve_trim), tool == 1) { setTool(1) }
                ToolButton(Ic.SPEED, stringResource(R.string.tool_speed), tool == 2) { setTool(2) }
                ToolButton(Ic.CROP, stringResource(R.string.tool_crop), tool == 3) { setTool(3) }
                ToolButton(Ic.ROTATE, stringResource(R.string.tool_rotate), false) { vm.vRotate = (vm.vRotate + 90) % 360 }
                ToolButton(Ic.FLIP, stringResource(R.string.flip_h), vm.vFlipH) { vm.vFlipH = !vm.vFlipH }
                ToolButton(Ic.FLIP, stringResource(R.string.flip_v), vm.vFlipV) { vm.vFlipV = !vm.vFlipV }
                ToolButton(Ic.STICKER, stringResource(R.string.copy_segment), false, vm.selSeg in vm.segments.indices) { vm.duplicateSeg(vm.selSeg) }
                ToolButton(Ic.CLOSE, stringResource(R.string.delete_segment), false, vm.segments.size > 1 && vm.selSeg in vm.segments.indices) { vm.removeSeg(vm.selSeg); vm.selSeg = (vm.selSeg - 1).coerceAtLeast(0) }
            }
            when (tool) {
                1 -> {
                    val sg = vm.segments.getOrNull(vm.selSeg)
                    if (sg == null) Text(stringResource(R.string.ve_select_segment), style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                    else {
                        Stepper(stringResource(R.string.video_start), fmt(sg.startMs)) { vm.trimSelected(it, 0) }
                        Stepper(stringResource(R.string.video_end), fmt(sg.endMs)) { vm.trimSelected(0, it) }
                    }
                }
                2 -> Chips(listOf("0.25×", "0.5×", "1×", "2×", "4×"), listOf(0.25f, 0.5f, 1f, 2f, 4f).indexOf(vm.vSpeed)) { vm.vSpeed = listOf(0.25f, 0.5f, 1f, 2f, 4f)[it] }
                3 -> {
                    val aspects = listOf(0f to "", 1f to "1:1", 0.8f to "4:5", 16f / 9f to "16:9", 9f / 16f to "9:16")
                    Chips(aspects.mapIndexed { i, (_, l) -> if (i == 0) stringResource(R.string.aspect_original) else l },
                        aspects.indexOfFirst { it.first == vm.vCrop }.coerceAtLeast(0)) { vm.vCrop = aspects[it].first }
                }
                else -> Text(stringResource(R.string.ve_edit_note), style = MaterialTheme.typography.bodySmall, color = Storm.muted)
            }
        }
        VTab.AUDIO -> {
            var fxOpen by remember { mutableStateOf(false) }
            ToolRow {
                ToolButton(Ic.MUSIC, stringResource(R.string.import_music), false) { pickAudio() }
                Box {
                    ToolButton(Ic.BOLT, stringResource(R.string.fx_menu), false) { fxOpen = true }
                    DropdownMenu(expanded = fxOpen, onDismissRequest = { fxOpen = false }) {
                        fxItems.forEach { (k, res) -> DropdownMenuItem(text = { Text(stringResource(res)) }, onClick = { fxOpen = false; vm.useFx(k, res) }) }
                    }
                }
                if (vm.recording) ToolButton(Ic.CLOSE, stringResource(R.string.rec_stop), true) { vm.stopRecording() }
                else ToolButton(Ic.VOLUME, stringResource(R.string.rec_start), false) { record() }
                ToolButton(Ic.VOLUME, stringResource(R.string.tool_volume), tool == 1) { setTool(1) }
                ToolButton(Ic.CLOSE, stringResource(R.string.mute_original), vm.muteOriginal) { vm.muteOriginal = !vm.muteOriginal }
            }
            if (vm.recording) Text(stringResource(R.string.rec_running), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
            if (tool == 1) {
                Param(stringResource(R.string.vol_original), vm.volOrig, 0f..2f) { vm.volOrig = it }
                Param(stringResource(R.string.vol_music), vm.volMusic, 0f..2f) { vm.volMusic = it }
                Param(stringResource(R.string.vol_voice), vm.volVoice, 0f..2f) { vm.volVoice = it }
            }
            val c = vm.audioClips.firstOrNull { vm.selKind == 3 && it.id == vm.selId }
            if (c != null) {
                Text(c.label + "  ·  " + fmt(c.durMs), style = MaterialTheme.typography.labelLarge, color = Storm.bolt)
                Stepper(stringResource(R.string.ve_start), fmt(c.startMs)) { vm.moveSelected(it) }
                Param(stringResource(R.string.ve_clip_volume), c.vol, 0f..2f) { v -> vm.updateAudio(c.id) { it.copy(vol = v) } }
                OutlinedButton(onClick = { vm.deleteSelected() }) { Text(stringResource(R.string.delete_segment)) }
            } else Text(stringResource(R.string.ve_audio_note), style = MaterialTheme.typography.bodySmall, color = Storm.muted)
        }
        VTab.TEXT -> {
            ToolRow {
                ToolButton(Ic.TEXT, stringResource(R.string.ve_add_text), false) { vm.addText(vm.textClips.lastOrNull()?.text ?: "Text") }
                ToolButton(Ic.STICKER, stringResource(R.string.ve_add_sticker), tool == 1) { setTool(1) }
                ToolButton(Ic.TEXT, stringResource(R.string.ve_import_font), false) { pickFont() }
            }
            if (tool == 1) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    emojis.forEach { e ->
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Storm.surfaceTop).clickable { vm.addSticker(e) }, contentAlignment = Alignment.Center) {
                            Text(e, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
            val t = vm.textClips.firstOrNull { vm.selKind == 1 && it.id == vm.selId }
            val st = vm.stickerClips.firstOrNull { vm.selKind == 2 && it.id == vm.selId }
            if (t != null) {
                OutlinedTextField(value = t.text, onValueChange = { v -> vm.updateText(t.id) { it.copy(text = v) } }, singleLine = true,
                    label = { Text(stringResource(R.string.overlay_text)) }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    tColors.forEach { c ->
                        Box(Modifier.size(36.dp).clip(CircleShape).background(Color(c))
                            .border(if (t.color == c) 3.dp else 1.dp, if (t.color == c) Storm.bolt else Storm.outline, CircleShape)
                            .selectable(selected = t.color == c, onClick = { vm.updateText(t.id) { it.copy(color = c) } }, role = Role.RadioButton))
                    }
                    FilterChip(t.bg, { vm.updateText(t.id) { it.copy(bg = !it.bg) } }, { Text(stringResource(R.string.text_bg)) })
                }
                Chips(listOf(R.string.size_small, R.string.size_medium, R.string.size_large).map { stringResource(it) }, t.size) { s -> vm.updateText(t.id) { it.copy(size = s) } }
                Chips(listOf(R.string.pos_top, R.string.pos_center, R.string.pos_bottom).map { stringResource(it) }, t.pos) { p -> vm.updateText(t.id) { it.copy(pos = p) } }
                if (vm.fonts.isNotEmpty()) Chips(listOf(stringResource(R.string.ve_font_default)) + vm.fonts.map { java.io.File(it).nameWithoutExtension },
                    if (t.font == null) 0 else vm.fonts.indexOf(t.font) + 1) { i -> vm.updateText(t.id) { it.copy(font = if (i == 0) null else vm.fonts[i - 1]) } }
                Stepper(stringResource(R.string.ve_start), fmt(t.startMs)) { vm.moveSelected(it) }
                Stepper(stringResource(R.string.ve_length), fmt(t.endMs - t.startMs)) { vm.resizeSelected(it) }
                OutlinedButton(onClick = { vm.deleteSelected() }) { Text(stringResource(R.string.delete_segment)) }
            } else if (st != null) {
                Chips(listOf(R.string.size_small, R.string.size_medium, R.string.size_large).map { stringResource(it) }, st.size) { s -> vm.updateSticker(st.id) { it.copy(size = s) } }
                Chips(listOf(R.string.pos_top, R.string.pos_center, R.string.pos_bottom).map { stringResource(it) }, st.pos) { p -> vm.updateSticker(st.id) { it.copy(pos = p) } }
                Stepper(stringResource(R.string.ve_start), fmt(st.startMs)) { vm.moveSelected(it) }
                Stepper(stringResource(R.string.ve_length), fmt(st.endMs - st.startMs)) { vm.resizeSelected(it) }
                OutlinedButton(onClick = { vm.deleteSelected() }) { Text(stringResource(R.string.delete_segment)) }
            } else if (tool != 1) Text(stringResource(R.string.ve_text_note), style = MaterialTheme.typography.bodySmall, color = Storm.muted)
        }
        VTab.EFFECTS -> {
            ToolRow {
                ToolButton(Ic.CROP, stringResource(R.string.ve_zoom_in_here), false) { vm.addZoom(2f) }
                ToolButton(Ic.CROP, stringResource(R.string.ve_zoom_normal_here), false) { vm.addZoom(1f) }
            }
            val z = vm.zoomKeys.firstOrNull { vm.selKind == 4 && it.id == vm.selId }
            if (z != null) {
                Param(stringResource(R.string.ve_zoom_scale), z.scale, 1f..4f) { v -> vm.updateZoom(z.id) { it.copy(scale = v) } }
                Stepper(stringResource(R.string.ve_time), fmt(z.timeMs)) { vm.moveSelected(it) }
                OutlinedButton(onClick = { vm.deleteSelected() }) { Text(stringResource(R.string.delete_segment)) }
            } else Text(stringResource(R.string.ve_zoom_note), style = MaterialTheme.typography.bodySmall, color = Storm.muted)
            Text(stringResource(R.string.ve_not_yet), style = MaterialTheme.typography.labelMedium, color = Storm.bolt)
            Status(fxStatus)
        }
        VTab.FILTERS -> {
            Chips(VideoFx.filters.map { stringResource(it.nameRes) }, vm.vFilter) { vm.vFilter = it }
            Param(stringResource(R.string.brightness), vm.vBrightness, -1f..1f) { vm.vBrightness = it }
            Param(stringResource(R.string.contrast), vm.vContrast, -1f..1f) { vm.vContrast = it }
            Text(stringResource(R.string.ve_filters_note), style = MaterialTheme.typography.bodySmall, color = Storm.muted)
        }
        VTab.AI -> {
            Text(stringResource(R.string.ve_ai_intro), style = MaterialTheme.typography.bodySmall, color = Storm.muted)
            Status(aiStatus)
        }
        VTab.TEMPLATES -> Text(stringResource(R.string.ve_templates_note), style = MaterialTheme.typography.bodyMedium, color = Storm.muted)
        VTab.EXPORT -> {
            val hs = listOf(0, 360, 480, 720, 1080, 2160)
            Chips(hs.map { if (it == 0) stringResource(R.string.aspect_original) else "${it}p" }, hs.indexOf(vm.exportHeight).coerceAtLeast(0)) { vm.exportHeight = hs[it] }
            Chips(listOf(R.string.q_low, R.string.q_balanced, R.string.q_high).map { stringResource(it) }, vm.exportQuality) { vm.exportQuality = it }
            val mbps = listOf(2f, 8f, 16f)[vm.exportQuality]
            Text(stringResource(R.string.ve_estimate, String.format(java.util.Locale.US, "%.0f", mbps * vm.totalMs / 8000f)),
                style = MaterialTheme.typography.bodySmall, color = Storm.muted)
            Button(onClick = { vm.exportVideo() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.export_video)) }
            if (vm.busy) {
                Text(stringResource(vm.stage) + "  " + (vm.progress * 100).toInt() + "%", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { vm.cancel() }) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}
