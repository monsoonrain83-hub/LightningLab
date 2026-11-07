package com.lightninglab

/** WMO International Cloud Atlas vocabulary (Latin names are language-neutral). Sources: WMO ICA 2017; AMS Glossary. */
object CloudInfo {
    private val GENERA = mapOf(
        "Ac" to "Altocumulus", "As" to "Altostratus", "Cb" to "Cumulonimbus", "Cc" to "Cirrocumulus",
        "Ci" to "Cirrus", "Cs" to "Cirrostratus", "Cu" to "Cumulus",
        "Ns" to "Nimbostratus", "Sc" to "Stratocumulus", "St" to "Stratus")

    fun genusName(code: String) = (GENERA[code] ?: code) + " (" + code + ")"

    /** Codes in CloudClassifier.LABELS order: the ten WMO genera. Contrails are not a genus and are handled separately. */
    val genusCodes = CloudClassifier.LABELS

    /** Supplementary features and accessory clouds (nine classic + 2017 additions). Observer-tagged, NOT detected. */
    val supplementary = listOf("arcus", "asperitas", "virga", "praecipitatio", "tuba", "mamma", "incus",
        "murus", "cauda", "fluctus", "cavum")
    /** Accessory clouds are a separate WMO classification column, not supplementary features. */
    val accessoryClouds = listOf("pannus", "pileus", "velum", "flumen")

    /** A selection of species and varieties that observers commonly tag. Observer-tagged, NOT detected. */
    val speciesVarieties = listOf("fibratus", "uncinus", "castellanus", "floccus", "lenticularis", "volutus",
        "undulatus", "radiatus", "lacunosus", "intortus", "translucidus", "opacus", "duplicatus")
}
