# Lightning Lab — Atmospheric Intelligence

The project now includes the Phase 1 offline atmospheric knowledge base, the Phase 2 cloud-model preparation/training pipeline, and the Phase 2.3 WMO-aligned morphology assessment workflow.

- `docs/ATMOSPHERIC_INTELLIGENCE_PHASE1.md` — offline knowledge architecture and scientific limits.
- `docs/ATMOSPHERIC_INTELLIGENCE_PHASE2.md` — cloud-model preparation and training pipeline.
- `docs/PHASE2_3_CHANGELOG.md` — genus-aware species, variety, supplementary-feature and accessory-cloud assessment.
- `docs/PHASE2_3_USER_GUIDE_FA.md` — Persian user guide.
- `tools/cloud_ai/README.md` — dataset and model training workflow.

No trained cloud model is bundled yet. The current Phase 2.3 morphology engine runs offline from observer-selected visual cues; it is not a trained image model.


## Phase 2.5 — Offline cloud video analysis

See [the Persian guide](docs/PHASE2_5_VIDEO_ANALYSIS_FA.md) for video sampling, per-frame analysis, limitations, and test notes.


## Phase 2.6 — Multi-label cloud morphology model

See `docs/PHASE2_6_MULTILABEL_FEATURE_MODEL_FA.md` for the optional 11-label structural-feature model, annotation contract, training pipeline, and Android import workflow. The model weights are not bundled; train with expert-reviewed licensed images before enabling automatic feature proposals.
