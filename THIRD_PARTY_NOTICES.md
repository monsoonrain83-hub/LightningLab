# Third-party / reference audit (LightningLab)

Rule: no code is copied from a repo unless its license is verified AND compatible. Ideas/algorithm concepts only otherwise.

| Repo | License (verified how) | Used? |
|---|---|---|
| Krzysztofz01/video-lightning-detector | **AGPL-3.0** (GitHub page, this round) | **No code.** AGPL would force LightningLab's whole source to be AGPL if distributed. Only general concepts (brightness + inter-frame difference, moving mean) are public knowledge. |
| andregasser/lightning-strike-extractor | **No LICENSE file** in repo root (GitHub page, this round) | **No code.** Without a license, all rights are reserved. Concepts only (rolling baseline, edge-ignore, affine stabilization limits for Stack alignment, Hough thin-line scoring). Python/OpenCV/FFmpeg anyway. |
| yaph/boltseeker, ImageToolbox, WhimsicalArt, LumaStack, cloud-classification repo | **NOT yet verified** | Nothing used. |
| LibreCuts, ClearCut (MIT), ActionCut, FFmpeg-Video-Editor-Android (no license found) | Per earlier PROJECT_STATUS.md; not re-verified this round | Nothing copied. |
| Media3 Transformer 1.3.1 | Apache-2.0 | Dependency (already in use). |

The transient-flash analyzer (`FlashAnalyzer.kt`) is original code written for this project.
