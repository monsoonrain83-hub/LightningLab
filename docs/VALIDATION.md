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
