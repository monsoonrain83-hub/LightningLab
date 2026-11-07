# Phase 2.5 changelog

- Added offline cloud-video selection in the Weather screen.
- Added bounded temporal sampling (up to 30 frames) across the clip, including clip endpoints.
- Added scaled frame extraction, metadata rotation handling, per-frame cloud cover/okta/texture analysis, and optional TFLite genus candidates.
- Connected per-frame genus candidates to the existing CloudEvolutionAnalyzer.
- Added progress reporting and a Persian timeline report with explicit scientific caveats.
- Serialized access to the TFLite Interpreter to avoid concurrent inference calls.
- Added unit tests for deterministic video sampling plans.
- No INTERNET permission, upload, or remote processing added.
- APK build not performed in this environment.
