"""Dependency-free, reproducible evaluation primitives; no fabricated benchmark values."""
from __future__ import annotations

import hashlib
import math
import random
from collections import Counter, defaultdict
from typing import Callable, Iterable


def safe_div(numerator: float, denominator: float) -> float | None:
    return numerator / denominator if denominator > 0 else None


def quantile(values: list[float], q: float) -> float:
    if not values:
        raise ValueError("Quantile requires nonempty data")
    s = sorted(values)
    p = (len(s) - 1) * q
    lo = math.floor(p)
    hi = math.ceil(p)
    return s[lo] * (hi - p) + s[hi] * (p - lo) if hi != lo else s[lo]


def cluster_interval(
    records: list[dict], group_key: str, scorer: Callable[[list[dict]], float | None],
    seed: int = 2026, iterations: int = 400,
) -> dict:
    """Percentile CI resampling independent *groups*, never correlated frames as IID."""
    if iterations < 1 or iterations > 10000:
        raise ValueError("Bootstrap iterations must be between 1 and 10000")
    groups: dict[str, list[dict]] = defaultdict(list)
    for r in records:
        group = str(r[group_key]).strip()
        if not group:
            raise ValueError("Missing independent group ID")
        groups[group].append(r)
    keys = sorted(groups)
    if len(keys) < 2:
        return {"lower": None, "upper": None, "clusters": len(keys),
                "valid_resamples": 0, "reason": "requires >=2 independent groups"}
    rng = random.Random(seed)
    values: list[float] = []
    for _ in range(iterations):
        sample = [row for _ in keys for row in groups[rng.choice(keys)]]
        score = scorer(sample)
        if score is not None and math.isfinite(score):
            values.append(score)
    if len(values) < max(20, iterations // 4):
        return {"lower": None, "upper": None, "clusters": len(keys),
                "valid_resamples": len(values), "reason": "too few valid resamples"}
    return {"lower": quantile(values, 0.025), "upper": quantile(values, 0.975),
            "clusters": len(keys), "valid_resamples": len(values),
            "method": "percentile-group-bootstrap", "seed": seed}


def classification_metrics(rows: list[dict], labels: list[str], bins: int = 10) -> dict:
    """Rows contain 'truth' and complete normalized 'scores' in fixed label order."""
    if not labels or len(labels) != len(set(labels)) or bins < 2:
        raise ValueError("Invalid labels or calibration bin count")
    if not rows:
        return {"n": 0, "accuracy": None, "macro_f1": None,
                "brier_multiclass": None, "ece": None, "per_class": {},
                "confusion_matrix": [[0] * len(labels) for _ in labels]}
    indices = {k: i for i, k in enumerate(labels)}
    confusion = [[0] * len(labels) for _ in labels]
    brier_sum = 0.0
    buckets = [{"count": 0, "confidence": 0.0, "correct": 0.0}
               for _ in range(bins)]
    for row in rows:
        truth = row["truth"]
        scores = row["scores"]
        if truth not in indices or len(scores) != len(labels):
            raise ValueError("Unknown truth or wrong probability vector")
        if any(not math.isfinite(x) or x < 0.0 or x > 1.0 for x in scores):
            raise ValueError("Invalid model score vector")
        if abs(sum(scores) - 1.0) > 0.001:
            raise ValueError("Scores must be normalized; do not silently renormalize")
        target = indices[truth]
        prediction = max(range(len(labels)), key=lambda i: scores[i])
        confusion[target][prediction] += 1
        brier_sum += sum((p - (1.0 if k == target else 0.0)) ** 2
                         for k, p in enumerate(scores))
        confidence = scores[prediction]
        b = min(bins - 1, int(confidence * bins))
        buckets[b]["count"] += 1
        buckets[b]["confidence"] += confidence
        buckets[b]["correct"] += int(target == prediction)
    result = {}
    f1s = []
    for j, label in enumerate(labels):
        tp = confusion[j][j]
        support = sum(confusion[j])
        predicted = sum(confusion[i][j] for i in range(len(labels)))
        precision = safe_div(tp, predicted)
        recall = safe_div(tp, support)
        f1 = safe_div(2 * tp, support + predicted)
        if support > 0:
            f1s.append(f1 if f1 is not None else 0.0)
        result[label] = {"support": support, "predicted": predicted,
                         "precision": precision, "recall": recall, "f1": f1}
    n = len(rows)
    ece = sum(abs(b["correct"] / b["count"] - b["confidence"] / b["count"])
              * b["count"] / n for b in buckets if b["count"])
    return {"n": n, "accuracy": sum(confusion[i][i] for i in range(len(labels))) / n,
            "macro_f1": sum(f1s) / len(f1s) if f1s else None,
            "macro_f1_supported_classes": len(f1s), "total_classes": len(labels),
            "brier_multiclass": brier_sum / n, "ece": ece,
            "per_class": result, "confusion_matrix": confusion,
            "calibration_bins": [
                {"count": b["count"],
                 "mean_confidence": safe_div(b["confidence"], b["count"]),
                 "empirical_accuracy": safe_div(b["correct"], b["count"])}
                for b in buckets]}


def selective_metrics(rows: list[dict], labels: list[str], threshold: float) -> dict:
    if not 0.0 <= threshold <= 1.0:
        raise ValueError("Threshold must be between 0 and 1")
    accepted = [row for row in rows if max(row["scores"]) >= threshold]
    m = classification_metrics(accepted, labels)
    return {"threshold": threshold, "total": len(rows), "retained": len(accepted),
            "coverage": safe_div(len(accepted), len(rows)),
            "accuracy_on_retained": m["accuracy"], "macro_f1_on_retained": m["macro_f1"]}


def dataset_fingerprint(rows: Iterable[dict], fields: list[str]) -> str:
    lines = ["\t".join(str(row.get(k, "")) for k in fields) for row in rows]
    return hashlib.sha256("\n".join(sorted(lines)).encode("utf-8")).hexdigest()
