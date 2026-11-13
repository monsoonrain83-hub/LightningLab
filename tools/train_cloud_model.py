#!/usr/bin/env python3
"""Train Lightning Lab's 10-WMO-genus image classifier and export TFLite.

Expected folders: DATA/Ac, DATA/As, ... DATA/St (10 genera only).
Run on a PC/Colab, never on the Android device:
  pip install -r tools/cloud_ai/requirements.txt
  python tools/cloud_ai/prepare_ccaim.py --out data/clouds/ccaim
  python tools/train_cloud_model.py --data data/clouds/ccaim --out out/clouds

Input contract matches CloudClassifier.kt: float32 RGB [1,224,224,3], 0..255.
The model includes MobileNetV3 preprocessing. Test images are held out and not used
for training/model selection. Exact duplicates are grouped before splitting.
"""
import argparse, csv, hashlib, json, os, random
from pathlib import Path
import numpy as np
import tensorflow as tf
from sklearn.metrics import classification_report, confusion_matrix

LABELS = ['Ac','As','Cb','Cc','Ci','Cs','Cu','Ns','Sc','St']
SIZE = 224

def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--data',required=True); ap.add_argument('--out',default='out/clouds')
    ap.add_argument('--epochs-head',type=int,default=12); ap.add_argument('--epochs-ft',type=int,default=15)
    ap.add_argument('--batch-size',type=int,default=24); ap.add_argument('--seed',type=int,default=42)
    a=ap.parse_args(); out=Path(a.out); out.mkdir(parents=True,exist_ok=True)
    random.seed(a.seed); np.random.seed(a.seed); tf.random.set_seed(a.seed)
    root=Path(a.data)
    found=sorted(p.name for p in root.iterdir() if p.is_dir())
    if found != sorted(LABELS): raise SystemExit(f'Expected exactly these class folders: {sorted(LABELS)}; found {found}')

    paths=[]; ys=[]; hashes=[]
    for yi, code in enumerate(LABELS):
        for p in sorted((root/code).glob('*')):
            if p.suffix.lower() not in {'.jpg','.jpeg','.png','.webp'}: continue
            try:
                raw=p.read_bytes(); digest=hashlib.sha256(raw).hexdigest()
                # Validate decodability before adding it.
                tf.io.decode_image(raw,channels=3,expand_animations=False)
            except Exception as e:
                print(f'SKIP unreadable: {p}: {e}'); continue
            paths.append(str(p)); ys.append(yi); hashes.append(digest)
    if not paths: raise SystemExit('No readable images found.')
    ys=np.asarray(ys,dtype=np.int32)
    # Keep byte-identical images in one split to reduce leakage.
    unique_groups={}
    for i,h in enumerate(hashes): unique_groups.setdefault(h,[]).append(i)
    by_class={i:[] for i in range(len(LABELS))}
    for group in unique_groups.values():
        cls=int(ys[group[0]])
        if any(int(ys[j]) != cls for j in group): raise SystemExit('Identical image bytes have conflicting labels; review dataset.')
        by_class[cls].append(group)
    train_idx=[]; val_idx=[]; test_idx=[]
    for cls, groups in by_class.items():
        random.shuffle(groups)
        n=sum(len(g) for g in groups)
        if n < 5 or len(groups) < 3:
            raise SystemExit(f'Class {LABELS[cls]} needs at least 5 images across 3 unique image groups; found {n} images/{len(groups)} groups.')
        # Split by duplicate group, approximately 70/15/15, forcing all three splits to exist.
        n_groups=len(groups); n_tr=max(1,int(round(.70*n_groups))); n_va=max(1,int(round(.15*n_groups)))
        if n_tr+n_va >= n_groups: n_tr=n_groups-2; n_va=1
        for gi,g in enumerate(groups):
            slot=0 if gi < n_tr else (1 if gi < n_tr+n_va else 2)
            [train_idx,val_idx,test_idx][slot].extend(g)
    train_idx=np.asarray(train_idx); val_idx=np.asarray(val_idx); test_idx=np.asarray(test_idx)
    paths=np.asarray(paths)
    def dataset(indices, training=False):
        d=tf.data.Dataset.from_tensor_slices((paths[indices],ys[indices]))
        if training: d=d.shuffle(len(indices),seed=a.seed,reshuffle_each_iteration=True)
        def load(path,label):
            image=tf.io.decode_image(tf.io.read_file(path),channels=3,expand_animations=False)
            # Match Android inference exactly: RGB, aspect-preserving scale,
            # centered neutral-grey (128) padding. resize_with_pad() in TensorFlow
            # pads with zeros, so it must NOT be used here.
            image=tf.cast(image,tf.float32)
            shape=tf.cast(tf.shape(image)[:2],tf.float32)
            scale=tf.minimum(float(SIZE)/shape[0],float(SIZE)/shape[1])
            new_h=tf.maximum(1,tf.minimum(SIZE,tf.cast(tf.round(shape[0]*scale),tf.int32)))
            new_w=tf.maximum(1,tf.minimum(SIZE,tf.cast(tf.round(shape[1]*scale),tf.int32)))
            image=tf.image.resize(image,[new_h,new_w],method='bilinear',antialias=True)
            pad_h=SIZE-new_h; pad_w=SIZE-new_w
            top=pad_h//2; bottom=pad_h-top; left=pad_w//2; right=pad_w-left
            image=tf.pad(image,[[top,bottom],[left,right],[0,0]],constant_values=128.0)
            image.set_shape((SIZE,SIZE,3))
            return image,label
        return d.map(load,num_parallel_calls=tf.data.AUTOTUNE).batch(a.batch_size).prefetch(tf.data.AUTOTUNE)
    dtr=dataset(train_idx,True); dva=dataset(val_idx); dte=dataset(test_idx)
    counts=np.bincount(ys[train_idx],minlength=len(LABELS))
    weights={i:float(len(train_idx)/(len(LABELS)*max(1,counts[i]))) for i in range(len(LABELS))}
    aug=tf.keras.Sequential([
        tf.keras.layers.RandomFlip('horizontal'),
        tf.keras.layers.RandomRotation(0.035),
        tf.keras.layers.RandomZoom(0.08),
        tf.keras.layers.RandomBrightness(0.10,value_range=(0,255)),
    ],name='training_augmentation')
    base=tf.keras.applications.MobileNetV3Small(input_shape=(SIZE,SIZE,3),include_top=False,
        weights='imagenet',include_preprocessing=True)
    base.trainable=False
    inp=tf.keras.Input((SIZE,SIZE,3),name='rgb_0_255')
    x=aug(inp); x=base(x,training=False); x=tf.keras.layers.GlobalAveragePooling2D()(x)
    x=tf.keras.layers.Dropout(.35)(x); out_layer=tf.keras.layers.Dense(len(LABELS),activation='softmax',name='genus_probabilities')(x)
    model=tf.keras.Model(inp,out_layer)
    best=str(out/'best.keras')
    def callbacks(): return [tf.keras.callbacks.ModelCheckpoint(best,monitor='val_loss',save_best_only=True),
        tf.keras.callbacks.EarlyStopping(monitor='val_loss',patience=5,restore_best_weights=True),
        tf.keras.callbacks.ReduceLROnPlateau(monitor='val_loss',factor=.3,patience=2,min_lr=1e-6)]
    model.compile(tf.keras.optimizers.Adam(1e-3),'sparse_categorical_crossentropy',['accuracy'])
    model.fit(dtr,validation_data=dva,epochs=a.epochs_head,class_weight=weights,callbacks=callbacks())
    base.trainable=True
    for layer in base.layers[:-25]: layer.trainable=False
    model.compile(tf.keras.optimizers.Adam(3e-5),'sparse_categorical_crossentropy',['accuracy'])
    model.fit(dtr,validation_data=dva,epochs=a.epochs_ft,class_weight=weights,callbacks=callbacks())
    model=tf.keras.models.load_model(best)
    yt=np.concatenate([y.numpy() for _,y in dte]); probs=model.predict(dte,verbose=0); yp=probs.argmax(axis=1)
    report=classification_report(yt,yp,labels=list(range(len(LABELS))),target_names=LABELS,zero_division=0,digits=4)
    cm=confusion_matrix(yt,yp,labels=list(range(len(LABELS))))
    metrics=f'TEST accuracy: {(yt==yp).mean():.4f} (n={len(yt)})\n\n{report}\nConfusion matrix (rows=true, cols=pred; order={LABELS}):\n{cm}\n'
    (out/'metrics.txt').write_text(metrics,encoding='utf-8'); print(metrics)
    # Export validation separately for calibration; test remains untouched.
    val_probs=model.predict(dva,verbose=0)
    columns=['sample_id','source_group','split','truth','domain','sha256']+['p_'+label for label in LABELS]
    evaluation_rows=[]
    for split, indexes, scores in [('validation',val_idx,val_probs),('test',test_idx,probs)]:
        if len(indexes) != len(scores): raise RuntimeError('Cloud prediction order mismatch')
        for index, predicted in zip(indexes.tolist(), scores.tolist()):
            row={'sample_id':str(Path(paths[index]).relative_to(root)),
                 'source_group':hashes[index],'split':split,
                 'truth':LABELS[int(ys[index])],
                 'domain':'ccaim_single_collection','sha256':hashes[index]}
            row.update({'p_'+label:format(float(predicted[i]),'.9g') for i,label in enumerate(LABELS)})
            evaluation_rows.append(row)
    pred_file=out/'test_predictions.csv'
    val_file=out/'validation_predictions.csv'
    combined_file=out/'evaluation_predictions.csv'
    for file, rows in [
        (pred_file,[r for r in evaluation_rows if r['split']=='test']),
        (val_file,[r for r in evaluation_rows if r['split']=='validation']),
        (combined_file,evaluation_rows)]:
        with file.open('w',encoding='utf-8',newline='') as stream:
            writer=csv.DictWriter(stream,fieldnames=columns)
            writer.writeheader();writer.writerows(rows)
    converter=tf.lite.TFLiteConverter.from_keras_model(model); converter.optimizations=[tf.lite.Optimize.DEFAULT]
    tflite=converter.convert(); (out/'cloud_model.tflite').write_bytes(tflite)
    (out/'labels.json').write_text(json.dumps({'input':'float32 RGB [1,224,224,3], 0..255; aspect-preserving resize, centered RGB pad=128 (matches Android)','output_order':LABELS,
      'model':'MobileNetV3Small transfer learning','test_count':int(len(yt))},indent=2),encoding='utf-8')
    (out/'split_counts.json').write_text(json.dumps({'train':len(train_idx),'validation':len(val_idx),'test':len(test_idx),
      'class_counts_train':{LABELS[i]:int(counts[i]) for i in range(len(LABELS))}},indent=2),encoding='utf-8')
    split_for_index={int(i):split for split,part in
                     [('train',train_idx),('validation',val_idx),('test',test_idx)] for i in part}
    manifest_rows=sorted(str(Path(p).relative_to(root))+'|'+hashes[i]+'|'+split_for_index[i]
                         for i,p in enumerate(paths))
    meta={'labels':LABELS, 'input_shape':[1,SIZE,SIZE,3], 'output_shape':[1,len(LABELS)],
          'tflite_sha256':hashlib.sha256(tflite).hexdigest(),
          'manifest_sha256':hashlib.sha256('\n'.join(manifest_rows).encode('utf-8')).hexdigest(),
          'test_predictions_sha256':hashlib.sha256(pred_file.read_bytes()).hexdigest(),
          'validation_predictions_sha256':hashlib.sha256(val_file.read_bytes()).hexdigest(),
          'evaluation_predictions_sha256':hashlib.sha256(combined_file.read_bytes()).hexdigest(),
          'training_clips':len(train_idx), 'validation_clips':len(val_idx),
          'test_clips':len(test_idx), 'seed':a.seed,
          'dataset_license':'UNVERIFIED',
          'dataset_license_caveat':'Verify original CCAiM dataset card and redistribution rights before public release',
          'sampling':'aspect_preserving_center_rgb128',
          'domain_limit':'single-starter-collection; independent sites/cameras untested'}
    (out/'model_metadata.json').write_text(json.dumps(meta,indent=2)+'\n',encoding='utf-8')
    print(f'Wrote {out}/cloud_model.tflite')

if __name__=='__main__': main()
