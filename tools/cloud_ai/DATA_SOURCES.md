# Lightning Lab — cloud image data sources (Phase 2)

## CCAiM starter source

**CCAiM-CloudsDataset** — https://huggingface.co/datasets/serbekun/CCAiM-CloudsDataset

- Dataset card declares MIT; 916 ground-based images; 10 WMO genera.
- Highly imbalanced and small; useful for a baseline and pipeline smoke test, not operational claims.

## Multi-observer WMO synoptic source

**A Multi-Annotator Ground-Based Sky Camera Dataset for Cloud Observation in Montenegro** — https://zenodo.org/records/21787669

- 2,522 ground-based images and 11,191 annotations from nine professional observers; most images have four to five independent judgements.
- WMO synoptic variables include total cover, low-cloud amount/base height, and CL, CM, CH cloud-type codes. These codes provide more detailed low/middle/high cloud forms.
- License CC BY 4.0. Attribution: Plamenac, Aleksandar; Tomovic, Savo (2026), Zenodo DOI 10.5281/zenodo.21787669.
- Preserve observer disagreement as soft targets. "/" is an explicit not-determinable label, not missing data.
- Single site, autumn/early-winter and daylight-only. The dataset README warns that adjacent images are correlated; split chronologically by day.
- This is a separate three-head synoptic task. Do not remap its labels into the CCAiM ten-genus classes.

## Other candidates

- WEBCAM — https://github.com/MarcusCoteFIT/webcam-ground-based-cloud-image-dataset — 15,500+ images in nine aviation-weather categories; reusable license and label mapping need review.
- TJNU GCD — https://github.com/shuangliutjnu/TJNU-Ground-based-Cloud-Dataset — 19,000 images in seven composite categories; access agreement applies.
- GOA-ALLSKY — 7,467 all-sky images for okta cloud-cover classification; not a direct genus/species dataset.
- WMO 30-class research: https://doi.org/10.1029/2024EA004112. Multi-label classification and observer disagreement are important; source imagery terms must be checked before reuse.

## Constraints

1. Keep ten-genus classification separate from CL/CM/CH synoptic coding.
2. Contrails are not a WMO genus; handle separately.
3. Species, varieties and structural features require their own labels and validation.
4. Ground RGB, all-sky fisheye, satellite and infrared are different domains.
5. Report per-class precision/recall/F1, macro-F1, confusion matrices, calibration and held-out temporal/geographic tests.
6. Never infer exact cloud-base altitude, CAPE, wind shear, rainfall rate or severe-weather risk from one RGB photo.
