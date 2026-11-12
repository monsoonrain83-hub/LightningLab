# r10.64 — Lightning Intelligence: evidence export + optional temporal Android inference

## Delivered in source
- **No downgrade:** based on r10.63 branch (which includes merged r10.62), versionName `1.0-r10.64`, versionCode **71**.
- Lightning results can save real UTF-8 **CSV** and **JSON** evidence documents via Android's Storage Access Framework. Exports are chronological, schema-versioned and include pulse count, timestamps, heuristic score, visible type and spread. No network permission is required.
- Optional local TFLite loader enforces `[1,8,224,224,3] float32` RGB 0..255 input and `[1,7] float32` output. Rejected imports restore the last working model; untrained weights are **not** included.
- Event-centered eight-frame timestamp sampler; Android video frames are directly RGB-resized in the same manner as the training pipeline.
- Results UI offers model import and on-device analysis; the algorithmic detection remains operational without ML. Model softmax is displayed as a separate **uncalibrated** score, never as CG/IC or polarity.
- JVM tests cover output format, chronology, JSON decoding, tensor class ordering and timestamp bounds.

## Scientific and operational limitations
- Published Brixton camera collection is one-site data. A model trained on it is not externally validated and must **not** be represented as universally accurate.
- Recording frame timestamps and seek accuracy depend on source codec and platform. Frame decode, video lifecycle, inference latency and peak RAM require actual Android instrumentation and physical-device tests.
- Channel/polarity/CG/IC classification and energy are not established by ordinary RGB video.
- Python reproducible training still requires separately licensed videos and model weights. No mock weights are shipped.
- GitHub Actions CI must pass on **the exact final commit** before the APK can be certified. Verify its artifact hash and source commit.
