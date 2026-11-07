# Lightning Lab — Atmospheric Intelligence / Phase 1

This phase adds the **offline scientific knowledge foundation** to the existing Android/Kotlin project. It does not replace existing screens or claim that image classification is already implemented.

## Included

- `app/src/main/assets/knowledge/AtmosphericKnowledge.db` — pre-populated, read-only SQLite database.
- `AtmosphericKnowledgeDatabase.kt` — installs the bundled database on first use and exposes read-only queries.
- `AtmosphericKnowledgeModels.kt` — typed data models.
- `AtmosphericKnowledgeRepository.kt` — domain repository.
- `AtmosphericEngine.kt` — initial knowledge-engine facade for future on-device model integration.
- `docs/atmospheric-knowledge-schema.sql` — schema export.

## Current seed content

- 10 WMO cloud genera.
- 15 selected cloud species / related classification entries.
- 9 cloud varieties.
- 15 supplementary or accessory features.
- 11 additional atmospheric phenomena (electrical, optical, precipitation, visibility and severe-weather categories).
- Formation notes, qualitative altitude profiles, impacts and reference-image slots.
- Source metadata and explicit uncertainty notes.

## Android integration

No additional Gradle dependency is required. The code uses Android's built-in SQLite APIs.

Example:

```kotlin
val engine = AtmosphericEngine(context)

// Search in the offline knowledge base.
val matches = engine.searchKnowledge("کومولونیمبوس")

// Build a scientific reference report by known taxonomy code.
// This is a knowledge report, not a claim that an image was classified.
val report = engine.buildReferenceReport("Cb")

// Browse the 10 cloud genera.
val genera = engine.cloudCatalog()
```

The database is copied from assets to the app's private database directory on first access, then opened read-only. Updating the bundled database in a future app release requires a versioned migration strategy; this phase intentionally does not overwrite user data silently.

## Scientific and product limitations

1. **The database is not an image classifier.** It provides curated reference information after a model or user selects a phenomenon.
2. **Altitude values are qualitative in this seed.** Cloud-base and cloud-top heights vary with latitude, season, synoptic regime and local thermodynamics. No universal fixed height is implied. Numerical profiles should be added only with explicit reference, region/season qualifiers and provenance.
3. **An image cannot reveal CAPE, humidity profiles, pressure-level heights, actual cloud-top altitude, precipitation rate or imminent severe-weather risk reliably on its own.** Those require independent observations or model data.
4. **Image slots are deliberately empty.** No unlicensed or fabricated field photograph is bundled. Populate `reference_images` only with images whose license, attribution, label and source have been checked. A future app screen should hide records where `verified=0` or `status != 'ready'`.
5. **Confidence calibration is not included yet.** A model's softmax score must not be presented as calibrated probability until validated on a representative held-out dataset.
6. This is a first-pass knowledge seed. Scientific editorial review is required before treating every descriptive note as publication-ready operational guidance.

## Sources and provenance

The database includes source records for the WMO International Cloud Atlas, AMS Glossary and NOAA educational resources, plus clearly marked Lightning Lab editorial seed notes. The WMO International Cloud Atlas is the primary taxonomy reference. See the `sources` table for URLs and notes.

## Next engineering phase

1. Curate and license reference photographs with genus/species/feature labels.
2. Build a field-photo dataset split by photographer/location/event to prevent leakage.
3. Train and evaluate a new cloud genus model; retain the current 11-label classifier contract until a compatible replacement is validated.
4. Add a multi-label head for species and supplementary features, with abstention/uncertainty behavior.
5. Integrate inference results with `AtmosphericEngine` and build the report UI.
