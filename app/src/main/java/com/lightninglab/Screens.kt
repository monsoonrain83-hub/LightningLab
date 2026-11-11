package com.lightninglab

import java.util.Locale

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
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun Home(vm: MainVM) {
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) vm.prepare(u)
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 5.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(color = Storm.text)) { append("Lightning") }
                    withStyle(SpanStyle(color = Storm.bolt)) { append("Lab") }
                }, style = MaterialTheme.typography.titleLarge.copy(fontSize = 23.sp, fontWeight = FontWeight.Bold))
                Text(stringResource(R.string.subtitle), style = MaterialTheme.typography.labelSmall, color = Storm.muted)
            }
            Surface(shape = RoundedCornerShape(50), color = Color(0xAA10251F),
                border = BorderStroke(1.dp, Color(0x5576D6AD))) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF65D6A5)))
                    Text(stringResource(R.string.on_device), color = Color(0xFFBCEED9), style = MaterialTheme.typography.labelSmall)
                }
            }
            LangMenu()
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 15.dp).padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            // Primary workflow: one clear action, with secondary tools kept visually quieter.
            Box(Modifier.fillMaxWidth().height(178.dp).clip(RoundedCornerShape(25.dp))) {
                LightningHero(Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                    listOf(Color(0xD9081422), Color(0x77101D30), Color(0x11101D30)))))
                Column(Modifier.align(Alignment.CenterStart).padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.home_hero_title), color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(stringResource(R.string.home_hero_body),
                        color = Color(0xFFD5E1EE), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                        enabled = !vm.busy, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.heightIn(min = 42.dp)) {
                        AppIcon(Ic.BOLT, Storm.onBolt, Modifier.size(18.dp), filled = true)
                        Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.detect), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Surface(Modifier.align(Alignment.TopEnd).padding(12.dp), shape = RoundedCornerShape(50),
                    color = Color(0xAA0C1725), border = BorderStroke(1.dp, Color(0x557EC8FF))) {
                    Text("AI  •  OFFLINE", Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = Color(0xFFB9D8F6), style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.home_tools), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.home_tool_count), style = MaterialTheme.typography.labelSmall, color = Storm.muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassQuickAction(Modifier.weight(1f), Ic.PHOTO, stringResource(R.string.edit_photo),
                    stringResource(R.string.photo_short), Storm.arc) { vm.screen = Screen.PHOTO }
                GlassQuickAction(Modifier.weight(1f), Ic.VIDEO, stringResource(R.string.edit_video),
                    stringResource(R.string.video_short), Color(0xFF9D9BFF)) { vm.screen = Screen.VIDEO }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassQuickAction(Modifier.weight(1f), Ic.CLOUD, stringResource(R.string.weather_title),
                    stringResource(R.string.weather_short), Color(0xFF69C9D7)) { vm.screen = Screen.WEATHER }
                GlassQuickAction(Modifier.weight(1f), Ic.CLOUD, stringResource(R.string.home_cloud_atlas),
                    stringResource(R.string.home_cloud_atlas_sub), Color(0xFF86C6FF)) { vm.screen = Screen.CLOUD_ATLAS }
            }
            if (vm.busy) {
                GlassPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.home_processing), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text("${(vm.progress * 100).roundToInt()}%", color = Storm.bolt, style = MaterialTheme.typography.labelLarge)
                        }
                        LinearProgressIndicator(progress = { vm.progress }, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(vm.stage), style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                        TextButton(onClick = { vm.cancel() }) { Text(stringResource(R.string.cancel)) }
                    }
                }
            }
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_sensitivity), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.home_sensitivity_hint), style = MaterialTheme.typography.labelSmall, color = Storm.muted)
                    }
                    Text((vm.sensitivity * 100).roundToInt().toString() + "%", style = MaterialTheme.typography.titleMedium, color = Storm.bolt)
                }
                Slider(value = vm.sensitivity, onValueChange = { vm.sensitivity = it }, enabled = !vm.busy)
            }
            InfoText(vm)
            Text(stringResource(R.string.home_offline_note),
                style = MaterialTheme.typography.labelSmall, color = Storm.muted,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun GlassQuickAction(
    modifier: Modifier = Modifier,
    icon: Ic,
    title: String,
    subtitle: String,
    accent: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        modifier = modifier.heightIn(min = 104.dp).clip(shape).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = shape,
        color = Color(0xB91A293B),
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(accent.copy(alpha = 0.65f), Color(0x223D5B7A), Color(0x5577A9D8))))
    ) {
        Column(
            Modifier.background(Brush.verticalGradient(listOf(Color(0x22FFFFFF), Color.Transparent, Color(0x11000000))))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.08f)))),
                contentAlignment = Alignment.Center
            ) { AppIcon(icon, accent, Modifier.size(21.dp), filled = true) }
            Text(title, style = MaterialTheme.typography.titleSmall, color = Storm.text, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Storm.muted, maxLines = 2)
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
                Text("${vm.items.size} رخداد تصویری شناسایی شد", style = MaterialTheme.typography.titleMedium, color = Storm.text)
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
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("#${item.event.id}  ${fmt(item.event.bestMs)}  ·  امتیاز الگوریتم ${(item.event.score * 100).roundToInt()}",
                                style = MaterialTheme.typography.bodySmall)
                            Text(item.event.visualType.labelFa + "  ·  ${(item.event.visualEvidence * 100).roundToInt()}٪ شواهد ظاهری",
                                style = MaterialTheme.typography.labelSmall, color = Storm.arc)
                            Text("بازه: ${fmt(item.event.startMs)} تا ${fmt(item.event.endMs)}",
                                style = MaterialTheme.typography.labelSmall, color = Storm.muted)
                            Text("${item.event.pulseCount} پالس  ·  گستردگی روشنایی ${(item.event.spatialSpread * 100).roundToInt()}٪",
                                style = MaterialTheme.typography.labelSmall, color = Storm.muted)
                        }
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

private enum class PTab { LIGHT, COLOR, HSL, CURVES, DETAIL, CROP, REMOVE, LAYERS, EXPORT }

