# Atmospheric Intelligence — Phase 2

## Scope delivered

- Defined the first deployable computer-vision task: ground-based photograph classification into the ten WMO cloud genera.
- Updated the Android TFLite classifier to support the ten-genus output contract while retaining backward compatibility with eleven-class models that include contrails (`Ct`). It now validates input/output tensor shape and type, preserves image aspect ratio with neutral-grey padding, and rejects invalid/non-finite output scores.
- Added a Hugging Face dataset preparation utility for the MIT-declared CCAiM-CloudsDataset.
- Reworked `tools/train_cloud_model.py` into a reproducible MobileNetV3Small transfer-learning pipeline with class weighting, duplicate-aware train/validation/test splits, per-class metrics, TensorFlow Lite export, and the same aspect-preserving resize/pad preprocessing used by Android.
- Added dataset provenance and licensing notes, training instructions, and output label contract.

## Not claimed / not delivered

- No trained `.tflite` model is included. Training dependencies and source images were not present in this environment.
- No claim of accuracy or field readiness is made.
- Species, varieties, supplementary features, cloud segmentation, and other atmospheric-phenomena classifiers remain future tasks.
- The Android project has not been compiled or APK rebuilt in this phase.

## Why the model has ten outputs

The ten WMO genera are the primary cloud-genus taxonomy. Contrails are a special cloud/feature of anthropogenic origin, not an eleventh genus. Keeping them inside the genus classifier would distort the taxonomy and require a compatible labelled dataset. A separate contrail detector can be introduced later.

## Required next validation

1. Run `prepare_ccaim.py` on a networked training machine.
2. Inspect `dataset_report.json`, labels, sample images, and license.
3. Train using `train_cloud_model.py`.
4. Review per-class test metrics and confusion matrix. If weak or unstable, do not import as a trusted classifier; expand the dataset first.
5. Build the Android project and test inference on actual phone photographs under varied lighting, focal lengths, haze, and mixed-cloud conditions.


## Phase 2.1 engineering correction

Android inference and model training now use an identical 224×224 aspect-preserving letterbox transform with neutral-grey padding. The prior square stretch could geometrically distort cloud structures. Tensor contracts are validated before a model is accepted. These source changes are not a substitute for a trained model: no accuracy claim is possible until the dataset is downloaded, reviewed, trained, and tested on a held-out set.
