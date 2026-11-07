# Phase 2.9 changelog

- Added a reproducible small U-Net training/export pipeline for cloud-vs-background segmentation.
- Added CSV manifest contract, group-aware split, paired image/mask decoding, aspect-preserving letterbox, BCE+Dice objective, Dice/IoU metrics, and float32 TFLite export.
- Added Persian training instructions and mask annotation conventions.
- Android version: `1.0-r10.9` (`versionCode 19`).
- No trained weights or dataset are bundled. Training and APK build were not run in this environment.
