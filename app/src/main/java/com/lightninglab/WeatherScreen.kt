package com.lightninglab

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color as ComposeColor
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
    val pickFeatureModel = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) vm.importFeatureModel(u)
    }
    val pickSynopticModel = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) vm.importSynopticModel(u)
    }
    val pickSegmentationModel = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) vm.importSegmentationModel(u)
    }
    val pickAtmosphericSegmentationModel = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) vm.importAtmosphericSegmentationModel(u)
    }
    val pickCloudVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) vm.analyzeCloudVideo(u)
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
            OutlinedButton(
                onClick = { pickCloudVideo.launch("video/*") },
                enabled = !vm.cloudVideoBusy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
            ) {
                AppIcon(Ic.CLOUD, Storm.muted, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("تحلیل آفلاین ویدئوی ابرها")
            }
            if (vm.cloudVideoBusy) {
                Panel {
                    Text("در حال استخراج و بررسی فریم‌های ویدئو…", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(progress = { vm.cloudVideoProgress }, modifier = Modifier.fillMaxWidth())
                    Text("${(vm.cloudVideoProgress * 100).toInt()}٪", style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                }
            }
            vm.cloudVideoReport?.let { report ->
                Panel {
                    Text("گزارش تحلیل ویدئو", style = MaterialTheme.typography.titleMedium)
                    Text("مدت ویدئو: ${report.durationMillis / 1000} ثانیه | فریم‌های بررسی‌شده: ${report.sampledFrames}", style = MaterialTheme.typography.bodySmall)
                    Text(if (report.modelWasAvailable) "مدل TFLite فعال بوده است؛ خروجی‌ها نامزد تشخیص‌اند." else "مدل TFLite نصب نیست؛ فقط شاخص‌های سادهٔ پوشش و بافت گزارش شده‌اند.", style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                    Text("روند زمانی: ${report.evolution.trendLabelPersian}", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text("روند پوشش ابری در ویدئو: ${report.cloudCoverTrend.labelPersian}", style = MaterialTheme.typography.titleSmall)
                    Text("میانگین ابتدای ویدئو: ${report.cloudCoverTrend.firstMeanPercent}٪ | انتها: ${report.cloudCoverTrend.lastMeanPercent}٪ | تغییر: ${if (report.cloudCoverTrend.changePercentagePoints > 0) "+" else ""}${report.cloudCoverTrend.changePercentagePoints} واحد درصد", style = MaterialTheme.typography.bodySmall)
                    Text("کمینه تا بیشینهٔ برآوردشده: ${report.cloudCoverTrend.minimumPercent} تا ${report.cloudCoverTrend.maximumPercent}٪", style = MaterialTheme.typography.bodySmall)
                    Text(report.cloudCoverTrend.notePersian, style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                    if (report.temporalConsensus.framesWithModelOutput > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text("جمع‌بندی چندفریمی مدل", style = MaterialTheme.typography.titleSmall)
                        Text("خروجی معتبر مدل در ${report.temporalConsensus.framesWithModelOutput} فریم از ${report.temporalConsensus.analyzedFrames} فریم | پایداری نامزد پرتکرار: ${report.temporalConsensus.stabilityPercent}٪", style = MaterialTheme.typography.bodySmall)
                        report.temporalConsensus.candidates.forEachIndexed { index, candidate ->
                            Text("${index + 1}. ${CloudInfo.genusName(candidate.genusCode)} — حضور در ${candidate.frameCount} فریم (${candidate.frameSharePercent}٪) | میانگین امتیاز خام ${String.format(Locale.US, "%.2f", candidate.meanRawScore)} | بیشینه ${String.format(Locale.US, "%.2f", candidate.peakRawScore)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(report.temporalConsensus.notePersian, style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("پایش زمانی ویژگی‌های ساختاری", style = MaterialTheme.typography.titleSmall)
                    if (report.structuralFeatureTrend.modelWasAvailable) {
                        Text("مدل ویژگی‌ها در ${report.structuralFeatureTrend.modelOutputFrames} فریم از ${report.structuralFeatureTrend.sampledFrames} فریم خروجی داده است.", style = MaterialTheme.typography.bodySmall)
                        if (report.structuralFeatureTrend.candidates.isEmpty()) {
                            Text("در آستانهٔ امتیاز خام ۰٫۵۰، ویژگی ساختاری پایداری پیشنهاد نشد.", style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                        } else {
                            report.structuralFeatureTrend.candidates.forEachIndexed { index, candidate ->
                                Text(
                                    "${index + 1}. ${CloudFeatureTemporalAnalyzer.displayName(candidate.code)} — ${candidate.framesDetected} فریم (${candidate.frameSharePercent}٪) | میانگین امتیاز خام ${String.format(Locale.US, "%.2f", candidate.meanRawScore)} | اوج ${String.format(Locale.US, "%.2f", candidate.peakRawScore)} | ${candidate.trendPersian}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    } else {
                        Text("برای پایش سندان، مامّا، ویرگا، آرکوس، قیف ابری و سایر ویژگی‌ها، مدل مستقل cloud_features.tflite را از بخش مدل‌ها وارد کنید.", style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                    }
                    Text(report.structuralFeatureTrend.notePersian, style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                    OutlinedButton(onClick = { vm.shareCloudVideoReport() }, modifier = Modifier.fillMaxWidth()) {
                        Text("اشتراک‌گذاری گزارش تحلیل")
                    }
                    report.evolution.genusSequence.takeIf { it.isNotEmpty() }?.let {
                        Text("جنس‌های پیشنهادی مدل: ${it.joinToString(" ← ")}", style = MaterialTheme.typography.bodySmall)
                    }
                    report.frameResults.forEach { frame ->
                        val time = String.format(Locale.US, "%02d:%02d", frame.elapsedMillis / 60000, (frame.elapsedMillis / 1000) % 60)
                        val genusText = frame.genusCode?.let { CloudInfo.genusName(it) } ?: "مدل جنس مشخصی پیشنهاد نکرد"
                        val scoreText = frame.score?.let { " · امتیاز خام ${String.format(Locale.US, "%.2f", it)}" } ?: ""
                        Text("$time — $genusText$scoreText | پوشش ${frame.coverPercent}٪ (${frame.oktas}/8 اکتا) | ${frame.kindLabel}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (report.evolution.events.isNotEmpty()) {
                        Text("رویدادهای زمانی", style = MaterialTheme.typography.titleSmall)
                        report.evolution.events.forEach { event ->
                            val time = String.format(Locale.US, "%02d:%02d", event.elapsedMillis / 60000, (event.elapsedMillis / 1000) % 60)
                            Text("$time — ${event.titlePersian}: ${event.detailPersian}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    report.evolution.cautionsPersian.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = Storm.muted) }
                    Text("محدودیت: این نسخه از نمونه‌برداری زمانی و طبقه‌بندی جنس استفاده می‌کند؛ گونه‌ها، سندان، مامّا و شدت طوفان را خودکار تشخیص نمی‌دهد.", style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                }
            }
            vm.segmentationResult?.let { result ->
                Panel {
                    Text("جداسازی ناحیهٔ ابر", style = MaterialTheme.typography.titleSmall)
                    Image(result.overlay.asImageBitmap(), null, Modifier.fillMaxWidth()
                        .aspectRatio(result.overlay.width.toFloat() / result.overlay.height).clip(MaterialTheme.shapes.medium))
                    Text("پوشش پیکسلی ابر در خروجی مدل: ${result.cloudPixelPercent}٪", style = MaterialTheme.typography.bodyMedium)
                    Text("میانگین امتیاز ماسک: ${(result.meanCloudScore * 100).roundToInt()}٪ | آستانه: ${(result.threshold * 100).roundToInt()}٪", style = MaterialTheme.typography.bodySmall)
                    Text("قرمز فقط ناحیه‌ای را نشان می‌دهد که مدل در ماسک ابر تشخیص داده است. این مقدار، اندازه‌گیری هواشناسی یا تأیید قطعی ابر نیست و به کیفیت و دادهٔ آموزش مدل وابسته است.", style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                }
            }
            vm.atmosphericSegmentationResult?.let { result ->
                Panel {
                    Text("نقشهٔ چندکلاسهٔ پدیده‌های جوی", style = MaterialTheme.typography.titleSmall)
                    Image(result.overlay.asImageBitmap(), null, Modifier.fillMaxWidth()
                        .aspectRatio(result.overlay.width.toFloat() / result.overlay.height).clip(MaterialTheme.shapes.medium))
                    result.stats.forEach { stat ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Spacer(Modifier.size(11.dp).background(ComposeColor(stat.colorArgb), CircleShape))
                            Text("${stat.labelFa}: ${stat.percent}٪", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(result.disclaimerFa, style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                }
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
                }
                vm.synopticPredictions?.let { result ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("کدهای سینوپتیکی WMO — سه تراز ابر", style = MaterialTheme.typography.titleSmall)
                        Text("سه سر مستقل CL/CM/CH؛ امتیاز خام مدل است و درصد اطمینان کالیبره‌شده نیست.",
                            style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                        listOf("CL" to result.cl, "CM" to result.cm, "CH" to result.ch).forEach { pair ->
                            val level = pair.first
                            val description = pair.second.take(3).joinToString("  |  ") { candidate ->
                                candidate.first + ": " + CloudSynopticInfo.label(level, candidate.first) +
                                    " (" + String.format(Locale.US, "%.2f", candidate.second) + ")"
                            }
                            Text(level + ": " + description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (vm.topK.isNotEmpty()) {
                    Panel {
                        Text("نامزدهای مدل (امتیاز خام؛ نه درصد اطمینان)", style = MaterialTheme.typography.titleSmall)
                        vm.topK.forEach { (c, pr) -> Text(CloudInfo.genusName(c) + "  " + String.format(Locale.US, "%.2f", pr), style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                Panel {
                    Text(stringResource(R.string.wx_genus_pick), style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CloudInfo.genusCodes.forEach { c -> FilterChip(vm.genus == c, { vm.genus = c; vm.morphologyAssessment = null }, label = { Text(c) }) }
                    }
                    Text(stringResource(R.string.wx_features_pick), style = MaterialTheme.typography.titleSmall)
                    listOf(CloudInfo.supplementary.take(8), CloudInfo.supplementary.drop(8), CloudInfo.accessoryClouds, CloudInfo.speciesVarieties.take(6),
                        CloudInfo.speciesVarieties.drop(6)).forEach { row ->
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { f -> FilterChip(f in vm.features, { vm.toggleFeature(f) }, label = { Text(f) }) }
                        }
                    }
                }
                vm.multiScaleReport?.let { report ->
                    Panel {
                        Text("تحلیل چندناحیه‌ای تصویر", style = MaterialTheme.typography.titleSmall)
                        Text(report.disclaimerPersian, style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                        report.regions.forEach { region ->
                            Spacer(Modifier.height(6.dp))
                            Text(region.regionLabelPersian, style = MaterialTheme.typography.labelLarge)
                            if (region.genusCandidates.isNotEmpty()) {
                                Text("جنس‌های پیشنهادی: " + region.genusCandidates.joinToString("، ") { (code, score) ->
                                    "${CloudInfo.genusName(code)} ${String.format(Locale.US, "%.2f", score)}"
                                }, style = MaterialTheme.typography.bodySmall)
                            } else Text("مدل جنس مشخصی پیشنهاد نکرد", style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                            if (region.featureCandidates.isNotEmpty()) {
                                Text("ویژگی‌های پیشنهادی: " + region.featureCandidates.joinToString("، ") { (code, score) ->
                                    "$code ${String.format(Locale.US, "%.2f", score)}"
                                }, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (vm.featurePredictions.isNotEmpty()) {
                    Panel {
                        Text("ویژگی‌های ساختاری پیشنهادی مدل", style = MaterialTheme.typography.titleSmall)
                        Text("این موارد خروجی یک مدل چندبرچسبی مستقل‌اند؛ امتیازها احتمال کالیبره‌شده یا تأیید قطعی نیستند.", style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                        vm.featurePredictions.forEach { (code, score) ->
                            val label = when (code) {
                                "incus" -> "Incus — سندان"; "mamma" -> "Mamma — کیسه‌های آویخته"; "virga" -> "Virga — رگه بارش تبخیرشونده"
                                "praecipitatio" -> "Praecipitatio — بارش قابل مشاهده"; "arcus" -> "Arcus — لبه قوسی"; "tuba" -> "Tuba — قیف ابری"
                                "asperitas" -> "Asperitas — ساختار موج‌دار"; "murus" -> "Murus — دیواره ابری"; "cauda" -> "Cauda — دنباله ابری"
                                "fluctus" -> "Fluctus — موج شکسته"; "cavum" -> "Cavum — حفره ابری"; else -> code
                            }
                            Text("$label · امتیاز خام ${String.format(Locale.US, "%.2f", score)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Panel {
                    Text("بررسی گونه و ویژگی‌های تکمیلی", style = MaterialTheme.typography.titleSmall)
                    Text("نشانه‌های قابل مشاهده را انتخاب کن؛ برنامه بر اساس آن‌ها نامزدهای احتمالی را از بانک دانش آفلاین پیشنهاد می‌دهد.",
                        style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                    val cueRows = listOf(
                        listOf("filaments" to "رشته‌های مستقیم/خمیده", "hooked" to "رشته‌های قلاب‌دار", "intortus" to "رشته‌های درهم‌پیچیده", "fibrous" to "بافت رشته‌ای یخی"),
                        listOf("anvil" to "سندان مشخص", "smooth_tops" to "قله صاف", "cauliflower" to "برجستگی گل‌کلمی", "shallow" to "رشد کم‌عمق"),
                        listOf("ragged" to "قطعات پاره‌پاره", "lens" to "شکل عدسی", "roll" to "ابر رول‌مانند", "layer" to "لایه یا ورقه"),
                        listOf("tufts" to "توده‌های کوچک", "hazy" to "ظاهر مه‌آلود", "mamma" to "کیسه‌های آویخته", "streaks" to "رگه‌های بارش"),
                        listOf("reaches_ground" to "بارش تا سطح زمین", "pileus" to "کلاهک بالای ابر", "arcus" to "لبه قوسی یا shelf", "funnel" to "قیف ابری"),
                        listOf("asperitas" to "زیرسطح موج‌دار", "murus" to "دیواره ابری", "cauda" to "نوار دنباله‌دار", "cavum" to "حفره در لایه"),
                        listOf("fluctus" to "موج شکسته در قله", "velum" to "پرده پیرامون برج", "pannus" to "قطعات زیر ابر", "flumen" to "نوار جریان ورودی"),
                        listOf("waves" to "الگوی موجی", "radiating" to "نوارهای شعاعی", "regular_holes" to "حفره‌های منظم", "vertebra" to "آرایش استخوان‌مانند"),
                        listOf("translucent" to "عبور نور خورشید/ماه", "gaps" to "فاصله میان اجزا", "multiple_layers" to "چند لایه ابری", "strong_growth" to "رشد قائم قوی")
                    )
                    cueRows.forEach { row ->
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { (code, label) ->
                                FilterChip(code in vm.morphologyCues, { vm.toggleMorphologyCue(code) }, label = { Text(label) })
                            }
                        }
                    }
                    Button(onClick = { vm.assessMorphology() }, enabled = vm.genus != null || vm.topK.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()) { Text("تحلیل نشانه‌ها و پیشنهاد گونه‌ها") }
                    vm.morphologyAssessment?.let { assessment ->
                        if (assessment.speciesCandidates.isEmpty() && assessment.featureCandidates.isEmpty() &&
                            assessment.varietyCandidates.isEmpty() && assessment.accessoryCandidates.isEmpty()) {
                            Text("با نشانه‌های انتخاب‌شده نامزد مشخصی پیدا نشد؛ نشانه‌ها یا جنس ابر را دوباره بررسی کن.",
                                style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                        }
                        if (assessment.speciesCandidates.isNotEmpty()) {
                            Text("گونه‌های احتمالی", style = MaterialTheme.typography.labelLarge)
                            assessment.speciesCandidates.forEach { Text("• ${it.nameEnglish} — ${it.namePersian}: ${it.evidencePersian.joinToString("، ")}", style = MaterialTheme.typography.bodySmall) }
                        }
                        if (assessment.featureCandidates.isNotEmpty()) {
                            Text("ویژگی‌های تکمیلی احتمالی", style = MaterialTheme.typography.labelLarge)
                            assessment.featureCandidates.forEach { Text("• ${it.nameEnglish} — ${it.namePersian}: ${it.evidencePersian.joinToString("، ")}", style = MaterialTheme.typography.bodySmall) }
                        }
                        if (assessment.varietyCandidates.isNotEmpty()) {
                            Text("واریته‌های احتمالی", style = MaterialTheme.typography.labelLarge)
                            assessment.varietyCandidates.forEach { Text("• ${it.nameEnglish} — ${it.namePersian}: ${it.evidencePersian.joinToString("، ")}", style = MaterialTheme.typography.bodySmall) }
                        }
                        if (assessment.accessoryCandidates.isNotEmpty()) {
                            Text("ابرهای همراه احتمالی", style = MaterialTheme.typography.labelLarge)
                            assessment.accessoryCandidates.forEach { Text("• ${it.nameEnglish} — ${it.namePersian}: ${it.evidencePersian.joinToString("، ")}", style = MaterialTheme.typography.bodySmall) }
                        }
                        assessment.cautionsPersian.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = Storm.muted) }
                    }
                }
                Button(onClick = { vm.saveInfographic() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.save_infographic))
                }
            }
            OutlinedButton(onClick = { pickModel.launch("*/*") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(if (vm.modelReady) R.string.model_replace else R.string.model_import))
            }
            if (!vm.modelReady) Text(stringResource(R.string.model_none_hint), style = MaterialTheme.typography.bodySmall, color = Storm.muted)
            OutlinedButton(onClick = { pickSynopticModel.launch("*/*") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (vm.synopticModelReady) "جایگزینی مدل سینوپتیکی CL/CM/CH" else "واردکردن مدل سینوپتیکی CL/CM/CH")
            }
            if (!vm.synopticModelReady) Text(
                "مدل سه‌خروجی CL/CM/CH همراه APK نیست. پس از آموزش با train_montenegro_synoptic.py فایل cloud_synoptic_model.tflite را وارد کنید.",
                style = MaterialTheme.typography.bodySmall, color = Storm.muted)
            OutlinedButton(onClick = { pickFeatureModel.launch("*/*") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (vm.featureModelReady) "جایگزینی مدل ویژگی‌های ساختاری" else "واردکردن مدل ویژگی‌های ساختاری")
            }
            if (!vm.featureModelReady) Text("مدل ویژگی‌ها اختیاری است؛ تا زمان آموزش و واردکردن فایل cloud_features.tflite، تشخیص خودکار این ویژگی‌ها فعال نیست.", style = MaterialTheme.typography.bodySmall, color = Storm.muted)
            OutlinedButton(onClick = { pickSegmentationModel.launch("*/*") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (vm.segmentationModelReady) "جایگزینی مدل جداسازی ابر" else "واردکردن مدل جداسازی ابر")
            }
            if (!vm.segmentationModelReady) Text(
                "مدل segmentation هنوز همراه برنامه نیست. پس از آموزش یک مدل سازگار و واردکردن cloud_segmentation.tflite، پوشش پیکسلی و تصویر ماسک‌گذاری‌شده نمایش داده می‌شود.",
                style = MaterialTheme.typography.bodySmall, color = Storm.muted)
            OutlinedButton(onClick = { pickAtmosphericSegmentationModel.launch("*/*") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (vm.atmosphericSegmentationReady) "جایگزینی مدل چندکلاسهٔ پدیده‌های جوی" else "واردکردن مدل چندکلاسهٔ پدیده‌های جوی")
            }
            if (!vm.atmosphericSegmentationReady) Text(
                "این مدل مستقل از مدل جداسازی دودویی ابر است. فایل atmosphere_multiclass.tflite باید با اسکریپت آموزش هفت‌کلاسه و قرارداد خروجی ۷ کاناله ساخته شود.",
                style = MaterialTheme.typography.bodySmall, color = Storm.muted)
            InfoText(vm)
            Spacer(Modifier.height(24.dp))
        }
    }
}