@Composable
fun PhotoEditor(vm: MainVM) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) vm.loadPhoto(u)
    }
    val pickOverlay = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) vm.addPhotoImageLayer(u)
    }
    val prev = vm.photoPrev
    var tab by remember { mutableStateOf(PTab.LIGHT) }
    var showPhotoProjects by remember { mutableStateOf(false) }
    if (showPhotoProjects) PhotoProjectsDialog(vm) { showPhotoProjects = false }
    var brush by remember { mutableStateOf(0.02f) }
    var layerDraft by remember { mutableStateOf("") }
    var selectedLayerId by remember { mutableStateOf<Long?>(null) }
    var selectedLayerKind by remember { mutableStateOf("TEXT") }
    var maskMode by remember { mutableStateOf(false) }
    var maskRadius by remember { mutableStateOf(0.025f) }
    var maskSoftness by remember { mutableStateOf(0.35f) }
    var activeMaskPoints by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    val layerSnapshot = vm.photoLayers.toList()
    val shapeSnapshot = vm.photoShapeLayers.toList()
    val imageSnapshot = vm.photoImageLayers.toList()
    val maskSnapshot = vm.photoMaskStrokes.toList()
    val maskInverted = vm.photoMaskInverted
    val healMode = tab == PTab.REMOVE
    var shown by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var histogram by remember { mutableStateOf<PhotoHistogramData?>(null) }
    LaunchedEffect(shown, prev, vm.showOriginal, healMode, maskMode) {
        val source = if (vm.showOriginal || (healMode && !maskMode)) prev else shown ?: prev
        histogram = if (source == null) null else {
            val safeBitmap = source.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { PhotoHistogram.compute(safeBitmap) }
            } finally {
                if (!safeBitmap.isRecycled) safeBitmap.recycle()
            }
        }
    }
    LaunchedEffect(vm.params, prev, layerSnapshot, shapeSnapshot, imageSnapshot, maskSnapshot, maskInverted) {
        shown = null
        if (prev != null) {
            // Render from the already downscaled editor preview, never the full-resolution export source.
            val paramsSnapshot = vm.params
            var rendered: android.graphics.Bitmap? = null
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val adjusted = PhotoEdit.apply(prev, paramsSnapshot)
                    val composed = PhotoLayers.render(adjusted, layerSnapshot, shapeSnapshot, imageSnapshot, maskSnapshot, maskInverted)
                    if (composed !== adjusted && !adjusted.isRecycled) adjusted.recycle()
                    composed.also { rendered = it }
                }
                val result = rendered
                if (kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.isActive == true) shown = result
                else if (result != null && !result.isRecycled) result.recycle()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                rendered?.let { if (!it.isRecycled) it.recycle() }
                throw cancelled
            }
        }
    }
    DisposableEffect(shown, prev) {
        val ownedPreview = shown
        onDispose {
            if (ownedPreview != null && ownedPreview !== prev && !ownedPreview.isRecycled) ownedPreview.recycle()
        }
    }
    val pickReq = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.nav_photo), onBack = { vm.screen = Screen.HOME }) {
            TextButton(onClick = { showPhotoProjects = true }) { Text("پروژه‌ها") }
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
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF17283B), Color(0xFF0B1420), Color(0xFF101A29))))
                .border(1.dp, Brush.linearGradient(listOf(Color(0x887EC8FF), Color(0x225D83AE), Color(0x6677A9D8))), RoundedCornerShape(26.dp)),
            contentAlignment = Alignment.Center
        ) {
            (if (vm.showOriginal || (healMode && !maskMode)) prev else shown)?.let { bmp ->
                Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp)
                    .aspectRatio(bmp.width.toFloat() / bmp.height).clip(RoundedCornerShape(17.dp))
                    .then(if (maskMode && !vm.showOriginal) Modifier.pointerInput(maskMode, maskRadius) {
                        detectDragGestures(
                            onDragStart = { pos -> activeMaskPoints = listOf((pos.x / size.width).coerceIn(0f, 1f) to (pos.y / size.height).coerceIn(0f, 1f)) },
                            onDrag = { change, _ -> activeMaskPoints = activeMaskPoints + ((change.position.x / size.width).coerceIn(0f, 1f) to (change.position.y / size.height).coerceIn(0f, 1f)); change.consume() },
                            onDragEnd = { vm.addPhotoMaskStroke(PhotoMaskStroke(activeMaskPoints, maskRadius, maskSoftness)); activeMaskPoints = emptyList() },
                            onDragCancel = { activeMaskPoints = emptyList() }
                        )
                    } else if (selectedLayerId != null && !healMode) Modifier.pointerInput(selectedLayerId, selectedLayerKind, bmp.width, bmp.height) {
                        detectDragGestures(
                            onDrag = { change, drag ->
                                change.consume()
                                selectedLayerId?.let { id -> vm.translatePhotoLayer(selectedLayerKind, id,
                                    drag.x / size.width.toFloat().coerceAtLeast(1f), drag.y / size.height.toFloat().coerceAtLeast(1f)) }
                            },
                            onDragEnd = { vm.finishPhotoLayerGesture() }
                        )
                    } else if (!healMode) Modifier.pointerInput(Unit) {
                        detectTapGestures(onPress = { vm.showOriginal = true; tryAwaitRelease(); vm.showOriginal = false })
                    } else Modifier)) {
                    Image(bmp.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.Fit)
                    val histogramSnapshot = histogram
                    if (!healMode) {
                        histogramSnapshot?.let { data -> PhotoHistogramMini(data, Modifier.align(Alignment.BottomEnd).padding(8.dp)) }
                    }
                    if (healMode && !maskMode) HealOverlay(vm.healCircles, brush, { vm.addHeal(it) })
                }
            }
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                shape = RoundedCornerShape(50), color = Color(0xAA101B2A),
                border = BorderStroke(1.dp, Color(0x557EC8FF))
            ) {
                Text(stringResource(if (vm.showOriginal) R.string.original_view else R.string.live_preview), color = Color(0xFFE1F2FF),
                    style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
            Text(stringResource(if (healMode) R.string.heal_hint else R.string.hold_compare),
                style = MaterialTheme.typography.labelSmall, color = Storm.text,
                modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp)
                    .background(Color(0xCC101B2A), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp))
        }
        GlassPanel(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Column(Modifier.padding(top = 5.dp)) {
                Tabs(listOf(R.string.tab_light, R.string.tab_color, R.string.tab_hsl, "منحنی RGB", R.string.tab_detail, R.string.tab_crop,
                    R.string.tab_remove, R.string.tab_layers, R.string.tab_export).map { if (it is Int) stringResource(it) else it as String }, tab.ordinal) { tab = PTab.values()[it] }
                if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
                Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())
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
                                PTab.CURVES -> {
                                    var curveChannel by remember { mutableStateOf(0) }
                                    ChoiceRow(listOf("RGB", "قرمز", "سبز", "آبی"), curveChannel) { curveChannel = it }
                                    Text("منحنی تُن پنج‌نقطه‌ای؛ مقدار خروجی هر نقطه بین ۰ تا ۱۰۰٪ تنظیم می‌شود.",
                                        color = Storm.muted, style = MaterialTheme.typography.bodySmall)
                                    val channels = if (curveChannel == 0) listOf(0, 1, 2) else listOf(curveChannel - 1)
                                    val graphValues = (0 until PhotoToneCurves.POINTS).map { point ->
                                        if (curveChannel == 0) {
                                            (0..2).map { channel -> p.rgbCurves.getOrElse(channel * PhotoToneCurves.POINTS + point) { point / 4f } }.average().toFloat()
                                        } else p.rgbCurves.getOrElse((curveChannel - 1) * PhotoToneCurves.POINTS + point) { point / 4f }
                                    }
                                    PhotoToneCurveGraph(
                                        graphValues,
                                        Modifier.fillMaxWidth().height(164.dp),
                                        onPointChange = { point, v ->
                                            val next = p.rgbCurves.toMutableList()
                                            channels.forEach { channel -> next[channel * PhotoToneCurves.POINTS + point] = v }
                                            vm.params = p.copy(rgbCurves = next)
                                        },
                                        onGestureFinished = { vm.commit() }
                                    )
                                    val labels = listOf("سایه عمیق", "سایه", "میانه", "روشنایی", "سفیدی")
                                    for (point in 0 until PhotoToneCurves.POINTS) {
                                        val value = if (curveChannel == 0) {
                                            (0..2).map { channel -> p.rgbCurves.getOrElse(channel * PhotoToneCurves.POINTS + point) { point / 4f } }.average().toFloat()
                                        } else p.rgbCurves.getOrElse((curveChannel - 1) * PhotoToneCurves.POINTS + point) { point / 4f }
                                        Param(labels[point], value, 0f..1f, vm::commit) { v ->
                                            val next = p.rgbCurves.toMutableList()
                                            channels.forEach { channel -> next[channel * PhotoToneCurves.POINTS + point] = v }
                                            vm.params = p.copy(rgbCurves = next)
                                        }
                                    }
                                    OutlinedButton(onClick = {
                                        vm.params = p.copy(rgbCurves = PhotoToneCurves.identity())
                                        vm.commit()
                                    }, modifier = Modifier.fillMaxWidth()) { Text("بازنشانی منحنی‌ها") }
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
                                    Divider(color = Color(0x335D83AE), modifier = Modifier.padding(vertical = 8.dp))
                                    Text("ماسک پاک‌کن موضعی", style = MaterialTheme.typography.titleSmall, color = Storm.text)
                                    Text("حالت قلم را روشن کن و روی تصویر بکش؛ قسمت‌های انتخاب‌شده در خروجی شفاف می‌شوند.", color = Storm.muted, style = MaterialTheme.typography.bodySmall)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(selected = maskMode, onClick = { maskMode = !maskMode }, label = { Text(if (maskMode) "قلم روشن" else "فعال‌کردن قلم") })
                                        FilterChip(selected = vm.photoMaskInverted, onClick = { vm.togglePhotoMaskInvert() }, label = { Text("ماسک معکوس") })
                                        OutlinedButton(onClick = { vm.undoPhotoMaskStroke() }, enabled = vm.photoMaskStrokes.isNotEmpty()) { Text("واگرد") }
                                        OutlinedButton(onClick = { vm.clearPhotoMask() }, enabled = vm.photoMaskStrokes.isNotEmpty()) { Text("پاک‌کردن") }
                                    }
                                    Text("اندازه قلم", color = Storm.muted, style = MaterialTheme.typography.labelSmall)
                                    Slider(value = maskRadius, onValueChange = { maskRadius = it }, valueRange = 0.005f..0.09f)
                                    Text("لبه نرم", color = Storm.muted, style = MaterialTheme.typography.labelSmall)
                                    Slider(value = maskSoftness, onValueChange = { maskSoftness = it }, valueRange = 0f..1f)
                                    Text("تعداد حرکت‌ها: ${vm.photoMaskStrokes.size}", color = Storm.muted, style = MaterialTheme.typography.labelSmall)
                                    Button(onClick = { vm.cutoutBackground() }, enabled = !vm.busy,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.cutout_btn)) }
                                    Text(stringResource(R.string.cutout_note), style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                                }
                                PTab.LAYERS -> {
                                    Text("لایه‌های ترکیبی و مستقل", style = MaterialTheme.typography.titleSmall, color = Storm.text)
                                    Text("ترتیب بالا و پایین، بین متن، شکل و تصویر مشترک است. لایه را انتخاب کن و مستقیماً روی تصویر بکش تا جابه‌جا شود.", style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                                    OutlinedTextField(
                                        value = layerDraft, onValueChange = { layerDraft = it },
                                        modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 3,
                                        label = { Text("متن جدید") }, placeholder = { Text("عنوان یا توضیح را وارد کنید") }
                                    )
                                    Button(onClick = {
                                        vm.addPhotoTextLayer(layerDraft)
                                        selectedLayerId = vm.photoLayers.lastOrNull()?.id
                                        selectedLayerKind = "TEXT"
                                        layerDraft = ""
                                    }, enabled = layerDraft.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("افزودن لایه متن") }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { vm.addPhotoShape(PhotoShapeLayer.Shape.RECTANGLE) }, modifier = Modifier.weight(1f)) { Text("＋ مستطیل") }
                                        OutlinedButton(onClick = { vm.addPhotoShape(PhotoShapeLayer.Shape.ELLIPSE) }, modifier = Modifier.weight(1f)) { Text("＋ بیضی") }
                                    }
                                    OutlinedButton(onClick = { pickOverlay.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) {
                                        Text("＋ افزودن تصویر از گالری")
                                    }
                                    vm.photoImageLayers.forEachIndexed { imageIndex, imageLayer ->
                                        Row(Modifier.fillMaxWidth().clickable { selectedLayerId = imageLayer.id; selectedLayerKind = "IMAGE" }, verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = imageLayer.visible, onCheckedChange = { v -> vm.updatePhotoImage(imageLayer.id) { it.copy(visible = v) } })
                                            Text(imageLayer.name, Modifier.weight(1f), color = Storm.text)
                                            TextButton(onClick = { vm.movePhotoLayerGlobal("IMAGE", imageLayer.id, -1) }) { Text("↑") }
                                            TextButton(onClick = { vm.movePhotoLayerGlobal("IMAGE", imageLayer.id, 1) }) { Text("↓") }
                                            TextButton(onClick = { selectedLayerId = vm.duplicatePhotoImageLayer(imageLayer.id); selectedLayerKind = "IMAGE" }) { Text("کپی") }
                                            TextButton(onClick = { vm.removePhotoImage(imageLayer.id) }) { Text("حذف") }
                                        }
                                        Text("اندازه تصویر", color = Storm.muted, style = MaterialTheme.typography.labelSmall)
                                        Slider(value = imageLayer.width, onValueChange = { v -> vm.updatePhotoImage(imageLayer.id) { it.copy(width = v) } }, valueRange = 0.05f..0.95f)
                                        Text("موقعیت افقی", color = Storm.muted, style = MaterialTheme.typography.labelSmall)
                                        Slider(value = imageLayer.x, onValueChange = { v -> vm.updatePhotoImage(imageLayer.id) { it.copy(x = v) } }, valueRange = 0f..1f)
                                        Text("موقعیت عمودی", color = Storm.muted, style = MaterialTheme.typography.labelSmall)
                                        Slider(value = imageLayer.y, onValueChange = { v -> vm.updatePhotoImage(imageLayer.id) { it.copy(y = v) } }, valueRange = 0f..1f)
                                        Text("شفافیت", color = Storm.muted, style = MaterialTheme.typography.labelSmall)
                                        Slider(value = imageLayer.opacity, onValueChange = { v -> vm.updatePhotoImage(imageLayer.id) { it.copy(opacity = v) } }, valueRange = 0f..1f)
                                        Text("چرخش: ${imageLayer.rotationDegrees.toInt()}°", color = Storm.muted, style = MaterialTheme.typography.labelSmall)
                                        Slider(value = imageLayer.rotationDegrees, onValueChange = { v -> vm.updatePhotoImage(imageLayer.id) { it.copy(rotationDegrees = v) } }, valueRange = -180f..180f)
                                    }
                                    if (vm.photoShapeLayers.isNotEmpty()) {
                                        Text("لایه‌های شکل", color = Storm.text, style = MaterialTheme.typography.titleSmall)
                                        vm.photoShapeLayers.forEachIndexed { shapeIndex, shape ->
                                            Row(Modifier.fillMaxWidth().clickable { selectedLayerId = shape.id; selectedLayerKind = "SHAPE" }, verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(checked = shape.visible, onCheckedChange = { v -> vm.updatePhotoShape(shape.id) { it.copy(visible = v) } })
                                                Text(shape.name, Modifier.weight(1f), color = Storm.text)
                                                TextButton(onClick = { vm.movePhotoLayerGlobal("SHAPE", shape.id, -1) }) { Text("↑") }
                                                TextButton(onClick = { vm.movePhotoLayerGlobal("SHAPE", shape.id, 1) }) { Text("↓") }
                                                TextButton(onClick = { selectedLayerId = vm.duplicatePhotoShapeLayer(shape.id); selectedLayerKind = "SHAPE" }) { Text("کپی") }
                                                TextButton(onClick = { vm.removePhotoShape(shape.id) }) { Text("حذف") }
                                            }
                                            Text("عرض", color = Storm.muted, style = MaterialTheme.typography.labelSmall)
                                            Slider(value = shape.width, onValueChange = { v -> vm.updatePhotoShape(shape.id) { it.copy(width = v) } }, valueRange = 0.03f..0.95f)
                                            Text("ارتفاع", color = Storm.muted, style = MaterialTheme.typography.labelSmall)
                                            Slider(value = shape.height, onValueChange = { v -> vm.updatePhotoShape(shape.id) { it.copy(height = v) } }, valueRange = 0.03f..0.95f)
                                            Text("موقعیت افقی", color = Storm.muted, style = MaterialTheme.typography.labelSmall)
                                            Slider(value = shape.x, onValueChange = { v -> vm.updatePhotoShape(shape.id) { it.copy(x = v) } }, valueRange = 0f..1f)
                                            Text("موقعیت عمودی", color = Storm.muted, style = MaterialTheme.typography.labelSmall)
                                            Slider(value = shape.y, onValueChange = { v -> vm.updatePhotoShape(shape.id) { it.copy(y = v) } }, valueRange = 0f..1f)
                                            Text("شفافیت", color = Storm.muted, style = MaterialTheme.typography.labelSmall)
                                            Slider(value = shape.opacity, onValueChange = { v -> vm.updatePhotoShape(shape.id) { it.copy(opacity = v) } }, valueRange = 0f..1f)
                                            Text("چرخش: ${shape.rotationDegrees.toInt()}°", color = Storm.muted, style = MaterialTheme.typography.labelSmall)
                                            Slider(value = shape.rotationDegrees, onValueChange = { v -> vm.updatePhotoShape(shape.id) { it.copy(rotationDegrees = v) } }, valueRange = -180f..180f)
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                FilterChip(selected = shape.color == 0xFFFFD43B.toInt(), onClick = { vm.updatePhotoShape(shape.id) { it.copy(color = 0xFFFFD43B.toInt()) } }, label = { Text("طلایی") })
                                                FilterChip(selected = shape.color == 0xFFFFFFFF.toInt(), onClick = { vm.updatePhotoShape(shape.id) { it.copy(color = 0xFFFFFFFF.toInt()) } }, label = { Text("سفید") })
                                                FilterChip(selected = shape.color == 0xFF6FB4FF.toInt(), onClick = { vm.updatePhotoShape(shape.id) { it.copy(color = 0xFF6FB4FF.toInt()) } }, label = { Text("آبی") })
                                            }
                                        }
                                    }
                                    if (vm.photoLayers.isEmpty() && vm.photoShapeLayers.isEmpty() && vm.photoImageLayers.isEmpty() && vm.photoMaskStrokes.isEmpty()) {
                                        Text("هنوز لایه‌ای اضافه نشده است.", color = Storm.muted, style = MaterialTheme.typography.bodySmall)
                                    }
                                    vm.photoLayers.forEachIndexed { index, layer ->
                                        val selected = selectedLayerId == layer.id
                                        Surface(
                                            color = if (selected) Color(0x334C9BD6) else Color(0x221A2C40),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, if (selected) Color(0x887EC8FF) else Color(0x335D83AE)),
                                            modifier = Modifier.fillMaxWidth().clickable { selectedLayerId = layer.id; selectedLayerKind = "TEXT" }
                                        ) {
                                            Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(checked = layer.visible, onCheckedChange = { v -> vm.updatePhotoLayer(layer.id) { it.copy(visible = v) } })
                                                Column(Modifier.weight(1f)) {
                                                    Text(layer.name, color = Storm.text, style = MaterialTheme.typography.labelMedium)
                                                    Text(layer.text, color = Storm.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                                }
                                                TextButton(onClick = { vm.movePhotoLayerGlobal("TEXT", layer.id, -1) }) { Text("↑") }
                                                TextButton(onClick = { vm.movePhotoLayerGlobal("TEXT", layer.id, 1) }) { Text("↓") }
                                                TextButton(onClick = { selectedLayerId = vm.duplicatePhotoTextLayer(layer.id); selectedLayerKind = "TEXT" }) { Text("کپی") }
                                                TextButton(onClick = { vm.removePhotoLayer(layer.id); if (selectedLayerId == layer.id) selectedLayerId = null }) { Text("حذف") }
                                            }
                                        }
                                    }
                                    vm.photoLayers.firstOrNull { it.id == selectedLayerId }?.let { layer ->
                                        OutlinedTextField(
                                            value = layer.text,
                                            onValueChange = { value -> vm.updatePhotoLayer(layer.id) { it.copy(text = value) } },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text("ویرایش متن لایه") },
                                            minLines = 1,
                                            maxLines = 3
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("ضخامت قلم", color = Storm.muted, modifier = Modifier.weight(1f))
                                            FilterChip(selected = layer.bold, onClick = { vm.updatePhotoLayer(layer.id) { it.copy(bold = !it.bold) } }, label = { Text(if (layer.bold) "ضخیم" else "معمولی") })
                                        }
                                        Text("اندازه", color = Storm.muted, style = MaterialTheme.typography.labelSmall)
                                        Slider(value = layer.size, onValueChange = { v -> vm.updatePhotoLayer(layer.id) { it.copy(size = v) } }, valueRange = 0.025f..0.18f)
                                        Text("موقعیت افقی", color = Storm.muted, style = MaterialTheme.typography.labelSmall)
                                        Slider(value = layer.x, onValueChange = { v -> vm.updatePhotoLayer(layer.id) { it.copy(x = v) } }, valueRange = 0.05f..0.95f)
                                        Text("موقعیت عمودی", color = Storm.muted, style = MaterialTheme.typography.labelSmall)
                                        Slider(value = layer.y, onValueChange = { v -> vm.updatePhotoLayer(layer.id) { it.copy(y = v) } }, valueRange = 0.05f..0.95f)
                                        Text("شفافیت", color = Storm.muted, style = MaterialTheme.typography.labelSmall)
                                        Slider(value = layer.opacity, onValueChange = { v -> vm.updatePhotoLayer(layer.id) { it.copy(opacity = v) } }, valueRange = 0f..1f)
                                        Text("چرخش: ${layer.rotationDegrees.toInt()}°", color = Storm.muted, style = MaterialTheme.typography.labelSmall)
                                        Slider(value = layer.rotationDegrees, onValueChange = { v -> vm.updatePhotoLayer(layer.id) { it.copy(rotationDegrees = v) } }, valueRange = -180f..180f)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilterChip(selected = layer.color == 0xFFFFFFFF.toInt(), onClick = { vm.updatePhotoLayer(layer.id) { it.copy(color = 0xFFFFFFFF.toInt()) } }, label = { Text("سفید") })
                                            FilterChip(selected = layer.color == 0xFFFFD43B.toInt(), onClick = { vm.updatePhotoLayer(layer.id) { it.copy(color = 0xFFFFD43B.toInt()) } }, label = { Text("طلایی") })
                                            FilterChip(selected = layer.color == 0xFF6FB4FF.toInt(), onClick = { vm.updatePhotoLayer(layer.id) { it.copy(color = 0xFF6FB4FF.toInt()) } }, label = { Text("آبی") })
                                        }
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

