package com.lightninglab

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun Home(vm: MainVM) {
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) vm.startDetect(u)
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B0F17), Color(0xFF111B33))))) {
        Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { LangMenu() }
            Image(painterResource(R.drawable.logo), null, Modifier.size(150.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.subtitle), style = MaterialTheme.typography.titleSmall, color = Color(0xFF9AA7BD))
            Spacer(Modifier.height(6.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151D2E))) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.sensitivity) + ": " + (vm.sensitivity * 100).roundToInt() + "%")
                    Slider(value = vm.sensitivity, onValueChange = { vm.sensitivity = it }, enabled = !vm.busy)
                }
            }
            if (vm.busy) {
                LinearProgressIndicator(progress = { vm.progress }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(vm.stage))
                OutlinedButton(onClick = { vm.cancel() }) { Text(stringResource(R.string.cancel)) }
            }
            InfoText(vm)
            FeatureCard(stringResource(R.string.detect), stringResource(R.string.detect_sub), enabled = !vm.busy) {
                pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            }
            FeatureCard(stringResource(R.string.about), null) { vm.screen = Screen.ABOUT }
        }
    }
}

@Composable
fun About(vm: MainVM) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(R.drawable.logo), null, Modifier.size(120.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.subtitle), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.about_text))
        OutlinedButton(onClick = { vm.screen = Screen.HOME }) { Text(stringResource(R.string.back)) }
    }
}

@Composable
fun Results(vm: MainVM) {
    LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { vm.screen = Screen.HOME }) { Text(stringResource(R.string.back)) }
                    TextButton(onClick = { vm.autoSelect() }) { Text(stringResource(R.string.auto_select)) }
                    TextButton(onClick = { vm.selectAll() }) { Text(stringResource(R.string.select_all)) }
                    TextButton(onClick = { vm.clearSel() }) { Text(stringResource(R.string.deselect_all)) }
                }
                Text(stringResource(R.string.order_hint), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.heuristic_note), style = MaterialTheme.typography.bodySmall, color = Color(0xFF9AA7BD))
            }
        }
        items(vm.items.size) { i ->
            val item = vm.items[i]
            Card(Modifier.clickable { vm.toggle(i) }, shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor =
                    if (item.order > 0) Color(0xFF173352) else Color(0xFF151D2E))) {
                Column {
                    Image(item.thumb.asImageBitmap(), null,
                        Modifier.fillMaxWidth().aspectRatio(item.thumb.width.toFloat() / item.thumb.height),
                        contentScale = ContentScale.Crop)
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (item.order > 0) "${item.order}" else "·", fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(22.dp))
                        Text("#${item.event.id}  ${fmt(item.event.bestMs)}  ·  ${(item.event.score * 100).roundToInt()}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val any = vm.items.any { it.order > 0 }
                Button(onClick = { vm.saveFrames() }, enabled = !vm.busy && any, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.save_frames))
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(vm.method == Composer.Method.LIGHTEN, { vm.method = Composer.Method.LIGHTEN },
                        { Text(stringResource(R.string.method_lighten)) })
                    FilterChip(vm.method == Composer.Method.SCREEN, { vm.method = Composer.Method.SCREEN },
                        { Text(stringResource(R.string.method_screen)) })
                    FilterChip(vm.method == Composer.Method.ADVANCED, { vm.method = Composer.Method.ADVANCED },
                        { Text(stringResource(R.string.method_advanced)) })
                }
                Param(stringResource(R.string.intensity), vm.intensity, 0.2f..1.5f) { vm.intensity = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(vm.align, { vm.align = it })
                    Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.align_frames))
                }
                Button(onClick = { vm.compose() }, enabled = !vm.busy && any, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.create_photo))
                }
                if (vm.busy) {
                    LinearProgressIndicator(progress = { vm.progress }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(vm.stage))
                }
                vm.composite?.let { c ->
                    Image(c.asImageBitmap(), null, Modifier.fillMaxWidth().aspectRatio(c.width.toFloat() / c.height))
                    Button(onClick = { vm.setPhoto(c) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.edit_composite))
                    }
                }
                InfoText(vm)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private val aspects = listOf("Original" to 0f, "1:1" to 1f, "4:5" to 0.8f, "4:3" to 4f / 3f,
    "3:2" to 1.5f, "16:9" to 16f / 9f, "9:16" to 9f / 16f)

