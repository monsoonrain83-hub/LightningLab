package com.lightninglab

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

@Composable
fun WeatherScreen(vm: MainVM) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) vm.analyzeSky(u)
    }
    val pickModel = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) vm.importModel(u)
    }
    LaunchedEffect(Unit) { vm.initModel() }
    Column(Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.weather_title), style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.wx_hint), style = MaterialTheme.typography.bodySmall, color = Color(0xFF9AA7BD))
        Button(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pick_sky)) }
        vm.wxBitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().aspectRatio(it.width.toFloat() / it.height)) }
        vm.wx?.let { r ->
            Text(stringResource(R.string.wx_report, (r.cover * 100).roundToInt(), r.oktas,
                String.format(Locale.US, "%.1f", r.texture), r.cloudLuma.roundToInt(), stringResource(kindRes(r.kind))))
            Text(stringResource(R.string.wx_disclaimer), style = MaterialTheme.typography.bodySmall, color = Color(0xFF9AA7BD))
            if (vm.topK.isNotEmpty()) {
                Text(stringResource(R.string.wx_model_top), fontWeight = FontWeight.SemiBold)
                vm.topK.forEach { (c, pr) -> Text("• " + CloudInfo.genusName(c) + "  " + (pr * 100).roundToInt() + "%") }
            }
            Text(stringResource(R.string.wx_genus_pick), fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CloudInfo.genusCodes.forEach { c ->
                    FilterChip(vm.genus == c, { vm.genus = c }, label = { Text(c) })
                }
            }
            Text(stringResource(R.string.wx_features_pick), fontWeight = FontWeight.SemiBold)
            listOf(CloudInfo.supplementary.take(8), CloudInfo.supplementary.drop(8), CloudInfo.speciesVarieties.take(6),
                CloudInfo.speciesVarieties.drop(6)).forEach { row ->
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { f -> FilterChip(f in vm.features, { vm.toggleFeature(f) }, label = { Text(f) }) }
                }
            }
            Button(onClick = { vm.saveInfographic() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.save_infographic))
            }
        }
        OutlinedButton(onClick = { pickModel.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (vm.modelReady) R.string.model_replace else R.string.model_import))
        }
        if (!vm.modelReady) Text(stringResource(R.string.model_none_hint), style = MaterialTheme.typography.bodySmall, color = Color(0xFF9AA7BD))
        InfoText(vm)
        Spacer(Modifier.height(24.dp))
    }
}