private enum class VTool { TRIM, MUSIC, VOLUME, FILTER, TEXT, STICKER, SPEED, CROP, ROTATE, FLIP, ADJUST, EXPORT }

private val fxItems = listOf(
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
fun VideoEditor(vm: MainVM) {
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) vm.loadVideo(u)
    }
    val pickClip = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) vm.addClip(u)
    }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) { vm.updateMusicSource(u); vm.musicName = null }
    }
    val pickSubRip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) vm.importSubRip(u)
    }
    val saveSubRip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip")) { u ->
        if (u != null) vm.exportSubRip(u)
    }
    val pickVideoImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) vm.addVideoImageClip(u)
    }
    val micCtx = androidx.compose.ui.platform.LocalContext.current
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startRecording() else vm.micDenied()
    }
    var showProjects by remember { mutableStateOf(false) }
    if (showProjects) ProjectsDialog(vm) { showProjects = false }
    val context = androidx.compose.ui.platform.LocalContext.current
    val u = vm.videoUri
    val player = remember(u) {
        u?.let { source -> androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(source)); prepare()
        } }
    }
    DisposableEffect(player) { onDispose { player?.release() } }
    var range by remember(vm.videoDur) { mutableStateOf(0f..vm.videoDur.toFloat().coerceAtLeast(1f)) }
    var tool by remember { mutableStateOf(VTool.TRIM) }
    var toolCategory by remember { mutableStateOf("edit") }
    val pickReq = { pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
    LaunchedEffect(player, vm.vSpeed) { player?.setPlaybackSpeed(vm.vSpeed.coerceIn(0.25f, 4f)) }
    val seekToTimeline: (Long) -> Unit = { requested ->
        val speed = vm.vSpeed.coerceAtLeast(0.05f)
        val total = ((if (vm.segments.isEmpty()) vm.videoDur else vm.segments.sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }) / speed).toLong().coerceAtLeast(1L)
        val target = requested.coerceIn(0L, total)
        vm.playheadMs = target
        if (vm.segments.isEmpty()) {
            if (player != null && u != null) {
                if (player.currentMediaItem?.localConfiguration?.uri != u) {
                    player.setMediaItem(androidx.media3.common.MediaItem.fromUri(u)); player.prepare()
                }
                player.seekTo((target * speed).toLong())
            }
        } else {
            val lengths = vm.segments.map { (it.endMs - it.startMs).coerceAtLeast(0L) }
            val (index, offset) = TimelineMath.locate(lengths, speed, target)
            val segment = vm.segments.getOrNull(index)
            if (segment != null) {
                val source = segment.uri ?: u
                if (source != null && player != null) {
                    if (player.currentMediaItem?.localConfiguration?.uri != source) {
                        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(source)); player.prepare()
                    }
                    player.seekTo(segment.startMs + offset)
                }
            }
        }
    }
    LaunchedEffect(player, u) {
        while (player != null) {
            val sourcePosition = runCatching { player.currentPosition }.getOrDefault(0L).coerceAtLeast(0L)
            if (vm.segments.isEmpty()) {
                vm.playheadMs = (sourcePosition / vm.vSpeed.coerceAtLeast(0.05f)).toLong()
                    .coerceAtMost((vm.videoDur / vm.vSpeed.coerceAtLeast(0.05f)).toLong().coerceAtLeast(1L))
            } else {
                val currentUri = player.currentMediaItem?.localConfiguration?.uri
                val index = vm.segments.indexOfFirst { segment ->
                    (segment.uri ?: u) == currentUri && sourcePosition >= segment.startMs && sourcePosition <= segment.endMs
                }
                if (index >= 0) {
                    val before = vm.segments.take(index).sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }
                    val local = (sourcePosition - vm.segments[index].startMs).coerceAtLeast(0L)
                    vm.playheadMs = ((before + local) / vm.vSpeed.coerceAtLeast(0.05f)).toLong()
                }
            }
            kotlinx.coroutines.delay(100)
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.nav_video), onBack = { vm.screen = Screen.HOME }) {
            IconAction(Ic.UNDO, "Undo", vm.canUndoSeg) { vm.undoVideoEdit() }
            IconAction(Ic.REDO, "Redo", vm.canRedoSeg) { vm.redoVideoEdit() }
            TextButton(onClick = { showProjects = true }) { Text(stringResource(R.string.projects_title)) }
            IconAction(Ic.VIDEO, stringResource(R.string.pick_video), true) { pickReq() }
        }
        if (u == null) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                AppIcon(Ic.VIDEO, Storm.muted, Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Button(onClick = { pickReq() }, modifier = Modifier.heightIn(min = 52.dp)) { Text(stringResource(R.string.pick_video)) }
            }
            return@Column
        }
        key(u) {
            Box(
                Modifier.weight(1f).fillMaxWidth().heightIn(min = 150.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(25.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF17283B), Color(0xFF080F19))))
                    .border(1.dp, Brush.linearGradient(listOf(Color(0x887EC8FF), Color(0x225D83AE), Color(0x6677A9D8))), RoundedCornerShape(25.dp))
            ) {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { c -> (android.view.LayoutInflater.from(c).inflate(R.layout.video_player_texture, null, false)
                        as androidx.media3.ui.PlayerView).apply { this.player = player } },
                    update = { view -> view.player = player },
                    modifier = Modifier.fillMaxSize().padding(5.dp).clip(RoundedCornerShape(19.dp)))
                vm.videoOverlayClips.filter { vm.playheadMs >= it.startMs && vm.playheadMs < it.endMs }.forEach { clip ->
                    val state = VideoFx.overlayStateAt(clip, vm.playheadMs)
                    val alignment = when (state.position) { 0 -> Alignment.TopCenter; 2 -> Alignment.BottomCenter; else -> Alignment.Center }
                    val activeAlpha = state.alpha.coerceIn(0f, 1f)
                    if (clip.kind == "IMAGE") {
                        vm.videoOverlayBitmap(clip.id)?.let { bitmap ->
                            Image(bitmap.asImageBitmap(), null, Modifier.align(alignment).fillMaxWidth(0.42f).alpha(activeAlpha)
                                .clickable { vm.selectOverlayClip(clip.id) }, contentScale = ContentScale.Fit)
                        }
                    } else {
                        Text(clip.content, Modifier.align(alignment).padding(12.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (clip.background) Color(0x99000000) else Color.Transparent).padding(horizontal = 10.dp, vertical = 5.dp)
                            .alpha(activeAlpha).clickable { vm.selectedOverlayClipId = clip.id },
                            color = Color(clip.color), fontSize = when (clip.size) { 0 -> 17.sp; 2 -> 34.sp; else -> 25.sp },
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        GlassPanel(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { seekToTimeline((vm.playheadMs - (vm.videoFrameDurationMs / vm.vSpeed.coerceAtLeast(0.05f)).toLong().coerceAtLeast(1L)).coerceAtLeast(0L)) }) { Text("−1 فریم") }
                Spacer(Modifier.width(6.dp))
                OutlinedButton(onClick = { player?.play() }) { Text("▶"); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.play)) }
                Spacer(Modifier.width(6.dp))
                OutlinedButton(onClick = { player?.pause() }) { Text("Ⅱ"); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.pause)) }
                Spacer(Modifier.width(6.dp))
                OutlinedButton(onClick = { seekToTimeline((vm.playheadMs + (vm.videoFrameDurationMs / vm.vSpeed.coerceAtLeast(0.05f)).toLong().coerceAtLeast(1L)).coerceAtMost(vm.videoDur.coerceAtLeast(1L))) }) { Text("+1 فریم") }
            }
        }
        GlassPanel(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmt(vm.playheadMs), color = Storm.bolt, style = MaterialTheme.typography.labelSmall)
                    Text(fmt(vm.videoDur), color = Storm.muted, style = MaterialTheme.typography.labelSmall)
                }
                Slider(value = vm.playheadMs.toFloat().coerceIn(0f, vm.videoDur.toFloat().coerceAtLeast(1f)),
                    onValueChange = { value -> seekToTimeline(value.toLong()) },
                    valueRange = 0f..vm.videoDur.toFloat().coerceAtLeast(1f))
            }
        }
        GlassPanel(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
            Column(Modifier.padding(top = 5.dp)) {
                if (vm.busy) {
                    LinearProgressIndicator(progress = { vm.progress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
                }
                Column(Modifier.heightIn(max = 230.dp).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Crossfade(targetState = tool, animationSpec = tween(180), label = "vtool") { t ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            when (t) {
                                VTool.TRIM -> {
                                    Text(stringResource(R.string.video_start) + " " + fmt(range.start.toLong()) + "    " +
                                        stringResource(R.string.video_end) + " " + fmt(range.endInclusive.toLong()),
                                        style = MaterialTheme.typography.labelLarge, color = Storm.bolt)
                                    RangeSlider(value = range, onValueChange = { r ->
                                        if (r.start != range.start) player?.seekTo(r.start.toLong()) else player?.seekTo(r.endInclusive.toLong())
                                        range = r
                                    }, valueRange = 0f..vm.videoDur.toFloat().coerceAtLeast(1f))
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Button(onClick = {
                                            if (range.endInclusive - range.start >= 100f) vm.addSeg(Seg(range.start.toLong(), range.endInclusive.toLong()))
                                        }) { Text(stringResource(R.string.add_segment)) }
                                        OutlinedButton(enabled = vm.selSeg in vm.segments.indices, onClick = {
                                            if (!vm.splitSelectedSegmentAtPlayhead()) vm.info(R.string.error_generic, "برای Split باید Playhead حداقل ۲۰۰ میلی‌ثانیه از دو لبه فاصله داشته باشد")
                                        }) { Text("Split در محل پخش") }
                                        OutlinedButton(onClick = { pickClip.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }) {
                                            Text(stringResource(R.string.add_clip))
                                        }
                                        OutlinedButton(onClick = { vm.clearSegs(); vm.selSeg = -1 }) { Text(stringResource(R.string.clear_segments)) }
                                        IconAction(Ic.UNDO, stringResource(R.string.undo), vm.canUndoSeg) { vm.undoSeg() }
                                        IconAction(Ic.REDO, stringResource(R.string.redo), vm.canRedoSeg) { vm.redoSeg() }
                                    }
                                    if (vm.segments.isEmpty()) {
                                        Text(stringResource(R.string.whole_video), style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                                    } else {
                                        // Keep the selected segment in the enclosing scope so the trim controls below can use it.
                                        val sel = vm.segments.getOrNull(vm.selSeg)
                                        Row(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(13.dp))
                                            .border(1.dp, Color(0x557EC8FF), RoundedCornerShape(13.dp)),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            vm.segments.forEachIndexed { i, sg ->
                                                val w = (sg.endMs - sg.startMs).toFloat().coerceAtLeast(1f)
                                                Box(Modifier.weight(w).fillMaxHeight()
                                                    .background(if (vm.selSeg == i) Storm.boltDim else Storm.surfaceTop)
                                                    .selectable(selected = vm.selSeg == i, onClick = { vm.selSeg = i }, role = Role.Tab),
                                                    contentAlignment = Alignment.Center) {
                                                    Text((i + 1).toString() + (if (sg.uri != null) "+" else ""),
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = if (vm.selSeg == i) Storm.bolt else Storm.text)
                                                }
                                            }
                                        }
                                        if (vm.segments.size > 1) {
                                            Text("انتخاب گروهی قطعات", style = MaterialTheme.typography.labelSmall, color = Storm.arc)
                                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                vm.segments.forEachIndexed { index, _ ->
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Checkbox(checked = index in vm.selectedSegmentIndices,
                                                            onCheckedChange = { vm.toggleSegmentSelection(index) })
                                                        Text((index + 1).toString(), style = MaterialTheme.typography.labelSmall, color = Storm.text)
                                                    }
                                                }
                                            }
                                            if (vm.selectedSegmentIndices.size > 1) {
                                                val ordered = vm.selectedSegmentIndices.sorted()
                                                val contiguous = ordered.last() - ordered.first() + 1 == ordered.size
                                                if (!contiguous) Text("برای جابه‌جایی گروهی، قطعات باید پشت‌سرهم انتخاب شوند.",
                                                    style = MaterialTheme.typography.labelSmall, color = Storm.muted)
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    OutlinedButton(enabled = contiguous && ordered.first() > 0,
                                                        onClick = { vm.moveSelectedSegmentGroup(-1) }) { Text("← انتقال گروه") }
                                                    OutlinedButton(enabled = contiguous && ordered.last() < vm.segments.lastIndex,
                                                        onClick = { vm.moveSelectedSegmentGroup(1) }) { Text("انتقال گروه →") }
                                                    OutlinedButton(onClick = { vm.deleteSelectedSegments() }) { Text("حذف گروه") }
                                                    TextButton(onClick = { vm.clearSegmentSelection() }) { Text("لغو") }
                                                }
                                            } else if (vm.selectedSegmentIndices.size == 1) {
                                                TextButton(onClick = { vm.clearSegmentSelection() }) { Text("لغو انتخاب گروه") }
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(if (sel != null) "${vm.selSeg + 1}.  ${fmt(sel.startMs)} – ${fmt(sel.endMs)}" else "",
                                                style = MaterialTheme.typography.bodySmall, color = Storm.muted, modifier = Modifier.weight(1f))
                                            OutlinedButton(enabled = sel != null, onClick = { vm.duplicateSeg(vm.selSeg) }) { Text(stringResource(R.string.copy_segment)) }
                                            OutlinedButton(enabled = sel != null && vm.selSeg > 0, onClick = { vm.moveSegment(vm.selSeg, vm.selSeg - 1) }) { Text("← جابه‌جایی") }
                                            OutlinedButton(enabled = sel != null && vm.selSeg < vm.segments.lastIndex, onClick = { vm.moveSegment(vm.selSeg, vm.selSeg + 1) }) { Text("جابه‌جایی →") }
                                            OutlinedButton(enabled = sel != null, onClick = { vm.removeSeg(vm.selSeg); vm.selSeg = -1 }) { Text(stringResource(R.string.delete_segment)) }
                                            OutlinedButton(enabled = sel != null, onClick = { vm.rippleDeleteSegment(vm.selSeg) }) { Text("حذف و بستن فاصله") }
                                        }
                                        if (sel != null) {
                                            var selectedRange by remember(vm.selSeg, sel.startMs, sel.endMs) {
                                                mutableStateOf(sel.startMs.toFloat()..sel.endMs.toFloat())
                                            }
                                            Text("برش دقیق قطعهٔ انتخاب‌شده · ${fmt(sel.startMs)} تا ${fmt(sel.endMs)}",
                                                style = MaterialTheme.typography.labelSmall, color = Storm.arc)
                                            RangeSlider(
                                                value = selectedRange,
                                                onValueChange = { r ->
                                                    selectedRange = r
                                                    vm.updateSegmentRange(vm.selSeg, r.start.toLong(), r.endInclusive.toLong())
                                                },
                                                onValueChangeFinished = { vm.finishVideoTimelineGesture() },
                                                valueRange = 0f..(if (sel.uri == null) vm.videoDur else sel.endMs).toFloat().coerceAtLeast(200f)
                                            )
                                            Text("برای تغییر ترتیب از دکمه‌های جابه‌جایی استفاده کنید؛ برش فقط محدودهٔ منبع همین قطعه را تغییر می‌دهد.",
                                                style = MaterialTheme.typography.labelSmall, color = Storm.muted)
                                        }
                                    }
                                }
                                VTool.MUSIC -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(vm.muteOriginal, { vm.muteOriginal = it; vm.commitVideoSettings() })
                                        Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.mute_original), style = MaterialTheme.typography.bodyMedium)
                                    }
                                    var fxOpen by remember { mutableStateOf(false) }
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Box {
                                            Button(onClick = { fxOpen = true }) { Text(stringResource(R.string.fx_menu)) }
                                            DropdownMenu(expanded = fxOpen, onDismissRequest = { fxOpen = false }) {
                                                fxItems.forEach { (k, res) ->
                                                    DropdownMenuItem(text = { Text(stringResource(res)) }, onClick = { fxOpen = false; vm.useFx(k, res) })
                                                }
                                            }
                                        }
                                        OutlinedButton(onClick = { pickAudio.launch("audio/*") }) { Text(stringResource(R.string.import_music)) }
                                        if (vm.music != null) TextButton(onClick = { vm.updateMusicSource(null) }) { Text(stringResource(R.string.remove_music)) }
                                    }
                                    vm.musicName?.let { Text(stringResource(R.string.fx_selected, stringResource(it)), style = MaterialTheme.typography.bodySmall, color = Storm.muted) }
                                    if (vm.music != null) AudioWaveformView(vm.musicWaveform, "موج واقعی موسیقی", Color(0xFFFFC45C), Modifier.fillMaxWidth().height(42.dp))
                                    if (vm.voice != null) AudioWaveformView(vm.voiceWaveform, "موج واقعی صدای ضبط‌شده", Color(0xFF77D7B0), Modifier.fillMaxWidth().height(42.dp))
                                    if (vm.waveformBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        if (vm.recording) {
                                            Button(onClick = { vm.stopRecording() },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                                                Text(stringResource(R.string.rec_stop))
                                            }
                                            Text(stringResource(R.string.rec_running), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                                        } else {
                                            OutlinedButton(onClick = {
                                                if (androidx.core.content.ContextCompat.checkSelfPermission(micCtx, android.Manifest.permission.RECORD_AUDIO) ==
                                                    android.content.pm.PackageManager.PERMISSION_GRANTED) vm.startRecording()
                                                else micPerm.launch(android.Manifest.permission.RECORD_AUDIO)
                                            }) { Text(stringResource(R.string.rec_start)) }
                                            if (vm.voice != null) TextButton(onClick = { vm.updateVoiceSource(null) }) { Text(stringResource(R.string.rec_remove)) }
                                        }
                                    }
                                }
                                VTool.VOLUME -> {
                                    Param(stringResource(R.string.vol_original), vm.volOrig, 0f..2f, onDone = { vm.commitVideoSettings() }) { vm.volOrig = it }
                                    Param(stringResource(R.string.vol_music), vm.volMusic, 0f..2f, onDone = { vm.commitVideoSettings() }) { vm.volMusic = it }
                                    Param(stringResource(R.string.vol_voice), vm.volVoice, 0f..2f, onDone = { vm.commitVideoSettings() }) { vm.volVoice = it }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("بی‌صدا کردن موسیقی", Modifier.weight(1f), color = Storm.text)
                                        Switch(checked = vm.muteMusic, onCheckedChange = { vm.muteMusic = it; vm.commitVideoSettings() })
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("بی‌صدا کردن صدای ضبط‌شده", Modifier.weight(1f), color = Storm.text)
                                        Switch(checked = vm.muteVoice, onCheckedChange = { vm.muteVoice = it; vm.commitVideoSettings() })
                                    }
                                    Text("Solo — پخش فقط یک ترک", style = MaterialTheme.typography.titleSmall, color = Storm.text)
                                    ChoiceRow(listOf("همه ترک‌ها", "صدای ویدئو", "موسیقی", "صدای ضبط‌شده"),
                                        when (vm.soloAudioTrack) { AudioTrackRouting.ORIGINAL -> 1; AudioTrackRouting.MUSIC -> 2; AudioTrackRouting.VOICE -> 3; else -> 0 }) {
                                        vm.soloAudioTrack = when (it) {
                                            1 -> AudioTrackRouting.ORIGINAL
                                            2 -> if (vm.music != null) AudioTrackRouting.MUSIC else AudioTrackRouting.NONE
                                            3 -> if (vm.voice != null) AudioTrackRouting.VOICE else AudioTrackRouting.NONE
                                            else -> AudioTrackRouting.NONE
                                        }
                                        vm.commitVideoSettings()
                                    }
                                    Divider(color = Storm.outline, modifier = Modifier.padding(vertical = 8.dp))
                                    Text("محو تدریجی موسیقی", style = MaterialTheme.typography.titleSmall, color = Storm.text)
                                    Param("شروع موسیقی (ثانیه)", vm.fadeInMusicMs / 1000f, 0f..5f, onDone = { vm.commitVideoSettings() }) { vm.fadeInMusicMs = (it * 1000f).toLong() }
                                    Param("پایان موسیقی (ثانیه)", vm.fadeOutMusicMs / 1000f, 0f..5f, onDone = { vm.commitVideoSettings() }) { vm.fadeOutMusicMs = (it * 1000f).toLong() }
                                    Text("محو تدریجی صدای ضبط‌شده", style = MaterialTheme.typography.titleSmall, color = Storm.text)
                                    Param("شروع صدا (ثانیه)", vm.fadeInVoiceMs / 1000f, 0f..5f, onDone = { vm.commitVideoSettings() }) { vm.fadeInVoiceMs = (it * 1000f).toLong() }
                                    Param("پایان صدا (ثانیه)", vm.fadeOutVoiceMs / 1000f, 0f..5f, onDone = { vm.commitVideoSettings() }) { vm.fadeOutVoiceMs = (it * 1000f).toLong() }
                                    Text(stringResource(R.string.vol_note), style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                                }
                                VTool.FILTER -> {
                                    ChoiceRow(VideoFx.filters.map { stringResource(it.nameRes) }, vm.vFilter) { vm.vFilter = it; vm.commitVideoSettings() }
                                    Text(stringResource(R.string.filter_note), style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                                    Button(onClick = { vm.addEffectClip() }, enabled = vm.videoDur > 0L) {
                                        Text("افزودن فیلتر انتخابی به تایم‌لاین")
                                    }
                                }
                                VTool.TEXT -> {
                                    OutlinedButton(onClick = { pickSubRip.launch(arrayOf("application/x-subrip", "text/plain", "application/octet-stream")) },
                                        enabled = vm.videoUri != null && !vm.busy, modifier = Modifier.fillMaxWidth()) {
                                        Text("واردکردن زیرنویس SRT")
                                    }
                                    OutlinedButton(onClick = { saveSubRip.launch("captions.srt") },
                                        enabled = vm.videoOverlayClips.any { it.kind == "TEXT" }, modifier = Modifier.fillMaxWidth()) {
                                        Text("خروجی زیرنویس SRT")
                                    }
                                    if (vm.subtitleImportStatus.isNotBlank()) {
                                        Text(vm.subtitleImportStatus, style = MaterialTheme.typography.labelSmall, color = Storm.arc)
                                    }
                                    OutlinedTextField(value = vm.overlayText, onValueChange = { vm.overlayText = it },
                                        label = { Text(stringResource(R.string.overlay_text)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        textColors.forEach { c ->
                                            Box(Modifier.size(40.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(c))
                                                .border(if (vm.textColor == c) 3.dp else 1.dp, if (vm.textColor == c) Storm.bolt else Storm.outline, CircleShape)
                                                .selectable(selected = vm.textColor == c, onClick = { vm.textColor = c }, role = Role.RadioButton))
                                        }
                                    }
                                    ChoiceRow(listOf(R.string.size_small, R.string.size_medium, R.string.size_large).map { stringResource(it) },
                                        vm.textSize) { vm.textSize = it }
                                    ChoiceRow(listOf(R.string.pos_top, R.string.pos_center, R.string.pos_bottom).map { stringResource(it) },
                                        vm.textPos) { vm.textPos = it }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(vm.textBg, { vm.textBg = it })
                                        Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.text_bg), style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Button(onClick = { if (vm.selectedOverlayClipId != null) vm.updateSelectedOverlayFromControls() else vm.addTextClip() },
                                        enabled = (vm.selectedOverlayClipId?.let { id -> vm.videoOverlayClips.firstOrNull { it.id == id }?.locked != true } ?: vm.overlayText.isNotBlank()) && vm.videoDur > 0L) {
                                        Text(if (vm.selectedOverlayClipId != null) "اعمال تغییرات روی لایه انتخابی" else "افزودن متن به تایم‌لاین")
                                    }
                                }
                                VTool.STICKER -> {
                                    OutlinedButton(onClick = { pickVideoImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                        enabled = vm.videoUri != null && !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("افزودن تصویر به‌عنوان لایه ویدئو") }
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        stickers.forEach { e ->
                                            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                                                .background(if (vm.sticker == e) Storm.boltDim else Storm.surfaceTop)
                                                .selectable(selected = vm.sticker == e, onClick = { vm.sticker = if (vm.sticker == e) null else e }, role = Role.RadioButton),
                                                contentAlignment = Alignment.Center) { Text(e, style = MaterialTheme.typography.titleLarge) }
                                        }
                                    }
                                    ChoiceRow(listOf(R.string.size_small, R.string.size_medium, R.string.size_large).map { stringResource(it) },
                                        vm.stickerSize) { vm.stickerSize = it }
                                    ChoiceRow(listOf(R.string.pos_top, R.string.pos_center, R.string.pos_bottom).map { stringResource(it) },
                                        vm.stickerPos) { vm.stickerPos = it }
                                    Button(onClick = { if (vm.selectedOverlayClipId != null) vm.updateSelectedOverlayFromControls() else vm.addStickerClip() },
                                        enabled = (vm.selectedOverlayClipId?.let { id -> vm.videoOverlayClips.firstOrNull { it.id == id }?.locked != true } ?: (vm.sticker != null)) && vm.videoDur > 0L) {
                                        Text(if (vm.selectedOverlayClipId != null) "اعمال تغییرات روی لایه انتخابی" else "افزودن استیکر به تایم‌لاین")
                                    }
                                }
                                VTool.SPEED -> {
                                    ChoiceRow(listOf("0.25×", "0.5×", "1×", "2×", "4×"),
                                        listOf(0.25f, 0.5f, 1f, 2f, 4f).indexOf(vm.vSpeed)) { vm.vSpeed = listOf(0.25f, 0.5f, 1f, 2f, 4f)[it]; vm.commitVideoSettings() }
                                    if (vm.vSpeed != 1f) Text(stringResource(R.string.speed_audio_note), style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                                }
                                VTool.CROP -> {
                                    ChoiceRow(cropAspects.mapIndexed { i, (_, l) -> if (i == 0) stringResource(R.string.aspect_original) else l },
                                        cropAspects.indexOfFirst { it.first == vm.vCrop }.coerceAtLeast(0)) { vm.vCrop = cropAspects[it].first; vm.commitVideoSettings() }
                                    if (vm.videoW <= 0) Text(stringResource(R.string.crop_unknown_size), style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                                }
                                VTool.ROTATE -> {
                                    OutlinedButton(onClick = { vm.vRotate = (vm.vRotate + 90) % 360; vm.commitVideoSettings() }) {
                                        Text(stringResource(R.string.rotate) + " " + vm.vRotate + "°")
                                    }
                                }
                                VTool.FLIP -> {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(vm.vFlipH, { vm.vFlipH = !vm.vFlipH; vm.commitVideoSettings() }, { Text(stringResource(R.string.flip_h)) })
                                        FilterChip(vm.vFlipV, { vm.vFlipV = !vm.vFlipV; vm.commitVideoSettings() }, { Text(stringResource(R.string.flip_v)) })
                                    }
                                }
                                VTool.ADJUST -> {
                                    Param(stringResource(R.string.brightness), vm.vBrightness, -1f..1f, onDone = { vm.commitVideoSettings() }) { vm.vBrightness = it }
                                    Param(stringResource(R.string.contrast), vm.vContrast, -1f..1f, onDone = { vm.commitVideoSettings() }) { vm.vContrast = it }
                                }
                                VTool.EXPORT -> {
                                    Text("پروفایل خروجی", style = MaterialTheme.typography.titleSmall, color = Storm.text)
                                    ChoiceRow(VideoExportProfile.values().map { it.labelFa }, vm.exportProfile.ordinal) {
                                        vm.chooseExportProfile(VideoExportProfile.values()[it])
                                    }
                                    val exportRate = vm.exportProfile.targetBitrate(vm.videoW, vm.videoH) / 1_000_000f
                                    Text("H.264 · حدود ${String.format(Locale.US, "%.1f", exportRate)} Mbps · بدون بزرگ‌نمایی اجباری",
                                        style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                                    Button(onClick = { if (vm.runVideoExportPreflight()) vm.exportVideo() }, enabled = !vm.busy,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.export_video)) }
                                    vm.exportPreflightIssues.forEach { issue ->
                                        Text("• $issue", color = Color(0xFFFFC27A), style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (vm.busy) {
                                        Text(stringResource(vm.stage), style = MaterialTheme.typography.bodyMedium)
                                        OutlinedButton(onClick = { vm.cancel() }) { Text(stringResource(R.string.cancel)) }
                                    }
                                }
                            }
                        }
                    }
                }
                Box(Modifier.padding(horizontal = 16.dp)) { InfoText(vm) }
                VideoTimelineTracks(vm, onAudioTap = { tool = VTool.MUSIC; toolCategory = "audio" }, onTextTap = { tool = VTool.TEXT; toolCategory = "text" }, onStickerTap = { tool = VTool.STICKER; toolCategory = "overlay" }, onMarkerTap = { seekToTimeline(it) })
                Box(Modifier.fillMaxWidth().height(1.dp).background(Storm.hairline))
                val tools = listOf(
                    Triple(VTool.TRIM, Ic.SCISSORS, R.string.tool_trim), Triple(VTool.MUSIC, Ic.MUSIC, R.string.tool_music),
                    Triple(VTool.VOLUME, Ic.VOLUME, R.string.tool_volume), Triple(VTool.FILTER, Ic.FILTER, R.string.tool_filter), Triple(VTool.TEXT, Ic.TEXT, R.string.tool_text),
                    Triple(VTool.STICKER, Ic.STICKER, R.string.tool_sticker), Triple(VTool.SPEED, Ic.SPEED, R.string.tool_speed),
                    Triple(VTool.CROP, Ic.CROP, R.string.tool_crop), Triple(VTool.ROTATE, Ic.ROTATE, R.string.tool_rotate),
                    Triple(VTool.FLIP, Ic.FLIP, R.string.tool_flip), Triple(VTool.ADJUST, Ic.ADJUST, R.string.tool_adjust),
                    Triple(VTool.EXPORT, Ic.EXPORT, R.string.tool_export))
                val categories = listOf(
                    "edit" to "برش و تصویر", "audio" to "صدا", "text" to "متن", "overlay" to "لایه‌ها", "export" to "خروجی")
                val visibleTools = tools.filter { (t, _, _) -> when (toolCategory) {
                    "audio" -> t == VTool.MUSIC || t == VTool.VOLUME
                    "text" -> t == VTool.TEXT
                    "overlay" -> t == VTool.STICKER
                    "export" -> t == VTool.EXPORT
                    else -> t == VTool.TRIM || t == VTool.FILTER || t == VTool.SPEED || t == VTool.CROP || t == VTool.ROTATE || t == VTool.FLIP || t == VTool.ADJUST
                } }
                Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("ابزارهای تدوین", color = Storm.text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Text("${visibleTools.size} ابزار", color = Storm.muted, style = MaterialTheme.typography.labelSmall)
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.forEach { (id, label) ->
                            FilterChip(selected = toolCategory == id, onClick = {
                                toolCategory = id
                                tool = when (id) { "audio" -> VTool.MUSIC; "text" -> VTool.TEXT; "overlay" -> VTool.STICKER; "export" -> VTool.EXPORT; else -> VTool.TRIM }
                            }, label = { Text(label, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        visibleTools.forEach { (t, ic, lb) -> ToolButton(ic, stringResource(lb), tool == t) { tool = t } }
                    }
                }
            }
        }
    }
}

