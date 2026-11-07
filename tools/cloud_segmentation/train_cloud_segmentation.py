#!/usr/bin/env python3
"""Train and export an offline cloud-vs-background segmentation model for Lightning Lab.

Dataset layout:
  DATA/images/*.jpg
  DATA/masks/*.png
  DATA/manifest.csv  (image,mask,group_id[,split])

Mask convention: white (>=128) = cloud; black (<128) = non-cloud.
If split is omitted, groups are split 70/15/15 so frames from the same capture
sequence cannot leak across train/validation/test.

Export contract: float32 RGB [1,256,256,3] in 0..255 -> float32 sigmoid
mask [1,256,256,1] in 0..1. Aspect-preserving grey letterbox matches Android.
"""
from __future__ import annotations
import argparse, csv, hashlib, json, random
from collections import defaultdict
from pathlib import Path

import numpy as np
import tensorflow as tf

SIZE = 256
SEED = 42
VALID_EXT = {'.jpg', '.jpeg', '.png', '.webp'}


def read_manifest(root: Path):
    manifest = root / 'manifest.csv'
    if not manifest.is_file():
        raise SystemExit(f'Missing {manifest}. Copy manifest_template.csv and edit it.')
    rows = []
    with manifest.open(newline='', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        required = {'image', 'mask', 'group_id'}
        if not required.issubset(reader.fieldnames or []):
            raise SystemExit('manifest.csv needs columns: image,mask,group_id and optional split')
        for line, row in enumerate(reader, 2):
            image = (root / row['image'].strip()).resolve()
            mask = (root / row['mask'].strip()).resolve()
            if not image.is_file() or not mask.is_file():
                raise SystemExit(f'Missing image or mask at manifest line {line}: {image} / {mask}')
            if image.suffix.lower() not in VALID_EXT:
                raise SystemExit(f'Unsupported image extension at line {line}: {image.suffix}')
            group = row['group_id'].strip()
            if not group:
                raise SystemExit(f'Empty group_id at line {line}')
            split = row.get('split', '').strip().lower()
            if split and split not in {'train', 'validation', 'test'}:
                raise SystemExit(f'Invalid split at line {line}: {split}')
            rows.append({'image': str(image), 'mask': str(mask), 'group': group, 'split': split})
    if len(rows) < 15:
        raise SystemExit(f'Only {len(rows)} image/mask pairs; at least 15 are required for a basic split.')
    return rows


def assign_splits(rows, seed):
    explicit = [r['split'] for r in rows]
    if any(explicit):
        if not all(explicit):
            raise SystemExit('Either provide split for every row or leave split blank for all rows.')
        group_splits = defaultdict(set)
        for r in rows: group_splits[r['group']].add(r['split'])
        bad = [g for g, s in group_splits.items() if len(s) != 1]
        if bad: raise SystemExit(f'Group leakage: groups assigned to multiple splits, e.g. {bad[:5]}')
        return rows
    groups = sorted({r['group'] for r in rows})
    if len(groups) < 5:
        raise SystemExit('At least 5 distinct group_id values are required for automatic train/validation/test split.')
    rng = random.Random(seed); rng.shuffle(groups)
    n = len(groups)
    n_train = max(1, int(round(n * .70)))
    n_val = max(1, int(round(n * .15)))
    if n_train + n_val >= n: n_train, n_val = n - 2, 1
    split_map = {g: ('train' if i < n_train else 'validation' if i < n_train+n_val else 'test') for i, g in enumerate(groups)}
    for r in rows: r['split'] = split_map[r['group']]
    return rows


def decode_pair(image_path, mask_path):
    image = tf.io.decode_image(tf.io.read_file(image_path), channels=3, expand_animations=False)
    image.set_shape([None, None, 3])
    mask = tf.io.decode_image(tf.io.read_file(mask_path), channels=1, expand_animations=False)
    mask.set_shape([None, None, 1])
    tf.debugging.assert_equal(tf.shape(image)[:2], tf.shape(mask)[:2], message='Image and mask dimensions differ')
    image = tf.image.resize_with_pad(tf.cast(image, tf.float32), SIZE, SIZE, method='bilinear', antialias=True, pad_value=127.5)
    mask = tf.image.resize_with_pad(tf.cast(mask, tf.float32), SIZE, SIZE, method='nearest', antialias=False, pad_value=0.0)
    mask = tf.cast(mask >= 128.0, tf.float32)
    return image, mask


def make_dataset(rows, split, batch, training):
    chosen = [r for r in rows if r['split'] == split]
    if not chosen: raise SystemExit(f'No records assigned to {split}.')
    ds = tf.data.Dataset.from_tensor_slices(([r['image'] for r in chosen], [r['mask'] for r in chosen]))
    if training: ds = ds.shuffle(len(chosen), seed=SEED, reshuffle_each_iteration=True)
    ds = ds.map(decode_pair, num_parallel_calls=tf.data.AUTOTUNE)
    if training:
        def augment(x, y):
            flip = tf.random.uniform(()) > .5
            x = tf.cond(flip, lambda: tf.image.flip_left_right(x), lambda: x)
            y = tf.cond(flip, lambda: tf.image.flip_left_right(y), lambda: y)
            # Mild photometric variation only; never alter the binary mask.
            x = tf.image.random_brightness(x, max_delta=12.0)
            x = tf.clip_by_value(x, 0.0, 255.0)
            return x, y
        ds = ds.map(augment, num_parallel_calls=tf.data.AUTOTUNE)
    return ds.batch(batch).prefetch(tf.data.AUTOTUNE), len(chosen)


def dice_loss(y_true, y_pred):
    yt = tf.reshape(y_true, [-1]); yp = tf.reshape(y_pred, [-1])
    smooth = 1.0
    dice = (2.0 * tf.reduce_sum(yt * yp) + smooth) / (tf.reduce_sum(yt) + tf.reduce_sum(yp) + smooth)
    return 1.0 - dice


def bce_dice(y_true, y_pred):
    return tf.keras.losses.binary_crossentropy(y_true, y_pred) + dice_loss(y_true, y_pred)


def dice_metric(y_true, y_pred):
    yt = tf.cast(y_true >= .5, tf.float32); yp = tf.cast(y_pred >= .5, tf.float32)
    return (2.0 * tf.reduce_sum(yt * yp) + 1.0) / (tf.reduce_sum(yt) + tf.reduce_sum(yp) + 1.0)


def iou_metric(y_true, y_pred):
    yt = tf.cast(y_true >= .5, tf.float32); yp = tf.cast(y_pred >= .5, tf.float32)
    inter = tf.reduce_sum(yt * yp); union = tf.reduce_sum(tf.maximum(yt, yp))
    return (inter + 1.0) / (union + 1.0)


def build_model():
    inp = tf.keras.Input((SIZE, SIZE, 3), dtype=tf.float32, name='rgb_0_255')
    x0 = tf.keras.layers.Rescaling(1.0 / 127.5, offset=-1.0, name='normalize_rgb')(inp)
    skips = []
    x = x0
    for filters in (24, 48, 96, 160):
        x = tf.keras.layers.Conv2D(filters, 3, padding='same', activation='relu')(x)
        x = tf.keras.layers.SeparableConv2D(filters, 3, padding='same', activation='relu')(x)
        skips.append(x)
        x = tf.keras.layers.MaxPool2D()(x)
    x = tf.keras.layers.Conv2D(256, 3, padding='same', activation='relu')(x)
    for filters, skip in zip((160, 96, 48, 24), reversed(skips)):
        x = tf.keras.layers.UpSampling2D(interpolation='bilinear')(x)
        x = tf.keras.layers.Concatenate()([x, skip])
        x = tf.keras.layers.Conv2D(filters, 3, padding='same', activation='relu')(x)
        x = tf.keras.layers.SeparableConv2D(filters, 3, padding='same', activation='relu')(x)
    out = tf.keras.layers.Conv2D(1, 1, activation='sigmoid', dtype='float32', name='cloud_probability_mask')(x)
    return tf.keras.Model(inp, out, name='lightninglab_cloud_unet_small')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--data', required=True, help='Dataset folder containing manifest.csv')
    ap.add_argument('--out', default='out/cloud_segmentation')
    ap.add_argument('--epochs', type=int, default=45)
    ap.add_argument('--batch-size', type=int, default=8)
    ap.add_argument('--seed', type=int, default=SEED)
    args = ap.parse_args()
    if args.epochs < 1 or args.batch_size < 1: raise SystemExit('epochs and batch-size must be positive')
    random.seed(args.seed); np.random.seed(args.seed); tf.random.set_seed(args.seed)
    root = Path(args.data); out = Path(args.out); out.mkdir(parents=True, exist_ok=True)
    rows = assign_splits(read_manifest(root), args.seed)
    train, ntr = make_dataset(rows, 'train', args.batch_size, True)
    val, nva = make_dataset(rows, 'validation', args.batch_size, False)
    test, nte = make_dataset(rows, 'test', args.batch_size, False)
    model = build_model()
    model.compile(optimizer=tf.keras.optimizers.Adam(1e-3), loss=bce_dice, metrics=[dice_metric, iou_metric])
    callbacks = [
        tf.keras.callbacks.ModelCheckpoint(str(out/'best.keras'), monitor='val_iou_metric', mode='max', save_best_only=True),
        tf.keras.callbacks.EarlyStopping(monitor='val_iou_metric', mode='max', patience=8, restore_best_weights=True),
        tf.keras.callbacks.ReduceLROnPlateau(monitor='val_loss', factor=.3, patience=3, min_lr=1e-6),
    ]
    model.fit(train, validation_data=val, epochs=args.epochs, callbacks=callbacks)
    model = tf.keras.models.load_model(out/'best.keras', custom_objects={'bce_dice': bce_dice, 'dice_loss': dice_loss, 'dice_metric': dice_metric, 'iou_metric': iou_metric})
    test_metrics = model.evaluate(test, return_dict=True, verbose=1)
    (out/'test_metrics.json').write_text(json.dumps({k: float(v) for k,v in test_metrics.items()} | {'test_images': nte, 'train_images': ntr, 'validation_images': nva}, indent=2), encoding='utf-8')
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    # Keep float32 input/output to match the Android interpreter contract exactly.
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
    tflite = converter.convert()
    target = out/'cloud_segmentation.tflite'; target.write_bytes(tflite)
    (out/'model_contract.json').write_text(json.dumps({
        'input': {'shape':[1,SIZE,SIZE,3], 'dtype':'float32', 'layout':'NHWC', 'channels':'RGB', 'range':[0,255], 'resize':'aspect-preserving bilinear resize with grey 127.5 letterbox'},
        'output': {'shape':[1,SIZE,SIZE,1], 'dtype':'float32', 'meaning':'per-pixel cloud score after sigmoid', 'range':[0,1]},
        'threshold_default':0.5, 'architecture':'small U-Net with separable convolutions',
        'test_metrics': {k:float(v) for k,v in test_metrics.items()},
        'warning':'Validate on independent locations, seasons, cameras, haze/fog/smoke and night scenes before operational use.'
    }, indent=2), encoding='utf-8')
    print(f'Exported {target} ({target.stat().st_size:,} bytes)')
    print('Copy cloud_segmentation.tflite to the phone and import it in Lightning Lab > Weather analysis.')

if __name__ == '__main__': main()
