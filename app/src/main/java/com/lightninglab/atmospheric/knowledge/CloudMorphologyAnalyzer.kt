package com.lightninglab.atmospheric.knowledge

/**
 * Evidence-led morphology assessor for cloud species and supplementary features.
 * This is NOT an image classifier: callers must supply observations from a person
 * or a separately validated vision model. It never upgrades a candidate to a
 * confirmed identification and never infers storm severity from morphology alone.
 */
data class CloudVisualEvidence(
    val genusCode: String,
    val hookedOrCurvedFibres: Boolean = false,
    val straightOrCurvedFilaments: Boolean = false,
    val denseFibrousVeil: Boolean = false,
    val distinctAnvil: Boolean = false,
    val smoothRoundedTowerTops: Boolean = false,
    val cauliflowerTowers: Boolean = false,
    val shallowSmallPuffs: Boolean = false,
    val moderateVerticalGrowth: Boolean = false,
    val strongVerticalGrowth: Boolean = false,
    val raggedFragments: Boolean = false,
    val lensShape: Boolean = false,
    val horizontalRoll: Boolean = false,
    val layerOrSheet: Boolean = false,
    val smallDetachedTufts: Boolean = false,
    val uniformHazyAppearance: Boolean = false,
    val hangingPouches: Boolean = false,
    val precipitationStreaks: Boolean = false,
    val precipitationReachesGround: Boolean = false,
    val smoothCapAboveTower: Boolean = false,
    val horizontalBannerOrShelf: Boolean = false,
    val funnelOrHangingColumn: Boolean = false,
    val waveLikeUnderside: Boolean = false,
    val wallCloudAppearance: Boolean = false,
    val tailCloudBand: Boolean = false,
    val holeInCloudLayer: Boolean = false,
    val waveBreakingTop: Boolean = false,
    val sunOrMoonVisibleThroughCloud: Boolean = false,
    val clearGapsBetweenCloudElements: Boolean = false,
    val multipleCloudLayers: Boolean = false,
    val visibleWavePattern: Boolean = false,
    val radiatingOrConvergingBands: Boolean = false,
    val regularCloudHoles: Boolean = false,
    val vertebraLikeArrangement: Boolean = false,
    val sheetAroundTower: Boolean = false,
    val raggedFragmentsUnderMainCloud: Boolean = false,
    val inflowBandToStorm: Boolean = false,
    val intertwinedFibres: Boolean = false
)

data class MorphologyCandidate(
    val code: String,
    val nameEnglish: String,
    val namePersian: String,
    val score: Int,
    val evidencePersian: List<String>,
    val statusPersian: String = "نامزد احتمالی؛ نیازمند بررسی تصویر و مشاهده مستقل"
)

data class MorphologyAssessment(
    val genusCode: String,
    val speciesCandidates: List<MorphologyCandidate>,
    val featureCandidates: List<MorphologyCandidate>,
    val varietyCandidates: List<MorphologyCandidate>,
    val accessoryCandidates: List<MorphologyCandidate>,
    val cautionsPersian: List<String>
)

class CloudMorphologyAnalyzer(private val knowledge: AtmosphericKnowledgeRepository) {
    private data class Rule(
        val code: String,
        val points: List<Pair<(CloudVisualEvidence) -> Boolean, String>>
    )

    fun assess(evidence: CloudVisualEvidence): MorphologyAssessment {
        val genus = evidence.genusCode.trim().uppercase()
        val speciesRules = speciesRules(genus)
        val species = rank(speciesRules, evidence).filter { it.score > 0 }
        val features = rank(featureRules().filter { it.code in featureCodes(genus) }, evidence, "feature").filter { it.score > 0 }
        val varieties = rank(varietyRules(genus), evidence, "variety").filter { it.score > 0 }
        val accessories = rank(accessoryRules(genus), evidence, "accessory").filter { it.score > 0 }
        val cautions = mutableListOf(
            "این خروجی تحلیل قواعدی بر پایه نشانه‌های ثبت‌شده است و تشخیص خودکار از پیکسل‌های عکس محسوب نمی‌شود.",
            "گونه، واریته و ویژگی تکمیلی رده‌های جداگانه‌اند و نباید به‌جای یکدیگر گزارش شوند.",
            "از یک عکس، ارتفاع واقعی ابر، نرخ بارش، CAPE، رطوبت پروفیل جو یا احتمال قطعی گردباد قابل استخراج نیست."
        )
        if (genus == "CB") cautions += "وجود ابر Cumulonimbus به‌تنهایی شدت طوفان، تگرگ یا وقوع گردباد را اثبات نمی‌کند."
        if (genus == "CU") cautions += "Cumulus congestus می‌تواند در شرایط مساعد رشد کند، اما تبدیل آن به Cumulonimbus قطعی نیست."
        return MorphologyAssessment(genus, species, features, varieties, accessories, cautions)
    }