/** Compact multi-track timeline inspired by mobile nonlinear editors. Tracks are selectable and
 * reflect the real project state; the these lanes render measured peak samples decoded from the selected audio files. */
@Composable
private fun AudioWaveformView(values: List<Float>, label: String, tint: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(7.dp)).background(tint.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
        if (values.isNotEmpty()) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 3.dp)) {
                val count = values.size
                val step = size.width / count.coerceAtLeast(1)
                val bar = (step * 0.66f).coerceAtLeast(1f)
                values.forEachIndexed { index, value ->
                    val h = (size.height * value.coerceIn(0.025f, 1f)).coerceAtLeast(2f)
                    drawRoundRect(tint, topLeft = androidx.compose.ui.geometry.Offset(index * step + (step - bar) / 2f, (size.height - h) / 2f),
                        size = androidx.compose.ui.geometry.Size(bar, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(bar / 2f))
                }
            }
        } else Text(label.ifBlank { "در حال تحلیل صدا…" }, color = tint, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun VideoTimelineTracks(
    vm: MainVM,
    onAudioTap: () -> Unit,
    onTextTap: () -> Unit,
    onStickerTap: () -> Unit,
    onMarkerTap: (Long) -> Unit
) {
    val duration = ((if (vm.segments.isEmpty()) vm.videoDur else vm.segments.sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) })
        / vm.vSpeed.coerceAtLeast(0.05f)).toLong().coerceAtLeast(1L)
    var keyTime by remember(vm.selectedOverlayClipId) { mutableFloatStateOf(0f) }
    var keyAlpha by remember(vm.selectedOverlayClipId) { mutableFloatStateOf(1f) }
    var keyPosition by remember(vm.selectedOverlayClipId) { mutableIntStateOf(1) }
    var timelineZoom by remember { mutableFloatStateOf(1f) }
    val timelineScroll = rememberScrollState()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("TIMELINE", style = MaterialTheme.typography.labelSmall, color = Storm.muted,
                modifier = Modifier.weight(1f))
            Text(fmt(duration), style = MaterialTheme.typography.labelSmall, color = Storm.arc)
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = vm.timelineSnapEnabled, onClick = { vm.timelineSnapEnabled = !vm.timelineSnapEnabled },
                label = { Text(if (vm.timelineSnapEnabled) "چسبندگی روشن" else "چسبندگی خاموش") })
            OutlinedButton(onClick = { vm.addVideoMarker(vm.playheadMs) }) { Text("+ نشانگر") }
        }
        if (vm.videoMarkers.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically) {
                vm.videoMarkers.forEach { marker ->
                    OutlinedButton(onClick = { onMarkerTap(marker) }, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp)) {
                        Text("◆ ${fmt(marker)}", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { vm.removeVideoMarker(marker) }, contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)) { Text("×") }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("زوم", color = Storm.muted, style = MaterialTheme.typography.labelSmall)
            Slider(value = timelineZoom, onValueChange = { timelineZoom = it }, valueRange = 1f..4f,
                modifier = Modifier.weight(1f).height(28.dp))
            Text("${timelineZoom.toInt()}×", color = Storm.arc, style = MaterialTheme.typography.labelSmall)
            TextButton(onClick = { timelineZoom = 1f; timelineScroll.dispatchRawDelta(-timelineScroll.value.toFloat()) },
                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)) {
                Text("تناسب", style = MaterialTheme.typography.labelSmall)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val scaledWidth = maxWidth * timelineZoom
            Column(Modifier.horizontalScroll(timelineScroll).width(scaledWidth), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                // Interactive ruler: tap or scrub anywhere on the timeline to move the playhead.
                // The ruler scales with the timeline, so zoom remains useful on long projects.
                Box(Modifier.fillMaxWidth().height(25.dp)
                    .pointerInput(duration) {
                        detectTapGestures { position ->
                            onMarkerTap(TimelineMath.timeAtFraction(position.x / size.width.coerceAtLeast(1), duration))
                        }
                    }
                    .pointerInput(duration) {
                        detectDragGestures(onDragStart = { position ->
                            onMarkerTap(TimelineMath.timeAtFraction(position.x / size.width.coerceAtLeast(1), duration))
                        }, onDrag = { change, _ ->
                            change.consume()
                            onMarkerTap(TimelineMath.timeAtFraction(change.position.x / size.width.coerceAtLeast(1), duration))
                        })
                    }) {
                    Row(Modifier.fillMaxSize().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        listOf("0:00", "25%", "50%", "75%", fmt(duration)).forEach {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = Storm.muted)
                        }
                    }
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        val w = size.width.coerceAtLeast(1f)
                        val x = (vm.playheadMs.toFloat() / duration.coerceAtLeast(1L)).coerceIn(0f, 1f) * w
                        drawLine(Storm.bolt, androidx.compose.ui.geometry.Offset(x, 0f),
                            androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 2.dp.toPx())
                        vm.videoMarkers.forEach { marker ->
                            val mx = (marker.toFloat() / duration.coerceAtLeast(1L)).coerceIn(0f, 1f) * w
                            drawLine(Color(0xFFFFC45C), androidx.compose.ui.geometry.Offset(mx, size.height * 0.52f),
                                androidx.compose.ui.geometry.Offset(mx, size.height), strokeWidth = 1.5.dp.toPx())
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${fmt(vm.playheadMs)}  /  ${fmt(duration)}", style = MaterialTheme.typography.labelSmall,
                        color = Storm.bolt)
                    Text("خط‌کش: جابه‌جایی پخش  •  کلیپ: کشیدن برای تغییر ترتیب", style = MaterialTheme.typography.labelSmall,
                        color = Storm.muted)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("VIDEO", Modifier.width(58.dp), style = MaterialTheme.typography.labelSmall, color = Storm.arc)
            Row(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(8.dp))
                .background(Storm.surfaceTop), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (vm.segments.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF315C79)), contentAlignment = Alignment.Center) {
                        Text("کل ویدئو", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                } else {
                    vm.segments.forEachIndexed { index, seg ->
                        val weight = (seg.endMs - seg.startMs).coerceAtLeast(1L).toFloat()
                        Box(Modifier.weight(weight).fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (vm.selSeg == index) Color(0xFFDF713E) else Color(0xFF315C79))
                            .clickable { vm.selSeg = index }
                            .pointerInput(index, vm.segments.size) {
                                var dragTotal = 0f
                                detectDragGestures(onDragStart = { dragTotal = 0f; vm.selSeg = index },
                                    onDrag = { change, amount -> change.consume(); dragTotal += amount.x },
                                    onDragEnd = {
                                        val threshold = size.width * 0.35f
                                        val destination = when {
                                            dragTotal > threshold -> index + 1
                                            dragTotal < -threshold -> index - 1
                                            else -> index
                                        }.coerceIn(0, vm.segments.lastIndex)
                                        if (destination != index) vm.moveSegment(index, destination)
                                    })
                            }, contentAlignment = Alignment.Center) {
                            Text(if (seg.uri == null) "${index + 1}  ⋮⋮" else "${index + 1} +  ⋮⋮",
                                style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
                        }
                    }
                }
            }
        }
        if (vm.music != null) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onAudioTap), verticalAlignment = Alignment.CenterVertically) {
                Text("MUSIC", Modifier.width(58.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFC45C))
                AudioWaveformView(vm.musicWaveform, "", Color(0xFFFFC45C), Modifier.weight(1f).height(30.dp))
            }
        }
        if (vm.voice != null) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onAudioTap), verticalAlignment = Alignment.CenterVertically) {
                Text("VOICE", Modifier.width(58.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF77D7B0))
                AudioWaveformView(vm.voiceWaveform, "", Color(0xFF77D7B0), Modifier.weight(1f).height(30.dp))
            }
        }
        if (vm.music == null && vm.voice == null) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onAudioTap), verticalAlignment = Alignment.CenterVertically) {
                Text("AUDIO", Modifier.width(58.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFC45C))
                Text("+ افزودن موسیقی یا صدا", color = Color(0xFFFFD58A), style = MaterialTheme.typography.labelSmall)
            }
        }
        vm.videoEffectClips.forEach { effect ->
            val durationMs = (if (vm.segments.isEmpty()) vm.videoDur else vm.segments.sumOf { (it.endMs-it.startMs).coerceAtLeast(0L) })
                .div(vm.vSpeed.coerceAtLeast(0.05f)).toLong().coerceAtLeast(1L)
            val startFraction = (effect.startMs.toFloat() / durationMs).coerceIn(0f, 1f)
            val maxWidthFraction = (1f - startFraction).coerceAtLeast(0.025f)
            val widthFraction = ((effect.endMs - effect.startMs).toFloat() / durationMs).coerceIn(0.025f, maxWidthFraction)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("FX", Modifier.width(58.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFFD6A7FF))
                BoxWithConstraints(Modifier.weight(1f).height(27.dp).clip(RoundedCornerShape(6.dp)).background(Storm.surfaceTop)) {
                    val w = maxWidth
                    val timelineWidthPx = with(LocalDensity.current) { w.toPx() }.coerceAtLeast(1f)
                    val clipWidth = (w * widthFraction).coerceAtLeast(18.dp)
                    Box(Modifier.offset(x = w * startFraction).width(clipWidth).fillMaxHeight()
                        .clip(RoundedCornerShape(5.dp)).background(if (vm.selectedVideoEffectId == effect.id) Color(0xFF8052A8) else Color(0xFF463A68))) {
                        Box(Modifier.fillMaxSize().pointerInput(effect.id, durationMs) {
                            var dragTotal = 0f
                            detectDragGestures(onDragStart = { dragTotal = 0f; vm.selectedVideoEffectId = effect.id },
                                onDrag = { change, amount ->
                                    change.consume(); dragTotal += amount.x
                                    val delta = (dragTotal / timelineWidthPx * durationMs).toLong()
                                    vm.updateEffectClip(effect.id, effect.startMs + delta, effect.endMs + delta, recordHistory = false)
                                }, onDragEnd = { vm.finishVideoTimelineGesture() }, onDragCancel = { vm.finishVideoTimelineGesture() })
                        }.clickable { vm.selectedVideoEffectId = effect.id }, contentAlignment = Alignment.Center) {
                            Text(stringResource(VideoFx.filters.getOrNull(effect.filterIndex)?.nameRes ?: R.string.filter_none),
                                color = Color.White, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                        }
                        Box(Modifier.align(Alignment.CenterStart).width(9.dp).fillMaxHeight()
                            .pointerInput(effect.id, durationMs) {
                                var dragTotal = 0f
                                detectDragGestures(onDragStart = { dragTotal = 0f; vm.selectedVideoEffectId = effect.id },
                                    onDrag = { change, amount ->
                                        change.consume(); dragTotal += amount.x
                                        val delta = (dragTotal / timelineWidthPx * durationMs).toLong()
                                        vm.updateEffectClip(effect.id, effect.startMs + delta, effect.endMs, recordHistory = false)
                                    }, onDragEnd = { vm.finishVideoTimelineGesture() }, onDragCancel = { vm.finishVideoTimelineGesture() })
                            }) {
                            Box(Modifier.align(Alignment.Center).width(3.dp).fillMaxHeight(0.65f).clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.8f)))
                        }
                        Box(Modifier.align(Alignment.CenterEnd).width(9.dp).fillMaxHeight()
                            .pointerInput(effect.id, durationMs) {
                                var dragTotal = 0f
                                detectDragGestures(onDragStart = { dragTotal = 0f; vm.selectedVideoEffectId = effect.id },
                                    onDrag = { change, amount ->
                                        change.consume(); dragTotal += amount.x
                                        val delta = (dragTotal / timelineWidthPx * durationMs).toLong()
                                        vm.updateEffectClip(effect.id, effect.startMs, effect.endMs + delta, recordHistory = false)
                                    }, onDragEnd = { vm.finishVideoTimelineGesture() }, onDragCancel = { vm.finishVideoTimelineGesture() })
                            }) {
                            Box(Modifier.align(Alignment.Center).width(3.dp).fillMaxHeight(0.65f).clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.8f)))
                        }
                    }
                }
                TextButton(onClick = { vm.removeEffectClip(effect.id) }, enabled = !effect.locked) { Text("×", color = Storm.muted) }
            }
            if (vm.selectedVideoEffectId == effect.id) {
                val start = effect.startMs.coerceIn(0L, durationMs - 1L)
                val end = effect.endMs.coerceIn(start + 1L, durationMs)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("قفل کلیپ افکت", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Storm.text)
                    Switch(checked = effect.locked, onCheckedChange = { vm.setEffectClipLocked(effect.id, it) })
                }
                Text("بازه زمانی فیلتر: ${fmt(start)} تا ${fmt(end)}", style = MaterialTheme.typography.labelSmall, color = Storm.muted)
                RangeSlider(value = start.toFloat()..end.toFloat(), onValueChange = { vm.updateEffectClip(effect.id, it.start.toLong(), it.endInclusive.toLong(), recordHistory = false) },
                    onValueChangeFinished = { vm.finishVideoTimelineGesture() }, valueRange = 0f..durationMs.toFloat(), enabled = !effect.locked)
            }
        }
        if (vm.overlayText.isNotBlank()) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onTextTap), verticalAlignment = Alignment.CenterVertically) {
                Text("TEXT", Modifier.width(58.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF73D6E8))
                Box(Modifier.weight(1f).height(25.dp).clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF24505B)), contentAlignment = Alignment.CenterStart) {
                    Text(vm.overlayText, Modifier.padding(horizontal = 9.dp), maxLines = 1,
                        style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }
        if (vm.sticker != null) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onStickerTap), verticalAlignment = Alignment.CenterVertically) {
                Text("STICKER", Modifier.width(58.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF8DD9A7))
                Box(Modifier.weight(1f).height(25.dp).clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF244B3C)), contentAlignment = Alignment.CenterStart) {
                    Text(vm.sticker ?: "", Modifier.padding(horizontal = 9.dp), color = Color.White)
                }
            }
        }
        vm.videoOverlayClips.forEach { clip ->
            val durationMs = (if (vm.segments.isEmpty()) vm.videoDur else vm.segments.sumOf { (it.endMs-it.startMs).coerceAtLeast(0L) })
                .div(vm.vSpeed.coerceAtLeast(0.05f)).toLong().coerceAtLeast(1L)
            val startFraction = (clip.startMs.toFloat() / durationMs).coerceIn(0f, 1f)
            val widthFraction = minOf(((clip.endMs - clip.startMs).toFloat() / durationMs).coerceAtLeast(0.025f), (1f-startFraction).coerceAtLeast(0.025f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(when (clip.kind) { "TEXT" -> "TEXT"; "IMAGE" -> "IMAGE"; else -> "STICKER" }, Modifier.width(58.dp),
                    style = MaterialTheme.typography.labelSmall, color = when (clip.kind) { "TEXT" -> Color(0xFF73D6E8); "IMAGE" -> Color(0xFFB7A0FF); else -> Color(0xFF8DD9A7) })
                BoxWithConstraints(Modifier.weight(1f).height(27.dp).clip(RoundedCornerShape(6.dp)).background(Storm.surfaceTop)) {
                    val totalWidth = maxWidth
                    val timelineWidthPx = with(LocalDensity.current) { totalWidth.toPx() }.coerceAtLeast(1f)
                    Box(Modifier.offset(x = totalWidth * startFraction).width((totalWidth * widthFraction).coerceAtLeast(18.dp))
                        .fillMaxHeight().clip(RoundedCornerShape(5.dp))
                        .background(if (vm.selectedOverlayClipId == clip.id || clip.id in vm.selectedOverlayClipIds) Color(0xFFDF713E) else if (clip.kind == "TEXT") Color(0xFF24505B) else if (clip.kind == "IMAGE") Color(0xFF463A68) else Color(0xFF244B3C))) {
                        Box(Modifier.fillMaxSize().pointerInput(clip.id, durationMs) {
                            var dragTotal = 0f
                            detectDragGestures(onDragStart = { dragTotal = 0f; vm.selectedOverlayClipId = clip.id },
                                onDrag = { change, amount ->
                                    change.consume(); dragTotal += amount.x
                                    val delta = (dragTotal / timelineWidthPx * durationMs).toLong()
                                    if (clip.id in vm.selectedOverlayClipIds && vm.selectedOverlayClipIds.size > 1)
                                        vm.moveOverlayGroup(clip.id, delta, recordHistory = false)
                                    else vm.updateOverlayClip(clip.id, clip.startMs + delta, clip.endMs + delta, recordHistory = false)
                                }, onDragEnd = { vm.finishVideoTimelineGesture() }, onDragCancel = { vm.finishVideoTimelineGesture() })
                        }.clickable { vm.selectedOverlayClipId = clip.id }, contentAlignment = Alignment.Center) {
                            Text(clip.content, Modifier.padding(horizontal = 8.dp), maxLines = 1, color = Color.White,
                                style = MaterialTheme.typography.labelSmall)
                        }
                        Box(Modifier.align(Alignment.CenterStart).width(9.dp).fillMaxHeight()
                            .pointerInput(clip.id, durationMs) {
                                var dragTotal = 0f
                                detectDragGestures(onDragStart = { dragTotal = 0f; vm.selectedOverlayClipId = clip.id },
                                    onDrag = { change, amount ->
                                        change.consume(); dragTotal += amount.x
                                        val delta = (dragTotal / timelineWidthPx * durationMs).toLong()
                                        vm.updateOverlayClip(clip.id, clip.startMs + delta, clip.endMs, recordHistory = false)
                                    }, onDragEnd = { vm.finishVideoTimelineGesture() }, onDragCancel = { vm.finishVideoTimelineGesture() })
                            }) {
                            Box(Modifier.align(Alignment.Center).width(3.dp).fillMaxHeight(0.65f).clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.8f)))
                        }
                        Box(Modifier.align(Alignment.CenterEnd).width(9.dp).fillMaxHeight()
                            .pointerInput(clip.id, durationMs) {
                                var dragTotal = 0f
                                detectDragGestures(onDragStart = { dragTotal = 0f; vm.selectedOverlayClipId = clip.id },
                                    onDrag = { change, amount ->
                                        change.consume(); dragTotal += amount.x
                                        val delta = (dragTotal / timelineWidthPx * durationMs).toLong()
                                        vm.updateOverlayClip(clip.id, clip.startMs, clip.endMs + delta, recordHistory = false)
                                    }, onDragEnd = { vm.finishVideoTimelineGesture() }, onDragCancel = { vm.finishVideoTimelineGesture() })
                            }) {
                            Box(Modifier.align(Alignment.Center).width(3.dp).fillMaxHeight(0.65f).clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.8f)))
                        }
                    }
                }
                TextButton(onClick = { vm.removeOverlayClip(clip.id) }, enabled = !clip.locked) { Text("×", color = Storm.muted) }
            }
            if (vm.selectedOverlayClipId == clip.id) {
                val safeStart = clip.startMs.coerceIn(0L, durationMs - 1L)
                val safeEnd = clip.endMs.coerceIn(safeStart + 1L, durationMs)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("قفل کلیپ", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Storm.text)
                    Switch(checked = clip.locked, onCheckedChange = { vm.setOverlayClipLocked(clip.id, it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("انتخاب در گروه", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Storm.text)
                    Checkbox(checked = clip.id in vm.selectedOverlayClipIds, onCheckedChange = { vm.toggleOverlaySelection(clip.id) })
                }
                if (vm.selectedOverlayClipIds.size > 1 && vm.videoOverlayClips.any { it.id in vm.selectedOverlayClipIds && it.locked }) {
                    Text("گروه شامل کلیپ قفل‌شده است؛ ابتدا قفل را بردارید.", style = MaterialTheme.typography.labelSmall, color = Storm.muted)
                }
                if (vm.selectedOverlayClipIds.size > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${vm.selectedOverlayClipIds.size} کلیپ انتخاب شده", Modifier.weight(1f), color = Storm.text, style = MaterialTheme.typography.labelSmall)
                        TextButton(onClick = { vm.removeSelectedOverlayClips() },
                            enabled = vm.videoOverlayClips.none { it.id in vm.selectedOverlayClipIds && it.locked }) { Text("حذف گروه") }
                        TextButton(onClick = { vm.clearOverlaySelection() }) { Text("لغو") }
                    }
                }
                Text("شروع: ${fmt(safeStart)}", style = MaterialTheme.typography.labelSmall, color = Storm.muted)
                Slider(value = safeStart.toFloat(), onValueChange = { vm.updateOverlayClip(clip.id, it.toLong(), safeEnd, recordHistory = false) },
                    onValueChangeFinished = { vm.finishVideoTimelineGesture() }, valueRange = 0f..(durationMs - 1L).toFloat(), enabled = !clip.locked)
                Text("پایان: ${fmt(safeEnd)}", style = MaterialTheme.typography.labelSmall, color = Storm.muted)
                Slider(value = safeEnd.toFloat(), onValueChange = { vm.updateOverlayClip(clip.id, safeStart, it.toLong(), recordHistory = false) },
                    onValueChangeFinished = { vm.finishVideoTimelineGesture() }, valueRange = (safeStart + 1L).toFloat()..durationMs.toFloat(), enabled = !clip.locked)
                Divider(color = Storm.hairline)
                Text("فریم‌های کلیدی حرکت و شفافیت", style = MaterialTheme.typography.labelMedium, color = Storm.arc)
                Text("زمان فریم: ${fmt(keyTime.toLong().coerceIn(safeStart, safeEnd))}", style = MaterialTheme.typography.labelSmall, color = Storm.muted)
                Slider(value = keyTime.coerceIn(safeStart.toFloat(), safeEnd.toFloat()), onValueChange = { keyTime = it },
                    valueRange = safeStart.toFloat()..safeEnd.toFloat())
                Text("شفافیت: ${(keyAlpha * 100).toInt()}٪", style = MaterialTheme.typography.labelSmall, color = Storm.muted)
                Slider(value = keyAlpha, onValueChange = { keyAlpha = it }, valueRange = 0f..1f)
                ChoiceRow(listOf("بالا", "وسط", "پایین"), keyPosition) { keyPosition = it }
                Button(onClick = { vm.addOverlayKeyframe(clip.id, keyTime.toLong(), keyAlpha, keyPosition) }, enabled = !clip.locked,
                    modifier = Modifier.fillMaxWidth()) { Text("ثبت فریم کلیدی") }
                if (clip.keyframes.isNotEmpty()) {
                    Text("فریم‌های ثبت‌شده", style = MaterialTheme.typography.labelSmall, color = Storm.muted)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        clip.keyframes.forEach { frame ->
                            AssistChip(onClick = { keyTime = frame.timeMs.toFloat(); keyAlpha = frame.alpha; keyPosition = frame.position },
                                label = { Text("${fmt(frame.timeMs)} · ${(frame.alpha * 100).toInt()}٪") },
                                trailingIcon = { TextButton(onClick = { vm.removeOverlayKeyframe(clip.id, frame.timeMs) }) { Text("×") } })
                        }
                    }
                }
            }
        }
        }
    }
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
fun PhotoProjectsDialog(vm: MainVM, onDismiss: () -> Unit) {
    var list by remember { mutableStateOf(vm.listPhotoProjects()) }
    AlertDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close_dialog)) } },
        title = { Text("پروژه‌های ادیت عکس") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.savePhotoProject(); onDismiss() }, enabled = vm.photoSrc != null && !vm.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("ذخیره پروژه قابل ویرایش") }
                Text("عکس اصلی، تنظیمات، ماسک‌ها و تصاویر لایه‌ها در پروژه نگهداری می‌شوند.",
                    style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                if (list.isEmpty()) Text("هنوز پروژه‌ای ذخیره نشده است.", style = MaterialTheme.typography.bodyMedium)
                list.forEach { project ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(project.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.loadPhotoProject(project.id); onDismiss() }) { Text("بازکردن") }
                        TextButton(onClick = { vm.deletePhotoProject(project.id); list = vm.listPhotoProjects() }) { Text("حذف") }
                    }
                }
            }
        })
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
