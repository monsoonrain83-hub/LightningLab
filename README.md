# Lightning Lab — Atmospheric Intelligence

The project includes the offline atmospheric knowledge base, cloud-model preparation/training pipeline, WMO-aligned morphology assessment, and optional on-device TensorFlow Lite inference.

- `docs/ATMOSPHERIC_INTELLIGENCE_PHASE1.md` — offline knowledge architecture and scientific limits.
- `docs/ATMOSPHERIC_INTELLIGENCE_PHASE2.md` — cloud-model preparation and training pipeline.
- `docs/PHASE2_3_CHANGELOG.md` — genus-aware species, variety, supplementary-feature and accessory-cloud assessment.
- `docs/PHASE2_3_USER_GUIDE_FA.md` — Persian user guide.
- `tools/cloud_ai/README.md` — dataset and model training workflow.

## Cloud image classification

Version r10.18 corrects the input contract for imported 11-class CCSN TFLite models:
- input tensor: `[1,224,224,3]`, `FLOAT32`
- RGB channel order
- direct resize to 224×224
- pixel normalization to 0..1
- output labels in the training folder order: `Ac, As, Cb, Cc, Ci, Cs, Ct, Cu, Ns, Sc, St`

The existing 10-class LightningLab model contract remains supported separately (letterbox preprocessing and RGB 0..255).

Model weights are not bundled. The upstream CCSN model repository does not provide a clear license file, so this project does not redistribute those weights. Import a model file through the app's existing model-import control. The upstream model is an experimental baseline, not a validated operational cloud-observation product.

## Phase 2.5 — Offline cloud video analysis

See [the Persian guide](docs/PHASE2_5_VIDEO_ANALYSIS_FA.md).

## Phase 2.6 — Multi-label cloud morphology model

See `docs/PHASE2_6_MULTILABEL_FEATURE_MODEL_FA.md`. The optional structural-feature model weights are not bundled.