@Composable
fun PhotoEditor(vm: MainVM) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) vm.loadPhoto(u)
    }
    val prev = vm.photoPrev
    var healMode by remember { mutableStateOf(false) }
    var brush by remember { mutableStateOf(0.02f) }
    var shown by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(vm.params, prev) {
        if (prev != null) shown = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            PhotoEdit.apply(prev, vm.params)
        }
    }
    Column(Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { vm.screen = Screen.HOME }) { Text(stringResource(R.string.back)) }
            TextButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text(stringResource(R.string.pick_image))
            }
            TextButton(onClick = { vm.params = PhotoParams(); vm.commit() }) { Text(stringResource(R.string.reset)) }
            TextButton(onClick = { vm.undo() }, enabled = vm.canUndo) { Text(stringResource(R.string.undo)) }
            TextButton(onClick = { vm.redo() }, enabled = vm.canRedo) { Text(stringResource(R.string.redo)) }
        }
        if (prev == null) {
            Button(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pick_image)) }
            return@Column
        }
        (if (vm.showOriginal || healMode) prev else shown)?.let { bmp ->
            Box(Modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height)) {
                Image(bmp.asImageBitmap(), null, Modifier.matchParentSize())
                if (healMode) HealOverlay(vm.healCircles, brush, { vm.addHeal(it) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(healMode, { healMode = it })
            Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.heal_mode))
        }
        if (healMode) {
            Param(stringResource(R.string.heal_brush), brush, 0.005f..0.05f) { brush = it }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.applyHeal() }, enabled = !vm.busy && vm.healCircles.isNotEmpty()) { Text(stringResource(R.string.heal_apply)) }
                OutlinedButton(onClick = { vm.healCircles.clear() }) { Text(stringResource(R.string.heal_clear)) }
                OutlinedButton(onClick = { vm.undoHeal() }, enabled = vm.canUndoHeal) { Text(stringResource(R.string.heal_undo)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(vm.showOriginal, { vm.showOriginal = it })
            Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.compare_original))
        }
        val p = vm.params
        Param(stringResource(R.string.exposure), p.exposure, -2f..2f, vm::commit) { vm.params = p.copy(exposure = it) }
        Param(stringResource(R.string.brightness), p.brightness, -1f..1f, vm::commit) { vm.params = p.copy(brightness = it) }
        Param(stringResource(R.string.contrast), p.contrast, -1f..1f, vm::commit) { vm.params = p.copy(contrast = it) }
        Param(stringResource(R.string.saturation), p.saturation, -1f..1f, vm::commit) { vm.params = p.copy(saturation = it) }
        Param(stringResource(R.string.warmth), p.warmth, -1f..1f, vm::commit) { vm.params = p.copy(warmth = it) }
        Param(stringResource(R.string.highlights), p.highlights, -1f..1f, vm::commit) { vm.params = p.copy(highlights = it) }
        Param(stringResource(R.string.shadows), p.shadows, -1f..1f, vm::commit) { vm.params = p.copy(shadows = it) }
        Param(stringResource(R.string.sharpness), p.sharpness, 0f..1f, vm::commit) { vm.params = p.copy(sharpness = it) }
        Param(stringResource(R.string.scurve), p.scurve, -1f..1f, vm::commit) { vm.params = p.copy(scurve = it) }
        Param(stringResource(R.string.vibrance), p.vibrance, -1f..1f, vm::commit) { vm.params = p.copy(vibrance = it) }
        Param(stringResource(R.string.denoise), p.denoise, 0f..1f, vm::commit) { vm.params = p.copy(denoise = it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.savePreset() }) { Text(stringResource(R.string.preset_save)) }
            OutlinedButton(onClick = { vm.loadPreset() }) { Text(stringResource(R.string.preset_load)) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.params = p.copy(rotate = (p.rotate + 90) % 360); vm.commit() }) { Text(stringResource(R.string.rotate)) }
            OutlinedButton(onClick = { vm.params = p.copy(flipH = !p.flipH); vm.commit() }) { Text(stringResource(R.string.flip_h)) }
            OutlinedButton(onClick = { vm.params = p.copy(flipV = !p.flipV); vm.commit() }) { Text(stringResource(R.string.flip_v)) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            aspects.forEach { (label, v) ->
                FilterChip(p.aspect == v, { vm.params = p.copy(aspect = v); vm.commit() },
                    { Text(if (v == 0f) stringResource(R.string.aspect_original) else label) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(vm.watermark, { vm.watermark = it })
            Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.watermark))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.savePhoto(true) }, enabled = !vm.busy) { Text(stringResource(R.string.save_png)) }
            Button(onClick = { vm.savePhoto(false) }, enabled = !vm.busy) { Text(stringResource(R.string.save_jpeg)) }
        }
        InfoText(vm)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun VideoEditor(vm: MainVM) {
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) vm.loadVideo(u)
    }
    val pickClip = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) vm.addClip(u)
    }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) vm.music = u
    }
    var vv by remember { mutableStateOf<android.widget.VideoView?>(null) }
    var range by remember(vm.videoDur) { mutableStateOf(0f..vm.videoDur.toFloat().coerceAtLeast(1f)) }
    Column(Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { vm.screen = Screen.HOME }) { Text(stringResource(R.string.back)) }
            TextButton(onClick = { pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }) {
                Text(stringResource(R.string.pick_video))
            }
        }
        val u = vm.videoUri
        if (u == null) {
            Button(onClick = { pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pick_video)) }
            return@Column
        }
        key(u) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { c ->
                    android.widget.VideoView(c).apply {
                        setVideoURI(u); setOnPreparedListener { seekTo(1) }; vv = this
                    }
                },
                modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(16.dp)))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vv?.start() }) { Text(stringResource(R.string.play)) }
            OutlinedButton(onClick = { vv?.pause() }) { Text(stringResource(R.string.pause)) }
        }
        Text(stringResource(R.string.video_start) + " " + fmt(range.start.toLong()) + "   " +
            stringResource(R.string.video_end) + " " + fmt(range.endInclusive.toLong()))
        RangeSlider(value = range, onValueChange = { r ->
            if (r.start != range.start) vv?.seekTo(r.start.toInt()) else vv?.seekTo(r.endInclusive.toInt())
            range = r
        }, valueRange = 0f..vm.videoDur.toFloat().coerceAtLeast(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (range.endInclusive - range.start >= 100f) vm.addSeg(Seg(range.start.toLong(), range.endInclusive.toLong()))
            }) { Text(stringResource(R.string.add_segment)) }
            OutlinedButton(onClick = { vm.clearSegs() }) { Text(stringResource(R.string.clear_segments)) }
            OutlinedButton(onClick = { pickClip.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }) {
                Text(stringResource(R.string.add_clip))
            }
            TextButton(onClick = { vm.undoSeg() }, enabled = vm.canUndoSeg) { Text(stringResource(R.string.undo)) }
            TextButton(onClick = { vm.redoSeg() }, enabled = vm.canRedoSeg) { Text(stringResource(R.string.redo)) }
        }
        Text(stringResource(R.string.segments_title), fontWeight = FontWeight.SemiBold)
        if (vm.segments.isEmpty()) Text(stringResource(R.string.whole_video), style = MaterialTheme.typography.bodySmall)
        vm.segments.forEachIndexed { i, s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}.  ${fmt(s.startMs)} – ${fmt(s.endMs)}" + (if (s.uri != null) "  🎞" else ""), modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.removeSeg(i) }) { Text("✕") }
            }
        }
        Param(stringResource(R.string.brightness), vm.vBrightness, -1f..1f) { vm.vBrightness = it }
        Param(stringResource(R.string.contrast), vm.vContrast, -1f..1f) { vm.vContrast = it }
        OutlinedTextField(value = vm.overlayText, onValueChange = { vm.overlayText = it },
            label = { Text(stringResource(R.string.overlay_text)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedButton(onClick = { vm.vRotate = (vm.vRotate + 90) % 360 }) {
            Text(stringResource(R.string.rotate) + " " + vm.vRotate + "°")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(vm.muteOriginal, { vm.muteOriginal = it })
            Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.mute_original))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { pickAudio.launch("audio/*") }) { Text(stringResource(R.string.add_music)) }
            if (vm.music != null) TextButton(onClick = { vm.music = null }) { Text(stringResource(R.string.remove_music)) }
        }
        Button(onClick = { vm.exportVideo() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.export_video))
        }
        if (vm.busy) {
            LinearProgressIndicator(progress = { vm.progress }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(vm.stage))
            OutlinedButton(onClick = { vm.cancel() }) { Text(stringResource(R.string.cancel)) }
        }
        InfoText(vm)
        Spacer(Modifier.height(24.dp))
    }
}
