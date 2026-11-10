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
    GlassPanel(modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun Tabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        labels.forEachIndexed { i, label ->
            val isSelected = i == selected
            val bg by animateColorAsState(if (isSelected) Storm.boltDim else Color(0x221C2B3E), tween(160), label = "tabBg")
            val fg by animateColorAsState(if (isSelected) Storm.bolt else Storm.muted, tween(160), label = "tabFg")
            Surface(Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(13.dp))
                .selectable(selected = isSelected, onClick = { onSelect(i) }, role = Role.Tab),
                shape = RoundedCornerShape(13.dp), color = bg,
                border = BorderStroke(1.dp, if (isSelected) Storm.bolt.copy(alpha = 0.55f) else Storm.hairline)) {
                Box(Modifier.padding(horizontal = 13.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
                    Text(label, color = fg, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
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
        Triple(Screen.WEATHER, Ic.CLOUD, R.string.nav_weather)
    )
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(21.dp), color = Color(0xF20E1724), shadowElevation = 12.dp,
        border = BorderStroke(1.dp, Color(0x444F759A))
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            items.forEach { (screen, icon, label) ->
                val selected = vm.screen == screen
                val fg by animateColorAsState(if (selected) Storm.bolt else Storm.muted, tween(170), label = "navFg")
                Column(Modifier.weight(1f).clip(RoundedCornerShape(15.dp))
                    .background(if (selected) Brush.verticalGradient(listOf(Color(0x443B83C8), Color(0x22233D5B)))
                        else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable(role = Role.Tab) { vm.screen = screen }
                    .padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AppIcon(icon, fg, Modifier.size(21.dp), filled = selected)
                    Text(stringResource(label), color = fg, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    Box(Modifier.width(if (selected) 14.dp else 4.dp).height(2.dp).clip(CircleShape)
                        .background(if (selected) Storm.bolt else Color.Transparent))
                }
            }
        }
    }
}

/** Bottom tool strip button (icon + label), >= 72 x 56 dp. */
@Composable
fun ToolButton(ic: Ic, label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val fg by animateColorAsState(if (selected) Storm.bolt else Storm.muted, tween(160), label = "toolFg")
    Column(Modifier.widthIn(min = 62.dp).heightIn(min = 54.dp).clip(RoundedCornerShape(12.dp))
        .background(if (selected) Storm.boltDim.copy(alpha = 0.72f) else Color.Transparent)
        .selectable(selected = selected, enabled = enabled, onClick = onClick, role = Role.Tab)
        .padding(horizontal = 7.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        AppIcon(ic, if (enabled) fg else Storm.muted.copy(alpha = 0.4f), Modifier.size(21.dp), filled = selected)
        Spacer(Modifier.height(2.dp))
        Text(label, color = if (enabled) fg else Storm.muted.copy(alpha = 0.4f),
            style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

