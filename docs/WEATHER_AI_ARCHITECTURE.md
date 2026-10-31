# LightningLab Weather AI — architecture and current implementation

## Product constraints

- All inference runs locally on Android. The app must not require network access.
- Training is an offline developer workflow (for example, Google Colab); trained artifacts are imported to the device.
- A model score is not a calibrated probability and is not a verified meteorological confidence value.
- A single RGB photograph cannot establish surface precipitation, lightning occurrence, hail, tornado occurrence, cloud altitude, or future weather.
- The WMO International Cloud Atlas is the taxonomy reference. CCSN dataset labels are retained as a legacy model contract; `Ct` is a dataset class for contrail/homogenitus and not one of the ten natural cloud genera.

## Current runtime flow

1. Android Photo Picker supplies a local image URI.
2. `MainVM.analyzeSky` decodes a bounded-size software bitmap.
3. `CloudAnalysis` calculates a heuristic cover/texture summary and frame-quality warnings.
4. `CloudClassifier` optionally runs the imported CCSN single-label TFLite model.
5. UI shows heuristic analysis separately from the model's top scores and explicitly labels scores as uncalibrated.
6. Observer tags remain user-entered; they are not represented as automatic AI detections.

## Model contract (legacy genus classifier)

- Input: `[1, 224, 224, 3]`, NHWC, float32 RGB, range 0..255.
- Preprocessing: aspect-ratio-preserving resize, centered neutral RGB padding (127,127,127). MobileNetV3 preprocessing is embedded in the model.
- Output: `[1, 11]` float32 scores.
- Labels, in exact order: `Ac, As, Cb, Cc, Ci, Cs, Ct, Cu, Ns, Sc, St`.
- Runtime validates tensor rank, dimensions, channel count, and tensor types before accepting a model.
- The old active model is preserved if an imported model fails validation or activation.

## Next architecture stages

### Stage A — multi-label cloud attributes

A separate sigmoid model should predict observer-visible attributes (species/varieties/features) with a CSV annotation format. It must be trained only on images with expert-quality labels. Do not train rare classes from a handful of weak labels. Runtime contract and UI must distinguish multi-label scores from genus classification.

### Stage B — localization and segmentation

A separate detector/segmenter should localize cloud regions and supplementary features. Do not call a global image classifier a detector. Evaluate mAP/IoU and per-class recall on an independent image set.

### Stage C — temporal analysis

For image sequences, require timestamps and preferably camera alignment. Report observed changes and apparent motion only; do not turn image motion into a forecast without a validated physical/meteorological model.

### Stage D — calibration and deployment validation

Calibrate model scores on a held-out validation set, measure per-class precision/recall and macro-F1, and test on independent phone-camera images across lighting, devices, seasons, and locations. Keep out-of-distribution and low-quality abstention behavior.

## Data and licensing

The app does not download datasets. Dataset terms, attribution, commercial-use permissions, and model redistribution rights must be checked independently before any dataset or model is distributed. The CCSN training script produces a local artifact only and does not grant rights to the source data.
