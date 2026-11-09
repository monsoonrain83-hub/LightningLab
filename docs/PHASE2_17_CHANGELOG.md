# Phase 2.17 — Cloud training pipeline alignment

- Aligned the trainer output class order with Android `CloudClassifier.LABELS`.
- Added direct Hugging Face dataset input as well as prepared-folder input.
- Added duplicate-image-group split, class-weight cap, reproducible seed, held-out per-class metrics JSON and confusion-matrix CSV.
- Aligned aspect-preserving 224px resize/pad RGB contract with neutral grey 127.5 and Android inference.
- Added machine-readable `model_contract.json` and Persian training handoff guide.
- No trained TFLite weights are bundled; TensorFlow and dataset download are not available in this packaging environment.
