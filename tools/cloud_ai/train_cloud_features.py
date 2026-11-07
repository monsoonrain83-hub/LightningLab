#!/usr/bin/env python3
"""Train an optional multi-label cloud supplementary-feature model.

CSV columns: image_path,labels ; labels is a semicolon-separated set of codes.
Use only expert-reviewed image labels. Empty labels are allowed (verified negatives).
Example:
  python tools/cloud_ai/train_cloud_features.py --csv data/cloud_features/annotations.csv --out out/cloud_features
Output: cloud_features.tflite (float32 RGB [1,224,224,3] 0..255 -> sigmoid [1,11]).
"""
import argparse, hashlib, json, random
from pathlib import Path
import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.metrics import classification_report

FEATURE_LABELS = ['incus','mamma','virga','praecipitatio','arcus','tuba','asperitas','murus','cauda','fluctus','cavum']
SIZE = 224

def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--csv',required=True); ap.add_argument('--out',default='out/cloud_features')
    ap.add_argument('--epochs',type=int,default=25); ap.add_argument('--batch-size',type=int,default=16); ap.add_argument('--seed',type=int,default=42)
    a=ap.parse_args(); random.seed(a.seed); np.random.seed(a.seed); tf.random.set_seed(a.seed)
    df=pd.read_csv(a.csv).fillna({'labels':''})
    if not {'image_path','labels'}.issubset(df.columns): raise SystemExit('CSV must contain image_path,labels columns')
    records=[]
    for row in df.itertuples(index=False):
        p=Path(str(row.image_path));
        if not p.is_file(): print('SKIP missing:',p); continue
        try:
            raw=p.read_bytes(); tf.io.decode_image(raw,channels=3,expand_animations=False)
            tags=set(x.strip().lower() for x in str(row.labels).split(';') if x.strip())
            unknown=tags-set(FEATURE_LABELS)
            if unknown: raise ValueError(f'unknown labels {sorted(unknown)}')
            records.append((str(p),[float(x in tags) for x in FEATURE_LABELS],hashlib.sha256(raw).hexdigest()))
        except Exception as e: print('SKIP invalid:',p,e)
    if len(records)<30: raise SystemExit(f'Need at least 30 reviewed images; found {len(records)}')
    groups={}
    for rec in records: groups.setdefault(rec[2],[]).append(rec)
    unique=list(groups.values()); random.shuffle(unique)
    if len(unique)<10: raise SystemExit('Need at least 10 unique image files after duplicate grouping')
    n=len(unique); ntr=max(1,int(n*.70)); nva=max(1,int(n*.15));
    if ntr+nva>=n: ntr=n-2; nva=1
    train=[x for g in unique[:ntr] for x in g]; val=[x for g in unique[ntr:ntr+nva] for x in g]; test=[x for g in unique[ntr+nva:] for x in g]
    def ds(items, training=False):
        paths=np.array([x[0] for x in items]); labels=np.array([x[1] for x in items],np.float32)
        d=tf.data.Dataset.from_tensor_slices((paths,labels))
        if training: d=d.shuffle(len(items),seed=a.seed)
        def load(path,y):
            im=tf.io.decode_image(tf.io.read_file(path),channels=3,expand_animations=False)
            im=tf.image.resize_with_pad(im,SIZE,SIZE,pad_value=127.5)
            im.set_shape((SIZE,SIZE,3)); return tf.cast(im,tf.float32),y
        return d.map(load,num_parallel_calls=tf.data.AUTOTUNE).batch(a.batch_size).prefetch(tf.data.AUTOTUNE)
    dtr=ds(train,True); dva=ds(val); dte=ds(test)
    base=tf.keras.applications.MobileNetV3Small(input_shape=(SIZE,SIZE,3),include_top=False,weights='imagenet',include_preprocessing=True)
    base.trainable=False
    inp=tf.keras.Input((SIZE,SIZE,3),name='rgb_0_255')
    x=base(inp,training=False); x=tf.keras.layers.GlobalAveragePooling2D()(x); x=tf.keras.layers.Dropout(.35)(x)
    out=tf.keras.layers.Dense(len(FEATURE_LABELS),activation='sigmoid',name='feature_scores')(x)
    model=tf.keras.Model(inp,out)
    model.compile(tf.keras.optimizers.Adam(1e-3),loss='binary_crossentropy',metrics=[tf.keras.metrics.AUC(multi_label=True,name='macro_auc')])
    outdir=Path(a.out); outdir.mkdir(parents=True,exist_ok=True)
    best=outdir/'best.keras'
    model.fit(dtr,validation_data=dva,epochs=a.epochs,callbacks=[tf.keras.callbacks.ModelCheckpoint(best,monitor='val_loss',save_best_only=True),tf.keras.callbacks.EarlyStopping(patience=5,restore_best_weights=True)])
    model=tf.keras.models.load_model(best)
    yt=np.concatenate([y.numpy() for _,y in dte]); probs=model.predict(dte,verbose=0); pred=(probs>=.5).astype(int)
    report=classification_report(yt,pred,target_names=FEATURE_LABELS,zero_division=0,digits=4)
    (outdir/'test_metrics.txt').write_text(report,encoding='utf-8')
    converter=tf.lite.TFLiteConverter.from_keras_model(model); converter.optimizations=[tf.lite.Optimize.DEFAULT]
    (outdir/'cloud_features.tflite').write_bytes(converter.convert())
    (outdir/'labels.json').write_text(json.dumps({'input':'float32 RGB [1,224,224,3],0..255; aspect-preserving resize-with-pad','output_order':FEATURE_LABELS,'activation':'independent sigmoid','threshold':.5,'test_images':len(test)},indent=2),encoding='utf-8')
    print(report); print('Wrote',outdir/'cloud_features.tflite')
if __name__=='__main__': main()
