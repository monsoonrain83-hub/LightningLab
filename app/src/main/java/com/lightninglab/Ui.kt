package com.lightninglab

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import java.util.Locale

fun fmt(ms: Long): String = String.format(Locale.US, "%d:%05.2f", ms / 60000, (ms % 60000) / 1000.0)

@Composable
fun LangMenu() {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(stringResource(R.string.language), color = Storm.muted) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf("en" to "English", "fa" to "فارسی", "ar" to "العربية").forEach { (tag, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    open = false
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                })
            }
        }
    }
}

@Composable
fun InfoText(vm: MainVM) {
    vm.infoRes?.let { Text(stringResource(it, *vm.infoArgs.toTypedArray()), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
}

enum class Ic { BOLT, PHOTO, VIDEO, CLOUD, UNDO, REDO, BACK, CHEVRON, CLOSE,
    SCISSORS, MUSIC, FILTER, TEXT, STICKER, SPEED, CROP, ROTATE, FLIP, ADJUST, EXPORT, VOLUME }

/** Vector icons drawn on a 24-unit grid; one stroke weight everywhere. `filled` is used for the selected nav item. */
@Composable
fun AppIcon(ic: Ic, tint: Color, modifier: Modifier = Modifier.size(24.dp), filled: Boolean = false) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val st = Stroke(width = 1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val main = if (filled) Fill else st
        val inner = if (filled) Storm.surface else tint
        fun o(x: Float, y: Float) = Offset(x * u, y * u)
        fun poly(vararg p: Float, closed: Boolean = false): Path = Path().apply {
            moveTo(p[0] * u, p[1] * u)
            var i = 2
            while (i < p.size) { lineTo(p[i] * u, p[i + 1] * u); i += 2 }
            if (closed) close()
        }
        fun undoArrow() {
            drawPath(poly(9f, 6f, 4f, 11f, 9f, 16f), tint, style = st)
            val curve = Path().apply {
                moveTo(4f * u, 11f * u); lineTo(14f * u, 11f * u)
                cubicTo(17.5f * u, 11f * u, 20f * u, 13.2f * u, 20f * u, 16.2f * u)
                cubicTo(20f * u, 19.2f * u, 17.5f * u, 21f * u, 14f * u, 21f * u)
            }
            drawPath(curve, tint, style = st)
        }
        when (ic) {
            Ic.BOLT -> drawPath(poly(13.5f, 2.5f, 5.5f, 13f, 11f, 13f, 10f, 21.5f, 18.5f, 10.5f, 13f, 10.5f, closed = true), tint, style = main)
            Ic.PHOTO -> {
                drawRoundRect(tint, Offset(3f * u, 4.5f * u), Size(18f * u, 15f * u), CornerRadius(3f * u, 3f * u), style = main)
                drawCircle(inner, 1.7f * u, Offset(8.5f * u, 9.5f * u))
                drawPath(poly(4f, 17f, 9f, 12.5f, 13f, 15.5f, 16f, 13f, 20f, 16.5f), inner, style = st)
            }
            Ic.VIDEO -> {
                drawRoundRect(tint, Offset(3f * u, 5.5f * u), Size(18f * u, 13f * u), CornerRadius(3f * u, 3f * u), style = main)
                drawPath(poly(10f, 9f, 10f, 15f, 15.5f, 12f, closed = true), inner, style = Fill)
            }
            Ic.CLOUD -> {
                fun circ(cx: Float, cy: Float, r: Float) = Path().apply { addOval(Rect(Offset(cx * u, cy * u), r * u)) }
                var cloud = Path.combine(PathOperation.Union, circ(8f, 14f, 4f), circ(13f, 11.5f, 5f))
                cloud = Path.combine(PathOperation.Union, cloud, circ(17.5f, 14.5f, 3.5f))
                val base = Path().apply { addRect(Rect(Offset(8f * u, 13f * u), Size(9.5f * u, 5.5f * u))) }
                cloud = Path.combine(PathOperation.Union, cloud, base)
                drawPath(cloud, tint, style = main)
            }
            Ic.UNDO -> undoArrow()
            Ic.REDO -> scale(-1f, 1f, pivot = Offset(size.width / 2f, size.height / 2f)) { undoArrow() }
            Ic.BACK -> {
                if (rtl) scale(-1f, 1f, pivot = Offset(size.width / 2f, size.height / 2f)) { drawPath(poly(15f, 5f, 8f, 12f, 15f, 19f), tint, style = st) }
                else drawPath(poly(15f, 5f, 8f, 12f, 15f, 19f), tint, style = st)
            }
            Ic.SCISSORS -> {
                drawCircle(tint, 2.6f * u, o(7f, 18f), style = st); drawCircle(tint, 2.6f * u, o(17f, 18f), style = st)
                drawPath(poly(8.6f, 15.8f, 17f, 4.5f), tint, style = st); drawPath(poly(15.4f, 15.8f, 7f, 4.5f), tint, style = st)
            }
            Ic.MUSIC -> {
                drawCircle(tint, 2.8f * u, o(10f, 17.5f), style = st)
                drawPath(poly(12.8f, 17.5f, 12.8f, 4.5f, 18.5f, 7f), tint, style = st)
            }
            Ic.FILTER -> {
                drawCircle(tint, 4.2f * u, o(12f, 8.5f), style = st); drawCircle(tint, 4.2f * u, o(8f, 14.5f), style = st)
                drawCircle(tint, 4.2f * u, o(16f, 14.5f), style = st)
            }
            Ic.TEXT -> {
                drawPath(poly(5f, 6f, 19f, 6f), tint, style = st); drawPath(poly(12f, 6f, 12f, 19f), tint, style = st)
            }
            Ic.STICKER -> {
                drawCircle(tint, 9f * u, o(12f, 12f), style = st)
                drawCircle(tint, 1.1f * u, o(9f, 10f)); drawCircle(tint, 1.1f * u, o(15f, 10f))
                drawArc(tint, 25f, 130f, false, o(7.5f, 8.5f), Size(9f * u, 7f * u), style = st)
            }
            Ic.SPEED -> {
                drawArc(tint, 180f, 180f, false, o(3f, 6f), Size(18f * u, 18f * u), style = st)
                drawPath(poly(12f, 15f, 17f, 9f), tint, style = st); drawCircle(tint, 1.4f * u, o(12f, 15f))
            }
            Ic.CROP -> {
                drawPath(poly(7f, 3f, 7f, 17f, 21f, 17f), tint, style = st); drawPath(poly(3f, 7f, 17f, 7f, 17f, 21f), tint, style = st)
            }
            Ic.ROTATE -> {
                drawArc(tint, 40f, 270f, false, o(5f, 5f), Size(14f * u, 14f * u), style = st)
                drawPath(poly(15f, 3f, 19f, 5.5f, 14.5f, 8f), tint, style = st)
            }
            Ic.FLIP -> {
                drawPath(poly(12f, 3f, 12f, 21f), tint, style = st)
                drawPath(poly(9f, 6f, 3f, 18f, 9f, 18f, closed = true), tint, style = st)
                drawPath(poly(15f, 6f, 21f, 18f, 15f, 18f, closed = true), tint, style = st)
            }
            Ic.ADJUST -> {
                drawPath(poly(3f, 6f, 21f, 6f), tint, style = st); drawPath(poly(3f, 12f, 21f, 12f), tint, style = st)
                drawPath(poly(3f, 18f, 21f, 18f), tint, style = st)
                drawCircle(tint, 2.3f * u, o(8f, 6f)); drawCircle(tint, 2.3f * u, o(16f, 12f)); drawCircle(tint, 2.3f * u, o(10f, 18f))
            }
            Ic.EXPORT -> {
                drawPath(poly(12f, 4f, 12f, 15f), tint, style = st); drawPath(poly(7f, 10f, 12f, 15f, 17f, 10f), tint, style = st)
                drawPath(poly(5f, 19f, 19f, 19f), tint, style = st)
            }
            Ic.VOLUME -> {
                drawPath(poly(4f, 9f, 8f, 9f, 13f, 5f, 13f, 19f, 8f, 15f, 4f, 15f, closed = true), tint, style = main)
                drawArc(tint, -50f, 100f, false, o(11f, 8f), Size(7f * u, 8f * u), style = st)
            }
            Ic.CLOSE -> {
                drawPath(poly(6f, 6f, 18f, 18f), tint, style = st)
                drawPath(poly(18f, 6f, 6f, 18f), tint, style = st)
            }
            Ic.CHEVRON -> {
                if (rtl) scale(-1f, 1f, pivot = Offset(size.width / 2f, size.height / 2f)) { drawPath(poly(9f, 5f, 16f, 12f, 9f, 19f), tint, style = st) }
                else drawPath(poly(9f, 5f, 16f, 12f, 9f, 19f), tint, style = st)
            }
        }
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            val backDesc = stringResource(R.string.back)
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = backDesc }) { AppIcon(Ic.BACK, Storm.text) }
        } else Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun IconAction(ic: Ic, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.semantics { contentDescription = description }) {
        AppIcon(ic, if (enabled) Storm.text else Storm.muted.copy(alpha = 0.4f))
    }
}

