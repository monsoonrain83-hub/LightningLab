# Phase 2.13 changelog

- Added `AtmosphericSegmentationModel.kt` for 7-class float32 TFLite inference.
- Added strict tensor shape/type and per-pixel score-sum validation.
- Added per-pixel class color overlay and class area statistics.
- Added safe model import/replace flow in `MainVM`.
- Integrated multi-class result and model import control in `WeatherScreen`.
- Documented the exact input/output and channel-order contract.
- Bumped Android version to code 22 / `1.0-r10.13`.

No trained model weights or APK are included. Android compilation/device testing was not performed.
