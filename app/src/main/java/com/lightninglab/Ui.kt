package com.lightninglab

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
        TextButton(onClick = { open = true }) { Text("🌐 " + stringResource(R.string.language)) }
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
    vm.infoRes?.let { Text(stringResource(it, *vm.infoArgs.toTypedArray()), color = MaterialTheme.colorScheme.primary) }
}

@Composable
fun FeatureCard(title: String, subtitle: String?, enabled: Boolean = true, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(enabled = enabled) { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151D2E))
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape)
                .background(if (enabled) MaterialTheme.colorScheme.primary else Color.Gray))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9AA7BD))
            }
        }
    }
}

@Composable
fun Param(label: String, v: Float, range: ClosedFloatingPointRange<Float>, onDone: () -> Unit = {}, onChange: (Float) -> Unit) {
    Column {
        Text(label + "  " + String.format(Locale.US, "%.2f", v), style = MaterialTheme.typography.bodySmall)
        Slider(value = v, onValueChange = onChange, onValueChangeFinished = onDone, valueRange = range)
    }
}

@Composable
fun NavBar(vm: MainVM) {
    val items = listOf(
        Triple(Screen.HOME, "⚡", R.string.nav_lightning),
        Triple(Screen.PHOTO, "🖼", R.string.nav_photo),
        Triple(Screen.VIDEO, "🎬", R.string.nav_video),
        Triple(Screen.WEATHER, "☁", R.string.nav_weather))
    NavigationBar {
        items.forEach { (sc, ic, lb) ->
            NavigationBarItem(selected = vm.screen == sc, onClick = { vm.screen = sc },
                icon = { Text(ic) }, label = { Text(stringResource(lb)) })
        }
    }
}
