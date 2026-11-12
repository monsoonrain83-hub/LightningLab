# LightningLab — Competitive capability audit and UX engineering plan
**Research date:** 2026-11-12 | **Baseline:** main r10.66 (versionCode 73) | **Next milestone:** r10.67

## Research methodology and boundaries
Sources are publicly available GitHub README files and official Android documentation. Feature claims refer to what the upstream projects **document**, not to independently reproduced performance. No upstream implementation code has been copied into LightningLab. MIT/Apache dependencies may be integrated later with notices; GPL projects (such as CalibRaw/CropTop) must be treated as **feature references only** unless the whole distribution's obligations have been reviewed. Some upstream tools describe prototype/stub functionality; we do not equate a claimed feature with real validated operation.

**Legend:** PRESENT = code exists in LightningLab r10.66; PARTIAL = some working primitives but not complete workflow; MISSING = not confirmed in current source. New r10.67 items are listed separately.

### 1. Video Studio
References:
- ActionCut: https://github.com/naveenneog/ActionCut — Compose+Media3, scrolling multitrack timeline, keyframes, WorkManager background export, LUTs, undo/redo.
- Vynox: https://github.com/gredeye/editor — portable project format, local atomic autosave, layer inspector, relinking, reproducibility/instrumentation.
- LibreCuts: https://github.com/tharunbirla/LibreCuts — snapping, transitions, freeze, background canvas, speed and reverse.
- Android Media3: https://developer.android.com/media/implement/editing-app — authoritative transform/export semantics.

PRESENT: trims, segments, effects, keyframes limited to overlay alpha/position, subtitle SRT, original/music/voice tracks, export profiles, project serialization, SHA-256 project media validation.
PARTIAL: multitrack UI (video/audio lane interaction not as extensible as general arbitrary tracks), per-clip audio automation, keyframe transforms, transitions, preview/export parity, upload/relinking.
MISSING: durable autosave/crash recovery, export WorkManager queue + resume, multi-project searchable library, native frame-accurate stepper, LUT pipeline, reverse/freeze, GPU preview/output parity.

Next sequence:
P0 — non-blocking and resilient project drafts (metadata snapshot + referenced URI/grant strategy + relink fallback), golden-file v7/v8 migration tests.
P0 — export cost preflight with required cache margin and duration/bitrate estimate; **delivered in r10.67** (estimated, not promised output size).
P1 — track/clip data engine separated from 130K-char MainVM and Screens, export background job and cancellation behavior.
P2 — real transitions and keyframe curves tested against actual encoded samples, 4K/HEVC thermal and RAM limits.

### 2. Photo Studio
References:
- CalibRaw: https://github.com/Duecki1/CalibRaw — scene-referred RAW, GPU, local masks, select sky/subject, color wheels, batch export, sidecars. GPLv3.
- CropTop: https://github.com/velinexlabs/croptop — layered docs, RAW, brush/shape masks, on-device optional model, stylus and tablet responsive UI. GPLv3.
- PhotoEditor library: https://github.com/burhanrashid52/PhotoEditor — move/scale text and stickers, brush, undo/redo. MIT; known filter memory and scaling problems documented upstream.

PRESENT: adjustments, HSL, RGB curves, masks, healing, composited text/image/shape layers, before/after, snapshots.
PARTIAL: advanced masks, RAW fidelity, tiling/working color space, output metadata, layer blending, preset library.
MISSING: lens profiles, perspective, scene-referred pipeline, truly local feathered adjustments, selective AI masks, batch jobs.
P0 — three independent validated look slots, slot 1 backwards-compatible with existing photo_preset; **delivered r10.67**.
P1 — split render engine from UI and introduce non-destructive project schema migration + load tests.
P2 — OpenGL/Vulkan tile pipeline, gamut/tone-map tests, RAW/DNG demosaic, responsive tablet inspector.

### 3. Lightning Intelligence
References:
- video-lightning-detector: https://github.com/Krzysztofz01/video-lightning-detector — temporal channel/segmentation differences, configurable thresholds, frame extraction, CSV/JSON.
- lightning-finder: https://github.com/end1989/lightning-finder — exact frame navigation, confirmation/rejection, flash groups, ranked bolt frames.
- lightning-strike-extractor: https://github.com/andregasser/lightning-strike-extractor — rolling baseline, visible channel geometry, codec-dependent high-FPS handling, provenance and reviewable outputs.

