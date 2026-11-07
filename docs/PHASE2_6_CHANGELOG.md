# Phase 2.6 changelog

- Added optional `CloudFeatureClassifier` with strict TFLite tensor validation and an 11-output independent-sigmoid contract.
- Added safe, atomic import of `cloud_features.tflite` and model state in `MainVM`.
- Connected feature inference to still-image analysis and added a Persian UI panel for candidate features.
- Added multi-label MobileNetV3Small training pipeline, annotation CSV template, fixed label order, and Persian scientific limitations guide.
- Bumped app metadata to `1.0-r10.6` / versionCode 16.
- No trained feature model is bundled; the model must be trained using expert-reviewed, properly licensed images before automatic feature suggestions are enabled.
