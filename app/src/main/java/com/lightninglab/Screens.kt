package com.lightninglab

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.VideoView
import android.net.Uri
import kotlin.math.roundToInt

@Composable
fun Home(vm: MainVM) {
    var heroPlayer by remember { mutableStateOf<VideoView?>(null) }
    DisposableEffect(Unit) { onDispose { heroPlayer?.stopPlayback() } }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) vm.prepare(u)
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) vm.loadPhoto(u)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(158.dp)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        heroPlayer = this
                        setVideoURI(Uri.parse("android.resource://${ctx.packageName}/${R.raw.lightning_hero}"))
                        setOnPreparedListener { player -> player.isLooping = true; start() }
                    }
                },
                update = { view -> if (!view.isPlaying) view.start() }
            )
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xAA080B12), Color(0x22080B12), Color(0x88080B12)))))
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) { LangMenu() }
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White)) { append("Lightning") }
                    withStyle(SpanStyle(color = Storm.bolt)) { append("Lab") }
                }, style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.subtitle), style = MaterialTheme.typography.bodySmall, color = Color.White)
            }
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.main_tools), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeTool(Ic.BOLT, stringResource(R.string.tool_lightning), Modifier.weight(1f)) {
                        pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    }
                    HomeTool(Ic.PHOTO, stringResource(R.string.tool_photo), Modifier.weight(1f)) {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeTool(Ic.VIDEO, stringResource(R.string.tool_video), Modifier.weight(1f)) { vm.screen = Screen.VIDEO }
                    HomeTool(Ic.CLOUD, stringResource(R.string.tool_cloud), Modifier.weight(1f)) { vm.screen = Screen.WEATHER }
                }
            }
            Text(stringResource(R.string.home_tools_note), style = MaterialTheme.typography.bodySmall, color = Storm.muted)
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.sensitivity), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text((vm.sensitivity * 100).roundToInt().toString() + "%", style = MaterialTheme.typography.labelLarge, color = Storm.bolt)
                }
                Slider(value = vm.sensitivity, onValueChange = { vm.sensitivity = it }, enabled = !vm.busy)
            }
            if (vm.busy) {
                Panel {
                    LinearProgressIndicator(progress = { vm.progress }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(vm.stage), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { vm.cancel() }) { Text(stringResource(R.string.cancel)) }
                }
            }
            InfoText(vm)
            TextButton(onClick = { vm.screen = Screen.ABOUT }) { Text(stringResource(R.string.about), color = Storm.bolt) }
        }
    }
}

@Composable
private fun HomeTool(ic: Ic, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.height(118.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Storm.surfaceHi),
        border = BorderStroke(1.dp, Storm.hairline)
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            AppIcon(ic, Storm.bolt, Modifier.size(30.dp), filled = true)
            Spacer(Modifier.height(10.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = Storm.text, maxLines = 2)
        }
    }
}

