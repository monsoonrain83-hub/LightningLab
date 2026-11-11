#!/usr/bin/env python3
"""Train an optional temporal TFLite classifier from the Brixton video manifest.

Install tools/lightning_ai/requirements-training.txt before running. The exported model
accepts 8 RGB frames (224x224, float32 values 0..255) and returns seven softmax scores.
"""
from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path

from model_contract import (
    INPUT_CHANNELS, INPUT_FRAMES, INPUT_HEIGHT, INPUT_WIDTH, LABELS,
    model_metadata, validate_manifest_rows,
)


def read_manifest(path: Path, root: Path) -> tuple[list[dict], dict[str, list[dict]]]:
    with path.open("r", encoding="utf-8-sig", newline="") as stream:
        rows = list(csv.DictReader(stream))
    _, counts = validate_manifest_rows(rows)
    if not rows or any(counts[split] == 0 for split in ("train", "validation", "test")):
        raise ValueError("Manifest must contain non-empty train, validation and test partitions.")
    for row in rows:
        clip = (root / row["relative_path"]).resolve()
        if root.resolve() not in clip.parents or not clip.is_file():
            raise FileNotFoundError(f"Missing or unsafe video path: {row['relative_path']}")
        row["_absolute_path"] = str(clip)
    partitions = {split: [row for row in rows if row["split"] == split]
                  for split in ("train", "validation", "test")}
    train_labels = {row["label"] for row in partitions["train"]}
    missing = sorted(set(LABELS) - train_labels)
    if missing:
        raise ValueError("Training split lacks classes: " + ", ".join(missing))
    return rows, partitions


