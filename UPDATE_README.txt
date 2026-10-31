LightningLab update - 2026-10-31

Implemented in this update:
- Replaced the four-item bottom navigation with a centered 2x2 home dashboard:
  تشخیص صاعقه / ادیت عکس / ادیت ویدئو / تشخیص ابر و پدیده
- Added a dedicated lightning-detection entry screen, retaining the existing detector workflow.
- Reduced the home hero height and replaced the static drawn lightning hero with a muted, looping video header made by crossfading the two supplied clips.
- Added Android Picture-in-Picture entry control to the video editor and enabled PiP support in the manifest.

Important implementation status:
- The supplied project already has basic photo and video editing, cropping presets, text overlays, music, filters, trim segments and Media3 export.
- This update does NOT yet implement a draggable freeform crop UI, user-imported fonts, a full multi-track timeline, per-segment keyframes/effects/audio automation, comprehensive export presets, or an embedded trained cloud model. These require additional engineering and validation; they must not be represented as complete.
- The header is a pre-rendered silent MP4 asset. Both source videos are retained outside the APK; the composed header asset is app/src/main/res/raw/lightning_header.mp4.

Build: GitHub Actions workflow builds the debug APK with Gradle 8.7 and Java 17.
