# Phase 2 — offline cloud classifier training

Training happens on a PC or Google Colab. The Android app performs inference locally using TensorFlow Lite; it does not need an internet connection.

## 1. Prepare starter images

Python 3.10 or 3.11 is recommended.

```bash
python -m venv .venv
# Windows: .venv\\Scripts\\activate
# macOS/Linux: source .venv/bin/activate
pip install -r tools/cloud_ai/requirements.txt
python tools/cloud_ai/prepare_ccaim.py --out data/clouds/ccaim
```

The preparation script downloads the CCAiM dataset from Hugging Face, maps its ten genus labels to the app's codes, removes byte-identical duplicates, and writes a provenance manifest and class-count report. Review the live dataset card and license before redistribution.

## 2. Train and evaluate

```bash
python tools/train_cloud_model.py --data data/clouds/ccaim --out out/clouds
```

Outputs:

- `out/clouds/cloud_model.tflite` — offline model for the app's model-import feature.
- `out/clouds/metrics.txt` — held-out test accuracy, per-class precision/recall/F1 and confusion matrix.
- `out/clouds/labels.json` — exact input/output contract.
- `out/clouds/split_counts.json` — split and training class counts.

The script splits exact duplicate images as a group to reduce leakage. It uses class weights because the starter dataset is imbalanced. It refuses classes with too few unique images to create train/validation/test splits.

## 3. Import into Android

Copy `cloud_model.tflite` to the phone and use the existing Weather screen's model-import control. The app currently validates that the output tensor has exactly ten values in this order:

`Ac, As, Cb, Cc, Ci, Cs, Cu, Ns, Sc, St`

Older 11-output models (which included `Ct` contrails) are intentionally rejected by this ten-genus model contract. Keep the old model separately if you need rollback.

## Important

No model binary is committed in this phase: TensorFlow and the full image data are not available in this build environment, and a model must never be fabricated. Run the training process and inspect the held-out metrics before using the model. The starter dataset is small and imbalanced; the first model is experimental, not an operational meteorological warning system.


## Android preprocessing contract

The app and training pipeline both preserve source-image aspect ratio and center-pad to 224×224 using neutral grey (RGB 127.5/128). Do not replace this with a square stretch or change the pad color without retraining. The app validates a float32 input tensor shaped `[1,224,224,3]` and a float32 output tensor containing ten WMO genera (or the supported legacy eleven-label format). Output scores are checked for finite, non-negative values and normalized before display.


## Multi-observer WMO synoptic dataset (CC BY 4.0)

A second source is available from Zenodo: A Multi-Annotator Ground-Based Sky Camera Dataset for Cloud Observation in Montenegro (2,522 ground-camera images, 11,191 annotations, nine professional observers). It records WMO synoptic CL, CM and CH codes, including detailed low/middle/high cloud forms. License: CC BY 4.0; preserve attribution and note modifications.

This is not a drop-in replacement for the ten-genus CCAiM model. It is a separate three-head synoptic task. Preserve observer disagreement as soft targets; "/" means that the observer could not determine the cloud level. The dataset is single-site, autumn/early-winter and daylight-only. Its README warns against random image splits because adjacent observations are correlated.

After downloading the four source files (images.zip, annotation_items.csv, annotations.csv, LICENSE.txt) from Zenodo into data/clouds/montenegro_raw, run:

\`\`\`bash
python tools/cloud_ai/prepare_montenegro_synoptic.py --record-dir data/clouds/montenegro_raw --out data/clouds/montenegro_synoptic
python -m unittest discover -s tools/cloud_ai/tests -v
\`\`\`

The manifest contains per-image CL/CM/CH vote distributions and chronological train/validation/test partitions. This is data preparation only; a trained multi-head TFLite model and Android runtime integration remain separate deliverables.
Dataset DOI: https://doi.org/10.5281/zenodo.21787669

## Train the three-head CL/CM/CH synoptic model

After preparing the Montenegro dataset, run:

\`\`\`bash
python tools/cloud_ai/train_montenegro_synoptic.py --manifest data/clouds/montenegro_synoptic/manifest.csv --out out/montenegro_synoptic --weights imagenet
\`\`\`

The script uses MobileNetV3Small, three softmax heads and the per-observer soft targets. It uses the chronological train/validation/test split, reports modal-observer macro-F1, soft cross-entropy, Brier score, per-class reports and confusion matrices, and exports cloud_synoptic_model.tflite with output shape [1,3,11]. This is an experimental single-site model; raw scores are not calibrated confidence and external camera/geographic validation is required.
