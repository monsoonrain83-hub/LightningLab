# r10.65 — Android Lightning Runtime performance and reliability

Baseline: r10.64 feature branch, retains all existing Photo Studio, Video Studio, Cloud Intelligence, and Lightning Intelligence functionality. Version `1.0-r10.65` / `versionCode=72`.

## Implemented
- A new `Detector.temporalFrames()` retrieves a whole 8-frame temporal window using **one** `MediaMetadataRetriever` session. It releases the session and recycles all partial bitmaps on failure/cancellation. Previously, the model path opened and released the media retriever for every sampled frame.
- Model inference processes **all** detected lightning events rather than silently limiting to the first 20; updates progress after each event and checks coroutine cancellation.
- Avoid model-import / active-inference races through the shared busy state.
- The evidence report now includes optional classifier label and **uncalibrated** softmax score alongside independent visual heuristic fields; if unavailable, columns are empty or JSON fields are omitted.
- Versioned evidence export schema `2` and JVM regression coverage for prediction-present and prediction-absent cases.

## Scientific boundaries
- RGB images do not determine lightning polarity, electrical energy, or CG vs IC conclusively. This is a visual classifier, not a calibrated lightning-location sensor.
- `[1,8,224,224,3]` model layout alone does **not** prove that imported weights share the intended training label order. Model metadata provenance and calibrated real-device validation remain necessary before any field-use accuracy claim.
- No external weights are bundled and no feature is declared complete by just adding a button.

## Validation gates
The exact r10.65 branch SHA must pass Python tests, JVM unit tests, Android compilation, and APK build. Run instrumented device tests on a video with zero events, many events, corrupt files, cancel/retry, orientation changes, and repeated model replacement. APK certification remains **blocked** until the exact commit's workflow and artifact can be verified.
