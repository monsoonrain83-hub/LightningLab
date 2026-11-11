#!/usr/bin/env python3
"""Train a three-head CL/CM/CH model from prepare_montenegro_synoptic.py output."""
import argparse, csv, json, random
from pathlib import Path
import numpy as np
import tensorflow as tf
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix, f1_score

SIZE, HEADS, CLASSES = 224, ("CL", "CM", "CH"), 11
LABELS = [str(i) for i in range(10)] + ["/"]

def load_manifest(path):
    root = path.parent
    paths, targets, splits = [], [], []
    with path.open("r", encoding="utf-8-sig", newline="") as f:
        for row in csv.DictReader(f):
            target = np.zeros((3, CLASSES), dtype=np.float32)
            for hi, head in enumerate(HEADS):
                dist = json.loads(row.get(head.lower() + "_soft_targets", "{}") or "{}")
                for code, probability in dist.items():
                    if code in LABELS:
                        target[hi, LABELS.index(code)] = float(probability)
            if target.sum() <= 0:
                continue
            image = root / row["image_path"]
            if not image.is_file():
                raise FileNotFoundError(image)
            paths.append(str(image)); targets.append(target); splits.append(row["split"])
    if not paths:
        raise ValueError("No labelled images found in manifest")
    return np.asarray(paths), np.asarray(targets), np.asarray(splits)

def make_dataset(paths, targets, batch_size, training=False, seed=42):
    ds = tf.data.Dataset.from_tensor_slices((paths, targets))
    if training:
        ds = ds.shuffle(len(paths), seed=seed, reshuffle_each_iteration=True)
    def decode(path, target):
        raw = tf.io.read_file(path)
        image = tf.io.decode_image(raw, channels=3, expand_animations=False)
        image.set_shape([None, None, 3])
        image = tf.cast(image, tf.float32)
        shape = tf.cast(tf.shape(image)[:2], tf.float32)
        scale = tf.minimum(float(SIZE) / shape[0], float(SIZE) / shape[1])
        height = tf.maximum(1, tf.minimum(SIZE, tf.cast(tf.round(shape[0] * scale), tf.int32)))
        width = tf.maximum(1, tf.minimum(SIZE, tf.cast(tf.round(shape[1] * scale), tf.int32)))
        image = tf.image.resize(image, [height, width], method="bilinear", antialias=True)
        ph, pw = SIZE - height, SIZE - width
        top, left = ph // 2, pw // 2
        image = tf.pad(image, [[top, ph-top], [left, pw-left], [0, 0]], constant_values=128.0)
        image.set_shape([SIZE, SIZE, 3])
        return image, target
    return ds.map(decode, num_parallel_calls=tf.data.AUTOTUNE).batch(batch_size).prefetch(tf.data.AUTOTUNE)

def masked_soft_cross_entropy(y_true, y_pred):
    mask = tf.reduce_sum(y_true, axis=-1)
    ce = -tf.reduce_sum(y_true * tf.math.log(tf.clip_by_value(y_pred, 1e-7, 1.0)), axis=-1)
    return tf.reduce_sum(ce * mask, axis=-1) / tf.maximum(tf.reduce_sum(mask, axis=-1), 1.0)

