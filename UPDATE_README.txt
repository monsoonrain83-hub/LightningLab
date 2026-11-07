Lightning Lab — Atmospheric Intelligence + Glass UI (Phase 2.14)

This package contains the source project with Phase 1 offline atmospheric knowledge database and Phase 2 cloud-AI training pipeline, plus Phase 2.1 inference/training preprocessing corrections.

Important: this is a source package, not a newly built APK. No trained cloud_model.tflite is included because the external image dataset could not be downloaded in this execution environment and no verified training run was possible. See docs/PHASE2_1_STATUS_FA.txt.

Main documentation:
- docs/ATMOSPHERIC_INTELLIGENCE_PHASE1.md
- docs/ATMOSPHERIC_INTELLIGENCE_PHASE2.md
- docs/PHASE2_1_STATUS_FA.txt
- tools/cloud_ai/README.md


PHASE 2.4: Added a pure-Kotlin video cloud evolution analyzer. See docs/PHASE2_4_VIDEO_GUIDE_FA.md and docs/PHASE2_4_CHANGELOG.md. This component analyzes caller-supplied frame observations; automatic frame extraction and visual inference are not yet connected.


PHASE 2.14: Integrated the Phase 2.13 Glassmorphism dashboard/UI with the atmospheric-intelligence source branch while preserving the seven-class atmospheric segmentation runner, model import/inference state, and its Weather screen UI. Added a glass-styled Photo Editor preview/control surface; existing adjustment, HSL, detail, crop/rotate/flip, healing, background cutout, presets, undo/redo and PNG/JPEG export actions remain wired to the existing ViewModel and PhotoEdit pipeline. Version: 1.0-r10.14 (versionCode 24).

Verification: archive integrity, resource XML parsing, Python tool syntax and merge-presence checks were run. Android Gradle build/APK and device interaction tests were not run because this environment has no Gradle executable, Gradle wrapper or Android SDK. The ML Kit subject-cutout feature may require Google Play Services model delivery; the app should not yet be described as fully offline for every AI feature.


PHASE 2.15: Extended the Glass UI treatment to Video Editor without changing playback, trim-range, segment selection/duplicate/delete, audio/music, recording, filters, text, stickers, speed, crop, rotation, flip, adjustment or export callbacks. The preview now sits in a framed glass stage; playback controls and the editor tool surface use the reusable GlassPanel. The existing proportional segment lane remains interactive and now has a clearer glass rim. Version: 1.0-r10.15 (versionCode 25).

Phase 2.16:
- Photo editor preview bitmap lifecycle and intermediate bitmap cleanup.
- GitHub Actions workflow for unit tests and debug APK build (not yet executed).
- versionCode 26 / versionName 1.0-r10.16.
