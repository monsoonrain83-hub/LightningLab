#!/usr/bin/env python3
"""Leakage-safe scientific evaluation for LightningLab model predictions.

Input: JSONL rows with split, group_id, label, probabilities (array), and
optionally sha256, site_id, camera_id, sample_id, head. No dependencies.
This is an evaluator, NOT a trained model or proof of generalization.
"""
import argparse
import hashlib
import json
import math
import random
from collections import Counter, defaultdict
from pathlib import Path

LIGHTNING_LABELS = (
    "towerupward", "towerdownward", "close", "far",
    "unclear", "unclearbehind", "CC",
)
CLOUD_HEAD_LABELS = tuple(map(str, range(10))) + ("/",)


def read_predictions(path, labels, *, head=None):
    rows = []
    seen_samples = set()
    with Path(path).open(encoding="utf-8-sig") as stream:
        for line_no, line in enumerate(stream, 1):
            if not line.strip():
                continue
            try:
                row = json.loads(line)
                if not isinstance(row, dict):
                    raise ValueError("row must be an object")
                if head is not None and row.get("head") != head:
                    continue
                split = str(row["split"]).lower()
                if split not in ("train", "validation", "test"):
                    raise ValueError("split must be train/validation/test")
                group = str(row["group_id"]).strip()
                if not group:
                    raise ValueError("missing group_id")
                label = str(row["label"])
                if label not in labels:
                    raise ValueError("unknown label " + label)
                probs = row["probabilities"]
                if not isinstance(probs, list) or len(probs) != len(labels):
                    raise ValueError("probabilities must have one value per class")
                if any(isinstance(x, bool) or not isinstance(x, (int, float))
                       or not math.isfinite(x) or x < 0 or x > 1 for x in probs):
                    raise ValueError("probability outside [0,1] or non-finite")
                if abs(sum(probs) - 1) > 0.001:
                    raise ValueError("probabilities do not sum to 1")
                sample = str(row.get("sample_id", "")).strip()
                if sample:
                    if sample in seen_samples:
                        raise ValueError("duplicate sample_id")
                    seen_samples.add(sample)
                sha = str(row.get("sha256", "")).lower()
                if sha and (len(sha) != 64 or any(c not in "0123456789abcdef" for c in sha)):
                    raise ValueError("invalid sha256")
                rows.append({
                    "split": split, "group_id": group, "label": label,
                    "probabilities": [float(p) for p in probs],
                    "sha256": sha, "site_id": str(row.get("site_id", "")).strip(),
                    "camera_id": str(row.get("camera_id", "")).strip(),
                    "sample_id": sample,
                })
            except (ValueError, TypeError, KeyError, json.JSONDecodeError) as exc:
                raise ValueError(f"{path}:{line_no}: {exc}") from exc
    if not rows:
        raise ValueError("no evaluation rows for selected model head")
    return rows


def leakage_errors(rows):
    """Block shared event groups, hashes and sample IDs across partitions."""
    errors = []
    for key in ("group_id", "sha256"):
        owners = defaultdict(set)
        for row in rows:
            if row[key]:
                owners[row[key]].add(row["split"])
        duplicates = [value for value, splits in owners.items() if len(splits) > 1]
        if duplicates:
            # Hashes may be sensitive/private; never print full identifiers.
            errors.append(f"{key}: {len(duplicates)} values cross data splits")
    return errors


def site_errors(rows):
    """A site-independent benchmark cannot reuse a site across splits."""
    owners = defaultdict(set)
    for row in rows:
        if row["site_id"]:
            owners[row["site_id"]].add(row["split"])
    return [f"site_id: {len([v for v in owners.values() if len(v)>1])} sites cross splits"] \
        if any(len(v)>1 for v in owners.values()) else []


def metrics(rows, labels, abstain_below=0.0):
    if not rows:
        raise ValueError("selected split has no rows")
    n = len(labels)
    conf = [[0] * n for _ in range(n)]
    accepted = 0
    correct = 0
    brier = 0.0
    reliability = [[] for _ in range(10)]
    by_class = []
    for row in rows:
        actual = labels.index(row["label"])
        probs = row["probabilities"]
        predicted = max(range(n), key=lambda i: probs[i])
        confidence = probs[predicted]
        hit = predicted == actual
        brier += sum((p - (1.0 if i == actual else 0.0)) ** 2
                     for i, p in enumerate(probs)) / n
        bucket = min(int(confidence * 10), 9)
        reliability[bucket].append((float(hit), confidence))
        if confidence >= abstain_below:
            accepted += 1
            correct += int(hit)
            conf[actual][predicted] += 1
    for i, name in enumerate(labels):
        tp = conf[i][i]
        support = sum(conf[i])
        predicted_count = sum(conf[j][i] for j in range(n))
        precision = tp / predicted_count if predicted_count else 0.0
        recall = tp / support if support else 0.0
        f1 = (2 * precision * recall / (precision + recall)) if precision + recall else 0.0
        by_class.append({
            "label": name, "support": support, "precision": precision,
            "recall": recall, "f1": f1,
        })
    # Do not count absent classes in macro-F1. Disclose missing labels explicitly.
    supported = [r["f1"] for r in by_class if r["support"]]
    ece = 0.0
    for group in reliability:
        if group:
            ece += (len(group) / len(rows)) * abs(
                sum(v[0] for v in group) / len(group) -
                sum(v[1] for v in group) / len(group))
    return {
        "n": len(rows),
        "accepted": accepted,
        "coverage": accepted / len(rows),
        "accuracy_on_accepted": correct / accepted if accepted else None,
        "macro_f1_on_accepted": sum(supported) / len(supported) if supported else None,
        "multiclass_brier": brier / len(rows),
        "ece_10_bins": ece,
        "classes_missing_from_accepted": [
            row["label"] for row in by_class if row["support"] == 0],
        "confusion_matrix_truth_rows": conf,
        "per_class_on_accepted": by_class,
    }


