LightningLab update package

Upload this file's companion update.zip to the repository root using the existing GitHub workflow.
The workflow in .github/workflows/build.yml extracts update.zip, copies the project files into their normal paths, commits the applied update, and builds the debug APK.

This package is a complete project snapshot rooted at LightningLab-update/. It is designed for the existing update.zip workflow.

After upload:
1. Open the repository's Actions tab.
2. Open the newest Build APK run.
3. Wait for the run to finish successfully.
4. Download the LightningLab-debug-apk artifact.

If the build fails, share the first Kotlin/Gradle error beginning with 'e:' or the first 'What went wrong' section.
