"""
Train the LightningLab cloud-genus classifier (run on a PC or free Google Colab GPU).  UNTESTED here - no TensorFlow/data
was available in the authoring environment; run it and READ the printed test metrics before trusting the model.

DATA: CCSN database (Zhang et al.; 2543 images 256x256, 11 classes Ac As Cb Cc Ci Cs Ct Cu Ns Sc St; released CC0).
      Download it yourself (Harvard Dataverse doi:10.7910/DVN/CADDPD or GitHub upuil/CCSN-Database) and arrange as
          CCSN/Ac/*.jpg  CCSN/As/*.jpg ... CCSN/St/*.jpg
      Optional: put YOUR OWN labelled sky photos in the same folders (best way to fit the model to phone images).

RUN:  pip install tensorflow scikit-learn
      python train_cloud_model.py --data CCSN --out out

OUTPUT: out/cloud_model.tflite  (import in the app: Weather tab -> Import model)
        out/metrics.txt  (test accuracy, per-class precision/recall, confusion matrix)
Preprocessing is inside the model: the app feeds raw RGB 0..255 at 224x224.
Splits are by FILE, 70/15/15 stratified, fixed seed; the test split is never used for training or model selection.
"""
import argparse, os, random, json
import numpy as np
import tensorflow as tf
from sklearn.metrics import classification_report, confusion_matrix

LABELS = ["Ac", "As", "Cb", "Cc", "Ci", "Cs", "Ct", "Cu", "Ns", "Sc", "St"]   # MUST match CloudClassifier.LABELS
SIZE = 224

ap = argparse.ArgumentParser()
ap.add_argument("--data", required=True); ap.add_argument("--out", default="out")
ap.add_argument("--epochs_head", type=int, default=8); ap.add_argument("--epochs_ft", type=int, default=12)
a = ap.parse_args()
os.makedirs(a.out, exist_ok=True)
random.seed(1); np.random.seed(1); tf.random.set_seed(1)

folders = sorted(d for d in os.listdir(a.data) if os.path.isdir(os.path.join(a.data, d)))
assert folders == LABELS, f"class folders must be exactly {LABELS}, found {folders}"

files, labels = [], []
for i, c in enumerate(LABELS):
    for f in sorted(os.listdir(os.path.join(a.data, c))):
        if f.lower().endswith((".jpg", ".jpeg", ".png")):
            files.append(os.path.join(a.data, c, f)); labels.append(i)
files = np.array(files); labels = np.array(labels)

tr, va, te = [], [], []
for i in range(len(LABELS)):
    idx = np.where(labels == i)[0]; np.random.shuffle(idx)
    n = len(idx); n_tr = int(.7 * n); n_va = int(.15 * n)
    tr += list(idx[:n_tr]); va += list(idx[n_tr:n_tr + n_va]); te += list(idx[n_tr + n_va:])

def load(path, y):
    img = tf.io.decode_image(tf.io.read_file(path), channels=3, expand_animations=False)
    img = tf.image.resize(img, (SIZE, SIZE)); img.set_shape((SIZE, SIZE, 3))
    return img, y

def ds(ix, train):
    d = tf.data.Dataset.from_tensor_slices((files[ix], labels[ix]))
    if train: d = d.shuffle(len(ix), seed=1)
    d = d.map(load, num_parallel_calls=tf.data.AUTOTUNE).batch(32)
    return d.prefetch(tf.data.AUTOTUNE)

dtr, dva, dte = ds(tr, True), ds(va, False), ds(te, False)

aug = tf.keras.Sequential([tf.keras.layers.RandomFlip("horizontal"), tf.keras.layers.RandomRotation(0.05),
                           tf.keras.layers.RandomZoom(0.1), tf.keras.layers.RandomBrightness(0.15, value_range=(0, 255))])
base = tf.keras.applications.MobileNetV3Small(input_shape=(SIZE, SIZE, 3), include_top=False, weights="imagenet",
                                              include_preprocessing=True)   # expects raw 0..255
base.trainable = False
inp = tf.keras.Input((SIZE, SIZE, 3))
x = aug(inp); x = base(x, training=False)
x = tf.keras.layers.GlobalAveragePooling2D()(x); x = tf.keras.layers.Dropout(0.3)(x)
out = tf.keras.layers.Dense(len(LABELS), activation="softmax")(x)
model = tf.keras.Model(inp, out)

best = os.path.join(a.out, "best.keras")
cb = [tf.keras.callbacks.ModelCheckpoint(best, monitor="val_accuracy", save_best_only=True),
      tf.keras.callbacks.EarlyStopping(monitor="val_accuracy", patience=5, restore_best_weights=True)]
model.compile(tf.keras.optimizers.Adam(1e-3), "sparse_categorical_crossentropy", ["accuracy"])
model.fit(dtr, validation_data=dva, epochs=a.epochs_head, callbacks=cb)
base.trainable = True
for l in base.layers[:-30]: l.trainable = False
model.compile(tf.keras.optimizers.Adam(1e-4), "sparse_categorical_crossentropy", ["accuracy"])
model.fit(dtr, validation_data=dva, epochs=a.epochs_ft, callbacks=cb)
model = tf.keras.models.load_model(best)

yt = np.concatenate([y.numpy() for _, y in dte]); yp = np.argmax(model.predict(dte), axis=1)
acc = float((yt == yp).mean())
rep = classification_report(yt, yp, target_names=LABELS, digits=3)
cm = confusion_matrix(yt, yp)
with open(os.path.join(a.out, "metrics.txt"), "w") as f:
    f.write(f"TEST accuracy: {acc:.4f}  (n={len(yt)})\n\n{rep}\nConfusion matrix (rows=true, cols=pred, order {LABELS}):\n{cm}\n")
print(open(os.path.join(a.out, "metrics.txt")).read())

conv = tf.lite.TFLiteConverter.from_keras_model(model)
conv.optimizations = [tf.lite.Optimize.DEFAULT]
open(os.path.join(a.out, "cloud_model.tflite"), "wb").write(conv.convert())
print("Wrote", os.path.join(a.out, "cloud_model.tflite"))
