# Weather AI Roadmap

This file distinguishes implemented runtime behavior from planned work. A taxonomy entry or UI tag is not an AI capability.

## Status

| Capability | Status | Notes |
|---|---|---|
| Offline heuristic cloud-cover/texture analysis | Implemented | Heuristic only; not a WMO genus classifier |
| Frame-quality warnings | Implemented | RGB heuristics; warns about likely ground/vegetation and strong warm cast |
| Optional on-device CCSN genus model | Implemented (runtime) | Requires a separately trained/imported `.tflite` file |
| Model tensor validation | Implemented | NHWC RGB float32, output count, dimensions, data types |
| Aspect-ratio-preserving model preprocessing | Implemented | Centered neutral padding, matching training script |
| Safe model replacement | Implemented | Candidate validation and rollback to prior model |
| WMO genus/species/variety vocabulary | Implemented (reference data) | Not equivalent to automatic recognition |
| Automatic multi-label species/variety/features | Not implemented | Needs expert-labelled data, trained model, runtime integration |
| Automatic supplementary-feature detection | Not implemented | Manual observer tags remain manual |
| Cloud object detection / bounding boxes | Not implemented | Requires detector model and evaluation dataset |
| Pixel-level cloud segmentation | Not implemented | Requires segmentation model and masks |
| Grad-CAM / explainability overlay | Not implemented | Needs compatible model and UI overlay |
| Sequence/cloud-motion analysis | Not implemented | Needs timestamped, aligned image sequences |
| Calibrated confidence / OOD abstention | Not implemented | Current scores are uncalibrated model outputs |

## Release gate for an operational AI model

A model may be marked operational only after:

1. Dataset provenance and licence are documented.
2. Train/validation/test split prevents duplicate and source leakage.
3. Test metrics include per-class precision, recall, F1 and confusion matrix.
4. Independent phone-camera validation is completed.
5. Input preprocessing and label order are verified against the Android runtime.
6. Runtime performance and memory use are measured on representative Android devices.

## Immediate next steps

1. Build and run this update through GitHub Actions.
2. Test model import rollback with valid, invalid, and truncated TFLite files.
3. Train the genus model only after dataset permission is confirmed.
4. Add the separate multi-label annotation/training pipeline and runtime contract.
5. Add detector/segmenter only when labelled bounding-box or mask datasets are available.
