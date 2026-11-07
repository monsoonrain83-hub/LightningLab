#!/usr/bin/env python3
"""Download the MIT-labelled CCAiM Hugging Face dataset and export class folders.
Run on a training PC, not on the Android device:
  pip install datasets pillow
  python prepare_ccaim.py --out data/clouds/ccaim
"""
import argparse, csv, hashlib, json
from pathlib import Path

LABEL_TO_CODE = {
    "altocumulus": "Ac", "altostratus": "As", "cumulonimbus": "Cb",
    "cirrocumulus": "Cc", "cirrus": "Ci", "cirrostratus": "Cs",
    "cumulus": "Cu", "nimbostratus": "Ns", "stratocumulus": "Sc", "stratus": "St",
}
DATASET_ID = "serbekun/CCAiM-CloudsDataset"

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--out', default='data/clouds/ccaim')
    ap.add_argument('--split', default='train')
    args = ap.parse_args()
    try:
        from datasets import load_dataset
    except ImportError as e:
        raise SystemExit('Missing dependency. Run: pip install datasets pillow') from e

    ds = load_dataset(DATASET_ID, split=args.split)
    feature = ds.features.get('label')
    names = getattr(feature, 'names', None)
    out = Path(args.out); out.mkdir(parents=True, exist_ok=True)
    counts = {c: 0 for c in sorted(set(LABEL_TO_CODE.values()))}
    manifest = out / 'metadata.csv'
    seen = set()
    with manifest.open('w', newline='', encoding='utf-8') as f:
        w = csv.DictWriter(f, fieldnames=['filename','class_code','class_name','sha256','source_dataset','source_split','source_row'])
        w.writeheader()
        for idx, row in enumerate(ds):
            raw_label = row['label']
            label = names[int(raw_label)] if names and isinstance(raw_label, int) else str(raw_label)
            code = LABEL_TO_CODE.get(label.strip().lower())
            if not code:
                raise ValueError(f'Unknown label at row {idx}: {label!r}; update mapping only after review')
            image = row['image']
            if image is None:
                continue
            image = image.convert('RGB')
            import io
            buf = io.BytesIO(); image.save(buf, format='JPEG', quality=95)
            data = buf.getvalue(); digest = hashlib.sha256(data).hexdigest()
            if digest in seen:
                continue
            seen.add(digest)
            folder = out / code; folder.mkdir(exist_ok=True)
            filename = f'{code}_{idx:06d}.jpg'
            (folder / filename).write_bytes(data)
            w.writerow({'filename':f'{code}/{filename}','class_code':code,'class_name':label,
                        'sha256':digest,'source_dataset':DATASET_ID,'source_split':args.split,'source_row':idx})
            counts[code] += 1
    report = {'dataset': DATASET_ID, 'split': args.split, 'label_mapping': LABEL_TO_CODE,
              'unique_images': sum(counts.values()), 'class_counts': counts,
              'license_as_stated_on_dataset_card': 'MIT',
              'note': 'Check the dataset card and image-level provenance before redistribution.'}
    (out/'dataset_report.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report, indent=2))

if __name__ == '__main__': main()
