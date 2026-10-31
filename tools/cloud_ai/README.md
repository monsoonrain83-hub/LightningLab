# Weather AI training workspace

Training scripts are intentionally separate from the Android app. No training dependency is added to the APK.

## Genus classifier

Use `../train_cloud_model.py` with an authorized CCSN-style dataset arranged into the 11 legacy folders:

`Ac As Cb Cc Ci Cs Ct Cu Ns Sc St`

Example:

```bash
pip install tensorflow scikit-learn pillow
python tools/train_cloud_model.py --data /path/to/CCSN --out tools/cloud_ai/out
```

Outputs:

- `cloud_model.tflite`
- `manifest.json`
- `metrics.txt`
- `training_log.csv`

Import `cloud_model.tflite` in Weather > Import cloud model. Check the dataset's terms before training or distributing the model. The training script does not download data.

## Important

The model is an experimental dataset classifier until independently validated on phone-camera images. The app currently supports only the legacy single-label genus model. Species, varieties, supplementary-feature multi-label models, detectors, and segmenters are separate future model contracts; they are not implied by the current model.
