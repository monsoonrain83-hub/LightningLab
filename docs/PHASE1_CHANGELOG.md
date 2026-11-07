# Phase 1 change log

- Added a bundled SQLite knowledge database under `app/src/main/assets/knowledge/`.
- Added read-only database access and typed knowledge models.
- Added repository methods for search, cloud profiles, taxonomy, formation, altitude notes, impacts and reference-image metadata.
- Added `AtmosphericEngine` facade as the integration boundary for future local ML inference.
- Added schema export, image-curation manifest and database validation script.
- Existing Lightning Lab screens, video processing and current classifier were left unchanged.
- No network permission or online service was added.
- No model weights or reference photographs were added; model training and licensed image curation remain later steps.