@Composable
fun About(vm: MainVM) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val ver = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" } }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.about), onBack = { vm.screen = Screen.HOME })
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Image(painterResource(R.drawable.logo), null, Modifier.size(96.dp))
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color = Storm.text)) { append("Lightning") }
                withStyle(SpanStyle(color = Storm.bolt)) { append("Lab") }
            }, style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.subtitle), style = MaterialTheme.typography.titleSmall, color = Storm.muted)
            Panel { Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodyMedium) }
            Text("v" + ver, style = MaterialTheme.typography.bodySmall, color = Storm.muted)
        }
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
                Text(stringResource(R.string.heuristic_note), style = MaterialTheme.typography.bodySmall, color = Storm.muted)
            }
        }
        items(vm.items.size) { i ->
            val item = vm.items[i]
            Card(Modifier.clickable { vm.toggle(i) }, shape = MaterialTheme.shapes.small,
                border = BorderStroke(if (item.order > 0) 1.5.dp else 1.dp, if (item.order > 0) Storm.bolt else Storm.hairline),
                colors = CardDefaults.cardColors(containerColor =
                    if (item.order > 0) Storm.selected else Storm.surfaceHi)) {
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
                    FilterChip(vm.method == Composer.Method.MULTIPLY, { vm.method = Composer.Method.MULTIPLY },
                        { Text(stringResource(R.string.method_multiply)) })
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

private enum class PTab { LIGHT, COLOR, HSL, DETAIL, CROP, REMOVE, EXPORT }

@Composable
fun PhotoEditor(vm: MainVM) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) vm.loadPhoto(u)
    }
    val prev = vm.photoPrev
    var tab by remember { mutableStateOf(PTab.LIGHT) }
    var brush by remember { mutableStateOf(0.02f) }
    val healMode = tab == PTab.REMOVE
    var shown by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(vm.params, prev) {
        if (prev != null) shown = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            PhotoEdit.apply(prev, vm.params)
        }
    }
    val pickReq = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.nav_photo), onBack = { vm.screen = Screen.HOME }) {
            IconAction(Ic.UNDO, stringResource(R.string.undo), vm.canUndo) { vm.undo() }
            IconAction(Ic.REDO, stringResource(R.string.redo), vm.canRedo) { vm.redo() }
            IconAction(Ic.PHOTO, stringResource(R.string.pick_image), true) { pickReq() }
        }
        if (prev == null) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                AppIcon(Ic.PHOTO, Storm.muted, Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Button(onClick = { pickReq() }, modifier = Modifier.heightIn(min = 52.dp)) { Text(stringResource(R.string.pick_image)) }
            }
            return@Column
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
            (if (vm.showOriginal || healMode) prev else shown)?.let { bmp ->
                Box(Modifier.aspectRatio(bmp.width.toFloat() / bmp.height).clip(MaterialTheme.shapes.small)
                    .then(if (!healMode) Modifier.pointerInput(Unit) {
                        detectTapGestures(onPress = { vm.showOriginal = true; tryAwaitRelease(); vm.showOriginal = false })
                    } else Modifier)) {
                    Image(bmp.asImageBitmap(), null, Modifier.matchParentSize())
                    if (healMode) HealOverlay(vm.healCircles, brush, { vm.addHeal(it) })
                }
            }
            Text(stringResource(if (healMode) R.string.heal_hint else R.string.hold_compare),
                style = MaterialTheme.typography.labelSmall, color = Storm.text,
                modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp)
                    .background(Color(0x99000000), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        Surface(Modifier.fillMaxWidth(), color = Storm.surfaceHi,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            border = BorderStroke(1.dp, Storm.hairline)) {
            Column(Modifier.padding(top = 8.dp)) {
                Tabs(listOf(R.string.tab_light, R.string.tab_color, R.string.tab_hsl, R.string.tab_detail, R.string.tab_crop,
                    R.string.tab_remove, R.string.tab_export).map { stringResource(it) }, tab.ordinal) { tab = PTab.values()[it] }
                if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
                Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp)) {
                    val p = vm.params
                    Crossfade(targetState = tab, animationSpec = tween(180), label = "ptab") { t ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            when (t) {
                                PTab.LIGHT -> {
                                    Param(stringResource(R.string.exposure), p.exposure, -2f..2f, vm::commit) { vm.params = p.copy(exposure = it) }
                                    Param(stringResource(R.string.brightness), p.brightness, -1f..1f, vm::commit) { vm.params = p.copy(brightness = it) }
                                    Param(stringResource(R.string.contrast), p.contrast, -1f..1f, vm::commit) { vm.params = p.copy(contrast = it) }
                                    Param(stringResource(R.string.highlights), p.highlights, -1f..1f, vm::commit) { vm.params = p.copy(highlights = it) }
                                    Param(stringResource(R.string.shadows), p.shadows, -1f..1f, vm::commit) { vm.params = p.copy(shadows = it) }
                                    Param(stringResource(R.string.scurve), p.scurve, -1f..1f, vm::commit) { vm.params = p.copy(scurve = it) }
                                }
                                PTab.COLOR -> {
                                    Param(stringResource(R.string.saturation), p.saturation, -1f..1f, vm::commit) { vm.params = p.copy(saturation = it) }
                                    Param(stringResource(R.string.vibrance), p.vibrance, -1f..1f, vm::commit) { vm.params = p.copy(vibrance = it) }
                                    Param(stringResource(R.string.warmth), p.warmth, -1f..1f, vm::commit) { vm.params = p.copy(warmth = it) }
                                }
                                PTab.HSL -> {
                                    var band by remember { mutableStateOf(0) }
                                    ChoiceRow(listOf(R.string.hsl_red, R.string.hsl_orange, R.string.hsl_yellow, R.string.hsl_green,
                                        R.string.hsl_aqua, R.string.hsl_blue, R.string.hsl_purple).map { stringResource(it) }, band) { band = it }
                                    Param(stringResource(R.string.hsl_hue), p.hsl[band], -1f..1f, vm::commit) {
                                        vm.params = p.copy(hsl = p.hsl.toMutableList().also { l -> l[band] = it })
                                    }
                                    Param(stringResource(R.string.saturation), p.hsl[Hsl.BANDS + band], -1f..1f, vm::commit) {
                                        vm.params = p.copy(hsl = p.hsl.toMutableList().also { l -> l[Hsl.BANDS + band] = it })
                                    }
                                    Param(stringResource(R.string.hsl_lum), p.hsl[2 * Hsl.BANDS + band], -1f..1f, vm::commit) {
                                        vm.params = p.copy(hsl = p.hsl.toMutableList().also { l -> l[2 * Hsl.BANDS + band] = it })
                                    }
                                }
                                PTab.DETAIL -> {
                                    Param(stringResource(R.string.sharpness), p.sharpness, 0f..1f, vm::commit) { vm.params = p.copy(sharpness = it) }
                                    Param(stringResource(R.string.denoise), p.denoise, 0f..1f, vm::commit) { vm.params = p.copy(denoise = it) }
                                }
                                PTab.CROP -> {
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
                                }
                                PTab.REMOVE -> {
                                    Param(stringResource(R.string.heal_brush), brush, 0.005f..0.05f) { brush = it }
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { vm.applyHeal() }, enabled = !vm.busy && vm.healCircles.isNotEmpty()) { Text(stringResource(R.string.heal_apply)) }
                                        OutlinedButton(onClick = { vm.healCircles.clear() }) { Text(stringResource(R.string.heal_clear)) }
                                        OutlinedButton(onClick = { vm.undoHeal() }, enabled = vm.canUndoHeal) { Text(stringResource(R.string.heal_undo)) }
                                    }
                                }
                                PTab.EXPORT -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(vm.watermark, { vm.watermark = it })
                                        Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.watermark), style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { vm.savePreset() }) { Text(stringResource(R.string.preset_save)) }
                                        OutlinedButton(onClick = { vm.loadPreset() }) { Text(stringResource(R.string.preset_load)) }
                                        OutlinedButton(onClick = { vm.params = PhotoParams(); vm.commit() }) { Text(stringResource(R.string.reset)) }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { vm.savePhoto(true) }, enabled = !vm.busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.save_png)) }
                                        Button(onClick = { vm.savePhoto(false) }, enabled = !vm.busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.save_jpeg)) }
                                    }
                                }
                            }
                        }
                    }
                }
                Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 6.dp)) { InfoText(vm) }
            }
        }
    }
}

