# r10.66 — Video Studio: project integrity and safe reopening

## Baseline and scope
- Based directly on merged main r10.65, `versionName=1.0-r10.66`, `versionCode=73`.
- No legacy editor feature was removed or replaced.
- Existing two-stage project save (hidden staging directory -> published directory) is retained.

## Implemented
- New project snapshots include an integrity index: a SHA-256 digest and byte length for the main video, every referenced clip, audio file, overlay asset, and project.json.
- The index is written to a temporary file and published only after serialization. The project folder is published only after all referenced assets pass verification.
- On reopen, media integrity is checked **before** loading a video or changing editor state. Corrupt or missing assets are rejected with an actionable message.
- Paths in project documents must be flat, valid filenames inside the project root. Absolute paths and directory traversal are rejected.
- User-provided project folder identifiers are validated before opening or recursive deletion.
- Older project files with no integrity.json remain readable if all their referenced files are present and paths are safe.
- JVM tests cover successful save/verify, corruption, absent media, traversal, malformed integrity records, and identifier rules.

## Boundaries
- This phase does **not** add automatic periodic autosave or post-crash draft restoration; it hardens explicit project save and reopen.
- SHA-256 verification of large videos incurs storage I/O; measure open time on low-end Android devices.
- The stored project format remains ProjectCodec v7; the integrity file is a backward-compatible sidecar. Existing project folders are not rewritten.
- Test and debug APK must pass GitHub Actions at the final commit. Physical-device storage-full, external URI expiration, interrupted I/O, and low-memory tests are not covered by JVM tests.
