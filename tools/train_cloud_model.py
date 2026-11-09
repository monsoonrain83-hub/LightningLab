#!/usr/bin/env python3
"""Lightning Lab ground-camera 10-WMO-genus trainer.

Accepts either prepared class folders (--data) or the CCAiM HF dataset (--dataset).
Exports an Android-compatible float32 TFLite model and reproducible evaluation.
This small, imbalanced starter dataset is experimental only.
"""
import argparse, csv, hashlib, json, random
from pathlib import Path
from collections import Counter
import numpy as np
import tensorflow as tf
from sklearn.metrics import classification_report, confusion_matrix, accuracy_score, f1_score

LABELS = ['Ac','As','Cb','Cc','Ci','Cs','Cu','Ns','Sc','St']
LONG_TO_CODE = {'altocumulus':'Ac','altostratus':'As','cumulonimbus':'Cb','cirrocumulus':'Cc','cirrus':'Ci','cirrostratus':'Cs','cumulus':'Cu','nimbostratus':'Ns','stratocumulus':'Sc','stratus':'St'}
SIZE=224

def load_inputs(data_dir, dataset_id, split):
    paths=[]; ys=[]; hashes=[]
    if data_dir:
        root=Path(data_dir)
        found=sorted(p.name for p in root.iterdir() if p.is_dir())
        if found != sorted(LABELS): raise SystemExit(f'Expected exactly folders {sorted(LABELS)}; found {found}')
        for yi, code in enumerate(LABELS):
            for p in sorted((root/code).glob('*')):
                if p.suffix.lower() not in {'.jpg','.jpeg','.png','.webp'}: continue
                raw=p.read_bytes()
                try: tf.io.decode_image(raw,channels=3,expand_animations=False)
                except Exception as e: print(f'SKIP unreadable {p}: {e}'); continue
                paths.append(str(p)); ys.append(yi); hashes.append(hashlib.sha256(raw).hexdigest())
    else:
        from datasets import load_dataset
        ds=load_dataset(dataset_id,split=split)
        names=getattr(ds.features.get('label'),'names',None)
        import io
        out=Path('data/clouds/ccaim_download'); out.mkdir(parents=True,exist_ok=True)
        for idx,row in enumerate(ds):
            label=row['label']; label=names[int(label)] if names and isinstance(label,(int,np.integer)) else str(label)
            code=LONG_TO_CODE.get(label.strip().lower())
            if not code or row.get('image') is None: continue
            img=row['image'].convert('RGB'); b=io.BytesIO(); img.save(b,format='JPEG',quality=95); raw=b.getvalue()
            digest=hashlib.sha256(raw).hexdigest(); p=out/f'{code}_{idx:06d}.jpg'
            if not p.exists(): p.write_bytes(raw)
            paths.append(str(p)); ys.append(LABELS.index(code)); hashes.append(digest)
    if not paths: raise SystemExit('No readable labelled images found.')
    return np.asarray(paths),np.asarray(ys,dtype=np.int32),hashes