def build_model(weights):
    backbone = tf.keras.applications.MobileNetV3Small(
        include_top=False, weights=weights, input_shape=(SIZE, SIZE, 3), include_preprocessing=True)
    backbone.trainable = False
    inputs = tf.keras.Input((SIZE, SIZE, 3), dtype=tf.float32, name="image_rgb_0_255")
    x = backbone(inputs, training=False)
    x = tf.keras.layers.GlobalAveragePooling2D()(x)
    x = tf.keras.layers.Dropout(.25)(x)
    x = tf.keras.layers.Dense(3 * CLASSES)(x)
    x = tf.keras.layers.Reshape((3, CLASSES))(x)
    outputs = tf.keras.layers.Softmax(axis=-1, name="wmo_synoptic_CL_CM_CH")(x)
    return tf.keras.Model(inputs, outputs), backbone

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--manifest", required=True); ap.add_argument("--out", required=True)
    ap.add_argument("--weights", choices=("imagenet", "none"), default="imagenet")
    ap.add_argument("--epochs-head", type=int, default=10); ap.add_argument("--epochs-ft", type=int, default=8)
    ap.add_argument("--batch-size", type=int, default=16); ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()
    random.seed(args.seed); np.random.seed(args.seed); tf.random.set_seed(args.seed)
    out = Path(args.out); out.mkdir(parents=True, exist_ok=True)
    paths, targets, splits = load_manifest(Path(args.manifest))
    indices = {name: np.where(splits == name)[0] for name in ("train", "validation", "test")}
    if any(len(indices[name]) == 0 for name in indices):
        raise ValueError("Manifest must contain non-empty train, validation and test splits")
    datasets = {name: make_dataset(paths[idx], targets[idx], args.batch_size, name == "train", args.seed)
                for name, idx in indices.items()}
    model, backbone = build_model(None if args.weights == "none" else "imagenet")
    model.compile(optimizer=tf.keras.optimizers.Adam(1e-3), loss=masked_soft_cross_entropy)
    model.fit(datasets["train"], validation_data=datasets["validation"], epochs=args.epochs_head,
              callbacks=[tf.keras.callbacks.EarlyStopping(patience=3, restore_best_weights=True)])
    if args.epochs_ft > 0:
        backbone.trainable = True
        for layer in backbone.layers[:-30]:
            layer.trainable = False
        model.compile(optimizer=tf.keras.optimizers.Adam(1e-5), loss=masked_soft_cross_entropy)
        model.fit(datasets["train"], validation_data=datasets["validation"], epochs=args.epochs_ft,
                  callbacks=[tf.keras.callbacks.EarlyStopping(patience=3, restore_best_weights=True)])

    y_true = targets[indices["test"]]
    y_pred = model.predict(datasets["test"], verbose=0)
    metrics = {}
    for hi, head in enumerate(HEADS):
        truth_dist, pred = y_true[:, hi, :], y_pred[:, hi, :]
        valid = truth_dist.sum(axis=1) > 0
        truth, guess = truth_dist[valid].argmax(axis=1), pred[valid].argmax(axis=1)
        soft_ce = float(np.mean(-np.sum(truth_dist[valid] * np.log(np.clip(pred[valid], 1e-7, 1.0)), axis=1)))
        brier = float(np.mean(np.sum((pred[valid] - truth_dist[valid]) ** 2, axis=1)))
        metrics[head] = {
            "labelled_test_images": int(valid.sum()),
            "modal_observer_accuracy": float(accuracy_score(truth, guess)),
            "modal_observer_macro_f1": float(f1_score(truth, guess, labels=list(range(CLASSES)), average="macro", zero_division=0)),
            "soft_cross_entropy": soft_ce, "soft_brier_score": brier,
            "classification_report": classification_report(truth, guess, labels=list(range(CLASSES)),
                target_names=LABELS, output_dict=True, zero_division=0),
            "confusion_matrix": confusion_matrix(truth, guess, labels=list(range(CLASSES))).tolist()
        }
    model.save(out / "cloud_synoptic.keras")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    (out / "cloud_synoptic_model.tflite").write_bytes(converter.convert())
    contract = {
        "input_name": "image_rgb_0_255", "input_shape": [1, SIZE, SIZE, 3], "input_dtype": "float32",
        "input_range": [0, 255], "resize": "aspect-preserving", "padding_rgb": [128, 128, 128],
        "output_shape": [1, 3, 11], "output_dtype": "float32", "output_activation": "softmax_per_head",
        "head_order": list(HEADS), "class_order_per_head": LABELS,
        "slash_semantics": "not determinable by observer", "scores": "raw model scores; not calibrated probabilities"
    }
    (out / "model_contract.json").write_text(json.dumps(contract, indent=2) + "\n", encoding="utf-8")
    (out / "metrics.json").write_text(json.dumps(metrics, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({h: {k: v for k, v in metrics[h].items() if k not in ("classification_report", "confusion_matrix")} for h in HEADS}, indent=2))

if __name__ == "__main__":
    main()