    private fun rank(rules: List<Rule>, e: CloudVisualEvidence, taxonomy: String = "species"): List<MorphologyCandidate> =
        rules.mapNotNull { rule ->
            val matched = rule.points.filter { point -> point.first(e) }.map { point -> point.second }
            if (matched.isEmpty()) null else {
                val entry = knowledge.taxonomyEntry(rule.code, taxonomy) ?: return@mapNotNull null
                MorphologyCandidate(entry.code, entry.nameEnglish, entry.namePersian, matched.size, matched)
            }
        }.sortedWith(compareByDescending<MorphologyCandidate> { it.score }.thenBy { it.code })

    private fun speciesRules(genus: String): List<Rule> = when (genus) {
        "CI" -> listOf(
            Rule("fibratus", listOf({ e: CloudVisualEvidence -> e.straightOrCurvedFilaments } to "رشته‌های باریک، مستقیم یا خمیده")),
            Rule("uncinus", listOf({ e: CloudVisualEvidence -> e.hookedOrCurvedFibres } to "انتهای رشته‌ها قلاب‌مانند یا برگشته است")),
            Rule("spissatus", listOf({ e: CloudVisualEvidence -> e.denseFibrousVeil } to "توده سیروس متراکم و نسبتاً ضخیم")),
            Rule("castellanus", listOf({ e: CloudVisualEvidence -> e.smoothRoundedTowerTops } to "برجستگی‌های برج‌مانند در سطح بالا")),
            Rule("floccus", listOf({ e: CloudVisualEvidence -> e.smallDetachedTufts } to "توده‌های کوچک جدا از هم"))
        )
        "CS" -> listOf(
            Rule("fibratus", listOf({ e: CloudVisualEvidence -> e.straightOrCurvedFilaments } to "پرده نازک با ساختار رشته‌ای")),
            Rule("nebulosus", listOf({ e: CloudVisualEvidence -> e.uniformHazyAppearance } to "پرده یکنواخت و مه‌آلود"))
        )
        "CC" -> listOf(
            Rule("stratiformis", listOf({ e: CloudVisualEvidence -> e.layerOrSheet } to "گستره ورقه‌ای یا لایه‌ای")),
            Rule("lenticularis", listOf({ e: CloudVisualEvidence -> e.lensShape } to "اجزای عدسی‌شکل")),
            Rule("castellanus", listOf({ e: CloudVisualEvidence -> e.smoothRoundedTowerTops } to "برجستگی‌های کوچک برج‌مانند")),
            Rule("floccus", listOf({ e: CloudVisualEvidence -> e.smallDetachedTufts } to "توده‌های کوچک و جدا"))
        )
        "AC" -> listOf(
            Rule("stratiformis", listOf({ e: CloudVisualEvidence -> e.layerOrSheet } to "گستره لایه‌ای")),
            Rule("lenticularis", listOf({ e: CloudVisualEvidence -> e.lensShape } to "اجزای عدسی‌شکل")),
            Rule("castellanus", listOf({ e: CloudVisualEvidence -> e.smoothRoundedTowerTops } to "برجستگی‌های برج‌مانند")),
            Rule("floccus", listOf({ e: CloudVisualEvidence -> e.smallDetachedTufts } to "توده‌های کوچک جدا")),
            Rule("volutus", listOf({ e: CloudVisualEvidence -> e.horizontalRoll } to "ابر رول‌مانند افقی"))
        )
        "SC" -> listOf(
            Rule("stratiformis", listOf({ e: CloudVisualEvidence -> e.layerOrSheet } to "آرایش لایه‌ای")),
            Rule("lenticularis", listOf({ e: CloudVisualEvidence -> e.lensShape } to "اجزای عدسی‌شکل")),
            Rule("castellanus", listOf({ e: CloudVisualEvidence -> e.smoothRoundedTowerTops } to "برجستگی‌های برج‌مانند")),
            Rule("floccus", listOf({ e: CloudVisualEvidence -> e.smallDetachedTufts } to "توده‌های کوچک جدا")),
            Rule("volutus", listOf({ e: CloudVisualEvidence -> e.horizontalRoll } to "ابر رول‌مانند افقی"))
        )
        "ST" -> listOf(
            Rule("nebulosus", listOf({ e: CloudVisualEvidence -> e.uniformHazyAppearance } to "لایه یکنواخت و مه‌آلود")),
            Rule("fractus", listOf({ e: CloudVisualEvidence -> e.raggedFragments } to "قطعات پاره‌پاره و نامنظم"))
        )
        "CU" -> listOf(
            Rule("humilis", listOf({ e: CloudVisualEvidence -> e.shallowSmallPuffs } to "ابرهای کم‌عمق با رشد قائم محدود")),
            Rule("mediocris", listOf({ e: CloudVisualEvidence -> e.moderateVerticalGrowth } to "رشد قائم متوسط")),
            Rule("congestus", listOf({ e: CloudVisualEvidence -> e.strongVerticalGrowth || e.cauliflowerTowers } to "رشد قائم قوی و برجستگی‌های گل‌کلمی")),
            Rule("fractus", listOf({ e: CloudVisualEvidence -> e.raggedFragments } to "قطعات ابری پاره‌پاره"))
        )
        "CB" -> listOf(
            Rule("calvus", listOf({ e: CloudVisualEvidence -> e.smoothRoundedTowerTops && !e.distinctAnvil } to "قله نسبتاً صاف و بدون سندان مشخص")),
            Rule("capillatus", listOf({ e: CloudVisualEvidence -> e.denseFibrousVeil || e.distinctAnvil } to "ساختار رشته‌ای یخی یا سندان مشخص"))
        )
        // Nimbostratus has no formally assigned species in the WMO genus/species scheme.
        else -> emptyList()
    }

