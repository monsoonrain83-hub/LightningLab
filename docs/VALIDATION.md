# FlashAnalyzer validation (honest status)
- NOT compiled / NOT run on Android (no Kotlin compiler or Gradle in the authoring environment).
- Logic validated with an equivalent Python port (`flash_analyzer_port.py`) on SYNTHETIC clips only:
  night double flash 0.5 s apart (2 events), faint day flash over 10 cells (1), scene cut (0), camera-shake mixed-sign (0),
  slow exposure ramp (0), flash in first 150 ms (0), noisy clip + weak flash (1), 60 s pure noise (0).
- Deadband sweep (5 seeds): cell noise sigma 0.7 and 1.5 -> 0 false positives/60 s, recall 5/5; sigma 3 -> recall 0-1/5 (fails; unrealistic for 16x9 cell means, but real clips with heavy grain could behave so).
- NOT validated on real footage. Run `./gradlew test` (7 JUnit tests in app/src/test) and then tune on your real videos.

## Round 2 additions
- `Aligner.kt` (NCC on gradient images, coarse 256 px + full-res refine). Python port `aligner_port.py` on a synthetic 1024x576 scene with sky brightening + a lightning line: exact recovery of (0,0), (5,-3), (-9,6), (14,2); featureless noise -> not confident (frame left unshifted). Translation only; rotation/zoom not handled.
- `PhotoEdit.tonePass` (highlights, shadows, sharpness, banded processing) and photo Undo/Redo + before/after: NOT numerically tested, NOT compiled.
- 9 JUnit tests total (7 FlashAnalyzer + 2 Aligner), none executed yet.

## Round 3 additions (all uncompiled, unexecuted)
- Bottom navigation (4 sections: Lightning / Photo / Video / Weather); Photo and Video cards removed from Home to avoid duplicate entry points.
- `CloudAnalysis.kt` + Weather tab: cloud cover (oktas) from normalised blue-red difference, texture/contrast, ROUGH rule-based type guess. Thresholds are hand-set, NOT validated on labelled data. 4 JUnit tests (clear, overcast, half, dark).
- Video editor: brightness, contrast, 90-degree rotation (Media3 Brightness/Contrast/ScaleAndRotateTransformation effects), segment Undo/Redo. API names written from memory of Media3 1.3.1 - if the build errors here, send the `e:` lines.
- Still missing vs PROJECT_STATUS.md: multi-clip join, music library/effects, speed, text/transitions, project save, curves/HSL/denoise/object removal, infographic export, trained cloud model.

## Round 4 additions (uncompiled, unexecuted on Android)
- Photo: S-curve, vibrance, luminance noise reduction, adjustment presets (SharedPreferences), object removal (paint marks -> `Inpaint` onion-peel fill, one-level undo). Inpaint logic checked with Python port (`inpaint_port.py`): flat hole -> exact colour, gradient hole stays within neighbour range. Smooth fill only; no texture reconstruction.
- Video: join extra videos (each added video is a section), text overlay (Media3 TextOverlay/OverlayEffect). Guava added as explicit dependency. API names from memory of Media3 1.3.1 - unverified until CI build.
- Weather: save infographic (photo + analysis text, StaticLayout so Persian/Arabic render).
- 15 JUnit tests in total, never executed.
- NOT built: playback speed, transitions, stickers/subtitles, keyframes, LUTs, PiP, multiple music tracks, music library, project files, trained cloud model (no licensed dataset/weights reviewed).

## Round 5 additions (uncompiled)
- Daytime lightning: structure-transient path in `FlashAnalyzer` (robust per-video MAD threshold). Python port `day_lightning_port.py`, synthetic only: two faint day flashes found (+0.004 single frame, +0.003 two frames); scene cut and slow drift rejected; 0 false events in 10 seeds x 4 noise levels (0.0005-0.0015) over 120 s each (one stray event in one extra run). Weak single-frame flashes below ~0.0028 structure rise, or in very shaky/noisy clips, are missed. NOT tested on real daytime footage - needs real clips to tune.
- Cloud types: optional TFLite classifier (11 CCSN classes: Ac As Cb Cc Ci Cs Ct Cu Ns Sc St) loaded from a user-imported `cloud_model.tflite`; `tools/train_cloud_model.py` (CCSN needs a request form; licence unverified) trains/exports it with a held-out test split and writes metrics.txt. Script and model code are UNRUN: no TensorFlow, no data, no accuracy figure exists yet. Without a model the app falls back to the rule-based guess.
- Observer tags (WMO supplementary features / species / varieties such as arcus, asperitas, virga, tuba, mamma, lenticularis, undulatus) are chosen by the user and saved into the infographic; the app does NOT detect them (no labelled data for them in CCSN).
- Known limit: CCSN is small (2543 images, 256 px); add your own labelled sky photos to the folders before training.

## Round 6 (UI redesign) - NOT visually verified
- No emulator/device in the authoring environment: layouts were never rendered. Static checks only: braces balanced in every .kt file, every R.string used exists, no duplicate/missing strings in en/fa/ar.
- Things to eyeball on a phone: hero bolt placement and flicker, nav icon drawing (cloud/photo/video), tab sheet heights (photo max 240 dp, video max 220 dp) on small screens, hold-to-compare gesture, RTL (Persian/Arabic) mirroring of back arrow, text clipping in tabs.
- Figma/Stitch were not available as tools here; design was derived from the brief by hand.

## Section 1 (r8): video editor tool strip - NOT compiled, NOT run on a device
- Layout like the reference screenshots: preview, play/pause, tool panel, bottom tool strip (Trim, Music, Filter, Text, Sticker, Speed, Crop, Rotate, Flip, Adjust, Save).
- New: colour filters (RgbAdjustment + Contrast presets), crop to 1:1/4:5/16:9/9:16 (Crop effect, NDC maths unit-tested), flip H/V (+ rotate in one ScaleAndRotateTransformation), styled text (colour/size/position/background), emoji stickers (as text overlays), segment strip with copy/delete, proportional timeline of segments.
- API names checked against developer.android.com reference pages: RgbAdjustment.Builder (setRed/Green/BlueScale), Crop(left,right,bottom,top), TextOverlay.createStaticTextOverlay. The project pins Media3 1.3.1; older-version availability of Crop/RgbAdjustment is believed but not proven until the CI build.
- Text/sticker TOP/BOTTOM placement uses blank-line padding (library centres overlays); look at it on a device. Emoji rendering depends on the phone's emoji font.
- NOT built (needs custom GL shaders, ML models, or large subsystems): glitch/VHS/film/beat/VFX effects, PiP, templates, AI enhance, auto captions, text-to-speech, AI trim, cutout, voice changer, reverse, freeze frame, curve speed, keyframe animation, opacity, background blur, GIF stickers (network), voice recording.