PRESENT: MediaCodec event detection, pulse grouping, day line heuristics, best frame extract, CSV/JSON, optional seven-class temporal TFLite, model integrity controls.
PARTIAL: objective local background suppression, handheld motion compensation, scene cuts, exact frame stepping, user confirmation and expert annotation.
MISSING: audited cross-camera labeled data, calibration, line/branch segmentation, high-resolution tracing, uncertainty from shutter/rolling shutter.
P0 — separate CONFIRMED/REJECTED/UNCERTAIN by human reviewer and export alongside raw algorithm and optional uncalibrated model score; **delivered r10.67**.
P1 — exact-frame review UI using decoder timestamp indexing and metadata-backed correction.
P2 — optical stabilization + line segmentation with day/night benchmark datasets; no CG/IC/polarity/energy claims from RGB alone.

### 4. Weather phenomena and sky-photo analysis
References:
- simpleCloudDetect: https://github.com/chvvkumar/simpleCloudDetect — all-sky clear/wisps/overcast/rain/snow categories, optional local models, APIs.
- AI-Based Cloud Detection: https://github.com/arpitsain05/AI-Based-Cloud-Detection-For-Weather-Forecasting — many candidate weather classes, Grad-CAM and batch model comparison (evaluate external validity separately).
- SkyClf: https://skyclf.github.io/skyclf/ — all-sky state categories via ONNX REST.

PRESENT: image-based heuristic optical cover/texture/oktas, candidate WMO cloud genus models when imported, structural feature and segmentation model slots, Cloud Atlas.
PARTIAL: transparent provenance, image-quality alerts, explainability and distinguish weather *observed evidence* from inferred categories.
MISSING: independent rain/hail/snow/fog measured ground truth, exposure/ground/solar glare exclusion, robust model calibration across climates.
P0 — export per-photo optical proxy metrics and optional uncalibrated genus candidate separately; **delivered r10.67**.
P1 — sky/land/sun masks with explicit invalid-observation quality flags; manually adjustable ROI.
P2 — multi-label atmospheric observable taxonomy with annotated ground truth; never equate visual dark cloud with measured rainfall.

### 5. Cloud type and motion analysis of sky video
References:
- LenghuSky-8: https://github.com/ruiyicheng/LenghuSky-8 — cloud segmentation, preprocessing and benchmarking.
- Waggle CMV: https://github.com/waggle-sensor/plugin-cmv-fftpc — optical flow/phase correlation cloud motion vectors, camera viewpoint and noise limitations.
- DLR Sky Imaging: https://github.com/DLR-SF/Sky-Imaging — fish-eye geometric calibration, image preprocess, mask ROI.
- AllSkyTeam: https://github.com/AllskyTeam/allsky — keograms, periodic sky image capture and add-on cloud detection.

PRESENT: sample up to 30 frames, temporal cloud coverage trend, structure changes, optional TFLite candidate outputs.
PARTIAL: export/reproducibility, camera-motion compensation, time-adaptive sampling, sky masking.
MISSING: optical-flow cloud motion, camera geometry calibration, cloud-base altitude/parallax, per-frame reinspection UI.
P0 — timestamped frame-by-frame CSV export with cover/oktas/texture and separate candidate class; **delivered r10.67**.
P1 — configurable frame sampling and avoid motion artifacts from camera panning.
P2 — cloud-motion vectors *in image pixel units*; physical velocity needs geometric calibration and cloud-base height.

## Unified modern RTL UI/UX system — implementation contract
1. Navigation: five distinct named modules in a single dashboard, with no fake disabled feature buttons. Every module uses a consistent hierarchy: Import → Observe/Preview → Adjust/Review → Export.
2. Responsive layout: respect 320dp–tablet widths, usable 48dp min tap targets, dynamic font scaling, no controls hidden under bottom player/timeline and no horizontally clipped action labels. Test portrait, landscape and RTL.
3. Visual language: existing Glass UI remains the baseline, dark neutral surfaces, restrained sky cyan for observed measurements, violet for editing, amber for uncertainty, and one dominant primary CTA per screen. Avoid stacking translucent panels/overlapping controls.
4. Scientific truth: every record labels source **heuristic / learned model / human review**; no uncalibrated score formatted as measurement certainty. An absent model is clearly described; no placeholder predictions.
5. Feedback: every long-running scan/import/export exposes progress, cancel, disabled concurrent actions and informative non-crashing errors.
6. Preservation: incremental refactors rather than rewriting entire screens; baseline snapshots and regression tests before visual restyling.
7. Quality gates: Python checks, JVM tests, debug APK build, instrumented tap/scroll and lifecycle tests and real-device footage. Only verified binaries may be called release-ready.

## Release definition
r10.67 is an **incremental implemented slice across all five domains**, not a claim that competitive parity has been reached. Manual reviews are in active analysis state and exported, not yet stored in an independent video-sidecar project. Photo looks use three local slots. Video size is an estimate. Sky CSVs record optical proxy metrics, not calibrated WMO observations.
