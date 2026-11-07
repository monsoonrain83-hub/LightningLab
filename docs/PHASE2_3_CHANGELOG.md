# Lightning Lab — Phase 2.3

## What changed

- Added an offline, evidence-led cloud morphology assessor for candidate WMO species, varieties, and supplementary features.
- Added genus-aware candidate filtering and Persian explanations of the visual cues that matched each candidate.
- Separated supplementary features from accessory clouds in both the offline SQLite taxonomy and the report UI; accessory clouds now have their own table and result section.
- Added a Weather-screen workflow: after selecting a photo, choose the observed genus and visible cues; the app displays candidate species/features/varieties from the bundled SQLite taxonomy.
- Added repository/database lookup for taxonomy records by explicit taxonomy class (`species`, `variety`, `feature`, `accessory`) using a fixed table allowlist.
- Versioned the copied read-only database filename so users upgrading from earlier APKs cannot accidentally keep an older schema missing the new accessory-cloud table.
- Added explicit caution text: this rule engine processes observer-entered cues; it is not a trained image model and does not infer cloud-base height, CAPE, precipitation rate, or tornado occurrence from a single photo.
- Clarified that imported model scores are relative candidate scores, not calibrated probabilities.
- Bumped Android version to 1.0-r10.3 (versionCode 13).

## Taxonomy coverage in this stage

- Species candidate rules: Cirrus, Cirrocumulus, Cirrostratus, Altocumulus, Stratocumulus, Stratus, Cumulus, Cumulonimbus.
- Variety candidate rules: intortus, radiatus, vertebratus, duplicatus, undulatus, lacunosus, translucidus, perlucidus, opacus. `cavum` is treated only as a supplementary feature, not a variety. The WMO accessory clouds (`pannus`, `pileus`, `velum`, `flumen`) are stored and displayed separately from supplementary features.
- Supplementary feature candidate rules: incus, mamma, virga, praecipitatio, arcus, tuba, asperitas, murus, cauda, fluctus, cavum. Accessory-cloud candidate rules: pannus, pileus, velum, flumen.

Altostratus and Nimbostratus are intentionally not assigned species candidates; Nimbostratus has no varieties either. Variety and supplementary-feature applicability is restricted by genus using the WMO classification summary. The database was corrected so `volutus` applies to Altocumulus and Stratocumulus, and the varieties table now stores genus applicability.

## Verification

- Morphology analyzer compiled independently with Kotlin/JVM against a small repository stub.
- Smoke tests passed for Cumulonimbus capillatus + incus + mamma, Cumulus congestus + pileus accessory cloud, Cirrus uncinus + vertebratus + intortus, Altostratus varieties without a species, and Nimbostratus without species/variety.
- SQLite integrity and taxonomy table checks are run separately by `tools_validate_knowledge_db.py`.
- Full Android Gradle build and APK generation were not performed in this environment; no claim of a successful device build is made.

## Scientific source

The genus-specific species, varieties, supplementary features, and accessory-cloud mapping is aligned to the WMO International Cloud Atlas, Table 2: https://cloudatlas.wmo.int/en/cloud-classification-summary.html (2017 edition).

## Important limitation

The visual cue controls are explicit observer annotations. They do not represent automatic detection from image pixels. Automatic species/feature recognition requires a curated, licensed, adequately sized dataset and a separately trained and validated multi-label model.
