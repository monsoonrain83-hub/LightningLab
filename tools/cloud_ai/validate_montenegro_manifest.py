#!/usr/bin/env python3
"""Audit Montenegro multi-observer dataset before three-head cloud training.

Stdlib only. Enforces safe paths, valid soft labels, immutable image hashes,
and prevents byte-identical sky images from crossing train/validation/test.
This audit does NOT establish geographic generalization.
"""
import argparse
import csv
import hashlib
import json
import math
from collections import Counter, defaultdict
from pathlib import Path

HEADS = ("CL", "CM", "CH")
LABELS = {str(i) for i in range(10)} | {"/"}
SPLITS = ("train", "validation", "test")


def file_sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def audit_manifest(manifest):
    manifest = Path(manifest)
    root = manifest.parent.resolve()
    with manifest.open(encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))
    if not rows:
        raise ValueError("Empty cloud manifest")

    split_counts = Counter()
    class_counts = {head: Counter() for head in HEADS}
    sha_owners = defaultdict(set)
    path_owners = set()
    image_owners = set()
    missing_heads = Counter()
    for line_no, row in enumerate(rows, 2):
        split = str(row.get("split") or "").strip().lower()
        if split not in SPLITS:
            raise ValueError(f"Line {line_no}: invalid split {split!r}")
        split_counts[split] += 1
        identifier = str(row.get("image_id") or "").strip()
        if not identifier or identifier in image_owners:
            raise ValueError(f"Line {line_no}: missing or duplicate image_id")
        image_owners.add(identifier)
        relative = str(row.get("image_path") or "").strip()
        if not relative or Path(relative).is_absolute() or ".." in Path(relative).parts:
            raise ValueError(f"Line {line_no}: unsafe image_path")
        image = (root / relative).resolve()
        if not image.is_relative_to(root) or not image.is_file():
            raise ValueError(f"Line {line_no}: missing or unsafe image")
        if image in path_owners:
            raise ValueError(f"Line {line_no}: duplicate image_path")
        path_owners.add(image)
        digest = file_sha256(image)
        sha_owners[digest].add(split)
        expected = str(row.get("sha256") or "").strip().lower()
        if expected and expected != digest:
            raise ValueError(f"Line {line_no}: SHA-256 mismatch")
        has_label = False
        for head in HEADS:
            raw = row.get(head.lower() + "_soft_targets") or "{}"
            try:
                distribution = json.loads(raw)
            except (ValueError, TypeError) as error:
                raise ValueError(f"Line {line_no}: invalid {head} JSON") from error
            if not isinstance(distribution, dict):
                raise ValueError(f"Line {line_no}: {head} must be a distribution object")
            total = 0.0
            for label, value in distribution.items():
                if label not in LABELS or isinstance(value, bool) or not isinstance(value, (int, float)):
                    raise ValueError(f"Line {line_no}: invalid {head} label/probability")
                if not math.isfinite(value) or not 0 <= value <= 1:
                    raise ValueError(f"Line {line_no}: invalid {head} probability")
                total += value
                class_counts[head][label] += 1
            if distribution and abs(total - 1.0) > 0.001:
                raise ValueError(f"Line {line_no}: {head} distribution does not sum to 1")
            if distribution:
                has_label = True
            else:
                missing_heads[head] += 1
        if not has_label:
            raise ValueError(f"Line {line_no}: image has no labelled head")

    leakage = sum(1 for splits in sha_owners.values() if len(splits) > 1)
    if leakage:
        raise ValueError(f"DATA LEAKAGE: {leakage} identical image hashes cross splits")
    missing_splits = [split for split in SPLITS if not split_counts[split]]
    if missing_splits:
        raise ValueError("Missing partitions: " + ", ".join(missing_splits))
    return {
        "images": len(rows),
        "unique_sha256": len(sha_owners),
        "split_counts": dict(split_counts),
        "class_observation_counts": {k: dict(v) for k, v in class_counts.items()},
        "unlabelled_heads": dict(missing_heads),
        "site_generalization_validated": False,
        "caveat": "Montenegro is single-site; split integrity does not imply WMO classifier accuracy or external validity.",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    result = audit_manifest(args.manifest)
    report = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(report, encoding="utf-8")
    print(report)


if __name__ == "__main__":
    main()
