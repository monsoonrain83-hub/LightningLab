package com.lightninglab.atmospheric.knowledge

import android.content.Context

/**
 * Small domain-facing API used by the UI and, later, by the on-device inference engine.
 * This class retrieves knowledge only; it does not claim that a phenomenon was detected.
 */
class AtmosphericKnowledgeRepository(context: Context) {
    private val db = AtmosphericKnowledgeDatabase.getInstance(context)

    fun search(query: String): List<PhenomenonRecord> = db.search(query)

    fun reportFor(code: String): AtmosphericKnowledgeReport? {
        val phenomenon = db.getPhenomenon(code) ?: return null
        val cloud = db.getCloudProfile(code)
        return AtmosphericKnowledgeReport(
            phenomenon = phenomenon,
            cloudProfile = cloud,
            species = if (cloud != null) db.getSpeciesForGenus(code) else emptyList(),
            formation = db.getFormationConditions(code),
            altitude = db.getAltitudeNotes(code),
            impacts = db.getImpacts(code),
            referenceImages = db.getReferenceImages(code)
        )
    }

    /** Look up a species or supplementary-feature code in the offline taxonomy. */
    fun taxonomyEntry(code: String, taxonomy: String = "species"): TaxonomyEntry? = db.getTaxonomyEntry(code, taxonomy)

    fun accessoryClouds(): List<TaxonomyEntry> = db.getAccessoryClouds()

    fun supplementaryFeatures(): List<TaxonomyEntry> = db.getFeatures()

    fun varieties(): List<TaxonomyEntry> = db.getVarieties()

    fun cloudGenera(): List<PhenomenonRecord> =
        listOf("Ci", "Cc", "Cs", "Ac", "As", "Ns", "Sc", "St", "Cu", "Cb")
            .mapNotNull(db::getPhenomenon)
}
