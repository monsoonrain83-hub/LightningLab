"""Train LightningLab's optional offline cloud-genus model.

Dataset layout (CCSN-style): DATA/Ac/*.jpg ... DATA/St/*.jpg
Labels: Ac, As, Cb, Cc, Ci, Cs, Ct, Cu, Ns, Sc, St.
The model is a research/experimental classifier until it has been evaluated on
independent phone-camera images representative of deployment conditions.

Install: pip install tensorflow scikit-learn pillow
Run:     python tools/train_cloud_model.py --data /path/to/CCSN --out tools/cloud_ai/out

The script never downloads datasets. Verify dataset licence/terms yourself before
training or redistributing data and models. The CCSN authors' access terms must
be checked before use; do not assume redistribution rights.
"""
import argparse
import hashlib
import json
import os
import random
from datetime import datetime, timezone

import numpy as np
import tensorflow as tf
from sklearn.metrics import classification_report, confusion_matrix, f1_score

LABELS = ["Ac", "As", "Cb", "Cc", "Ci", "Cs", "Ct", "Cu", "Ns", "Sc", "St"]
SIZE = 224
PAD_VALUE = 127.0


def parse_args():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True, help="Root containing one folder per class")
    ap.add_argument("--out", default="out")
    ap.add_argument("--epochs-head", type=int, default=10)
    ap.add_argument("--epochs-ft", type=int, default=15)
    ap.add_argument("--batch-size", type=int, default=32)
    ap.add_argument("--seed", type=int, default=42)
    return ap.parse_args()


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def collect_files(root):
    folders = sorted(d for d in os.listdir(root) if os.path.isdir(os.path.join(root, d)))
    if folders != sorted(LABELS):
        raise ValueError(f"Class folders must be exactly {sorted(LABELS)}, found {folders}")
    paths, labels, seen = [], [], {}
    duplicate_count = 0
    for label_id, label in enumerate(LABELS):
        folder = os.path.join(root, label)
        class_count = 0
        for name in sorted(os.listdir(folder)):
            if not name.lower().endswith((".jpg", ".jpeg", ".png", ".webp")):
                continue
            path = os.path.join(folder, name)
            digest = sha256(path)
            if digest in seen:
                duplicate_count += 1
                if seen[digest][1] != label_id:
                    raise ValueError(f"Identical image occurs in multiple classes: {path} and {seen[digest][0]}")
                continue
            seen[digest] = (path, label_id)
            paths.append(path)
            labels.append(label_id)
            class_count += 1
        if class_count < 5:
            raise ValueError(f"Class {label} has only {class_count} unique images; at least 5 are required")
    print(f"Unique images: {len(paths)}; exact duplicates skipped: {duplicate_count}")
    return np.asarray(paths), np.asarray(labels, dtype=np.int32)


def letterbox(image):
    """Aspect-ratio-preserving resize with the same neutral RGB pad as Android."""
    shape = tf.shape(image)
    h = tf.cast(shape[0], tf.float32)
    w = tf.cast(shape[1], tf.float32)
    scale = tf.minimum(SIZE / h, SIZE / w)
    nh = tf.maximum(1, tf.cast(tf.round(h * scale), tf.int32))
    nw = tf.maximum(1, tf.cast(tf.round(w * scale), tf.int32))
    resized = tf.image.resize(image, tf.stack([nh, nw]), method="bilinear")
    pad_h = SIZE - nh
    pad_w = SIZE - nw
    top = pad_h // 2
    bottom = pad_h - top
    left = pad_w // 2
    right = pad_w - left
    padded = tf.pad(resized,
                    [[top, bottom], [left, right], [0, 0]],
                    constant_values=PAD_VALUE)
    padded.set_shape((SIZE, SIZE, 3))
    return padded


def load_image(path, label):
    raw = tf.io.read_file(path)
    image = tf.io.decode_image(raw, channels=3, expand_animations=False)
    image.set_shape((None, None, 3))
    image = letterbox(tf.cast(image, tf.float32))
    return image, label


def make_dataset(paths, labels, indices, batch_size, training, seed):
    ds = tf.data.Dataset.from_tensor_slices((paths[indices], labels[indices]))
    if training:
        ds = ds.shuffle(len(indices), seed=seed, reshuffle_each_iteration=True)
    ds = ds.map(load_image, num_parallel_calls=tf.data.AUTOTUNE)
    ds = ds.batch(batch_size).prefetch(tf.data.AUTOTUNE)
    return ds


def split_per_class(labels, seed):
    rng = np.random.default_rng(seed)
    train, val, test = [], [], []
    for class_id in range(len(LABELS)):
        idx = np.flatnonzero(labels == class_id)
        rng.shuffle(idx)
        n = len(idx)
        n_train = max(1, int(n * 0.70))
        n_val = max(1, int(n * 0.15))
        if n_train + n_val >= n:
            n_train = n - 2
            n_val = 1
        train.extend(idx[:n_train])
        val.extend(idx[n_train:n_train + n_val])
        test.extend(idx[n_train + n_val:])
    return np.asarray(train), np.asarray(val), np.asarray(test)


