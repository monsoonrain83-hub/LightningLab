"""Strict, leakage-aware external evaluation for seven-label lightning and cloud classifiers."""
from __future__ import annotations

import csv
import json
import math
from collections import Counter, defaultdict
from pathlib import Path

from metrics import classification_metrics, cluster_interval, dataset_fingerprint, selective_metrics

BRIXTON = ("towerupward", "towerdownward", "close", "far", "unclear", "unclearbehind", "CC")
CLOUD_10 = ("Ac", "As", "Cb", "Cc", "Ci", "Cs", "Cu", "Ns", "Sc", "St")
WMO_LEVEL = tuple(str(i) for i in range(10)) + ("/",)
TASK_LABELS = {"brixton": BRIXTON, "cloud-genus": CLOUD_10,
               "wmo-cl": WMO_LEVEL, "wmo-cm": WMO_LEVEL, "wmo-ch": WMO_LEVEL}


def parse_csv(path: Path, task: str) -> list[dict]:
    if task not in TASK_LABELS:
        raise ValueError("Unsupported scientific task")
    labels = TASK_LABELS[task]
    required = {"sample_id", "source_group", "split", "truth", "domain", "sha256"} | {
        "p_" + label for label in labels
    }
    with path.open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames is None or not required <= set(reader.fieldnames):
            raise ValueError("CSV missing fields: " + ", ".join(sorted(required - set(reader.fieldnames or []))))
        rows = []
        seen_ids = set()
        group_splits, payload_splits, id_splits = {}, {}, {}
        for index, raw in enumerate(reader, 2):
            sample_id = raw["sample_id"].strip()
            group = raw["source_group"].strip()
            split = raw["split"].strip().lower()
            domain = raw["domain"].strip()
            digest = raw["sha256"].strip().lower()
            truth = raw["truth"].strip()
            if not sample_id or sample_id in seen_ids or not group or not domain:
                raise ValueError(f"Line {index}: missing/duplicate ID or group/domain")
            seen_ids.add(sample_id)
            if split not in ("train", "validation", "test", "external"):
                raise ValueError(f"Line {index}: unsupported partition")
            if truth not in labels:
                raise ValueError(f"Line {index}: unrecognized class {truth!r}")
            if len(digest) != 64 or any(c not in "0123456789abcdef" for c in digest):
                raise ValueError(f"Line {index}: 64-character SHA-256 is required")
            if group in group_splits and group_splits[group] != split:
                raise ValueError(f"Line {index}: group leakage across partitions")
            if digest in payload_splits and payload_splits[digest] != split:
                raise ValueError(f"Line {index}: identical image/video leaks between partitions")
            group_splits[group] = split
            payload_splits[digest] = split
            try:
                scores = [float(raw["p_" + label]) for label in labels]
            except (ValueError, TypeError) as exc:
                raise ValueError(f"Line {index}: score is missing/not numeric") from exc
            if any(not math.isfinite(x) or x < 0 or x > 1 for x in scores):
                raise ValueError(f"Line {index}: score is nonfinite/out of range")
            if abs(sum(scores) - 1.0) > 0.001:
                raise ValueError(f"Line {index}: scores do not sum to one")
            rows.append({"sample_id": sample_id, "source_group": group,
                         "split": split, "domain": domain, "sha256": digest,
                         "truth": truth, "scores": scores})
    if not rows:
        raise ValueError("Empty classification evaluation file")
    return rows


def evaluate(rows: list[dict], task: str, seed: int = 2026, bootstraps: int = 400) -> dict:
    labels = list(TASK_LABELS[task])
    split_counts = dict(sorted(Counter(r["split"] for r in rows).items()))
    evaluated = [r for r in rows if r["split"] in ("test", "external")]
    if not evaluated:
        raise ValueError("No independent test/external observations")
    subsets = {}
    for split in ("test", "external"):
        section = [r for r in evaluated if r["split"] == split]
        if not section:
            continue
        unknown = [r for r in section if r["truth"] == "/" and task.startswith("wmo-")]
        # "/" means not determinable by observer; it is NOT proof of a physical cloud class.
        usable = [r for r in section if r not in unknown]
        metrics = classification_metrics(usable, labels)
        group_ci = cluster_interval(usable, "source_group",
                                    lambda sample: classification_metrics(sample, labels)["macro_f1"],
                                    seed=seed, iterations=bootstraps)
        breakdown = {}
        for domain in sorted({r["domain"] for r in section}):
            members = [r for r in usable if r["domain"] == domain]
            breakdown[domain] = classification_metrics(members, labels)
        subsets[split] = {
            "total": len(section), "evaluable": len(usable),
            "observer_undeterminable_excluded": len(unknown),
            "metrics": metrics, "macro_f1_cluster_ci_95": group_ci,
            "selective_analysis": [
                selective_metrics(usable, labels, t) for t in (0.5, 0.7, 0.85, 0.95)
            ],
            "by_domain": breakdown,
            "independent_groups": len({r["source_group"] for r in section}),
        }
    return {
        "schema": "lightninglab-scientific-classification-v1",
        "task": task, "labels": labels,
        "split_counts": split_counts,
        "fingerprint_sha256": dataset_fingerprint(rows,
             ["sample_id", "source_group", "split", "truth", "domain", "sha256", "scores"]),
        "results": subsets,
        "limitations": [
            "Scores are model outputs, not calibrated physical probabilities.",
            "Confidence intervals resample independent source_group clusters; fewer than two groups means CI unavailable.",
            "Macro-F1 averages only classes present in ground truth; missing-class count is explicitly reported.",
            "Selective accuracy/coverage is descriptive; thresholds must be chosen without looking at the test set.",
            "Brixton categories represent a single tower collection, not CG/IC polarity or return-stroke physics.",
            "Results are not scientifically generalizable until separately tested on different sites and cameras.",
        ],
    }


def write_report(input_csv: Path, output: Path, task: str,
                 seed: int = 2026, bootstraps: int = 400) -> dict:
    rows = parse_csv(input_csv, task)
    report = evaluate(rows, task, seed, bootstraps)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return report
