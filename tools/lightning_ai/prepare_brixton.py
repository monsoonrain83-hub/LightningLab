#!/usr/bin/env python3
"""Prepare a leakage-aware manifest for the labelled Brixton lightning video dataset.

Expected filename: YYYY-MM-DD_HH_MM_SS_<label>_Cam[1-3].mp4
Only the published, manually assigned event labels are retained.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path
from typing import Iterable

LABELS = {
    "towerupward": "towerupward",
    "towerdownward": "towerdownward",
    "close": "close",
    "far": "far",
    "unclear": "unclear",
    "unclearbehind": "unclearbehind",
    "cc": "CC",
}
FILENAME = re.compile(
    r"^(?P<date>\d{4}-\d{2}-\d{2})_(?P<time>\d{2}_\d{2}_\d{2})_"
    r"(?P<label>[^_]+)_Cam(?P<camera>[1-3])\.mp4$",
    re.IGNORECASE,
)
FIELDS = [
    "relative_path", "label", "timestamp_sast", "camera", "event_group",
    "split", "sha256", "file_size_bytes",
]


def parse_filename(path: Path) -> dict | None:
    match = FILENAME.match(path.name)
    if not match:
        return None
    label = LABELS.get(match.group("label").lower())
    if label is None:
        return None
    timestamp = datetime.strptime(
        match.group("date") + "_" + match.group("time"), "%Y-%m-%d_%H_%M_%S"
    )
    return {
        "label": label,
        "timestamp": timestamp,
        "camera": int(match.group("camera")),
    }


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


class UnionFind:
    def __init__(self, values: Iterable[str]):
        self.parent = {value: value for value in values}

    def find(self, value: str) -> str:
        parent = self.parent[value]
        if parent != value:
            self.parent[value] = self.find(parent)
        return self.parent[value]

    def union(self, left: str, right: str) -> None:
        a, b = self.find(left), self.find(right)
        if a != b:
            self.parent[max(a, b)] = min(a, b)


def chronological_splits(group_times: list[datetime]) -> dict[datetime, str]:
    """Split event groups chronologically; retain at least one group per split when possible."""
    ordered = sorted(set(group_times))
    count = len(ordered)
    if count == 0:
        return {}
    if count == 1:
        return {ordered[0]: "train"}
    if count == 2:
        return {ordered[0]: "train", ordered[1]: "validation"}
    train_end = min(count - 2, max(1, int(count * 0.70)))
    validation_end = min(count - 1, max(train_end + 1, int(count * 0.85)))
    result = {}
    for index, timestamp in enumerate(ordered):
        result[timestamp] = (
            "train" if index < train_end
            else "validation" if index < validation_end
            else "test"
        )
    return result


def build_manifest(root: Path) -> tuple[list[dict], list[str]]:
    root = root.resolve()
    candidates = sorted(
        (path for path in root.rglob("*") if path.is_file() and path.suffix.lower() == ".mp4"),
        key=lambda p: p.as_posix().lower(),
    )
    records: list[dict] = []
    skipped: list[str] = []
    for path in candidates:
        parsed = parse_filename(path)
        if parsed is None:
            skipped.append(path.relative_to(root).as_posix())
            continue
        records.append({
            **parsed,
            "path": path,
            "relative_path": path.relative_to(root).as_posix(),
            "sha256": sha256_file(path),
            "file_size_bytes": path.stat().st_size,
        })
    if not records:
        raise ValueError("No labelled Brixton MP4 files matched the documented filename format.")

    # A timestamp is the event key. Identical video payloads at different timestamps are
    # joined too, preventing duplicate content from leaking across chronological splits.
    event_keys = sorted({record["timestamp"].isoformat() for record in records})
    union = UnionFind(event_keys)
    first_by_hash: dict[str, str] = {}
    for record in records:
        key = record["timestamp"].isoformat()
        previous = first_by_hash.setdefault(record["sha256"], key)
        union.union(previous, key)

    components: dict[str, list[str]] = defaultdict(list)
    for key in event_keys:
        components[union.find(key)].append(key)
    component_times = {
        component: min(datetime.fromisoformat(key) for key in keys)
        for component, keys in components.items()
    }
    component_splits = chronological_splits(list(component_times.values()))
    component_for_key = {
        key: component for component, keys in components.items() for key in keys
    }
    split_for_key = {
        key: component_splits[component_times[component]]
        for key, component in component_for_key.items()
    }
    event_group_for_key = {
        key: hashlib.sha256("|".join(sorted(keys)).encode("utf-8")).hexdigest()[:16]
        for keys in components.values() for key in keys
    }

    output = []
    for record in records:
        key = record["timestamp"].isoformat()
        output.append({
            "relative_path": record["relative_path"],
            "label": record["label"],
            "timestamp_sast": key,
            "camera": record["camera"],
            "event_group": event_group_for_key[key],
            "split": split_for_key[key],
            "sha256": record["sha256"],
            "file_size_bytes": record["file_size_bytes"],
        })
    output.sort(key=lambda row: (row["timestamp_sast"], row["camera"], row["relative_path"]))
    return output, skipped


def write_manifest(root: Path, output: Path) -> dict:
    rows, skipped = build_manifest(root)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(rows)
    summary = {
        "clips": len(rows),
        "skipped_mp4": len(skipped),
        "labels": dict(sorted(Counter(row["label"] for row in rows).items())),
        "splits": dict(sorted(Counter(row["split"] for row in rows).items())),
        "cameras": dict(sorted(Counter(str(row["camera"]) for row in rows).items())),
        "event_groups": len({row["event_group"] for row in rows}),
        "skipped_files": skipped,
    }
    output.with_suffix(".summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return summary


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True, help="Extracted dataset root")
    parser.add_argument("--out", type=Path, required=True, help="Output CSV manifest path")
    args = parser.parse_args()
    summary = write_manifest(args.root, args.out)
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
