package com.lightninglab

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lightninglab.atmospheric.knowledge.*

private enum class AtlasTab(val title: String) {
    GENERA("جنس‌های ابر"), SPECIES("گونه‌ها و واریته‌ها"), FEATURES("ویژگی‌های ساختاری"),
    ACCESSORY("ابرهای همراه"), SATELLITE("الگوهای ماهواره‌ای")
}

@Composable
fun CloudAtlasScreen(vm: MainVM) {
    val context = LocalContext.current
    val repository = remember(context) { AtmosphericKnowledgeRepository(context) }
    var tab by remember { mutableStateOf(AtlasTab.GENERA) }
    var selectedGenus by remember { mutableStateOf("Cb") }
    var selectedRecord by remember { mutableStateOf<PhenomenonRecord?>(null) }
    var selectedTaxonomy by remember { mutableStateOf<TaxonomyEntry?>(null) }
    val genera = remember(repository) { repository.cloudGenera() }
    val features = remember(repository) { repository.supplementaryFeatures() }
    val accessories = remember(repository) { repository.accessoryClouds() }
    val varieties = remember(repository) { repository.varieties() }
    val species = remember(repository, selectedGenus) { repository.reportFor(selectedGenus)?.species.orEmpty() }

    Column(Modifier.fillMaxSize()) {
        TopBar("Cloud Atlas | اطلس ابرها", onBack = { vm.screen = Screen.HOME })
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("دانشنامه و موتور شناسایی جوی", style = MaterialTheme.typography.titleMedium)
                        Text("اطلس آفلاین روی دستگاه · طبقه‌بندی علمی WMO", style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                    }
                    Surface(shape = RoundedCornerShape(50), color = Color(0x222DCA91), border = BorderStroke(1.dp, Color(0x6659D9A6))) {
                        Text("OFFLINE", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = Color(0xFF8BE0B8), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                AtlasTab.entries.forEach { item ->
                    FilterChip(selected = tab == item, onClick = { tab = item; selectedTaxonomy = null; selectedRecord = null }, label = { Text(item.title) })
                }
            }
            Spacer(Modifier.height(8.dp))
            when (tab) {
                AtlasTab.GENERA -> {
                    if (selectedRecord == null) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                            items(genera, key = { it.code }) { record ->
                                AtlasRecordCard(record = record, selected = selectedGenus == record.code) {
                                    selectedGenus = record.code
                                    selectedRecord = record
                                }
                            }
                        }
                    } else {
                        AtlasGenusDetail(record = selectedRecord!!, report = repository.reportFor(selectedRecord!!.code), onBack = { selectedRecord = null })
                    }
                }
                AtlasTab.SPECIES -> {
                    Column(Modifier.fillMaxSize()) {
                        Text("جنس پایه را انتخاب کن", color = Storm.muted, style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            genera.forEach { g -> FilterChip(selected = selectedGenus == g.code, onClick = { selectedGenus = g.code }, label = { Text(g.code) }) }
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                            items(species, key = { it.code }) { entry -> TaxonomyCard(entry) }
                            item { Text("واریته‌های ثبت‌شده در اطلس", style = MaterialTheme.typography.titleSmall, color = Storm.arc) }
                            items(varieties, key = { "v-${it.code}" }) { entry -> TaxonomyCard(entry) }
                        }
                    }
                }
                AtlasTab.FEATURES -> TaxonomyList(features, selectedTaxonomy) { selectedTaxonomy = it }
                AtlasTab.ACCESSORY -> TaxonomyList(accessories, selectedTaxonomy) { selectedTaxonomy = it }
                AtlasTab.SATELLITE -> SatelliteAtlasContent()
            }
        }
    }
}

