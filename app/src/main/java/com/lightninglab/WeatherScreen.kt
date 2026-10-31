package com.lightninglab

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

private fun kindRes(k: CloudAnalysis.Kind) = when (k) {
    CloudAnalysis.Kind.CLEAR -> R.string.kind_clear
    CloudAnalysis.Kind.THIN_STREAKS -> R.string.kind_thin
    CloudAnalysis.Kind.CUMULIFORM -> R.string.kind_cumuliform
    CloudAnalysis.Kind.UNIFORM_LAYER -> R.string.kind_layer
    CloudAnalysis.Kind.DARK_OVERCAST -> R.string.kind_dark
    CloudAnalysis.Kind.MIXED -> R.string.kind_mixed
}

private fun frameQualityRes(q: CloudAnalysis.FrameQuality) = when (q) {
    CloudAnalysis.FrameQuality.GOOD -> R.string.wx_frame_good
    CloudAnalysis.FrameQuality.CHECK_FRAME -> R.string.wx_frame_check
    CloudAnalysis.FrameQuality.LOW_SKY_CONTENT -> R.string.wx_frame_low_sky
}

@Composable
fun WeatherScreen(vm: MainVM) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) vm.analyzeSky(u)
    }
    val pickModel = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) vm.importModel(u)
    }
    LaunchedEffect(Unit) { vm.initModel() }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.weather_title))
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.wx_hint), style = MaterialTheme.typography.bodySmall, color = Storm.muted)
            Button(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                AppIcon(Ic.CLOUD, Storm.onBolt, Modifier.size(20.dp), filled = true)
                Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.pick_sky), style = MaterialTheme.typography.labelLarge)
            }
            vm.wxBitmap?.let {
                Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().aspectRatio(it.width.toFloat() / it.height).clip(MaterialTheme.shapes.medium))
            }
            vm.wx?.let { r ->
                Panel {
                    Text(stringResource(R.string.wx_report, (r.cover * 100).roundToInt(), r.oktas,
                        String.format(Locale.US, "%.1f", r.texture), r.cloudLuma.roundToInt(), stringResource(kindRes(r.kind))),
                        style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.wx_disclaimer), style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                    Text(stringResource(frameQualityRes(r.frameQuality)), style = MaterialTheme.typography.bodySmall,
                        color = if (r.frameQuality == CloudAnalysis.FrameQuality.GOOD) Storm.muted else MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.wx_sky_fraction, (r.skyLikeFraction * 100).roundToInt()),
                        style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                }
                if (vm.topK.isNotEmpty()) {
                    Panel {
                        Text(stringResource(R.string.wx_model_top), style = MaterialTheme.typography.titleSmall)
                        vm.topK.forEach { (c, score) -> Text(CloudInfo.genusName(c) + "  " + (score * 100).roundToInt() + "%", style = MaterialTheme.typography.bodyMedium) }
                        Text(stringResource(R.string.wx_model_score_note), style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                    }
                }
                Panel {
                    Text(stringResource(R.string.wx_genus_pick), style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CloudInfo.genusCodes.forEach { c -> FilterChip(vm.genus == c, { vm.genus = c }, label = { Text(c) }) }
                    }
                    Text(stringResource(R.string.wx_features_pick), style = MaterialTheme.typography.titleSmall)
                    listOf(CloudInfo.supplementary.take(8), CloudInfo.supplementary.drop(8), CloudInfo.speciesVarieties.take(6),
                        CloudInfo.speciesVarieties.drop(6)).forEach { row ->
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { f -> FilterChip(f in vm.features, { vm.toggleFeature(f) }, label = { Text(f) }) }
                        }
                    }
                }
                Button(onClick = { vm.saveInfographic() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.save_infographic))
                }
            }
            OutlinedButton(onClick = { pickModel.launch("*/*") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(if (vm.modelReady) R.string.model_replace else R.string.model_import))
            }
            Text(stringResource(if (vm.modelReady) R.string.wx_model_ready else R.string.wx_model_not_ready),
                style = MaterialTheme.typography.bodySmall, color = Storm.muted)
            if (!vm.modelReady) Text(stringResource(R.string.model_none_hint), style = MaterialTheme.typography.bodySmall, color = Storm.muted)
            InfoText(vm)
            Spacer(Modifier.height(24.dp))
        }
    }
}
