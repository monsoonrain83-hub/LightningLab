# r10.62 — Lightning temporal model training pipeline

- Adds a reproducible MobileNetV3Small + temporal mean-pooling training pipeline for seven published Brixton event labels.
- Samples eight evenly spaced RGB frames per clip at 224×224.
- Uses class-weighted training, chronological validation/test partitions, per-class report, confusion matrix, macro-F1 and multiclass Brier score.
- Exports a TFLite model with fixed input [1,8,224,224,3] float32 RGB 0..255 and output [1,7] softmax, plus metadata.
- Training dependencies are isolated from the Android app and CI's dependency-light tests.
- No trained weights are committed; model import/runtime and external-site evaluation remain separate work.
