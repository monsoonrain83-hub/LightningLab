# r10.63 — Temporal lightning training integrity / یکپارچگی داده و مدل

- Base: merged r10.62, **no feature removal**; app version 1.0-r10.63, versionCode 70.
- Reject duplicate SHA-256 payloads and duplicate clip paths when their splits disagree, even if their event group IDs differ.
- Validate each video file against the published manifest SHA-256 **before** training; fail closed if missing or altered.
- Record random seed, manifest SHA-256, TensorFlow version and exported TFLite SHA-256 in model metadata.
- Add dependency-light Python regression tests for split leakage, invalid digests, and corrupted video.
- Training/inference accuracy, calibration and external-camera validation are **not** established by CI.
- Android runtime integration and actual trained weights remain subsequent steps; no fake model shipped.

## Test evidence
GitHub Actions workflow: Python test discovery + compileall, JVM tests, debug APK build. Verify green job and APK artifact of the *exact* merge commit before declaring release ready.
