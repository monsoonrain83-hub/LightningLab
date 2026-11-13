"""One-to-one time-interval lightning event detection evaluation.

All intervals are [start_ms, end_ms) and measured in the original video timeline.
A single prediction can match at most one expert reference event.
"""
from __future__ import annotations

import csv
import json
import math
from collections import Counter, defaultdict
from pathlib import Path

from metrics import cluster_interval, dataset_fingerprint, safe_div, quantile


def read_table(path: Path, fields: set[str]) -> list[dict]:
    with path.open(encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        if reader.fieldnames is None or not fields <= set(reader.fieldnames):
            raise ValueError(f"Missing {path.name} fields: {sorted(fields - set(reader.fieldnames or []))}")
        return list(reader)


def parse(clips_path: Path, truth_path: Path, predictions_path: Path) -> list[dict]:
    clip_rows = read_table(clips_path, {"video_id", "source_group", "split", "domain", "duration_ms", "sha256"})
    clips = {}
    groups = {}
    hashes = {}
    for row in clip_rows:
        vid = row["video_id"].strip()
        group = row["source_group"].strip()
        split = row["split"].strip().lower()
        domain = row["domain"].strip()
        digest = row["sha256"].strip().lower()
        if not vid or vid in clips or not group or not domain or split not in ("train", "validation", "test", "external"):
            raise ValueError("Missing/duplicate video_id or invalid video metadata")
        if len(digest) != 64 or any(c not in "0123456789abcdef" for c in digest):
            raise ValueError("Invalid video SHA-256")
        try:
            duration = int(row["duration_ms"])
        except ValueError as exc:
            raise ValueError("Invalid duration_ms") from exc
        if duration <= 0 or duration > 24 * 3600 * 1000:
            raise ValueError("Duration must be between 1 ms and 24 hours")
        if group in groups and groups[group] != split:
            raise ValueError("Event/group leakage between train and evaluation")
        if digest in hashes and hashes[digest] != split:
            raise ValueError("Identical video leakage between partitions")
        groups[group] = split
        hashes[digest] = split
        clips[vid] = {"video_id": vid, "source_group": group, "split": split,
                      "domain": domain, "duration_ms": duration, "sha256": digest,
                      "truth": [], "predictions": []}
    if not clips:
        raise ValueError("No videos")
    for table, columns, key in (
        (truth_path, {"video_id", "event_id", "start_ms", "end_ms"}, "truth"),
        (predictions_path, {"video_id", "event_id", "start_ms", "end_ms", "score"}, "predictions"),
    ):
        seen = set()
        for row in read_table(table, columns):
            vid = row["video_id"].strip()
            event_id = row["event_id"].strip()
            if vid not in clips or not event_id or (vid, event_id) in seen:
                raise ValueError("Unknown video ID or repeated event ID in " + table.name)
            seen.add((vid, event_id))
            try:
                start = int(row["start_ms"])
                end = int(row["end_ms"])
                score = float(row["score"]) if key == "predictions" else None
            except ValueError as exc:
                raise ValueError("Invalid event time or score") from exc
            if start < 0 or end <= start or end > clips[vid]["duration_ms"]:
                raise ValueError("Event outside declared video duration")
            if key == "predictions" and (not math.isfinite(score) or not 0 <= score <= 1):
                raise ValueError("Prediction score must be finite and in [0,1]")
            clips[vid][key].append({"event_id": event_id, "start_ms": start,
                                    "end_ms": end, "score": score})
    return list(clips.values())


def interval_iou(a: dict, b: dict) -> float:
    intersection = max(0, min(a["end_ms"], b["end_ms"]) - max(a["start_ms"], b["start_ms"]))
    if intersection == 0:
        return 0.0
    union = max(a["end_ms"], b["end_ms"]) - min(a["start_ms"], b["start_ms"])
    return intersection / union


def score_events(clips: list[dict], threshold: float, min_iou: float = 0.3) -> dict:
    if not 0.0 <= threshold <= 1.0 or not 0 < min_iou <= 1.0:
        raise ValueError("Invalid score/IoU thresholds")
    tp = fp = fn = 0
    onset_errors: list[int] = []
    positives = negatives = 0
    duration = 0
    for clip in clips:
        duration += clip["duration_ms"]
        truths = clip["truth"]
        predictions = sorted(
            [p for p in clip["predictions"] if p["score"] >= threshold],
            key=lambda p: (-p["score"], p["start_ms"], p["event_id"])
        )
        if truths:
            positives += 1
        else:
            negatives += 1
        unmatched = set(range(len(truths)))
        for p in predictions:
            candidates = sorted(
                ((interval_iou(p, truths[j]), j) for j in unmatched),
                key=lambda item: (-item[0], item[1])
            )
            if candidates and candidates[0][0] >= min_iou:
                _, j = candidates[0]
                unmatched.remove(j)
                tp += 1
                onset_errors.append(abs(p["start_ms"] - truths[j]["start_ms"]))
            else:
                fp += 1
        fn += len(unmatched)
    precision = safe_div(tp, tp + fp)
    recall = safe_div(tp, tp + fn)
    f1 = safe_div(2 * tp, 2 * tp + fp + fn)
    return {"videos": len(clips), "positive_videos": positives, "negative_videos": negatives,
            "duration_hours": duration / 3_600_000,
            "reference_events": tp + fn, "detected_events": tp + fp,
            "tp": tp, "fp": fp, "fn": fn, "precision": precision, "recall": recall, "f1": f1,
            "false_positives_per_hour": safe_div(fp * 3_600_000, duration),
            "median_abs_onset_error_ms": quantile(onset_errors, 0.5) if onset_errors else None,
            "threshold": threshold, "min_iou": min_iou}


def evaluate(clips: list[dict], threshold: float = 0.5, min_iou: float = 0.3,
             seed: int = 2026, bootstraps: int = 400) -> dict:
    if not any(c["split"] in ("test", "external") for c in clips):
        raise ValueError("No independently labelled test/external videos")
    report = {}
    for split in ("test", "external"):
        subset = [c for c in clips if c["split"] == split]
        if not subset:
            continue
        result = score_events(subset, threshold, min_iou)
        result["f1_cluster_ci_95"] = cluster_interval(
            subset, "source_group", lambda sample: score_events(sample, threshold, min_iou)["f1"],
            seed=seed, iterations=bootstraps
        )
        result["by_domain"] = {
            domain: score_events([c for c in subset if c["domain"] == domain], threshold, min_iou)
            for domain in sorted({c["domain"] for c in subset})
        }
        report[split] = result
    return {
        "schema": "lightninglab-scientific-events-v1",
        "clips": len(clips),
        "partition_counts": dict(Counter(c["split"] for c in clips)),
        "source_fingerprint_sha256": dataset_fingerprint(clips,
            ["video_id", "source_group", "split", "sha256", "duration_ms"]),
        "results": report,
        "limitations": [
            "One-to-one interval IoU matching against expert event annotations.",
            "Detection score threshold and IoU threshold MUST be fixed using validation, before opening test.",
            "Negative clips are required to estimate deployment false alarms; no negative clips means no evidence of operational specificity.",
            "FP/hour is an event frequency over all annotated videos, not a probability of lightning occurrence.",
            "Video-only RGB cannot independently determine CG/IC, charge polarity, energy or return-stroke speed.",
            "Grouped confidence intervals resample source_group clusters; they may be missing when evidence is sparse.",
        ],
    }


def write_report(clips_csv: Path, truth_csv: Path, predicted_csv: Path, output: Path,
                 threshold: float = 0.5, min_iou: float = 0.3,
                 seed: int = 2026, bootstraps: int = 400) -> dict:
    data = evaluate(parse(clips_csv, truth_csv, predicted_csv), threshold, min_iou, seed, bootstraps)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return data
