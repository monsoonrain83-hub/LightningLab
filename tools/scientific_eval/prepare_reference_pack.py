#!/usr/bin/env python3
"""Build a reference-annotation CSV pack from real local videos using ffprobe.

Deliberately leaves group, split and domain UNVERIFIED. Review those fields
before running evaluation to avoid falsely independent samples.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import subprocess
from pathlib import Path

SUFFIXES = {".mp4", ".mov", ".mkv", ".webm", ".avi", ".m4v"}


def fingerprint(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def probe_duration_ms(path: Path) -> int:
    try:
        result = subprocess.run(
            ["ffprobe", "-v", "error", "-show_entries", "format=duration",
             "-of", "json", str(path)],
            capture_output=True, check=True, text=True, timeout=60,
        )
    except (OSError, subprocess.SubprocessError) as error:
        raise ValueError("ffprobe is required for trusted video duration: " + str(path)) from error
    try:
        duration = float(json.loads(result.stdout)["format"]["duration"])
    except (KeyError, ValueError, TypeError, json.JSONDecodeError) as error:
        raise ValueError("No trustworthy video duration from ffprobe: " + str(path)) from error
    milliseconds = round(duration * 1000)
    if not math.isfinite(duration) or not 1 <= milliseconds <= 24 * 3600 * 1000:
        raise ValueError("Invalid video duration: " + str(path))
    return milliseconds


def create_pack(root: Path, output: Path) -> int:
    root = root.resolve()
    if not root.is_dir():
        raise ValueError("Video input directory does not exist")
    files = [p for p in sorted(root.rglob("*")) if p.is_file()
             and not p.is_symlink() and p.suffix.lower() in SUFFIXES]
    if not files:
        raise ValueError("No video clips found")
    output.mkdir(parents=True, exist_ok=True)
    rows = []
    for item in files:
        if root not in item.resolve().parents:
            raise ValueError("Source file points outside video directory")
        rows.append({
            "video_id": item.relative_to(root).as_posix(),
            "source_group": "UNVERIFIED_GROUP",
            "split": "UNASSIGNED",
            "domain": "UNVERIFIED_DOMAIN",
            "duration_ms": probe_duration_ms(item),
            "sha256": fingerprint(item),
        })
    clips_path = output / "clips_REVIEW_BEFORE_EVALUATION.csv"
    columns = ["video_id", "source_group", "split", "domain", "duration_ms", "sha256"]
    with clips_path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=columns)
        writer.writeheader()
        writer.writerows(rows)
    for name, header in {
        "expert_intervals.csv": "video_id,event_id,start_ms,end_ms\n",
        "detector_intervals.csv": "video_id,event_id,start_ms,end_ms,score\n",
    }.items():
        (output / name).write_text(header, encoding="utf-8")
    (output / "READ_THIS_FIRST.txt").write_text(
        "Replace EVERY UNASSIGNED split, UNVERIFIED_GROUP and UNVERIFIED_DOMAIN "
        "using real provenance and event/camera grouping. Review measured durations. "
        "Expert labels must include missed lightning and clips without lightning. "
        "No scientific metric has been calculated.\n",
        encoding="utf-8",
    )
    return len(rows)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = create_pack(args.root, args.out)
        print(json.dumps({"real_clips_indexed": result, "ready_for_evaluation": False,
                          "review_csv": str(args.out / "clips_REVIEW_BEFORE_EVALUATION.csv")}))
        return 0
    except (ValueError, OSError) as error:
        print("Reference pack generation refused:", error)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
