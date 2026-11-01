# Detector bench (synthetic bolts on a real daytime cloud clip)

Reproduces the daytime-sensitivity measurements. Needs python3 + numpy + opencv and a Kotlin compiler (1.9.x).

    cd tools/bench
    python3 gen2.py          # builds stable/noisy/shaky clips (clean + 7 injected thin bolts of amplitude 6..45 luma)
    SRC=../../app/src/main/java/com/lightninglab
    kotlinc $SRC/FlashAnalyzer.kt $SRC/DayLine.kt $SRC/ImgUtil.kt Harness.kt -include-runtime -d bench.jar
    python3 score.py bench.jar x 0.6 0.8 1.0      # 2nd arg is a placeholder, then sensitivities

The harness runs the production defaults. To sweep a constant, temporarily change it from `const val` to `var` in DayLine/FlashAnalyzer.

Results (6 clips, 7 bolts each, recall by bolt amplitude; "false" = events on the same clips without bolts):
- Before (fixed pixel threshold 20): amp 12 -> 2/6, 16 -> 2/6, 22 -> 3/6, false events 11 (sens 0.6).
- After (adaptive noise threshold + alignment-error budget + gap bridging): amp 12 -> 3/6, 16 -> 6/6, 22 -> 6/6, false events 1 (sens 0.6);
  sens 0.8: amp 9 -> 2/6, 12 -> 5/6, false 3. Bolts of amplitude <= 9 luma in shaky/noisy clips remain undetectable.
