#!/usr/bin/env python3
"""Convert LightningLab schema-v3 event evidence to detector predictions CSV.

Human review flags on *detected* candidates are NEVER converted into a
complete ground-truth reference: missed strikes require independent annotation.
"""
from __future__ import annotations

import argparse
import csv
import json
import math
from pathlib import Path


def transform(evidence: dict, video_id: str) -> list[dict]:
    if not video_id or not isinstance(evidence, dict) or evidence.get("schema_version") != 3:
        raise ValueError("Expected LightningLab evidence schema v3 and a video_id")
    if evidence.get("source") != "visual_heuristic" or not isinstance(evidence.get("events"), list):
        raise ValueError("Only original visual_heuristic evidence events are supported")
    rows = []
    ids = set()
    for event in evidence["events"]:
        try:
            id_value = str(event["id"])
            start = int(event["start_ms"])
            end = int(event["end_ms"])
            score = float(event["algorithm_score"])
        except (TypeError, ValueError, KeyError) as error:
            raise ValueError("Invalid exported LightningLab event") from error
        if id_value in ids or start < 0 or end < start or not math.isfinite(score) or score < 0:
            raise ValueError("Invalid/duplicate event ID, timing or detector ranking score")
        ids.add(id_value)
        if end == start:
            # Half-open one-millisecond bookkeeping width. It is NOT a measured
            # physical flash duration or an instrument-exposure estimate.
            end += 1
        rows.append({"video_id": video_id, "event_id": id_value,
                     "start_ms": start, "end_ms": end, "score": repr(score)})
    return rows


def convert(source: Path, video_id: str, out: Path) -> int:
    data = json.loads(source.read_text(encoding="utf-8"))
    rows = transform(data, video_id)
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w", encoding="utf-8", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=["video_id", "event_id", "start_ms", "end_ms", "score"])
        writer.writeheader()
        writer.writerows(rows)
    return len(rows)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--video-id", required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    try:
        count = convert(args.evidence, args.video_id, args.out)
        print(json.dumps({"output": str(args.out), "candidate_events": count,
                          "complete_ground_truth_created": False}))
        return 0
    except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError) as e:
        print("Conversion refused:", e)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
