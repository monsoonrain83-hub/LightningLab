# Phase 2.2 — model safety and dataset quality

- Model loader now requires an exact `[1, N]` float32 output tensor, where N is 10 (WMO genera) or 11 (legacy model). Malformed tensor ranks are rejected.
- Model import stages to a temporary file and preserves/restores the previous model if replacement fails.
- AI top-three results are now explicitly candidates; the app no longer silently selects the highest-scoring genus as the confirmed/manual genus.
- Persian and English UI disclose that model scores are raw outputs, not calibrated probabilities or scientific confirmation.
- Added an offline dataset audit script for class counts, unreadable images, exact duplicates, image dimensions and perceptual-hash collision candidates.
- Added Persian annotation guidance and a CSV template for genus/species/variety/features and image-level licensing provenance.

## Not claimed
No trained weights, accuracy, APK build or on-device test are included. The dataset audit script is a tool for the point when images are available; it has not been run against the remote dataset in this environment.