internal val fxItems = listOf(
    SoundFx.Kind.RUMBLE to R.string.fx_thunder_rumble, SoundFx.Kind.CRACK to R.string.fx_thunder_crack,
    SoundFx.Kind.CLAP to R.string.fx_thunder_clap, SoundFx.Kind.RAIN to R.string.fx_rain,
    SoundFx.Kind.WIND to R.string.fx_wind, SoundFx.Kind.STORM to R.string.fx_storm,
    SoundFx.Kind.ZAP to R.string.fx_zap, SoundFx.Kind.BOOM to R.string.fx_boom,
    SoundFx.Kind.WHOOSH to R.string.fx_whoosh, SoundFx.Kind.RISER to R.string.fx_riser)

private val textColors = listOf(0xFFFFFFFF.toInt(), 0xFFFFD43B.toInt(), 0xFF6FB4FF.toInt(), 0xFF000000.toInt(), 0xFFFF5252.toInt())
private val stickers = listOf("⚡", "☁", "🌧", "⛈", "🌪", "❄", "🌈", "☀", "🌙", "⭐", "🔥", "💧")
private val cropAspects = listOf(0f to "", 1f to "1:1", 0.8f to "4:5", 16f / 9f to "16:9", 9f / 16f to "9:16")

@Composable
private fun ChoiceRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, l -> FilterChip(selected == i, { onSelect(i) }, { Text(l) }) }
    }
}