def main():
    ap=argparse.ArgumentParser()
    src=ap.add_mutually_exclusive_group(required=True); src.add_argument('--data',help='Folder containing Ac/As/.../St'); src.add_argument('--dataset',help='Hugging Face dataset id, e.g. serbekun/CCAiM-CloudsDataset')
    ap.add_argument('--split',default='train'); ap.add_argument('--out',default='out/clouds')
    ap.add_argument('--epochs-head',type=int,default=12); ap.add_argument('--epochs-ft',type=int,default=12)
    ap.add_argument('--batch-size',type=int,default=24); ap.add_argument('--seed',type=int,default=42)
    a=ap.parse_args(); out=Path(a.out); out.mkdir(parents=True,exist_ok=True)
    random.seed(a.seed); np.random.seed(a.seed); tf.random.set_seed(a.seed)
    paths,ys,hashes=load_inputs(a.data,a.dataset,a.split)
    # Exact duplicate bytes are grouped to prevent the same image leaking across splits.
    groups={}
    for i,h in enumerate(hashes): groups.setdefault(h,[]).append(i)
    by_class={i:[] for i in range(10)}
    for g in groups.values():
        cls=int(ys[g[0]])
        if any(int(ys[j])!=cls for j in g): raise SystemExit('Identical image bytes have conflicting labels; review labels.')
        by_class[cls].append(g)
    train=[]; val=[]; test=[]
    for cls,gs in by_class.items():
        random.shuffle(gs); n=len(gs)
        if n<3: raise SystemExit(f'{LABELS[cls]} has only {n} unique images; at least 3 required for train/val/test.')
        ntr=max(1,int(round(.70*n))); nva=max(1,int(round(.15*n)))
        if ntr+nva>=n: ntr=n-2; nva=1
        for j,g in enumerate(gs): (train if j<ntr else val if j<ntr+nva else test).extend(g)
    train=np.asarray(train); val=np.asarray(val); test=np.asarray(test)
    def make_ds(ids,training=False):
        d=tf.data.Dataset.from_tensor_slices((paths[ids],ys[ids]))
        if training: d=d.shuffle(len(ids),seed=a.seed,reshuffle_each_iteration=True)
        def load(path,label):
            im=tf.io.decode_image(tf.io.read_file(path),channels=3,expand_animations=False)
            im=tf.image.resize_with_pad(im,SIZE,SIZE,method='bilinear',antialias=True,pad_value=127.5)
            im.set_shape((SIZE,SIZE,3)); return tf.cast(im,tf.float32),label
        return d.map(load,num_parallel_calls=tf.data.AUTOTUNE).batch(a.batch_size).prefetch(tf.data.AUTOTUNE)
    dtr=make_ds(train,True); dva=make_ds(val); dte=make_ds(test)
    train_counts=np.bincount(ys[train],minlength=10)
    weights={i:float(min(8.0,len(train)/(10*max(1,train_counts[i])))) for i in range(10)}
    aug=tf.keras.Sequential([tf.keras.layers.RandomFlip('horizontal'),tf.keras.layers.RandomRotation(.025),tf.keras.layers.RandomZoom(.06)],name='train_only_augmentation')
    base=tf.keras.applications.MobileNetV3Small(input_shape=(SIZE,SIZE,3),include_top=False,weights='imagenet',include_preprocessing=True)
    base.trainable=False
    inp=tf.keras.Input((SIZE,SIZE,3),name='rgb_0_255')
    x=aug(inp); x=base(x,training=False); x=tf.keras.layers.GlobalAveragePooling2D()(x); x=tf.keras.layers.Dropout(.30)(x)
    out_tensor=tf.keras.layers.Dense(10,activation='softmax',name='genus_probabilities')(x)
    model=tf.keras.Model(inp,out_tensor)
    best=str(out/'best.keras')
    def cbs(): return [tf.keras.callbacks.ModelCheckpoint(best,monitor='val_loss',save_best_only=True),tf.keras.callbacks.EarlyStopping(monitor='val_loss',patience=4,restore_best_weights=True),tf.keras.callbacks.ReduceLROnPlateau(monitor='val_loss',factor=.3,patience=2,min_lr=1e-6)]
    model.compile(optimizer=tf.keras.optimizers.Adam(1e-3),loss='sparse_categorical_crossentropy',metrics=['accuracy'])
    model.fit(dtr,validation_data=dva,epochs=a.epochs_head,class_weight=weights,callbacks=cbs())
    base.trainable=True
    for layer in base.layers[:-25]: layer.trainable=False
    model.compile(optimizer=tf.keras.optimizers.Adam(2e-5),loss='sparse_categorical_crossentropy',metrics=['accuracy'])
    model.fit(dtr,validation_data=dva,epochs=a.epochs_ft,class_weight=weights,callbacks=cbs())
    model=tf.keras.models.load_model(best)
    truth=np.concatenate([y.numpy() for _,y in dte]); probs=model.predict(dte,verbose=0); pred=probs.argmax(axis=1)
    cm=confusion_matrix(truth,pred,labels=list(range(10)))
    report=classification_report(truth,pred,labels=list(range(10)),target_names=LABELS,output_dict=True,zero_division=0)
    metrics={'dataset':a.dataset or str(a.data),'rows_used':int(len(ys)),'unique_image_groups':len(groups),'split_counts':{'train':int(len(train)),'validation':int(len(val)),'test':int(len(test))},'class_counts_total':{LABELS[i]:int(np.sum(ys==i)) for i in range(10)},'test_accuracy':float(accuracy_score(truth,pred)),'test_macro_f1':float(f1_score(truth,pred,average='macro',zero_division=0)),'per_class':report,'confusion_matrix':cm.tolist(),'label_order':LABELS,'limitations':['Small, imbalanced pilot dataset','Random image-group split is not source/scene-independent','Not validated for operational meteorological use']}
    (out/'metrics.json').write_text(json.dumps(metrics,indent=2),encoding='utf-8')
    with (out/'confusion_matrix.csv').open('w',newline='',encoding='utf-8') as f:
        w=csv.writer(f); w.writerow(['true\\pred']+LABELS)
        for label,row in zip(LABELS,cm): w.writerow([label]+row.tolist())
    (out/'labels.txt').write_text('\n'.join(LABELS)+'\n',encoding='utf-8')
    contract={'input_name':'rgb_0_255','input_shape':[1,224,224,3],'input_dtype':'float32','input_color_order':'RGB','input_range':[0,255],'resize':'aspect-preserving bilinear resize with center padding to 224x224','padding_rgb':[127.5,127.5,127.5],'mobilenet_preprocessing':'inside model','output_name':'genus_probabilities','output_shape':[1,10],'output_dtype':'float32','output_activation':'softmax','output_order':LABELS,'abstention_threshold':0.55,'threshold_status':'provisional_not_calibrated'}
    (out/'model_contract.json').write_text(json.dumps(contract,indent=2),encoding='utf-8')
    converter=tf.lite.TFLiteConverter.from_keras_model(model); converter.optimizations=[tf.lite.Optimize.DEFAULT]
    (out/'cloud_model.tflite').write_bytes(converter.convert())
    print(json.dumps({'status':'DONE','outputs':str(out),'metrics':metrics},indent=2))
    print('WARNING: pilot model only; do not use for warnings or operational decisions.')
if __name__=='__main__': main()
