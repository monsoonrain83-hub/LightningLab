# Phase 2 changelog

- Added `tools/cloud_ai/prepare_ccaim.py` for reproducible CCAiM dataset download, label mapping, duplicate removal, provenance manifest, and class-count report.
- Replaced the legacy CCSN-specific training script with a ten-WMO-genus MobileNetV3Small transfer-learning pipeline.
- Added duplicate-aware split, class weighting, per-class test metrics, confusion matrix, and TFLite export.
- Updated `CloudClassifier` to support the new ten-genus output and retain compatibility with legacy eleven-output models; `CloudInfo` genus list now uses the ten WMO genera, with contrails outside the genus taxonomy.
- Added dataset licensing/source review and Phase 2 documentation.
- No training images or model weights bundled; no Android APK build claimed.

- Phase 2.1: Android classifier validates float32 tensor contract and finite output values.
- Phase 2.1: image inference and training both use aspect-preserving resize-with-pad (224×224, neutral-grey padding) to avoid geometric distortion.