/** Grouped content block: hairline border, no heavy shadow, animates its height when content changes. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth().animateContentSize(), shape = MaterialTheme.shapes.medium, color = Storm.surfaceHi,
        border = BorderStroke(1.dp, Storm.hairline)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun Tabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, l ->
            val sel = i == selected
            val bg by animateColorAsState(if (sel) Storm.boltDim else Color.Transparent, tween(160), label = "tabBg")
            val fg by animateColorAsState(if (sel) Storm.bolt else Storm.muted, tween(160), label = "tabFg")
            Box(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(bg)
                .selectable(selected = sel, onClick = { onSelect(i) }, role = Role.Tab).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                Text(l, color = fg, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun FeatureCard(title: String, subtitle: String?, enabled: Boolean = true, icon: Ic? = null, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(enabled = enabled, role = Role.Button) { onClick() },
        shape = MaterialTheme.shapes.medium, color = Storm.surfaceHi, border = BorderStroke(1.dp, Storm.hairline)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Storm.boltDim), contentAlignment = Alignment.Center) {
                    AppIcon(icon, if (enabled) Storm.bolt else Storm.muted, Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Storm.muted)
            }
            AppIcon(Ic.CHEVRON, Storm.muted, Modifier.size(20.dp))
        }
    }
}

@Composable
fun Param(label: String, v: Float, range: ClosedFloatingPointRange<Float>, onDone: () -> Unit = {}, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(String.format(Locale.US, "%.2f", v), style = MaterialTheme.typography.labelMedium,
                color = if (v != 0f) Storm.bolt else Storm.muted)
        }
        Slider(value = v, onValueChange = onChange, onValueChangeFinished = onDone, valueRange = range,
            modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
fun NavBar(vm: MainVM) {
    val items = listOf(
        Triple(Screen.HOME, Ic.BOLT, R.string.nav_lightning),
        Triple(Screen.PHOTO, Ic.PHOTO, R.string.nav_photo),
        Triple(Screen.VIDEO, Ic.VIDEO, R.string.nav_video),
        Triple(Screen.WEATHER, Ic.CLOUD, R.string.nav_weather))
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Storm.hairline))
        NavigationBar(containerColor = Storm.surface, tonalElevation = 0.dp) {
            items.forEach { (sc, ic, lb) ->
                val sel = vm.screen == sc
                NavigationBarItem(selected = sel, onClick = { vm.screen = sc },
                    icon = { AppIcon(ic, if (sel) Storm.bolt else Storm.muted, filled = sel) },
                    label = { Text(stringResource(lb), style = MaterialTheme.typography.labelMedium) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Storm.bolt, selectedTextColor = Storm.bolt,
                        indicatorColor = Storm.boltDim, unselectedIconColor = Storm.muted, unselectedTextColor = Storm.muted))
            }
        }
    }
}

/** Hero artwork drawn in code: storm-cloud mass, arc-blue glow and a branching bolt that flickers every few seconds. */
@Composable
fun LightningHero(modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "hero")
    val ctx = LocalContext.current
    val reduceMotion = remember {
        android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val flickAnim by tr.animateFloat(0f, 1f, infiniteRepeatable(keyframes<Float> {
        durationMillis = 5200
        0f at 0; 0f at 3400; 1f at 3460; 0.25f at 3560; 0.9f at 3640; 0f at 4100
    }), label = "flick")
    val flick = if (reduceMotion) 0.45f else flickAnim   // respect "remove animations": static glow, no flicker
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0A0E16), Color(0xFF131C2E), Storm.bg)))
        drawCircle(Brush.radialGradient(listOf(Color(0x55283853), Color.Transparent), center = Offset(w * 0.3f, 0f), radius = w * 0.8f),
            radius = w * 0.8f, center = Offset(w * 0.3f, 0f))
        drawCircle(Brush.radialGradient(listOf(Color(0x44202C45), Color.Transparent), center = Offset(w * 0.85f, h * 0.05f), radius = w * 0.6f),
            radius = w * 0.6f, center = Offset(w * 0.85f, h * 0.05f))
        val bx = w * 0.74f
        drawCircle(Brush.radialGradient(listOf(Storm.arc.copy(alpha = 0.08f + 0.26f * flick), Color.Transparent),
            center = Offset(bx, h * 0.55f), radius = h * 0.9f), radius = h * 0.9f, center = Offset(bx, h * 0.55f))
        fun px(x: Float) = bx + (x - 0.5f) * h * 0.9f
        val main = Path().apply {
            val p = listOf(0.62f to 0f, 0.50f to 0.22f, 0.58f to 0.24f, 0.42f to 0.50f, 0.52f to 0.52f, 0.34f to 0.80f, 0.40f to 0.82f, 0.28f to 1f)
            p.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(px(x), y * h) else lineTo(px(x), y * h) }
        }
        val branch = Path().apply {
            moveTo(px(0.46f), 0.38f * h); lineTo(px(0.60f), 0.55f * h); lineTo(px(0.57f), 0.62f * h); lineTo(px(0.66f), 0.80f * h)
        }
        val a = 0.25f + 0.75f * flick
        listOf(26f to 0.06f, 14f to 0.12f, 7f to 0.25f).forEach { (wd, al) ->
            drawPath(main, Storm.arc.copy(alpha = al * a), style = Stroke(wd.dp.toPx() / 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        drawPath(branch, Storm.arc.copy(alpha = 0.5f * a), style = Stroke(1.6f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(main, Storm.bolt.copy(alpha = 0.55f + 0.45f * flick), style = Stroke(2.6f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(main, Color.White.copy(alpha = 0.35f + 0.6f * flick), style = Stroke(1.1f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Bottom tool strip button (icon + label), >= 72 x 56 dp. */
@Composable
fun ToolButton(ic: Ic, label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val fg by animateColorAsState(if (selected) Storm.bolt else Storm.muted, tween(160), label = "toolFg")
    Column(Modifier.widthIn(min = 72.dp).heightIn(min = 56.dp).clip(MaterialTheme.shapes.small)
        .selectable(selected = selected, enabled = enabled, onClick = onClick, role = Role.Tab).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        AppIcon(ic, fg, Modifier.size(24.dp), filled = false)
        Spacer(Modifier.height(2.dp))
        Text(label, color = fg, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