def main():
    args = parse_args()
    os.makedirs(args.out, exist_ok=True)
    random.seed(args.seed)
    np.random.seed(args.seed)
    tf.random.set_seed(args.seed)

    paths, labels = collect_files(args.data)
    train_idx, val_idx, test_idx = split_per_class(labels, args.seed)
    if set(train_idx) & set(val_idx) or set(train_idx) & set(test_idx) or set(val_idx) & set(test_idx):
        raise RuntimeError("Dataset split overlap detected")

    train_ds = make_dataset(paths, labels, train_idx, args.batch_size, True, args.seed)
    val_ds = make_dataset(paths, labels, val_idx, args.batch_size, False, args.seed)
    test_ds = make_dataset(paths, labels, test_idx, args.batch_size, False, args.seed)

    augmentation = tf.keras.Sequential([
        tf.keras.layers.RandomFlip("horizontal"),
        tf.keras.layers.RandomRotation(0.04),
        tf.keras.layers.RandomZoom(0.08),
        tf.keras.layers.RandomBrightness(0.10, value_range=(0, 255)),
        tf.keras.layers.RandomContrast(0.10),
    ], name="training_augmentation")
    base = tf.keras.applications.MobileNetV3Small(
        input_shape=(SIZE, SIZE, 3), include_top=False, weights="imagenet",
        include_preprocessing=True)
    base.trainable = False
    inputs = tf.keras.Input((SIZE, SIZE, 3), name="rgb_0_255")
    x = augmentation(inputs)
    x = base(x, training=False)
    x = tf.keras.layers.GlobalAveragePooling2D()(x)
    x = tf.keras.layers.Dropout(0.30)(x)
    outputs = tf.keras.layers.Dense(len(LABELS), activation="softmax", name="class_scores")(x)
    model = tf.keras.Model(inputs, outputs)

    best_path = os.path.join(args.out, "best.keras")
    callbacks = [
        tf.keras.callbacks.ModelCheckpoint(best_path, monitor="val_loss", save_best_only=True),
        tf.keras.callbacks.EarlyStopping(monitor="val_loss", patience=5, restore_best_weights=True),
        tf.keras.callbacks.CSVLogger(os.path.join(args.out, "training_log.csv")),
    ]
    model.compile(tf.keras.optimizers.Adam(1e-3), "sparse_categorical_crossentropy", ["accuracy"])
    model.fit(train_ds, validation_data=val_ds, epochs=args.epochs_head, callbacks=callbacks)

    base.trainable = True
    for layer in base.layers[:-30]:
        layer.trainable = False
    model.compile(tf.keras.optimizers.Adam(1e-4), "sparse_categorical_crossentropy", ["accuracy"])
    model.fit(train_ds, validation_data=val_ds, epochs=args.epochs_ft, callbacks=callbacks)
    model = tf.keras.models.load_model(best_path)

    y_true = np.concatenate([y.numpy() for _, y in test_ds])
    scores = model.predict(test_ds, verbose=0)
    y_pred = np.argmax(scores, axis=1)
    report = classification_report(y_true, y_pred, labels=list(range(len(LABELS))),
                                   target_names=LABELS, digits=4, zero_division=0)
    cm = confusion_matrix(y_true, y_pred, labels=list(range(len(LABELS))))
    metrics = (
        f"TEST accuracy: {(y_true == y_pred).mean():.4f} (n={len(y_true)})\n"
        f"TEST macro-F1: {f1_score(y_true, y_pred, average='macro', zero_division=0):.4f}\n\n"
        f"{report}\nConfusion matrix (rows=true, cols=pred; order={LABELS}):\n{cm}\n"
    )
    with open(os.path.join(args.out, "metrics.txt"), "w", encoding="utf-8") as f:
        f.write(metrics)
    print(metrics)

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite = converter.convert()
    model_path = os.path.join(args.out, "cloud_model.tflite")
    with open(model_path, "wb") as f:
        f.write(tflite)

    manifest = {
        "model_id": "lightninglab_cloud_genus_ccsn",
        "model_version": "1.1.0",
        "task": "single_label_cloud_dataset_classification",
        "architecture": "MobileNetV3Small",
        "input": {"shape": [1, SIZE, SIZE, 3], "layout": "NHWC", "dtype": "float32", "range": [0, 255],
                  "resize": "aspect_ratio_preserving_letterbox", "pad_rgb": [127, 127, 127]},
        "output": {"shape": [1, len(LABELS)], "activation": "softmax"},
        "labels": LABELS,
        "dataset": "User-supplied CCSN-style folder dataset; verify source licence before use",
        "training_seed": args.seed,
        "training_date_utc": datetime.now(timezone.utc).isoformat(),
        "test_accuracy": float((y_true == y_pred).mean()),
        "test_macro_f1": float(f1_score(y_true, y_pred, average="macro", zero_division=0)),
        "model_file": os.path.basename(model_path),
        "warning": "Not calibrated; validate on independent phone-camera images before operational use."
    }
    with open(os.path.join(args.out, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)
    print("Wrote", model_path)
    print("Wrote manifest.json and metrics.txt")


if __name__ == "__main__":
    main()