@Composable
private fun AtlasRecordCard(record: PhenomenonRecord, selected: Boolean, onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp), color = if (selected) Color(0xCC202C3C) else Color(0xAA151E2B),
        border = BorderStroke(1.dp, if (selected) Storm.arc.copy(alpha = .65f) else Storm.hairline)
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Storm.arc.copy(alpha = .28f), Storm.bolt.copy(alpha = .10f)))), contentAlignment = Alignment.Center) {
                Text(record.code, color = Storm.arc, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(record.namePersian, style = MaterialTheme.typography.titleSmall)
                Text(record.nameEnglish, style = MaterialTheme.typography.labelMedium, color = Storm.muted)
                Text(record.summaryPersian, style = MaterialTheme.typography.bodySmall, color = Storm.muted, maxLines = 2)
            }
            Text("›", color = Storm.arc, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun AtlasGenusDetail(record: PhenomenonRecord, report: AtmosphericKnowledgeReport?, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        TextButton(onClick = onBack) { Text("← بازگشت به فهرست جنس‌ها") }
        Panel {
            Text(record.namePersian, style = MaterialTheme.typography.headlineSmall)
            Text("${record.nameEnglish} · ${record.code}", color = Storm.arc, style = MaterialTheme.typography.titleMedium)
            Text(record.summaryPersian, style = MaterialTheme.typography.bodyMedium)
            record.visualCuesPersian?.let { Text("نشانه‌های دیداری: $it", style = MaterialTheme.typography.bodySmall, color = Storm.muted) }
            report?.cloudProfile?.let { p ->
                Text("خانواده: ${p.family}", style = MaterialTheme.typography.bodySmall)
                p.typicalBasePersian?.let { Text("ارتفاع معمول پایه: $it", style = MaterialTheme.typography.bodySmall) }
                p.verticalExtentPersian?.let { Text("گسترش عمودی: $it", style = MaterialTheme.typography.bodySmall) }
                p.formationPersian?.let { Text("چگونگی تشکیل: $it", style = MaterialTheme.typography.bodySmall) }
                p.weatherPersian?.let { Text("ارتباط با وضعیت هوا: $it", style = MaterialTheme.typography.bodySmall) }
            }
            record.caveatsPersian?.let { Text("ملاحظات: $it", style = MaterialTheme.typography.bodySmall, color = Storm.muted) }
        }
        report?.altitude?.takeIf { it.isNotEmpty() }?.let { notes -> Panel {
            Text("اطلاعات ارتفاعی", style = MaterialTheme.typography.titleSmall)
            notes.forEach { Text("${it.layer}: ${it.rangePersian} — ${it.qualificationPersian}", style = MaterialTheme.typography.bodySmall) }
        } }
        report?.species?.takeIf { it.isNotEmpty() }?.let { list -> Panel {
            Text("گونه‌های ثبت‌شده", style = MaterialTheme.typography.titleSmall)
            list.forEach { Text("• ${it.namePersian} (${it.nameEnglish}) — ${it.descriptionPersian}", style = MaterialTheme.typography.bodySmall) }
        } }
        report?.formation?.takeIf { it.isNotEmpty() }?.let { notes -> Panel {
            Text("شرایط و سازوکار تشکیل", style = MaterialTheme.typography.titleSmall)
            notes.forEach { Text("${it.conditionPersian}: ${it.mechanismPersian}", style = MaterialTheme.typography.bodySmall) }
        } }
        Text("این صفحه دانشنامه‌ای است و به‌تنهایی به معنی تشخیص این ابر در تصویر فعلی نیست.", style = MaterialTheme.typography.bodySmall, color = Storm.muted)
    }
}

@Composable
private fun TaxonomyList(entries: List<TaxonomyEntry>, selected: TaxonomyEntry?, onSelect: (TaxonomyEntry) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
        items(entries, key = { it.code }) { entry ->
            Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onSelect(entry) }, color = Color(0xAA151E2B), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, if (selected?.code == entry.code) Storm.arc else Storm.hairline)) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${entry.namePersian} · ${entry.code}", style = MaterialTheme.typography.titleSmall)
                    Text(entry.nameEnglish, color = Storm.arc, style = MaterialTheme.typography.labelMedium)
                    if (selected?.code == entry.code) Text(entry.descriptionPersian, style = MaterialTheme.typography.bodySmall, color = Storm.muted)
                }
            }
        }
    }
}

@Composable
private fun TaxonomyCard(entry: TaxonomyEntry) {
    Panel {
        Text("${entry.namePersian} · ${entry.code}", style = MaterialTheme.typography.titleSmall)
        Text(entry.nameEnglish, color = Storm.arc, style = MaterialTheme.typography.labelMedium)
        Text(entry.descriptionPersian, style = MaterialTheme.typography.bodySmall, color = Storm.muted)
    }
}

@Composable
private fun SatelliteAtlasContent() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Panel {
            Text("AICCA · اطلس هوشمند الگوهای ابر ماهواره‌ای", style = MaterialTheme.typography.titleMedium)
            Text("این پروژه با خوشه‌بندی بدون‌ناظر تصاویر MODIS ماهواره‌های Aqua و Terra، الگوهای ابرهای اقیانوسی را در ۴۲ گروه داده‌محور دسته‌بندی کرده است.", style = MaterialTheme.typography.bodySmall)
            Text("کاربرد در Lightning Lab: اطلس پژوهشی و تحلیل الگوهای سازمان‌یافتگی ابر از داده ماهواره‌ای؛ نه تشخیص مستقیم عکس دوربین گوشی.", color = Storm.arc, style = MaterialTheme.typography.bodySmall)
            Text("محدودیت دسترسی: مخزن RDCEP اعلام کرده دسترسی کامل داده در حال حاضر محدود شده و بخشی از داده‌ها فقط با درخواست یا ثبت‌نام انتقال داده می‌شوند.", color = Storm.muted, style = MaterialTheme.typography.bodySmall)
        }
        listOf(
            "Fish" to "الگوهای رگه‌ای و شاخه‌ای سازمان‌یافتگی ابرهای دریایی.",
            "Flower" to "ساختارهای گل‌مانند در میدان ابرهای همرفتی دریایی.",
            "Gravel" to "بافت دانه‌دانه و خوشه‌ای ابرها.",
            "Sugar" to "بافت ریزدانه و پراکنده ابرهای کم‌عمق."
        ).forEach { (name, desc) -> Panel {
            Text(name, style = MaterialTheme.typography.titleSmall, color = Storm.arc)
            Text(desc, style = MaterialTheme.typography.bodySmall)
        } }
        Panel {
            Text("سایر منابع بررسی‌شده", style = MaterialTheme.typography.titleSmall)
            Text("Ground-based Cloud Classification: نزدیک‌ترین گزینه به تصاویر RGB دوربین زمینی و دارای مجوز MIT.", style = MaterialTheme.typography.bodySmall)
            Text("IRIS-CloudDeep: داده و مدل‌های مادون‌قرمز حرارتی شبانه؛ برای دوربین RGB موبایل قابل تعمیم مستقیم نیست.", style = MaterialTheme.typography.bodySmall)
            Text("Mask R-CNN Cloud Organization: برای الگوهای ماهواره‌ای Fish / Flower / Gravel / Sugar؛ گزارش پروژه mAP حدود 0.23 دارد و برای محصول عملیاتی نیازمند بهبود است.", style = MaterialTheme.typography.bodySmall)
        }
        Text("مدل ماهواره‌ای و مدل دوربین زمینی باید جداگانه آموزش، ارزیابی و در برنامه نگهداری شوند.", color = Storm.muted, style = MaterialTheme.typography.bodySmall)
    }
}
