#!/usr/bin/env python3
"""Fit one scalar probability temperature using VALIDATION ONLY.

Temperature does not change top-1 ordering or fix domain shift. The calibrated
model still requires a fresh, untouched test/external evaluation.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
from pathlib import Path

from classification import TASK_LABELS, parse_csv
from metrics import dataset_fingerprint


def scale_probabilities(probabilities: list[float], temperature: float) -> list[float]:
    if not math.isfinite(temperature) or temperature <= 0:
        raise ValueError("Invalid temperature")
    if not probabilities or any(not math.isfinite(p) or p < 0 or p > 1 for p in probabilities):
        raise ValueError("Invalid probability vector")
    if abs(sum(probabilities) - 1) > .001:
        raise ValueError("Probability vector is not normalized")
    logits = [math.log(max(1e-12, p)) / temperature for p in probabilities]
    pivot = max(logits)
    exp = [math.exp(z - pivot) for z in logits]
    total = sum(exp)
    return [v / total for v in exp]


def nll(rows: list[dict], labels: list[str], temperature: float) -> float:
    if not rows:
        raise ValueError("Cannot calibrate an empty evaluation partition")
    indices = {value: i for i, value in enumerate(labels)}
    return sum(-math.log(max(1e-12, scale_probabilities(r["scores"], temperature)[indices[r["truth"]]]))
               for r in rows) / len(rows)


def fit_temperature(rows: list[dict], task: str, min_samples: int = 30,
                    min_groups: int = 3) -> dict:
    if task not in TASK_LABELS:
        raise ValueError("Unsupported model taxonomy")
    labels = list(TASK_LABELS[task])
    validation = [r for r in rows if r["split"] == "validation"]
    if task.startswith("wmo-"):
        validation = [r for r in validation if r["truth"] != "/"]
    clusters = {r["source_group"] for r in validation}
    if len(validation) < min_samples or len(clusters) < min_groups:
        raise ValueError("Insufficient independent validation observations to calibrate")
    if len({r["truth"] for r in validation}) < 2:
        raise ValueError("Calibration needs at least two reference classes")
    # Golden section search over log(T) in [log(.25), log(8)]. No scipy dependency.
    lo, hi = math.log(.25), math.log(8.0)
    phi = (math.sqrt(5.0) - 1.0) / 2.0
    for _ in range(65):
        x1, x2 = hi - phi * (hi - lo), lo + phi * (hi - lo)
        if nll(validation, labels, math.exp(x1)) < nll(validation, labels, math.exp(x2)):
            hi = x2
        else:
            lo = x1
    proposed = math.exp((lo + hi) / 2.0)
    before = nll(validation, labels, 1.0)
    after = nll(validation, labels, proposed)
    # A non-improving calibration is a no-op, never silently makes NLL worse.
    t = proposed if after < before - 1e-8 else 1.0
    return {
        "schema": "lightninglab-temperature-calibration-v1",
        "task": task, "temperature": t,
        "validation_n": len(validation), "independent_validation_groups": len(clusters),
        "validation_nll_before": before, "validation_nll_after": nll(validation, labels, t),
        "validation_fingerprint_sha256": dataset_fingerprint(validation,
            ["sample_id", "source_group", "split", "truth", "sha256", "scores"]),
        "method": "multiclass-temperature-scaling-on-validation-only",
        "limitations": [
            "Temperature rescales softmax, does not improve top-1 classification accuracy.",
            "Use independently held-out test/external rows for evaluating calibration.",
            "Do not fit on test/external scores or on selected training examples.",
            "Android currently shows raw model scores; a calibrated model needs explicit validated runtime integration.",
        ],
    }


def write_calibrated_test_rows(rows: list[dict], task: str, temperature: float,
                               target: Path) -> int:
    labels = list(TASK_LABELS[task])
    held_out = [r for r in rows if r["split"] in ("test", "external")]
    if not held_out:
        raise ValueError("No untouched test/external rows to calibrate")
    target.parent.mkdir(parents=True, exist_ok=True)
    columns = ["sample_id", "source_group", "split", "truth", "domain", "sha256"] + [
        "p_" + label for label in labels
    ]
    with target.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=columns)
        writer.writeheader()
        for row in held_out:
            payload = {key: row[key] for key in columns if not key.startswith("p_")}
            calibrated = scale_probabilities(row["scores"], temperature)
            payload.update({"p_" + label: format(calibrated[i], ".12g")
                            for i, label in enumerate(labels)})
            writer.writerow(payload)
    return len(held_out)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--task", choices=sorted(TASK_LABELS), required=True)
    parser.add_argument("--calibrator", type=Path, required=True)
    parser.add_argument("--calibrated-predictions", type=Path, required=True)
    args = parser.parse_args()
    try:
        rows = parse_csv(args.input, args.task)
        artifact = fit_temperature(rows, args.task)
        count = write_calibrated_test_rows(rows, args.task, artifact["temperature"],
                                           args.calibrated_predictions)
        args.calibrator.parent.mkdir(parents=True, exist_ok=True)
        args.calibrator.write_text(json.dumps(artifact, indent=2, ensure_ascii=False) + "\n",
                                   encoding="utf-8")
        print(json.dumps({"temperature": artifact["temperature"],
                          "validation_n": artifact["validation_n"],
                          "heldout_rows_exported": count}))
        return 0
    except (ValueError, OSError, KeyError, TypeError) as error:
        print("Calibration refused:", error)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