    private fun varietyRules(genus: String): List<Rule> {
        val rules = mutableListOf<Rule>()
        if (genus == "CI") rules += Rule("intortus", listOf({ e: CloudVisualEvidence -> e.intertwinedFibres } to "رشته‌های سیروس به‌صورت نامنظم درهم‌پیچیده‌اند"))
        if (genus in setOf("AC", "AS", "SC", "ST")) {
            rules += Rule("translucidus", listOf({ e: CloudVisualEvidence -> e.sunOrMoonVisibleThroughCloud } to "موقعیت خورشید یا ماه از پشت لایه قابل تشخیص است"))
            rules += Rule("opacus", listOf({ e: CloudVisualEvidence -> e.layerOrSheet && !e.sunOrMoonVisibleThroughCloud } to "لایه ضخیم است و قرص خورشید یا ماه از پشت آن دیده نمی‌شود"))
        }
        if (genus in setOf("AC", "SC")) {
            rules += Rule("perlucidus", listOf({ e: CloudVisualEvidence -> e.clearGapsBetweenCloudElements } to "فاصله‌های روشن و باز میان اجزای ابر دیده می‌شود"))
            rules += Rule("lacunosus", listOf({ e: CloudVisualEvidence -> e.regularCloudHoles } to "ساختار شبکه‌ای با حفره‌های نسبتاً منظم دیده می‌شود"))
        }
        if (genus in setOf("CI", "CS", "AC", "AS", "SC")) {
            rules += Rule("duplicatus", listOf({ e: CloudVisualEvidence -> e.multipleCloudLayers } to "دو یا چند لایه ابری در ترازهای نزدیک دیده می‌شود"))
        }
        if (genus in setOf("CC", "CS", "AC", "AS", "SC", "ST")) {
            rules += Rule("undulatus", listOf({ e: CloudVisualEvidence -> e.visibleWavePattern } to "الگوی موجی منظم در لایه یا اجزای ابر دیده می‌شود"))
        }
        if (genus in setOf("CI", "AC", "AS", "SC", "CU")) {
            rules += Rule("radiatus", listOf({ e: CloudVisualEvidence -> e.radiatingOrConvergingBands } to "نوارهای ابری موازی با همگرایی یا واگرایی پرسپکتیوی دیده می‌شوند"))
        }
        if (genus == "CI") {
            rules += Rule("vertebratus", listOf({ e: CloudVisualEvidence -> e.vertebraLikeArrangement } to "آرایش استخوان‌مانند یا ستون فقرات‌مانند دیده می‌شود"))
        }
        return rules
    }