@Composable
fun PrepareScreen(vm: MainVM) {
    val u = vm.prepUri
    var vv by remember { mutableStateOf<android.widget.VideoView?>(null) }
    var range by remember(vm.prepDur) { mutableStateOf(0f..vm.prepDur.toFloat().coerceAtLeast(1f)) }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.prepare_title), onBack = { if (!vm.busy) vm.screen = Screen.HOME })
        if (u == null || vm.prepDur <= 0L) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.error_generic, "video"), color = Storm.muted)
            }
            return@Column
        }
        key(u) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { c -> android.widget.VideoView(c).apply { setVideoURI(u); setOnPreparedListener { seekTo(1) }; vv = this } },
                modifier = Modifier.weight(1f).heightIn(min = 160.dp).fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(MaterialTheme.shapes.medium))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { vv?.start() }) { Text(stringResource(R.string.play)) }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { vv?.pause() }) { Text(stringResource(R.string.pause)) }
        }
        Surface(Modifier.fillMaxWidth(), color = Storm.surfaceHi,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            border = BorderStroke(1.dp, Storm.hairline)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.video_start) + " " + fmt(range.start.toLong()) + "    " +
                    stringResource(R.string.video_end) + " " + fmt(range.endInclusive.toLong()) + "    (" +
                    fmt((range.endInclusive - range.start).toLong()) + ")",
                    style = MaterialTheme.typography.labelLarge, color = Storm.bolt)
                RangeSlider(value = range, enabled = !vm.busy, onValueChange = { r ->
                    if (r.start != range.start) vv?.seekTo(r.start.toInt()) else vv?.seekTo(r.endInclusive.toInt())
                    range = r; vm.prepStart = r.start.toLong(); vm.prepEnd = r.endInclusive.toLong()
                }, valueRange = 0f..vm.prepDur.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.semantics { contentDescription = "trim" })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !vm.busy, onClick = {
                        val pos = vv?.currentPosition?.toFloat() ?: 0f
                        if (pos < range.endInclusive - 100f) range = pos..range.endInclusive
                    }) { Text(stringResource(R.string.start_here)) }
                    OutlinedButton(enabled = !vm.busy, onClick = {
                        val pos = vv?.currentPosition?.toFloat() ?: 0f
                        if (pos > range.start + 100f) range = range.start..pos
                    }) { Text(stringResource(R.string.end_here)) }
                    OutlinedButton(enabled = !vm.busy, onClick = { range = 0f..vm.prepDur.toFloat() }) { Text(stringResource(R.string.reset)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.sensitivity), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text((vm.sensitivity * 100).roundToInt().toString() + "%", style = MaterialTheme.typography.labelLarge, color = Storm.bolt)
                }
                Slider(value = vm.sensitivity, onValueChange = { vm.sensitivity = it }, enabled = !vm.busy,
                    modifier = Modifier.semantics { contentDescription = "sensitivity" })
                Text(stringResource(R.string.prepare_hint), style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                if (vm.busy) {
                    LinearProgressIndicator(progress = { vm.progress }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(vm.stage), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { vm.cancel() }) { Text(stringResource(R.string.cancel)) }
                } else {
                    Button(onClick = { vm.startDetect(u, range.start.toLong(), range.endInclusive.toLong()) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        AppIcon(Ic.BOLT, Storm.onBolt, Modifier.size(20.dp), filled = true)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.detect), style = MaterialTheme.typography.labelLarge)
                    }
                }
                InfoText(vm)
            }
        }
    }
}


@Composable
fun ProjectsDialog(vm: MainVM, onDismiss: () -> Unit) {
    var list by remember { mutableStateOf(vm.listProjects()) }
    AlertDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close_dialog)) } },
        title = { Text(stringResource(R.string.projects_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveProject(); onDismiss() }, enabled = vm.videoUri != null && !vm.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.save_project)) }
                Text(stringResource(R.string.project_copy_note), style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                if (list.isEmpty()) Text(stringResource(R.string.no_projects), style = MaterialTheme.typography.bodyMedium)
                list.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.loadProject(p.id); onDismiss() }) { Text(stringResource(R.string.open_project)) }
                        TextButton(onClick = { vm.deleteProject(p.id); list = vm.listProjects() }) { Text(stringResource(R.string.delete_segment)) }
                    }
                }
            }
        })
}
