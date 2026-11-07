package com.lightninglab.atmospheric.knowledge

data class PhenomenonRecord(
    val code: String,
    val nameEnglish: String,
    val namePersian: String,
    val category: String,
    val summaryPersian: String,
    val visualCuesPersian: String?,
    val caveatsPersian: String?
)

data class CloudProfile(
    val code: String,
    val nameEnglish: String,
    val namePersian: String,
    val abbreviation: String,
    val family: String,
    val typicalBasePersian: String?,
    val verticalExtentPersian: String?,
    val formationPersian: String?,
    val weatherPersian: String?
)

data class TaxonomyEntry(
    val code: String,
    val nameEnglish: String,
    val namePersian: String,
    val descriptionPersian: String
)

data class KnowledgeNote(
    val conditionPersian: String,
    val mechanismPersian: String,
    val confidenceNotePersian: String?
)

data class AltitudeNote(
    val layer: String,
    val rangePersian: String,
    val minimumMeters: Double?,
    val maximumMeters: Double?,
    val qualificationPersian: String
)

data class ImpactNote(
    val type: String,
    val descriptionPersian: String,
    val likelihoodPersian: String?
)

data class ReferenceImageRecord(
    val assetPath: String?,
    val thumbnailPath: String?,
    val captionPersian: String?,
    val license: String?,
    val attribution: String?,
    val verified: Boolean,
    val status: String
)

data class AtmosphericKnowledgeReport(
    val phenomenon: PhenomenonRecord,
    val cloudProfile: CloudProfile?,
    val species: List<TaxonomyEntry>,
    val formation: List<KnowledgeNote>,
    val altitude: List<AltitudeNote>,
    val impacts: List<ImpactNote>,
    val referenceImages: List<ReferenceImageRecord>,
    val sourceDisclaimer: String =
        "این گزارش از بانک دانش آفلاین تهیه شده است. تشخیص تصویری و شرایط واقعی جو باید جداگانه ارزیابی شوند."
)
