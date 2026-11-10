package com.lightninglab

/** WMO International Cloud Atlas vocabulary (Latin names are language-neutral). Sources: WMO ICA 2017; AMS Glossary. */
object CloudInfo {
    private val GENERA = mapOf(
        "Ac" to "Altocumulus", "As" to "Altostratus", "Cb" to "Cumulonimbus", "Cc" to "Cirrocumulus",
        "Ci" to "Cirrus", "Cs" to "Cirrostratus", "Cu" to "Cumulus",
        "Ns" to "Nimbostratus", "Sc" to "Stratocumulus", "St" to "Stratus")

    /** Contrails are a dataset class, not one of the ten natural WMO cloud genera. */
    fun genusName(code: String): String = when (code) {
        "Ct" -> "Contrail (Ct؛ کلاس جداگانه، نه جنس WMO)"
        else -> (GENERA[code] ?: code) + " (" + code + ")"
    }

    fun isNaturalGenus(code: String): Boolean = code in GENERA
    fun isDatasetAuxiliaryClass(code: String): Boolean = code == "Ct"

    /** Codes in CloudClassifier.LABELS order: the ten WMO genera. Contrails are not a genus and are handled separately. */
    val genusCodes = CloudClassifier.LABELS

    /** Supplementary features and accessory clouds (nine classic + 2017 additions). Observer-tagged, NOT detected. */
    val supplementary = listOf("arcus", "asperitas", "virga", "praecipitatio", "tuba", "mamma", "incus",
        "murus", "cauda", "fluctus", "cavum")
    /** Accessory clouds are a separate WMO classification column, not supplementary features. */
    val accessoryClouds = listOf("pannus", "pileus", "velum", "flumen")

    /** WMO cloud species grouped by genus. These are observer vocabulary, not automatic predictions. */
    val speciesByGenus: Map<String, List<String>> = mapOf(
        "Ci" to listOf("fibratus", "uncinus", "spissatus", "castellanus", "floccus"),
        "Cc" to listOf("stratiformis", "lenticularis", "castellanus", "floccus"),
        "Cs" to listOf("fibratus", "nebulosus"),
        "Ac" to listOf("stratiformis", "lenticularis", "castellanus", "floccus", "volutus"),
        "As" to emptyList(),
        "Ns" to emptyList(),
        "Sc" to listOf("stratiformis", "lenticularis", "castellanus", "floccus", "volutus"),
        "St" to listOf("nebulosus", "fractus"),
        "Cu" to listOf("humilis", "mediocris", "congestus", "fractus"),
        "Cb" to listOf("calvus", "capillatus")
    )

    /** WMO varieties are a separate classification axis from species. */
    val varieties = listOf("intortus", "vertebratus", "radiatus", "duplicatus", "undulatus",
        "lacunosus", "perlucidus", "translucidus", "opacus")

    /** Combined observer vocabulary retained for older UI consumers. */
    val speciesVarieties = (speciesByGenus.values.flatten() + varieties).distinct()

    private val featureDisplayNames = mapOf(
        "arcus" to "Arcus", "asperitas" to "Asperitas", "virga" to "Virga",
        "praecipitatio" to "Praecipitatio", "tuba" to "Tuba",
        "mamma" to "Mamma (Mammatus)", "incus" to "Incus", "murus" to "Murus",
        "cauda" to "Cauda", "fluctus" to "Fluctus", "cavum" to "Cavum",
        "pannus" to "Pannus", "pileus" to "Pileus", "velum" to "Velum", "flumen" to "Flumen"
    )

    fun displayFeature(code: String): String =
        featureDisplayNames[code] ?: code.replaceFirstChar { it.uppercase() }
}
