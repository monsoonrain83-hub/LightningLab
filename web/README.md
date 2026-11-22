# LightningLab Web — first safe browser module

This directory is an intentionally limited **first browser port**, designed for embedding inside **MetStat → استودیو آوا**. Its authoring reference is the original Android repository at `app/src/main/java/com/lightninglab/Detector.kt`, `FlashAnalyzer.kt`, `PhotoEdit.kt`, `Timeline.kt`, `Screens.kt`, and `README.md`, not a claimed Kotlin-to-JavaScript translation.

## Implemented now
- `lightning-web-core.js`: a tiny deterministic RGBA luminance average and thresholded frame-brightness jump detector. Plain JavaScript (browser + Node), local-only, no network, no dependencies, no user media upload to a server. Test with `node web/tests/test_lightning_web.cjs`.
- MetStat `ui_tabler_vnext_r5` is the independent blue/white/scarlet browser UI prototype with user-local photo preview/brightness/contrast adjustment/PNG saving, HTML5 video preview/seek and frame-to-PNG, voice synthesis where available, and a sampled video bright-flash **candidate** list. The implementation in MetStat PR #17 is provisional and integrates this kernel. It is NOT deployed to production.

## Missing / not yet ported
- Android MediaCodec sequential full-fps decoder, FlashAnalyzer motion compensation, localized stroke geometry, temporal grouping, sophisticated per-frame quality scoring, review ledger, AI/ML classifiers and evidence exports.
- The source repository has **no trained TFLite weights**; do not claim cloud detection accuracy or meteorological lightning classification from this luminance-jump prototype. An observed illumination jump may be artificial lights, cuts, camera auto-exposure, or flashes unrelated to lightning.
- Full non-destructive photo layers, full clip editor, project state/persistence/recovery, WebCodecs/ffmpeg.wasm encode/export, audio render, native Android compatibility and cloud model download remain separate engineering work.

## Security and scientific constraints
Browser Local File & Canvas only, no server-side media handling or third-party scripts. Apply explicit media size/length ceilings, worker/off-main-thread decode for large clips, user consent for server jobs if added, CSP and sandboxing. Never label a threshold hit as verified CG/IC lightning. Preserve no fabricated probability or confidence scores.

## Integration
Cross-reference: [MetStat UI vNext r5 source in PR #17](https://github.com/monsoonrain83-hub/metstat-site/pull/17), `ui_tabler_vnext_r5/lightning-web-core.js`, `ava-studio-r5.js`. This folder contains source and tests for the reusable kernel, not a deployable standalone site.

## Acceptance for a real port
1. Define browser APIs/data structures for decoded frames and annotation timestamps with independent test vectors from the Kotlin pipeline.
2. Port Frame Analyzer with motion compensation and temporal grouping, compare identical frame sequences between Kotlin and JavaScript within numerical tolerances.
3. Browser E2E with actual recorded lightning and non-lightning footage, human-reviewed event labels, precision/recall and FPS/memory metrics; no model claim without validation.
4. Integrate robust media editor and safe export, isolated from authentication and production forecasting.
5. Launch behind a feature flag after repeatable cross-browser QA and security review.
