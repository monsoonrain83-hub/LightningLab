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

## Round 5 additions
| Item | License / status | Use |
|---|---|---|
| TensorFlow Lite runtime (`org.tensorflow:tensorflow-lite:2.14.0`) | Apache-2.0 | Dependency; runs the optional user-imported cloud model |
| CCSN cloud database (Zhang et al.) | CC0 1.0 (per dataset survey arXiv:2211.14709; verify on the Dataverse page before redistribution) | NOT bundled. Training script downloads nothing; user fetches it |
| SWIMCAT / GCD / MGCD / TJNU sets | Access by signed agreement or unclear | Not used |
| MobileNetV3 ImageNet weights (Keras) | Apache-2.0 code; ImageNet-derived weights - check terms before commercial release | Used only inside tools/train_cloud_model.py |
| WMO International Cloud Atlas (2017) vocabulary | Names only | Genus/feature tag lists in `CloudInfo.kt` |
| Guava | Apache-2.0 | Dependency (Media3 overlay API) |
