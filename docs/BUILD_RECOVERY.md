# Build recovery notes

- Last failed build checked: run 186 (2026-11-09), commit `ea60f50707f50be442056db749c9ef2dd3e73330`.
- The failing Kotlin compilation was against the source extracted from `update.zip`, not the unchanged `main` tree.
- The compiler reported three Float-literal-to-Int argument mismatches in `Screens.kt` at lines 1137, 1142, and 1145.
- The workflow previously deleted `update.zip` and suppressed push failures, while APK upload was skipped after compilation errors. That made the exact failing source unrecoverable from the repository and run artifacts.
- This recovery branch changes the workflow to upload the exact post-extraction source snapshot even when compilation fails. Once that artifact is available, the three call sites can be corrected against the actual failing source instead of guessing.
- The advanced editor implementation is recoverable from commit `f6b7904497`; it depends on the matching timeline model, project codec schema, strings, Media3 ExoPlayer, and additional MainVM state/actions. It must be restored as a coordinated set, not by copying only the screen.
