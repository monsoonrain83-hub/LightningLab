#!/usr/bin/env python3
"""Offline quality audit for a folder-per-class cloud image dataset.

Usage: python tools/cloud_ai/audit_cloud_dataset.py --data data/clouds/ccaim --out out/cloud_audit.json
Requires Pillow and numpy only. It does not alter images or labels.
"""
import argparse, hashlib, json
from collections import Counter, defaultdict
from pathlib import Path
from PIL import Image, UnidentifiedImageError

CLASSES = ["Ac", "As", "Cb", "Cc", "Ci", "Cs", "Cu", "Ns", "Sc", "St"]
EXTS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}

def dhash(image, size=8):
    image = image.convert("L").resize((size + 1, size), Image.Resampling.LANCZOS)
    px = image.tobytes()
    bits = 0
    for y in range(size):
        for x in range(size):
            i = y * (size + 1) + x
            bits = (bits << 1) | int(px[i] > px[i + 1])
    return bits

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True)
    ap.add_argument("--out", default="out/cloud_dataset_audit.json")
    a = ap.parse_args(); root = Path(a.data)
    if not root.is_dir(): raise SystemExit(f"Dataset folder not found: {root}")
    folders = {p.name for p in root.iterdir() if p.is_dir()}
    missing = sorted(set(CLASSES) - folders); extra = sorted(folders - set(CLASSES))
    counts = Counter(); exact = defaultdict(list); perceptual = defaultdict(list)
    unreadable=[]; dimensions=Counter(); total_bytes=0
    for code in CLASSES:
        folder=root/code
        if not folder.exists(): continue
        for path in sorted(folder.rglob("*")):
            if not path.is_file() or path.suffix.lower() not in EXTS: continue
            try:
                raw=path.read_bytes(); total_bytes += len(raw); sha=hashlib.sha256(raw).hexdigest()
                with Image.open(path) as im:
                    im.verify()
                with Image.open(path) as im:
                    w,h=im.size; dimensions[f"{w}x{h}"] += 1
                    ph=dhash(im)
                counts[code]+=1; exact[sha].append(f"{code}/{path.name}")
                perceptual[ph].append(f"{code}/{path.name}")
            except (OSError, UnidentifiedImageError, ValueError) as e:
                unreadable.append({"path":str(path.relative_to(root)),"error":str(e)[:200]})
    duplicate_groups=[v for v in exact.values() if len(v)>1]
    # dHash buckets are candidates only; collision is not proof of duplicate content.
    near_groups=[v for v in perceptual.values() if len(v)>1]
    nonzero=[v for v in counts.values() if v]
    report={
      "dataset_root":str(root), "expected_classes":CLASSES,
      "missing_class_folders":missing,"unexpected_class_folders":extra,
      "readable_image_count":sum(counts.values()),"total_file_bytes":total_bytes,
      "class_counts":{c:counts.get(c,0) for c in CLASSES},
      "smallest_class_count":min(nonzero) if nonzero else 0,
      "largest_class_count":max(nonzero) if nonzero else 0,
      "imbalance_ratio":round(max(nonzero)/min(nonzero),2) if nonzero else None,
      "dimension_histogram":dict(dimensions),"unreadable_images":unreadable,
      "exact_duplicate_groups":duplicate_groups,
      "dhash_collision_groups_review_manually":near_groups,
      "warnings":[]
    }
    if missing: report["warnings"].append("One or more of the ten WMO genus folders are missing.")
    if extra: report["warnings"].append("Unexpected folders exist; review labels before training.")
    if report["imbalance_ratio"] and report["imbalance_ratio"] > 3:
        report["warnings"].append("Strong class imbalance: use class-aware sampling/weights and report per-class metrics.")
    if unreadable: report["warnings"].append("Unreadable files were found; remove or investigate them before training.")
    if duplicate_groups: report["warnings"].append("Exact duplicates exist; keep each duplicate group in one split.")
    if near_groups: report["warnings"].append("Perceptual-hash collisions need manual review; these are candidates, not confirmed duplicates.")
    out=Path(a.out); out.parent.mkdir(parents=True,exist_ok=True); out.write_text(json.dumps(report,indent=2,ensure_ascii=False),encoding="utf-8")
    print(json.dumps(report,indent=2,ensure_ascii=False))
    if missing or extra or unreadable: raise SystemExit(2)

if __name__ == "__main__": main()
