#!/usr/bin/env python3
"""Train a 7-class offline atmospheric scene segmentation model.

Dataset layout:
  DATA/images/*.jpg
  DATA/masks/*.png  # single-channel indexed/grayscale PNG, pixel values 0..6
  DATA/manifest.csv # image,mask,group_id[,split]

Class IDs: 0 clear_sky, 1 cloud, 2 fog, 3 dust_haze, 4 smoke,
           5 snow_ice, 6 other.
All pixels must be labelled. Do not use anti-aliased/grayscale masks.
Export: float32 RGB [1,256,256,3] range 0..255 -> float32 softmax
class scores [1,256,256,7], channel order exactly as CLASS_NAMES.
"""
from __future__ import annotations
import argparse, csv, json, random
from collections import defaultdict
from pathlib import Path
import numpy as np
import tensorflow as tf

SIZE = 256
SEED = 42
CLASS_NAMES = ['clear_sky', 'cloud', 'fog', 'dust_haze', 'smoke', 'snow_ice', 'other']
NUM_CLASSES = len(CLASS_NAMES)
VALID_EXT = {'.jpg', '.jpeg', '.png', '.webp'}


def read_manifest(root: Path):
    path = root / 'manifest.csv'
    if not path.is_file(): raise SystemExit(f'Missing {path}; create it using the supplied template.')
    with path.open(newline='', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        if not {'image','mask','group_id'}.issubset(reader.fieldnames or []):
            raise SystemExit('manifest.csv needs image,mask,group_id and optional split columns')
        rows=[]
        for line,row in enumerate(reader,2):
            ip=(root/row['image'].strip()).resolve(); mp=(root/row['mask'].strip()).resolve()
            if root.resolve() not in ip.parents or root.resolve() not in mp.parents:
                raise SystemExit(f'Path escapes dataset folder at line {line}')
            if not ip.is_file() or not mp.is_file(): raise SystemExit(f'Missing image/mask at line {line}')
            if ip.suffix.lower() not in VALID_EXT: raise SystemExit(f'Unsupported image at line {line}')
            group=row['group_id'].strip(); split=row.get('split','').strip().lower()
            if not group: raise SystemExit(f'Empty group_id at line {line}')
            if split and split not in {'train','validation','test'}: raise SystemExit(f'Invalid split at line {line}')
            rows.append({'image':str(ip),'mask':str(mp),'group':group,'split':split})
    if len(rows)<30: raise SystemExit('At least 30 labelled image/mask pairs are required for a basic experimental run.')
    return rows


def assign_splits(rows, seed):
    values=[r['split'] for r in rows]
    if any(values):
        if not all(values): raise SystemExit('Fill split for every row or leave every split blank.')
        grouped=defaultdict(set)
        for r in rows: grouped[r['group']].add(r['split'])
        bad=[g for g,s in grouped.items() if len(s)>1]
        if bad: raise SystemExit(f'Group leakage across splits: {bad[:5]}')
        if {r['split'] for r in rows}!={'train','validation','test'}: raise SystemExit('Need train, validation and test rows.')
        return rows
    groups=sorted({r['group'] for r in rows})
    if len(groups)<8: raise SystemExit('At least 8 independent group_id values are required for automatic splitting.')
    random.Random(seed).shuffle(groups); n=len(groups)
    a=max(1,round(n*.70)); b=max(1,round(n*.15))
    if a+b>=n: a,b=n-2,1
    sm={g:('train' if i<a else 'validation' if i<a+b else 'test') for i,g in enumerate(groups)}
    for r in rows: r['split']=sm[r['group']]
    return rows


def decode_pair(image_path, mask_path):
    image=tf.io.decode_image(tf.io.read_file(image_path),channels=3,expand_animations=False); image.set_shape([None,None,3])
    mask=tf.io.decode_png(tf.io.read_file(mask_path),channels=1); mask.set_shape([None,None,1])
    tf.debugging.assert_equal(tf.shape(image)[:2],tf.shape(mask)[:2],message='Image/mask dimensions differ')
    mask=tf.cast(mask,tf.int32)
    tf.debugging.assert_greater_equal(mask,0); tf.debugging.assert_less(mask,NUM_CLASSES,message='Mask contains unknown class ID')
    image=tf.image.resize_with_pad(tf.cast(image,tf.float32),SIZE,SIZE,method='bilinear',pad_value=127.5)
    mask=tf.image.resize_with_pad(mask,SIZE,SIZE,method='nearest',pad_value=0)
    return image,tf.cast(tf.squeeze(mask,axis=-1),tf.int32)


def make_ds(rows, split, batch, training):
    selected=[r for r in rows if r['split']==split]
    if not selected: raise SystemExit(f'No samples in {split}')
    ds=tf.data.Dataset.from_tensor_slices(([r['image'] for r in selected],[r['mask'] for r in selected]))
    if training: ds=ds.shuffle(len(selected),seed=SEED,reshuffle_each_iteration=True)
    ds=ds.map(decode_pair,num_parallel_calls=tf.data.AUTOTUNE)
    if training:
        def aug(x,y):
            flip=tf.random.uniform(())>.5
            x=tf.cond(flip,lambda:tf.image.flip_left_right(x),lambda:x)
            y=tf.cond(flip,lambda:tf.image.flip_left_right(y),lambda:y)
            x=tf.clip_by_value(tf.image.random_brightness(x,10.0),0.,255.)
            return x,y
        ds=ds.map(aug,num_parallel_calls=tf.data.AUTOTUNE)
    return ds.batch(batch).prefetch(tf.data.AUTOTUNE),len(selected)


def build_model():
    inp=tf.keras.Input((SIZE,SIZE,3),dtype=tf.float32,name='rgb_0_255')
    x=tf.keras.layers.Rescaling(1/127.5,offset=-1.0)(inp); skips=[]
    for f in (24,48,96,160):
        x=tf.keras.layers.Conv2D(f,3,padding='same',activation='relu')(x)
        x=tf.keras.layers.SeparableConv2D(f,3,padding='same',activation='relu')(x)
        skips.append(x); x=tf.keras.layers.MaxPool2D()(x)
    x=tf.keras.layers.Conv2D(256,3,padding='same',activation='relu')(x)
    for f,skip in zip((160,96,48,24),reversed(skips)):
        x=tf.keras.layers.UpSampling2D(interpolation='bilinear')(x)
        x=tf.keras.layers.Concatenate()([x,skip])
        x=tf.keras.layers.Conv2D(f,3,padding='same',activation='relu')(x)
        x=tf.keras.layers.SeparableConv2D(f,3,padding='same',activation='relu')(x)
    out=tf.keras.layers.Conv2D(NUM_CLASSES,1,activation='softmax',dtype='float32',name='atmospheric_class_scores')(x)
    return tf.keras.Model(inp,out,name='lightninglab_atmosphere_unet_7class')


def main():
    ap=argparse.ArgumentParser(description='Train 7-class atmospheric scene segmentation')
    ap.add_argument('--data',required=True); ap.add_argument('--out',default='out/atmosphere_multiclass')
    ap.add_argument('--epochs',type=int,default=50); ap.add_argument('--batch-size',type=int,default=8); ap.add_argument('--seed',type=int,default=SEED)
    a=ap.parse_args()
    if a.epochs<1 or a.batch_size<1: raise SystemExit('epochs and batch-size must be positive')
    random.seed(a.seed); np.random.seed(a.seed); tf.random.set_seed(a.seed)
    root=Path(a.data); out=Path(a.out); out.mkdir(parents=True,exist_ok=True)
    rows=assign_splits(read_manifest(root),a.seed)
    tr,ntr=make_ds(rows,'train',a.batch_size,True); va,nva=make_ds(rows,'validation',a.batch_size,False); te,nte=make_ds(rows,'test',a.batch_size,False)
    model=build_model(); model.compile(optimizer=tf.keras.optimizers.Adam(1e-3),loss=tf.keras.losses.SparseCategoricalCrossentropy(),metrics=[tf.keras.metrics.SparseCategoricalAccuracy(name='pixel_accuracy')])
    callbacks=[tf.keras.callbacks.ModelCheckpoint(str(out/'best.keras'),monitor='val_pixel_accuracy',mode='max',save_best_only=True),tf.keras.callbacks.EarlyStopping(monitor='val_pixel_accuracy',mode='max',patience=9,restore_best_weights=True),tf.keras.callbacks.ReduceLROnPlateau(monitor='val_loss',factor=.3,patience=3,min_lr=1e-6)]
    model.fit(tr,validation_data=va,epochs=a.epochs,callbacks=callbacks)
    model=tf.keras.models.load_model(out/'best.keras'); metrics=model.evaluate(te,return_dict=True,verbose=1)
    confusion=np.zeros((NUM_CLASSES,NUM_CLASSES),dtype=np.int64)
    for images, masks in te:
        scores=model(images,training=False)
        predicted=tf.argmax(scores,axis=-1,output_type=tf.int32)
        cm=tf.math.confusion_matrix(tf.reshape(masks,[-1]),tf.reshape(predicted,[-1]),num_classes=NUM_CLASSES,dtype=tf.int64)
        confusion += cm.numpy()
    intersections=np.diag(confusion).astype(np.float64)
    unions=confusion.sum(axis=0).astype(np.float64)+confusion.sum(axis=1).astype(np.float64)-intersections
    class_iou={name:(float(intersections[i]/unions[i]) if unions[i] else None) for i,name in enumerate(CLASS_NAMES)}
    valid_ious=[v for v in class_iou.values() if v is not None]
    evaluation={k:float(v) for k,v in metrics.items()} | {
        'mean_iou':float(np.mean(valid_ious)) if valid_ious else None,
        'class_iou':class_iou,
        'confusion_matrix':confusion.tolist(),
        'test_images':nte,'train_images':ntr,'validation_images':nva,'classes':CLASS_NAMES
    }
    (out/'test_metrics.json').write_text(json.dumps(evaluation,indent=2),encoding='utf-8')
    converter=tf.lite.TFLiteConverter.from_keras_model(model); converter.target_spec.supported_ops=[tf.lite.OpsSet.TFLITE_BUILTINS]
    target=out/'atmosphere_multiclass.tflite'; target.write_bytes(converter.convert())
    contract={'input':{'shape':[1,SIZE,SIZE,3],'dtype':'float32','layout':'NHWC','channels':'RGB','range':[0,255],'resize':'aspect-preserving bilinear resize with grey 127.5 letterbox'},'output':{'shape':[1,SIZE,SIZE,NUM_CLASSES],'dtype':'float32','meaning':'per-pixel softmax class scores; channels ordered by classes array'},'classes':[{'id':i,'name':n} for i,n in enumerate(CLASS_NAMES)],'architecture':'small U-Net with separable convolutions','test_metrics':evaluation,'warning':'Experimental model. Validate each class on independent locations, seasons, sensors, night scenes and mixed fog/dust/smoke before operational use.'}
    (out/'model_contract.json').write_text(json.dumps(contract,indent=2),encoding='utf-8')
    print(f'Exported {target} ({target.stat().st_size:,} bytes)')
    print('NOTE: this 7-channel model is NOT interchangeable with cloud_segmentation.tflite; Android runtime integration is a separate phase.')

if __name__=='__main__': main()