def build_and_train(args) -> dict:
    import cv2
    import numpy as np
    import tensorflow as tf
    from sklearn.metrics import accuracy_score, classification_report, confusion_matrix, f1_score

    rows, partitions = read_manifest(args.manifest, args.root)
    label_index = {label: index for index, label in enumerate(LABELS)}

    class ClipSequence(tf.keras.utils.Sequence):
        def __init__(self, items, shuffle):
            super().__init__()
            self.items = list(items)
            self.shuffle = shuffle
            self.order = np.arange(len(self.items))
            self.on_epoch_end()

        def __len__(self):
            return (len(self.order) + args.batch_size - 1) // args.batch_size

        def __getitem__(self, batch_index):
            indices = self.order[batch_index * args.batch_size:(batch_index + 1) * args.batch_size]
            batch = [self.items[int(i)] for i in indices]
            x = np.zeros((len(batch), INPUT_FRAMES, INPUT_HEIGHT, INPUT_WIDTH, INPUT_CHANNELS), dtype=np.float32)
            y = np.zeros((len(batch), len(LABELS)), dtype=np.float32)
            for item_index, row in enumerate(batch):
                capture = cv2.VideoCapture(row["_absolute_path"])
                try:
                    frame_count = int(capture.get(cv2.CAP_PROP_FRAME_COUNT))
                    if frame_count <= 0:
                        raise ValueError("Video has no decodable frames: " + row["relative_path"])
                    positions = np.linspace(0, max(0, frame_count - 1), INPUT_FRAMES).astype(int)
                    previous = None
                    for frame_index, position in enumerate(positions):
                        capture.set(cv2.CAP_PROP_POS_FRAMES, int(position))
                        ok, frame = capture.read()
                        if not ok or frame is None:
                            if previous is None:
                                raise ValueError("Cannot decode first sampled frame: " + row["relative_path"])
                            frame = previous
                        previous = frame
                        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                        rgb = cv2.resize(rgb, (INPUT_WIDTH, INPUT_HEIGHT), interpolation=cv2.INTER_AREA)
                        x[item_index, frame_index] = rgb.astype(np.float32)
                finally:
                    capture.release()
                y[item_index, label_index[row["label"]]] = 1.0
            return x, y

        def on_epoch_end(self):
            if self.shuffle:
                np.random.shuffle(self.order)

    inputs = tf.keras.Input(
        shape=(INPUT_FRAMES, INPUT_HEIGHT, INPUT_WIDTH, INPUT_CHANNELS),
        dtype=tf.float32,
        name="rgb_video",
    )
    backbone = tf.keras.applications.MobileNetV3Small(
        input_shape=(INPUT_HEIGHT, INPUT_WIDTH, INPUT_CHANNELS),
        include_top=False,
        weights="imagenet",
        pooling="avg",
        include_preprocessing=True,
    )
    backbone.trainable = False
    features = tf.keras.layers.TimeDistributed(backbone, name="frame_encoder")(inputs)
    features = tf.keras.layers.GlobalAveragePooling1D(name="temporal_mean")(features)
    features = tf.keras.layers.Dense(256, activation="relu", name="projection")(features)
    features = tf.keras.layers.Dropout(0.35)(features)
    outputs = tf.keras.layers.Dense(len(LABELS), activation="softmax", name="event_type")(features)
    model = tf.keras.Model(inputs, outputs, name="brixton_lightning_temporal")
    model.compile(
        optimizer=tf.keras.optimizers.Adam(args.learning_rate),
        loss="categorical_crossentropy",
        metrics=["accuracy"],
    )

    train_seq = ClipSequence(partitions["train"], shuffle=True)
    validation_seq = ClipSequence(partitions["validation"], shuffle=False)
    test_seq = ClipSequence(partitions["test"], shuffle=False)
    counts = np.zeros(len(LABELS), dtype=np.float64)
    for row in partitions["train"]:
        counts[label_index[row["label"]]] += 1
    if np.any(counts == 0):
        raise ValueError("At least one class has no training examples.")
    class_weights = {i: float(len(train_seq.items) / (len(LABELS) * counts[i])) for i in range(len(LABELS))}
    output_dir = args.out
    output_dir.mkdir(parents=True, exist_ok=True)
    callbacks = [
        tf.keras.callbacks.EarlyStopping(monitor="val_loss", patience=4, restore_best_weights=True),
        tf.keras.callbacks.ModelCheckpoint(str(output_dir / "best.keras"), monitor="val_loss", save_best_only=True),
    ]
    model.fit(
        train_seq,
        validation_data=validation_seq,
        epochs=args.epochs,
        class_weight=class_weights,
        callbacks=callbacks,
        verbose=2,
    )

    y_true, y_score = [], []
    for index in range(len(test_seq)):
        batch_x, batch_y = test_seq[index]
        y_true.extend(np.argmax(batch_y, axis=1).tolist())
        y_score.extend(model.predict(batch_x, verbose=0).tolist())
    y_true = np.asarray(y_true, dtype=np.int32)
    y_score = np.asarray(y_score, dtype=np.float32)
    y_pred = np.argmax(y_score, axis=1)
    one_hot = np.eye(len(LABELS), dtype=np.float32)[y_true]
    metrics = {
        "test_clips": int(len(y_true)),
        "accuracy": float(accuracy_score(y_true, y_pred)),
        "macro_f1": float(f1_score(y_true, y_pred, average="macro", zero_division=0)),
        "brier_score": float(np.mean(np.sum((y_score - one_hot) ** 2, axis=1))),
        "labels": list(LABELS),
        "classification_report": classification_report(
            y_true, y_pred, labels=list(range(len(LABELS))), target_names=list(LABELS),
            output_dict=True, zero_division=0,
        ),
        "confusion_matrix": confusion_matrix(y_true, y_pred, labels=list(range(len(LABELS)))).tolist(),
    }
    (output_dir / "metrics.json").write_text(json.dumps(metrics, indent=2), encoding="utf-8")
    model.save(output_dir / "model.keras")

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite = converter.convert()
    model_path = output_dir / "lightning_brixton.tflite"
    model_path.write_bytes(tflite)

    interpreter = tf.lite.Interpreter(model_content=tflite)
    interpreter.allocate_tensors()
    input_shape = interpreter.get_input_details()[0]["shape"].tolist()
    output_shape = interpreter.get_output_details()[0]["shape"].tolist()
    expected_input = [1, INPUT_FRAMES, INPUT_HEIGHT, INPUT_WIDTH, INPUT_CHANNELS]
    expected_output = [1, len(LABELS)]
    if input_shape != expected_input or output_shape != expected_output:
        raise RuntimeError(f"TFLite contract mismatch: {input_shape} -> {output_shape}")
    metadata = model_metadata()
    metadata.update({
        "input_shape": input_shape,
        "output_shape": output_shape,
        "training_clips": len(partitions["train"]),
        "validation_clips": len(partitions["validation"]),
        "test_clips": len(partitions["test"]),
        "class_weights": {LABELS[i]: class_weights[i] for i in range(len(LABELS))},
    })
    (output_dir / "metadata.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    return metrics


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--epochs", type=int, default=20)
    parser.add_argument("--batch-size", type=int, default=4)
    parser.add_argument("--learning-rate", type=float, default=1e-3)
    args = parser.parse_args()
    metrics = build_and_train(args)
    print(json.dumps({key: metrics[key] for key in ("test_clips", "accuracy", "macro_f1", "brier_score")}, indent=2))


if __name__ == "__main__":
    main()
