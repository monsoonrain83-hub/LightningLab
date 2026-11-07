# Lightning Lab — Phase 2.4

## Video-based cloud evolution assessment

Added `CloudEvolutionAnalyzer`, a pure Kotlin temporal-analysis component that accepts observations sampled from successive video frames and reports:

- visible vertical-growth trend (increasing, decreasing, mostly stable, or uncertain),
- first appearance of anvil-like top, fibrous/icy-looking top, precipitation streaks, precipitation reaching the surface, and ragged fragments below the main cloud,
- changes in the model's candidate genus across frames,
- elapsed observation duration and a distinct genus sequence.

The component sorts frames by elapsed time and rejects duplicate timestamps, negative elapsed times, and sequences shorter than two observations. `AtmosphericEngine.analyzeCloudEvolution(...)` exposes the analyzer.

## Scientific safeguards

This is a temporal evidence aggregator, not a video pixel classifier. The caller must supply observations. Model scores are not described as calibrated probabilities. It does not infer storm severity, hail, tornado occurrence, rainfall rate, or forecast timing from visual evolution.

## Tests

Added JVM unit tests for increasing growth, precipitation-onset events, duplicate timestamps, and minimum sequence length. The pure Kotlin analyzer was also compiled independently with `kotlinc` in the build environment.

## Not included

No claim is made that the Android APK was built, or that a trained visual model now extracts these observations automatically. Connecting Media3 frame sampling and a validated image model is a separate integration task.
