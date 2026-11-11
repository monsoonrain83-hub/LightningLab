#!/usr/bin/env python3
"""Prepare the CC-BY-4.0 Montenegro multi-observer WMO synoptic cloud dataset.

Input directory: images.zip, annotation_items.csv, annotations.csv, LICENSE.txt.
Preserves observer disagreement as soft-label distributions and creates chronological
train/validation/test splits by day. CL/CM/CH are kept as a separate synoptic task.
"""
import argparse, csv, json, math, zipfile
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path, PurePosixPath

VARIABLES = {"CL": "CL - Low cloud type", "CM": "CM - Middle cloud type", "CH": "CH - High cloud type"}
CODES = [str(i) for i in range(10)] + ["/"]
DOI = "10.5281/zenodo.21787669"
URL = "https://zenodo.org/records/21787669"

def chronological_day_split(days):
    ordered = sorted(set(days))
    if len(ordered) < 3:
        raise ValueError("At least three distinct capture days are required.")
    n = len(ordered)
    n_train, n_val = max(1, math.floor(n * .70)), max(1, math.floor(n * .15))
    if n_train + n_val >= n:
        n_train, n_val = n - 2, 1
    return {d: ("train" if i < n_train else "validation" if i < n_train+n_val else "test")
            for i, d in enumerate(ordered)}

def soft_distribution(values):
    counts = Counter(v.strip() for v in values if v is not None and v.strip() in CODES)
    total = sum(counts.values())
    if not total:
        return {}, {}
    count_map = {code: counts[code] for code in CODES if counts[code]}
    return ({code: count/total for code, count in count_map.items()}, count_map)

def normalized_entropy(dist):
    if len(dist) <= 1:
        return 0.0
    h = -sum(p * math.log(p) for p in dist.values() if p > 0)
    return h / math.log(len(CODES))

def safe_image_name(name):
    p = PurePosixPath(name)
    if not name or p.is_absolute() or ".." in p.parts or len(p.parts) != 1:
        raise ValueError("Unsafe image filename: " + repr(name))
    if p.suffix.lower() not in {".jpg", ".jpeg", ".png"}:
        raise ValueError("Unexpected image extension: " + repr(name))
    return p.name

def read_rows(path):
    with path.open("r", encoding="utf-8-sig", newline="") as f:
        return list(csv.DictReader(f))

