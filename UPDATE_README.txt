LightningLab update package

Changes in this package:
- Restored from the original LightningLab source tree (not from the previous altered build).
- Fixed auto-select ordering: the highest-scored event indices are now the ones selected.
- Rebuilt the home screen with four centered tools in a 2x2 layout and removed the global bottom navigation bar.
- Added a compact animated header made from the two supplied storm/lightning clips.
- Kept all visible labels in Android string resources; added matching English, Persian, and Arabic labels.
- Added an explicit Picture-in-Picture entry control to the video editor and enabled PiP in the manifest.
- Completed missing Arabic string resources and validated XML resource files.

Install through the repository's existing GitHub Actions workflow:
1. Upload this archive to the repository root with the exact filename update.zip.
2. Commit the upload. The existing Build APK workflow extracts the project and runs `gradle assembleDebug`.
3. Download the APK from the successful workflow run's Artifacts section.

Important validation note:
This environment does not contain Android SDK or Gradle, so an APK build/device test could not be run here. GitHub Actions must confirm compilation. The advanced editing requests (manual freeform crop UI, text/font import and rendering, live effect preview, keyframe animation, multi-track audio timeline, export profile selection, and trained cloud model packaging) are not claimed as completed by this update.
