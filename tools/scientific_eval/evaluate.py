#!/usr/bin/env python3
"""Offline scientific-evaluation CLI for LightningLab. Never trains or invents models.

Usage:
 python3 tools/scientific_eval/evaluate.py classification --task brixton \
   --input predictions.csv --out assessment.json
 python3 tools/scientific_eval/evaluate.py events --clips clips.csv \
   --truth expert_events.csv --predictions detections.csv --out events.json
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from classification import TASK_LABELS, write_report as classify
from events import write_report as events


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="mode", required=True)
    c = sub.add_parser("classification", help="Brixton lightning type / cloud classification")
    c.add_argument("--task", choices=sorted(TASK_LABELS), required=True)
    c.add_argument("--input", type=Path, required=True)
    c.add_argument("--out", type=Path, required=True)
    c.add_argument("--seed", type=int, default=2026)
    c.add_argument("--bootstraps", type=int, default=400)

    e = sub.add_parser("events", help="One-to-one lightning detections against expert intervals")
    e.add_argument("--clips", type=Path, required=True)
    e.add_argument("--truth", type=Path, required=True)
    e.add_argument("--predictions", type=Path, required=True)
    e.add_argument("--out", type=Path, required=True)
    e.add_argument("--score-threshold", type=float, default=0.5)
    e.add_argument("--min-iou", type=float, default=0.3)
    e.add_argument("--seed", type=int, default=2026)
    e.add_argument("--bootstraps", type=int, default=400)

    args = parser.parse_args(argv)
    try:
        if args.mode == "classification":
            result = classify(args.input, args.out, args.task, args.seed, args.bootstraps)
        else:
            result = events(args.clips, args.truth, args.predictions, args.out,
                            args.score_threshold, args.min_iou, args.seed, args.bootstraps)
        print(json.dumps({
            "report_path": str(args.out),
            "schema": result["schema"],
            "task": result.get("task", "temporal-detection"),
            "evaluated_partitions": sorted(result["results"]),
            "real_observations_required": True
        }, ensure_ascii=False))
        return 0
    except (OSError, UnicodeError, ValueError, KeyError, TypeError) as error:
        print(f"SCIENTIFIC EVALUATION FAILED: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
