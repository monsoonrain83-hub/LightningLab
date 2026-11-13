#!/usr/bin/env python3
"""Train an optional temporal TFLite classifier from the Brixton video manifest.

Install tools/lightning_ai/requirements-training.txt before running. The exported model
accepts 8 RGB frames (224x224, float32 values 0..255) and returns seven softmax scores.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
from pathlib import Path

from model_contract import (
    INPUT_CHANNELS, INPUT_FRAMES, INPUT_HEIGHT, INPUT_WIDTH, LABELS,
    model_metadata, validate_manifest_rows, event_window_sample_times_ms,
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
        # Refuse stale or modified datasets before expensive training.
        expected_hash = (row.get("sha256") or "").lower()
        if len(expected_hash) != 64 or any(c not in "0123456789abcdef" for c in expected_hash):
            raise ValueError(f"Missing or invalid SHA-256: {row['relative_path']}")
        digest = hashlib.sha256()
        with clip.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
        if digest.hexdigest() != expected_hash:
            raise ValueError(f"Video SHA-256 mismatch: {row['relative_path']}")
        row["_absolute_path"] = str(clip)
    partitions = {split: [row for row in rows if row["split"] == split]
                  for split in ("train", "validation", "test")}
    train_labels = {row["label"] for row in partitions["train"]}
    missing = sorted(set(LABELS) - train_labels)
    if missing:
        raise ValueError("Training split lacks classes: " + ", ".join(missing))
    return rows, partitions


def attach_expert_event_windows(rows: list[dict], path: Path) -> None:
    """Require an independently annotated start/end window for EVERY clip.

    This is optional for experimental full-clip training but REQUIRED if the
    model is to match Android's per-detected-event temporal inference.
    """
    with path.open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        required = {"relative_path", "sha256", "start_ms", "end_ms", "reviewer"}
        if reader.fieldnames is None or not required <= set(reader.fieldnames):
            raise ValueError("Event window annotations require: " + ", ".join(sorted(required)))
        annotations = {}
        for entry in reader:
            relative = entry["relative_path"].strip()
            if not relative or relative in annotations:
                raise ValueError("Missing/duplicate expert annotation for " + relative)
            try:
                start = int(entry["start_ms"])
                end = int(entry["end_ms"])
            except ValueError as err:
                raise ValueError("Invalid expert event interval for " + relative) from err
            if start < 0 or end < start or end > 24 * 3600 * 1000 or not entry["reviewer"].strip():
                raise ValueError("Invalid expert event window/reviewer for " + relative)
            annotations[relative] = {
                "start_ms": start, "end_ms": end,
                "sha256": entry["sha256"].strip().lower(),
            }
    rows_by_path = {row["relative_path"]: row for row in rows}
    if set(annotations) != set(rows_by_path):
        raise ValueError("Expert annotation CSV must match the full training manifest exactly")
    for relative, annotation in annotations.items():
        row = rows_by_path[relative]
        if annotation["sha256"] != row["sha256"].lower():
            raise ValueError("Expert annotation source SHA-256 mismatch: " + relative)
        row["_event_window"] = (annotation["start_ms"], annotation["end_ms"])


def build_and_train(args) -> dict:
    import cv2
    import numpy as np
    import tensorflow as tf
    import random

    random.seed(args.seed)
    np.random.seed(args.seed)
    tf.keras.utils.set_random_seed(args.seed)
    from sklearn.metrics import accuracy_score, classification_report, confusion_matrix, f1_score

    rows, partitions = read_manifest(args.manifest, args.root)
    if args.event_times is not None:
        attach_expert_event_windows(rows, args.event_times)
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
                    if args.event_times is not None:
                        fps = float(capture.get(cv2.CAP_PROP_FPS))
                        if not np.isfinite(fps) or fps <= 0:
                            raise ValueError("Cannot time-align expert event without valid video FPS")
                        last_frame_ms = max(0, int(round((frame_count - 1) * 1000.0 / fps)))
                        event_start, event_end = row["_event_window"]
                        if event_end > last_frame_ms + int(round(1000.0 / fps)):
                            raise ValueError("Annotated event lies outside video timeline")
                        times = event_window_sample_times_ms(event_start, event_end, last_frame_ms)
                        positions = np.asarray([
                            min(frame_count - 1, max(0, int(round(t * fps / 1000.0))))
                            for t in times
                        ], dtype=int)
                    else:
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
    # Export every held-out prediction WITH original independent group, label,
    # SHA-256 and camera provenance, never just a summary accuracy number.
    predictions_file = output_dir / "test_predictions.csv"
    with predictions_file.open("w", encoding="utf-8", newline="") as stream:
        fieldnames = ["sample_id", "source_group", "split", "truth", "domain", "sha256"] + [
            "p_" + label for label in LABELS
        ]
        writer = csv.DictWriter(stream, fieldnames=fieldnames)
        writer.writeheader()
        for source_row, scores in zip(partitions["test"], y_score.tolist()):
            row = {
                "sample_id": source_row["relative_path"],
                "source_group": source_row["event_group"],
                "split": "test",
                "truth": source_row["label"],
                "domain": "brixton_camera_" + str(source_row["camera"]),
                "sha256": source_row["sha256"],
            }
            row.update({"p_" + label: format(float(scores[i]), ".9g")
                        for i, label in enumerate(LABELS)})
            writer.writerow(row)
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
        "seed": args.seed,
        "manifest_sha256": hashlib.sha256(args.manifest.read_bytes()).hexdigest(),
        "tflite_sha256": hashlib.sha256(tflite).hexdigest(),
        "test_predictions_sha256": hashlib.sha256(predictions_file.read_bytes()).hexdigest(),
        "temporal_sampling": (
            "android_detected_event_pm500ms" if args.event_times is not None
            else "full_clip_uniform_8_frames"
        ),
        "expert_event_annotations_sha256": (
            hashlib.sha256(args.event_times.read_bytes()).hexdigest()
            if args.event_times is not None else None
        ),
        "runtime_sampling_warning": (
            "Expert event windows supplied: training frame timestamps are aligned "
            "with Android +/-500 ms sampling. Video frame seeking and resolution "
            "still need on-device equivalence checks."
            if args.event_times is not None else
            "Experimental full-clip sampling differs from Android detected-event "
            "+/-500 ms windows; do not approve this model for scientific use."
        ),
        "tensorflow_version": tf.__version__,
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
    parser.add_argument("--event-times", type=Path, default=None,
                        help="Expert-annotated per-clip event windows CSV; enables Android-aligned sampling")
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--epochs", type=int, default=20)
    parser.add_argument("--seed", type=int, default=2026)
    parser.add_argument("--batch-size", type=int, default=4)
    parser.add_argument("--learning-rate", type=float, default=1e-3)
    args = parser.parse_args()
    metrics = build_and_train(args)
    print(json.dumps({key: metrics[key] for key in ("test_clips", "accuracy", "macro_f1", "brier_score")}, indent=2))


if __name__ == "__main__":
    main()