def grouped_bootstrap(rows, labels, rounds, seed, abstain_below):
    if rounds <= 0:
        return None
    by_group = defaultdict(list)
    for row in rows:
        by_group[row["group_id"]].append(row)
    groups = sorted(by_group)
    if len(groups) < 2:
        return {"note": "need >=2 independent event groups", "n_groups": len(groups)}
    rng = random.Random(seed)
    values = []
    for _ in range(rounds):
        chosen = [by_group[groups[rng.randrange(len(groups))]]
                  for _ in groups]
        result = metrics([row for block in chosen for row in block], labels, abstain_below)
        if result["macro_f1_on_accepted"] is not None:
            values.append(result["macro_f1_on_accepted"])
    if not values:
        return {"note": "no accepted predictions", "n_groups": len(groups)}
    values.sort()
    low = values[int((len(values) - 1) * 0.025)]
    high = values[int((len(values) - 1) * 0.975)]
    return {
        "n_groups": len(groups), "replicates": len(values),
        "macro_f1_95pct_group_bootstrap": [low, high],
        "note": "descriptive interval; not external-site generalization evidence",
    }


def evaluate(rows, labels, *, split="test", independent_sites=False,
             abstain_below=0.0, bootstrap=200, seed=42):
    failures = leakage_errors(rows)
    if independent_sites:
        if any(not row["site_id"] for row in rows):
            failures.append("independent-site mode requires site_id on every row")
        failures.extend(site_errors(rows))
    if failures:
        raise ValueError("DATA LEAKAGE / INVALID HOLDOUT: " + "; ".join(failures))
    selected = [r for r in rows if r["split"] == split]
    if not selected:
        raise ValueError("requested split contains no rows")
    result = metrics(selected, labels, abstain_below)
    result["group_bootstrap"] = grouped_bootstrap(selected, labels, bootstrap, seed,
                                                  abstain_below)
    result["split"] = split
    result["independent_sites_checked"] = independent_sites
    result["site_count"] = len({r["site_id"] for r in selected if r["site_id"]})
    result["camera_count"] = len({(r["site_id"], r["camera_id"])
                                  for r in selected if r["camera_id"]})
    result["model_is_validated"] = False  # Never infer validity from these stats.
    result["caveat"] = (
        "Internal test only. Dataset representativeness, independent annotation, "
        "calibration, latency and Android inference need separate validation. "
        "RGB cannot conclusively determine lightning polarity or CG/IC.")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--predictions", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--task", choices=("lightning", "cloud-head"), required=True)
    parser.add_argument("--head", choices=("CL", "CM", "CH"))
    parser.add_argument("--split", choices=("train", "validation", "test"), default="test")
    parser.add_argument("--independent-sites", action="store_true")
    parser.add_argument("--abstain-below", type=float, default=0.0)
    parser.add_argument("--bootstrap", type=int, default=200)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    if args.task == "cloud-head" and not args.head:
        parser.error("--head required for cloud-head")
    if args.task == "lightning" and args.head:
        parser.error("--head only applies to cloud-head")
    if not 0 <= args.abstain_below <= 1 or not math.isfinite(args.abstain_below):
        parser.error("--abstain-below must be a finite number in [0,1]")
    if not 0 <= args.bootstrap <= 10000:
        parser.error("--bootstrap must be within 0..10000")
    labels = LIGHTNING_LABELS if args.task == "lightning" else CLOUD_HEAD_LABELS
    rows = read_predictions(args.predictions, labels, head=args.head)
    report = evaluate(rows, labels, split=args.split,
                      independent_sites=args.independent_sites,
                      abstain_below=args.abstain_below,
                      bootstrap=args.bootstrap, seed=args.seed)
    report["task"] = args.task
    report["head"] = args.head
    report["labels"] = labels
    report["predictions_sha256"] = hashlib.sha256(args.predictions.read_bytes()).hexdigest()
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n",
                        encoding="utf-8")
    print(f"Evaluation saved: {args.out}; n={report['n']}; "
          f"macro_f1={report['macro_f1_on_accepted']}")


if __name__ == "__main__":
    main()
