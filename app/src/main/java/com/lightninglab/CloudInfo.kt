package com.lightninglab

/**
 * WMO International Cloud Atlas vocabulary.
 * The app keeps the legacy observer-tag lists for compatibility, while exposing
 * a correctly separated genus/species/variety/feature taxonomy for future AI labels.
 * Observer tags are not automatic detections unless a trained model explicitly says so.
 */
object CloudInfo {
    private val GENERA = linkedMapOf(
        "Ac" to "Altocumulus", "As" to "Altostratus", "Cb" to "Cumulonimbus", "Cc" to "Cirrocumulus",
        "Ci" to "Cirrus", "Cs" to "Cirrostratus", "Cu" to "Cumulus", "Ns" to "Nimbostratus",
        "Sc" to "Stratocumulus", "St" to "Stratus", "Ct" to "Contrail (homogenitus)"
    )

    fun genusName(code: String) = (GENERA[code] ?: code) + " (" + code + ")"

    /** Legacy CCSN order. Ct is retained as a dataset class, not a WMO cloud genus. */
    val genusCodes = CloudClassifier.LABELS

    /** WMO species grouped by genera; a species is not interchangeable with a variety. */
    val speciesByGenus: Map<String, List<String>> = linkedMapOf(
        "Ci" to listOf("fibratus", "uncinus", "spissatus", "castellanus", "floccus"),
        "Cc" to listOf("stratiformis", "lenticularis", "castellanus", "floccus"),
        "Cs" to listOf("fibratus", "nebulosus"),
        "Ac" to listOf("stratiformis", "lenticularis", "castellanus", "floccus", "volutus"),
        "As" to listOf("opacus", "translucidus"),
        "Ns" to emptyList(),
        "Sc" to listOf("stratiformis", "lenticularis", "castellanus", "floccus", "volutus"),
        "St" to listOf("nebulosus", "fractus"),
        "Cu" to listOf("humilis", "mediocris", "congestus", "fractus"),
        "Cb" to listOf("calvus", "capillatus")
    )

    /** WMO cloud varieties (the list is independent of the species list). */
    val varieties = listOf(
        "intortus", "vertebratus", "radiatus", "duplicatus", "undulatus",
        "lacunosus", "translucidus", "perlucidus", "opacus"
    )

    /** Supplementary features and accessory clouds from the WMO vocabulary. */
    val supplementary = listOf(
        "arcus", "asperitas", "cavum", "cauda", "fluctus", "incus", "mamma",
        "murus", "pannus", "praecipitatio", "tuba", "velum", "virga", "flumen", "pileus"
    )

    /** Familiar display alias; WMO term remains Mamma. */
    val displayAliases = mapOf("mamma" to "Mamma (Mammatus)")

    /** Legacy combined list used by the current observer-tag UI. */
    val speciesVarieties = (speciesByGenus.values.flatten() + varieties).distinct()

    fun genusCodesForSpecies(species: String): List<String> =
        speciesByGenus.filterValues { species in it }.keys.toList()

    fun displayFeature(code: String): String = displayAliases[code] ?: code.replaceFirstChar { it.uppercase() }
}
