#!/usr/bin/env python3
"""Train a real optional two-class TFLite verifier from labelled local clips.

Training runs on a workstation/GPU, not on the phone. Runtime inference is
offline. There is deliberately no bundled model or claimed accuracy without
independently labelled positive AND hard-negative video clips.
"""
import argparse
import hashlib
import json
import random
from pathlib import Path

from binary_verifier_manifest import LABELS, load_manifest


def train(args):
    import cv2
    import numpy as np
    import tensorflow as tf

    random.seed(args.seed)
    np.random.seed(args.seed)
    tf.keras.utils.set_random_seed(args.seed)

    rows, audit = load_manifest(args.manifest, args.root)
    partitions = {
        split: [row for row in rows if row["split"] == split]
        for split in ("train", "validation", "test")
    }
    labels = {name: i for i, name in enumerate(LABELS)}

    class Clips(tf.keras.utils.Sequence):
        def __init__(self, entries, shuffle):
            super().__init__()
            self.entries = list(entries)
            self.shuffle = shuffle
            self.order = np.arange(len(self.entries))
            self.on_epoch_end()

        def __len__(self):
            return (len(self.order) + args.batch_size - 1) // args.batch_size

        def __getitem__(self, index):
            indices = self.order[index * args.batch_size:(index + 1) * args.batch_size]
            batch = [self.entries[int(i)] for i in indices]
            x = np.zeros((len(batch), 8, 224, 224, 3), dtype=np.float32)
            y = np.zeros((len(batch), 2), dtype=np.float32)
            for item_index, row in enumerate(batch):
                cap = cv2.VideoCapture(row["_absolute_path"])
                try:
                    n = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
                    if n <= 0:
                        raise ValueError("Undecodable video: " + row["relative_path"])
                    positions = np.linspace(0, max(0, n - 1), 8).astype(int)
                    previous = None
                    for fi, pos in enumerate(positions):
                        cap.set(cv2.CAP_PROP_POS_FRAMES, int(pos))
                        success, frame = cap.read()
                        if not success or frame is None:
                            if previous is None:
                                raise ValueError("First frame cannot be decoded: " +
                                                 row["relative_path"])
                            frame = previous
                        previous = frame
                        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                        x[item_index, fi] = cv2.resize(
                            rgb, (224, 224), interpolation=cv2.INTER_AREA)
                finally:
                    cap.release()
                y[item_index, labels[row["label"]]] = 1
            return x, y

        def on_epoch_end(self):
            if self.shuffle:
                np.random.shuffle(self.order)

    inp = tf.keras.Input(shape=(8, 224, 224, 3), dtype=tf.float32, name="video_rgb")
    x = tf.keras.layers.Rescaling(1. / 255.)(inp)
    x = tf.keras.layers.TimeDistributed(
        tf.keras.layers.Conv2D(16, 5, strides=2, padding="same", activation="relu"))(x)
    x = tf.keras.layers.TimeDistributed(
        tf.keras.layers.MaxPooling2D(pool_size=2))(x)
    x = tf.keras.layers.TimeDistributed(
        tf.keras.layers.SeparableConv2D(32, 3, strides=2, padding="same", activation="relu"))(x)
    x = tf.keras.layers.TimeDistributed(
        tf.keras.layers.SeparableConv2D(64, 3, strides=2, padding="same", activation="relu"))(x)
    x = tf.keras.layers.TimeDistributed(tf.keras.layers.GlobalAveragePooling2D())(x)
    x = tf.keras.layers.Conv1D(64, 3, padding="same", activation="relu")(x)
    x = tf.keras.layers.GlobalMaxPooling1D()(x)
    x = tf.keras.layers.Dropout(0.25)(x)
    output = tf.keras.layers.Dense(2, activation="softmax", name="lightning_verifier")(x)
    model = tf.keras.Model(inp, output)
    model.compile(
        optimizer=tf.keras.optimizers.Adam(args.learning_rate),
        loss="categorical_crossentropy", metrics=["accuracy"])
    args.out.mkdir(parents=True, exist_ok=True)
    train_seq = Clips(partitions["train"], True)
    val_seq = Clips(partitions["validation"], False)
    test_seq = Clips(partitions["test"], False)
    model.fit(train_seq, validation_data=val_seq, epochs=args.epochs, verbose=2,
              callbacks=[tf.keras.callbacks.EarlyStopping(
                  monitor="val_loss", patience=4, restore_best_weights=True)])

    y_true, y_scores = [], []
    for i in range(len(test_seq)):
        batch_x, batch_y = test_seq[i]
        y_true.extend(np.argmax(batch_y, axis=1).tolist())
        y_scores.extend(model.predict(batch_x, verbose=0).tolist())
    if len(y_scores) != len(test_seq.entries):
        raise ValueError("Test predictions not aligned with input clips")

    predictions_path = args.out / "binary_test_predictions.jsonl"
    with predictions_path.open("w", encoding="utf-8") as f:
        for row, scores in zip(test_seq.entries, y_scores):
            f.write(json.dumps({
                "sample_id": row["relative_path"],
                "split": "test",
                "group_id": row["group_id"],
                "site_id": row.get("site_id", ""),
                "camera_id": row.get("camera_id", ""),
                "sha256": row["sha256"],
                "label": row["label"],
                "probabilities": [float(v) for v in scores],
            }, sort_keys=True) + "\n")

    # Evaluation remains on untouched test split; thresholds must be set on
    # validation only. An internal clip score is not full-video event recall.
    true_positive = sum(t == 1 and s[1] >= .5 for t, s in zip(y_true, y_scores))
    false_negative = sum(t == 1 and s[1] < .5 for t, s in zip(y_true, y_scores))
    false_positive = sum(t == 0 and s[1] >= .5 for t, s in zip(y_true, y_scores))
    true_negative = sum(t == 0 and s[1] < .5 for t, s in zip(y_true, y_scores))
    precision = true_positive / (true_positive + false_positive) if true_positive + false_positive else None
    recall = true_positive / (true_positive + false_negative) if true_positive + false_negative else None

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    tflite = converter.convert()
    model_path = args.out / "lightning_binary_verifier.tflite"
    model_path.write_bytes(tflite)

    # Validate the exact phone tensor ABI before publishing.
    interpreter = tf.lite.Interpreter(model_path=str(model_path))
    interpreter.allocate_tensors()
    in_tensor = interpreter.get_input_details()
    out_tensor = interpreter.get_output_details()
    if len(in_tensor) != 1 or len(out_tensor) != 1 or             list(in_tensor[0]["shape"]) != [1, 8, 224, 224, 3] or             list(out_tensor[0]["shape"]) != [1, 2]:
        raise RuntimeError("Exported model does not match LightningLab Android ABI")

    audit.update({
        "class_order": list(LABELS),
        "preprocessing": "RGB float32 0..255, model-internal Rescaling(1/255)",
        "tflite_sha256": hashlib.sha256(tflite).hexdigest(),
        "manifest_sha256": hashlib.sha256(args.manifest.read_bytes()).hexdigest(),
        "test_predictions_sha256": hashlib.sha256(predictions_path.read_bytes()).hexdigest(),
        "test_clips": len(y_true),
        "test_tp": int(true_positive),
        "test_tn": int(true_negative),
        "test_fp": int(false_positive),
        "test_fn": int(false_negative),
        "clip_precision_at_0_5": precision,
        "clip_recall_at_0_5": recall,
        "event_recall_validated": False,
        "false_alarms_per_hour_validated": False,
        "independent_site_validated": False,
        "caveat": "Results describe labelled clips, not all events in full videos. "
                  "Model must undergo external, device and calibration evaluation.",
    })
    (args.out / "binary_model_contract.json").write_text(
        json.dumps(audit, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps(audit, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--batch-size", type=int, default=2)
    parser.add_argument("--epochs", type=int, default=25)
    parser.add_argument("--learning-rate", type=float, default=0.0003)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    if args.batch_size < 1 or args.epochs < 1 or args.learning_rate <= 0:
        parser.error("positive batch size, epochs and learning rate required")
    train(args)


if __name__ == "__main__":
    main()
