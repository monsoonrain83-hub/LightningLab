#!/usr/bin/env python3
"""Offline quality control and manifest builder for Lightning Lab cloud masks.

Uses only Python stdlib + Pillow. It never edits source images or masks.
"""
from __future__ import annotations
import argparse, csv, hashlib, json, sys
from collections import defaultdict, Counter
from pathlib import Path
from PIL import Image

IMAGE_EXTS = {'.jpg', '.jpeg', '.png', '.webp', '.bmp'}
MASK_SUFFIXES = ('_mask', '-mask', '.mask')


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def rel(root: Path, path: Path) -> str:
    return path.relative_to(root).as_posix()


def load_manifest(root: Path, create: bool):
    manifest = root / 'manifest.csv'
    if not manifest.exists() and create:
        images = sorted(p for p in (root / 'images').glob('*') if p.is_file() and p.suffix.lower() in IMAGE_EXTS)
        masks_dir = root / 'masks'
        masks_by_stem = {}
        if masks_dir.exists():
            for p in masks_dir.iterdir():
                if p.is_file() and p.suffix.lower() == '.png':
                    stem = p.stem
                    for suffix in MASK_SUFFIXES:
                        if stem.lower().endswith(suffix):
                            stem = stem[:-len(suffix)]
                            break
                    masks_by_stem.setdefault(stem.lower(), p)
        rows = []
        for image in images:
            mask = masks_by_stem.get(image.stem.lower())
            if mask:
                # Conservative default: one group per still image. Users must merge
                # group_id values for frames from the same video/capture sequence.
                rows.append({'image': rel(root, image), 'mask': rel(root, mask),
                             'group_id': image.stem, 'split': ''})
        if not rows:
            raise ValueError('هیچ جفت تصویری پیدا نشد. نام فایل ماسک باید مانند sky01_mask.png باشد.')
        with manifest.open('w', newline='', encoding='utf-8-sig') as f:
            writer = csv.DictWriter(f, fieldnames=['image', 'mask', 'group_id', 'split'])
            writer.writeheader(); writer.writerows(rows)
        print(f'Manifest created: {manifest} ({len(rows)} pairs)')
    if not manifest.is_file():
        raise ValueError(f'فایل manifest.csv پیدا نشد: {manifest}')
    with manifest.open(newline='', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        needed = {'image', 'mask', 'group_id'}
        if not needed.issubset(reader.fieldnames or []):
            raise ValueError('ستون‌های لازم: image,mask,group_id و ستون اختیاری split')
        return list(reader)


def main():
    ap = argparse.ArgumentParser(description='Lightning Lab offline dataset quality checker')
    ap.add_argument('--data', required=True, help='Dataset folder containing images/, masks/ and manifest.csv')
    ap.add_argument('--create-manifest', action='store_true', help='Create manifest.csv by matching image/mask filename stems')
    ap.add_argument('--multiclass', action='store_true', help='Validate indexed masks for the 7-class atmospheric dataset (IDs 0..6)')
    ap.add_argument('--report-dir', default=None, help='Output folder (default: DATA/qc_report)')
    args = ap.parse_args()
    root = Path(args.data).expanduser().resolve()
    out = Path(args.report_dir).expanduser().resolve() if args.report_dir else root / 'qc_report'
    out.mkdir(parents=True, exist_ok=True)
    errors, warnings, results = [], [], []
    try:
        rows = load_manifest(root, args.create_manifest)
    except Exception as exc:
        print(f'ERROR: {exc}', file=sys.stderr); return 2
    if not rows:
        print('ERROR: manifest is empty', file=sys.stderr); return 2

    image_hashes, group_splits = defaultdict(list), defaultdict(set)
    dataset_class_counts = Counter()
    used_images, used_masks = set(), set()
    for line_no, row in enumerate(rows, 2):
        image_rel, mask_rel = (row.get('image') or '').strip(), (row.get('mask') or '').strip()
        group = (row.get('group_id') or '').strip()
        split = (row.get('split') or '').strip().lower()
        status, notes = 'OK', []
        ip, mp = (root / image_rel).resolve(), (root / mask_rel).resolve()
        # Prevent manifest paths from escaping the dataset root.
        if root not in ip.parents or root not in mp.parents:
            status = 'ERROR'; notes.append('مسیر فایل خارج از پوشه دیتاست است')
        elif not ip.is_file() or not mp.is_file():
            status = 'ERROR'; notes.append('فایل تصویر یا ماسک پیدا نشد')
        elif not group:
            status = 'ERROR'; notes.append('group_id خالی است')
        elif split and split not in {'train', 'validation', 'test'}:
            status = 'ERROR'; notes.append('مقدار split نامعتبر است')
        else:
            try:
                with Image.open(ip) as im:
                    image_size = im.size
                    image_hash = sha256(ip)
                with Image.open(mp) as mask_im:
                    mask_size = mask_im.size
                    total = mask_size[0] * mask_size[1]
                    if args.multiclass:
                        if mask_im.mode not in {'L', 'P', 'I', 'I;16'}:
                            raise ValueError(f'ماسک چندکلاسه باید تک‌کاناله/Indexed باشد، نه {mask_im.mode}')
                        values = [int(v) for v in mask_im.getdata()]
                        class_counts = Counter(values)
                        invalid = sorted(v for v in class_counts if v < 0 or v > 6)
                        if invalid:
                            status = 'ERROR'; notes.append(f'شناسهٔ کلاس خارج از ۰ تا ۶: {invalid[:10]}')
                        unique = len(class_counts)
                        dataset_class_counts.update(class_counts)
                        cloud_pct = 100.0 * class_counts.get(1, 0) / max(total, 1)
                        class_pct = {str(i): round(class_counts.get(i, 0) * 100.0 / max(total, 1), 3) for i in range(7)}
                    else:
                        mask_l = mask_im.convert('L')
                        hist = mask_l.histogram()
                        cloud_px = sum(hist[128:])
                        cloud_pct = 100.0 * cloud_px / max(total, 1)
                        unique = sum(1 for n in hist if n)
                        class_pct = None
                if image_size != mask_size:
                    status = 'ERROR'; notes.append(f'ابعاد تصویر {image_size} با ماسک {mask_size} برابر نیست')
                if not args.multiclass:
                    if cloud_pct <= 0.01:
                        warnings.append(f'ردیف {line_no}: ماسک کاملاً/تقریباً سیاه است؛ برچسب ابر ندارد')
                        notes.append('ماسک بدون پیکسل ابر')
                    elif cloud_pct >= 99.99:
                        warnings.append(f'ردیف {line_no}: ماسک تقریباً کاملاً سفید است؛ بازبینی شود')
                        notes.append('ماسک تقریباً تماماً ابر')
                    if unique > 2:
                        warnings.append(f'ردیف {line_no}: ماسک {unique} سطح خاکستری دارد؛ خروجی نهایی باید دودویی باشد')
                        notes.append(f'{unique} سطح خاکستری')
                image_hashes[image_hash].append(image_rel)
                used_images.add(ip); used_masks.add(mp)
                if split: group_splits[group].add(split)
                results.append({'line': line_no, 'image': image_rel, 'mask': mask_rel,
                                'group_id': group, 'split': split, 'width': image_size[0],
                                'height': image_size[1], 'cloud_pixel_percent': round(cloud_pct, 3),
                                'mask_gray_levels': unique, 'status': status,
                                'notes': '؛ '.join(notes)})
            except Exception as exc:
                status = 'ERROR'; notes.append(f'خواندن تصویر/ماسک ناموفق: {exc}')
        if status == 'ERROR':
            errors.append(f"ردیف {line_no}: {image_rel} — {'؛ '.join(notes)}")
            results.append({'line': line_no, 'image': image_rel, 'mask': mask_rel,
                            'group_id': group, 'split': split, 'status': status,
                            'notes': '؛ '.join(notes)})

    split_values = [(row.get('split') or '').strip().lower() for row in rows]
    if any(split_values) and not all(split_values):
        errors.append('ستون split باید برای همه ردیف‌ها پر باشد یا برای همه خالی بماند؛ حالت ترکیبی قابل آموزش نیست.')
    elif split_values and all(split_values):
        present = set(split_values)
        missing = {'train', 'validation', 'test'} - present
        if missing:
            errors.append('بخش‌های لازم در split وجود ندارند: ' + ', '.join(sorted(missing)))
    else:
        distinct_groups = {str((row.get('group_id') or '').strip()) for row in rows}
        if len(rows) < (30 if args.multiclass else 15):
            warnings.append(f'برای مسیر آموزش فعلی حداقل {30 if args.multiclass else 15} جفت تصویر/ماسک لازم است.')
        if len(distinct_groups) < 5:
            warnings.append('برای تقسیم خودکار train/validation/test حداقل ۵ گروه مستقل لازم است.')

    duplicates = [paths for paths in image_hashes.values() if len(paths) > 1]
    for paths in duplicates:
        warnings.append('تصاویر تکراری با هش یکسان: ' + ' | '.join(paths))
    for group, splits in group_splits.items():
        if len(splits) > 1:
            errors.append(f'نشت گروهی: group_id={group} در چند بخش قرار گرفته: {sorted(splits)}')
    all_images = {p.resolve() for p in (root / 'images').glob('*') if p.is_file()} if (root / 'images').exists() else set()
    all_masks = {p.resolve() for p in (root / 'masks').glob('*') if p.is_file()} if (root / 'masks').exists() else set()
    orphan_images = sorted(str(p.relative_to(root)) for p in all_images - used_images)
    orphan_masks = sorted(str(p.relative_to(root)) for p in all_masks - used_masks)
    if orphan_images: warnings.append(f'{len(orphan_images)} تصویر در manifest استفاده نشده است')
    if orphan_masks: warnings.append(f'{len(orphan_masks)} ماسک در manifest استفاده نشده است')

    class_distribution = {str(i): {'pixels': int(dataset_class_counts.get(i, 0)), 'percent': round(dataset_class_counts.get(i, 0) * 100.0 / max(sum(dataset_class_counts.values()), 1), 3)} for i in range(7)} if args.multiclass else None
    if args.multiclass:
        missing_classes = [i for i in range(7) if dataset_class_counts.get(i, 0) == 0]
        if missing_classes: warnings.append('کلاس‌های فاقد پیکسل در کل دیتاست: ' + ', '.join(map(str, missing_classes)))
    report = {
        'dataset': str(root), 'mode': 'multiclass_7' if args.multiclass else 'binary_cloud',
        'class_distribution': class_distribution, 'manifest_rows': len(rows),
        'valid_rows': sum(r.get('status') == 'OK' for r in results),
        'errors_count': len(errors), 'warnings_count': len(warnings),
        'duplicate_image_groups': duplicates, 'orphan_images': orphan_images,
        'orphan_masks': orphan_masks, 'errors': errors, 'warnings': warnings,
        'important_note': 'این گزارش کیفیت ساختاری و برچسب‌ها را بررسی می‌کند؛ صحت علمی تشخیص مرز ابر را نمی‌تواند به‌طور خودکار تأیید کند.'
    }
    (out / 'qc_report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    if results:
        fields = ['line','image','mask','group_id','split','width','height','cloud_pixel_percent','class_pixel_percentages','mask_gray_levels','status','notes']
        with (out / 'qc_report.csv').open('w', newline='', encoding='utf-8-sig') as f:
            writer = csv.DictWriter(f, fieldnames=fields, extrasaction='ignore')
            writer.writeheader(); writer.writerows(results)
    print(f'جفت‌های بررسی‌شده: {len(rows)} | خطاها: {len(errors)} | هشدارها: {len(warnings)}')
    print(f'گزارش‌ها: {out / "qc_report.json"} و {out / "qc_report.csv"}')
    for item in errors[:20]: print('ERROR:', item)
    for item in warnings[:20]: print('WARNING:', item)
    if not errors and not warnings: print('نتیجه: بررسی ساختاری بدون خطا و هشدار.')
    return 1 if errors else 0

if __name__ == '__main__':
    raise SystemExit(main())
