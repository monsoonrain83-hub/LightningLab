package com.lightninglab

import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

private fun fmt(ms: Long) = String.format(Locale.US, "%d:%05.2f", ms / 60000, (ms % 60000) / 1000.0)

@Composable
fun App(vm: MainVM = viewModel()) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFF4FC3F7), background = Color(0xFF0B0F17), surface = Color(0xFF121826))) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.systemBarsPadding()) {
                when (vm.screen) {
                    Screen.HOME -> Home(vm)
                    Screen.RESULTS -> Results(vm)
                    Screen.ABOUT -> About(vm)
                }
            }
        }
    }
}

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
fun Home(vm: MainVM) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) vm.startDetect(u)
    }
    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { LangMenu() }
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.subtitle), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.sensitivity) + ": " + (vm.sensitivity * 100).roundToInt() + "%")
        Slider(value = vm.sensitivity, onValueChange = { vm.sensitivity = it }, enabled = !vm.busy)
        Button(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
            enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.detect)) }
        if (vm.busy) {
            LinearProgressIndicator(progress = { vm.progress }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(vm.stage))
            OutlinedButton(onClick = { vm.cancel() }) { Text(stringResource(R.string.cancel)) }
        }
        InfoText(vm)
        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.edit_video_soon))
        }
        OutlinedButton(onClick = { vm.screen = Screen.ABOUT }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.about))
        }
    }
}

@Composable
fun Results(vm: MainVM) {
    LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { vm.screen = Screen.HOME }) { Text(stringResource(R.string.back)) }
                    TextButton(onClick = { vm.setAll(true) }) { Text(stringResource(R.string.select_all)) }
                    TextButton(onClick = { vm.setAll(false) }) { Text(stringResource(R.string.deselect_all)) }
                }
                Text(stringResource(R.string.heuristic_note), style = MaterialTheme.typography.bodySmall)
            }
        }
        items(vm.items.size) { i ->
            val item = vm.items[i]
            Card(Modifier.clickable { vm.toggle(i) }) {
                Column {
                    Image(item.thumb.asImageBitmap(), null,
                        Modifier.fillMaxWidth().aspectRatio(item.thumb.width.toFloat() / item.thumb.height)
                            .clip(MaterialTheme.shapes.small),
                        contentScale = ContentScale.Crop)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(item.selected, { vm.toggle(i) })
                        Text("#${item.event.id}  ${fmt(item.event.bestMs)}  ·  ${(item.event.score * 100).roundToInt()}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.compose() }, enabled = !vm.busy && vm.items.any { it.selected },
                    modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.create_photo)) }
                if (vm.busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(stringResource(vm.stage))
                }
                vm.composite?.let { c ->
                    Image(c.asImageBitmap(), null, Modifier.fillMaxWidth().aspectRatio(c.width.toFloat() / c.height))
                    Button(onClick = { vm.save() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.save_png))
                    }
                }
                InfoText(vm)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun About(vm: MainVM) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.subtitle), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.about_text))
        OutlinedButton(onClick = { vm.screen = Screen.HOME }) { Text(stringResource(R.string.back)) }
    }
}