    private fun featureCodes(genus: String): Set<String> = when (genus) {
        "CI" -> setOf("mamma", "fluctus")
        "CC" -> setOf("virga", "mamma", "cavum")
        "CS" -> emptySet()
        "AC" -> setOf("virga", "mamma", "cavum", "fluctus", "asperitas")
        "AS" -> setOf("virga", "praecipitatio", "mamma")
        "NS" -> setOf("praecipitatio", "virga")
        "SC" -> setOf("virga", "mamma", "praecipitatio", "fluctus", "asperitas", "cavum")
        "ST" -> setOf("praecipitatio", "fluctus")
        "CU" -> setOf("virga", "praecipitatio", "arcus", "fluctus", "tuba")
        "CB" -> setOf("praecipitatio", "virga", "incus", "mamma", "arcus", "murus", "cauda", "tuba")
        else -> emptySet()
    }

    private fun accessoryRules(genus: String): List<Rule> {
        val rules = mutableListOf<Rule>()
        if (genus in setOf("NS", "CU", "CB")) {
            rules += Rule("pannus", listOf({ e: CloudVisualEvidence -> e.raggedFragmentsUnderMainCloud } to "قطعات ابری پاره‌پاره در زیر ابر اصلی"))
        }
        if (genus in setOf("CU", "CB")) {
            rules += Rule("pileus", listOf({ e: CloudVisualEvidence -> e.smoothCapAboveTower } to "کلاهک صاف بر فراز برج همرفتی"))
            rules += Rule("velum", listOf({ e: CloudVisualEvidence -> e.sheetAroundTower } to "پرده افقی نازک پیرامون یا بالای ابر در حال رشد"))
        }
        if (genus == "CB") {
            rules += Rule("flumen", listOf({ e: CloudVisualEvidence -> e.inflowBandToStorm } to "نوار ابری مرتبط با جریان ورودی به ابر طوفانی"))
        }
        return rules
    }

    private fun featureRules(): List<Rule> = listOf(
        Rule("incus", listOf({ e: CloudVisualEvidence -> e.distinctAnvil } to "گسترش افقی سندانی‌شکل در بالای ابر")),
        Rule("mamma", listOf({ e: CloudVisualEvidence -> e.hangingPouches } to "کیسه‌های آویخته از سطح زیرین")),
        Rule("virga", listOf({ e: CloudVisualEvidence -> e.precipitationStreaks && !e.precipitationReachesGround } to "رگه‌های بارش که ظاهراً به زمین نمی‌رسند")),
        Rule("praecipitatio", listOf({ e: CloudVisualEvidence -> e.precipitationReachesGround } to "بارش قابل مشاهده در حال رسیدن به سطح")),
        Rule("arcus", listOf({ e: CloudVisualEvidence -> e.horizontalBannerOrShelf } to "ساختار افقی قوسی یا shelf در جلوی ابر")),
        Rule("tuba", listOf({ e: CloudVisualEvidence -> e.funnelOrHangingColumn } to "ستون یا قیف ابری آویخته")),
        Rule("asperitas", listOf({ e: CloudVisualEvidence -> e.waveLikeUnderside } to "سطح زیرین موج‌دار و ناهموار")),
        Rule("murus", listOf({ e: CloudVisualEvidence -> e.wallCloudAppearance } to "ناحیه دیواره‌مانند در پایه ابر طوفانی")),
        Rule("cauda", listOf({ e: CloudVisualEvidence -> e.tailCloudBand } to "نوار ابری دنباله‌مانند")),
        Rule("fluctus", listOf({ e: CloudVisualEvidence -> e.waveBreakingTop } to "موج‌های شکسته در بخش بالایی ابر")),
        Rule("cavum", listOf({ e: CloudVisualEvidence -> e.holeInCloudLayer } to "حفره مشخص در لایه ابر"))
    )
}