def prepare(record_dir, out):
    inv_path, ann_path = record_dir/"annotation_items.csv", record_dir/"annotations.csv"
    zip_path, lic_path = record_dir/"images.zip", record_dir/"LICENSE.txt"
    for p in (inv_path, ann_path, zip_path, lic_path):
        if not p.is_file():
            raise FileNotFoundError("Required dataset file not found: " + str(p))
    inventory, annotations = read_rows(inv_path), read_rows(ann_path)
    by_id = {}
    for row in inventory:
        item_id = row.get("id", "").strip()
        name = safe_image_name(row.get("name", "").strip())
        timestamp = row.get("item_received", "").strip()
        if not item_id or not timestamp or item_id in by_id:
            raise ValueError("Missing/duplicate inventory id or timestamp: " + repr(row))
        dt = datetime.strptime(timestamp, "%Y-%m-%d %H:%M:%S")
        by_id[item_id] = {"name": name, "timestamp": timestamp, "day": dt.date().isoformat()}

    grouped = defaultdict(lambda: {key: [] for key in VARIABLES})
    seen_pairs = set()
    annotators_by_id = defaultdict(set)
    for row in annotations:
        item_id, annotator = row.get("annotation_item_id", "").strip(), row.get("user_id", "").strip()
        if item_id not in by_id:
            raise ValueError("Annotation references unknown image id " + repr(item_id))
        pair = (item_id, annotator)
        if pair in seen_pairs:
            raise ValueError("Duplicate image/annotator submission: " + repr(pair))
        seen_pairs.add(pair)
        annotators_by_id[item_id].add(annotator)
        for code, column in VARIABLES.items():
            value = row.get(column, "").strip()
            if value and value not in CODES:
                raise ValueError("Unexpected " + code + " label " + repr(value))
            if value:
                grouped[item_id][code].append(value)

    day_split = chronological_day_split([v["day"] for v in by_id.values()])
    out.mkdir(parents=True, exist_ok=True)
    image_out = out/"images"
    image_out.mkdir(exist_ok=True)
    report_counts = {code: Counter() for code in VARIABLES}
    entropy_sum, entropy_n, split_counts = Counter(), Counter(), Counter()
    manifest_rows = []
    with zipfile.ZipFile(zip_path) as zf:
        bad = zf.testzip()
        if bad:
            raise ValueError("Corrupt ZIP member: " + bad)
        members = set(zf.namelist())
        for item_id, item in by_id.items():
            name = item["name"]
            candidates = ["images/" + name, name]
            member = next((m for m in candidates if m in members), None)
            if member is None:
                matches = [m for m in members if PurePosixPath(m).name == name]
                if len(matches) != 1:
                    raise FileNotFoundError("Expected exactly one archive image " + repr(name))
                member = matches[0]
            with zf.open(member) as src, (image_out/name).open("wb") as dst:
                dst.write(src.read())
            row = {"image_id": item_id, "image_path": "images/" + name,
                   "timestamp_local": item["timestamp"], "day": item["day"],
                   "split": day_split[item["day"]],
                   "annotator_count": len(annotators_by_id[item_id])}
            for code in VARIABLES:
                dist, counts = soft_distribution(grouped[item_id][code])
                row[code.lower()+"_soft_targets"] = json.dumps(dist, sort_keys=True)
                row[code.lower()+"_annotator_counts"] = json.dumps(counts, sort_keys=True)
                row[code.lower()+"_label_count"] = sum(counts.values())
                report_counts[code].update(counts)
                if dist:
                    entropy_sum[code] += normalized_entropy(dist)
                    entropy_n[code] += 1
            manifest_rows.append(row)
            split_counts[row["split"]] += 1

    with (out/"manifest.csv").open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(manifest_rows[0].keys()))
        writer.writeheader()
        writer.writerows(manifest_rows)
    (out/"LICENSE.txt").write_bytes(lic_path.read_bytes())
    attribution = ("Plamenac, Aleksandar; Tomovic, Savo (2026). A Multi-Annotator Ground-Based Sky Camera "
        "Dataset for Cloud Observation in Montenegro, Version 1.0.0. Zenodo. https://doi.org/"+DOI+
        ". Licensed under CC BY 4.0. Changes: extracted images and generated soft-label manifest.")
    (out/"ATTRIBUTION.txt").write_text(attribution+chr(10), encoding="utf-8")
    split_days = {s: sorted(d for d, value in day_split.items() if value == s)
                  for s in ("train", "validation", "test")}
    report = {
        "dataset": "A Multi-Annotator Ground-Based Sky Camera Dataset for Cloud Observation in Montenegro",
        "version": "1.0.0", "doi": DOI, "url": URL, "license": "CC BY 4.0",
        "attribution": attribution, "images": len(manifest_rows), "annotation_rows": len(annotations),
        "unique_image_annotator_pairs": len(seen_pairs), "split_image_counts": dict(split_counts),
        "split_days": split_days,
        "class_annotation_counts": {k: dict(v) for k, v in report_counts.items()},
        "mean_normalized_annotator_entropy": {k: (entropy_sum[k]/entropy_n[k] if entropy_n[k] else None) for k in VARIABLES},
        "label_contract": {"heads": list(VARIABLES), "classes_per_head": CODES,
            "slash_semantics": "observer could not determine this cloud level; separate class",
            "targets": "per-image observer vote distributions; disagreement is preserved"},
        "limitations": ["single camera/site", "autumn/early-winter only", "daylight-only acquisition",
            "rare classes and substantial expert disagreement", "chronological split does not establish geographic generalization"]
    }
    (out/"dataset_report.json").write_text(json.dumps(report, indent=2, ensure_ascii=False)+chr(10), encoding="utf-8")
    return report

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--record-dir", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    report = prepare(Path(args.record_dir), Path(args.out))
    print(json.dumps({k: report[k] for k in ("images", "annotation_rows", "split_image_counts",
        "mean_normalized_annotator_entropy")}, indent=2))

if __name__ == "__main__":
    main()
