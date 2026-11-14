#!/usr/bin/env python3
"""Strict clip manifest audit for supervised binary lightning verification.

CSV: relative_path,sha256,split,group_id,label[,site_id,camera_id].
Names and labels are explicit to avoid confusing Brixton's seven-class model
with a real binary positive/negative verifier.
"""
import argparse
import csv
import hashlib
import json
from collections import Counter, defaultdict
from pathlib import Path

LABELS = ("non_lightning", "lightning")
SPLITS = ("train", "validation", "test")


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_manifest(path, root):
    path = Path(path)
    root = Path(root).resolve()
    with path.open(encoding="utf-8-sig", newline="") as stream:
        rows = list(csv.DictReader(stream))
    if not rows:
        raise ValueError("Binary verifier manifest is empty")
    groups = defaultdict(set)
    hashes = defaultdict(set)
    counts = Counter()
    seen = set()
    for lineno, row in enumerate(rows, 2):
        split = str(row.get("split") or "").strip().lower()
        group = str(row.get("group_id") or "").strip()
        label = str(row.get("label") or "").strip()
        rel = str(row.get("relative_path") or "").strip()
        expected = str(row.get("sha256") or "").strip().lower()
        if split not in SPLITS or label not in LABELS or not group or not rel:
            raise ValueError(f"Line {lineno}: invalid split, label, group or path")
        if rel in seen:
            raise ValueError(f"Line {lineno}: duplicate relative_path")
        seen.add(rel)
        if len(expected) != 64 or any(ch not in "0123456789abcdef" for ch in expected):
            raise ValueError(f"Line {lineno}: missing/invalid SHA-256")
        target = (root / rel).resolve()
        if target == root or not target.is_relative_to(root) or not target.is_file():
            raise ValueError(f"Line {lineno}: unsafe/missing clip")
        digest = sha256(target)
        if digest != expected:
            raise ValueError(f"Line {lineno}: clip SHA-256 mismatch")
        groups[group].add(split)
        hashes[digest].add(split)
        counts[(split, label)] += 1
        row["_absolute_path"] = str(target)
        row["split"] = split
        row["label"] = label
        row["group_id"] = group
    if any(len(owners) > 1 for owners in groups.values()):
        raise ValueError("DATA LEAKAGE: video/event group crosses splits")
    if any(len(owners) > 1 for owners in hashes.values()):
        raise ValueError("DATA LEAKAGE: identical clip bytes cross splits")
    for split in SPLITS:
        for label in LABELS:
            if counts[(split, label)] == 0:
                raise ValueError(f"{split} lacks {label} examples")
    return rows, {
        "clips": len(rows), "groups": len(groups),
        "counts": {f"{split}/{label}": counts[(split, label)]
                   for split in SPLITS for label in LABELS},
        "external_site_validated": False,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    _, report = load_manifest(args.manifest, args.root)
    body = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(body, encoding="utf-8")
    print(body)


if __name__ == "__main__":
    main()
