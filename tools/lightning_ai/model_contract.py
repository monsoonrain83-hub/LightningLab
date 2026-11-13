"""Shared, dependency-light contract for the Brixton lightning video classifier."""
from __future__ import annotations

import math
from typing import Iterable

LABELS = (
    "towerupward",
    "towerdownward",
    "close",
    "far",
    "unclear",
    "unclearbehind",
    "CC",
)
INPUT_FRAMES = 8
INPUT_HEIGHT = 224
INPUT_WIDTH = 224
INPUT_CHANNELS = 3
INPUT_DTYPE = "float32"
INPUT_RANGE = "RGB 0..255"
OUTPUT_SHAPE = (1, len(LABELS))


def validate_manifest_rows(rows: Iterable[dict]) -> tuple[int, dict[str, int]]:
    """Reject unknown labels/splits and event groups crossing data partitions."""
    allowed_splits = {"train", "validation", "test"}
    group_split: dict[str, str] = {}
    hash_split: dict[str, str] = {}
    path_split: dict[str, str] = {}
    counts = {split: 0 for split in allowed_splits}
    total = 0
    for row in rows:
        label = row.get("label")
        split = row.get("split")
        group = row.get("event_group")
        if label not in LABELS:
            raise ValueError(f"Unknown Brixton label: {label!r}")
        if split not in allowed_splits:
            raise ValueError(f"Unknown split: {split!r}")
        if not group:
            raise ValueError("Every manifest row must have an event_group")
        previous = group_split.setdefault(group, split)
        if previous != split:
            raise ValueError(f"Event group {group!r} leaks across splits")
        # Identical payloads or paths must never cross train/validation/test,
        # even when a hand-edited manifest assigns different event groups.
        digest = (row.get("sha256") or "").strip().lower()
        if digest:
            if len(digest) != 64 or any(ch not in "0123456789abcdef" for ch in digest):
                raise ValueError("Invalid sha256 digest in manifest")
            previous_hash_split = hash_split.setdefault(digest, split)
            if previous_hash_split != split:
                raise ValueError(f"Identical clip payload leaks across splits: {digest}")
        path = (row.get("relative_path") or "").strip()
        if path:
            previous_path_split = path_split.setdefault(path, split)
            if previous_path_split != split:
                raise ValueError(f"Clip path leaks across splits: {path}")
        counts[split] += 1
        total += 1
    return total, counts


def decode_probabilities(values: Iterable[float]) -> list[tuple[str, float]] | None:
    """Validate and normalize one seven-class model output; malformed output is rejected."""
    try:
        scores = [float(value) for value in values]
    except (TypeError, ValueError):
        return None
    if len(scores) != len(LABELS) or any(not math.isfinite(v) or v < 0.0 for v in scores):
        return None
    total = sum(scores)
    if total <= 1e-12:
        return None
    normalized = [value / total for value in scores]
    return sorted(zip(LABELS, normalized), key=lambda item: item[1], reverse=True)


def event_window_sample_times_ms(start_ms: int, end_ms: int, duration_ms: int) -> list[int]:
    """Match Android LightningTemporalModel.sampleTimes, with 500 ms context."""
    if start_ms < 0 or end_ms < start_ms or duration_ms < 0:
        raise ValueError("Invalid human-annotated event window")
    start = min(duration_ms, max(0, start_ms - 500))
    end = max(start, min(duration_ms, end_ms + 500))
    return [start + (end - start) * i // (INPUT_FRAMES - 1)
            for i in range(INPUT_FRAMES)]


def model_metadata() -> dict:
    return {
        "model_family": "MobileNetV3Small temporal mean-pool",
        "labels": list(LABELS),
        "input_shape": [1, INPUT_FRAMES, INPUT_HEIGHT, INPUT_WIDTH, INPUT_CHANNELS],
        "input_dtype": INPUT_DTYPE,
        "input_range": INPUT_RANGE,
        "output_shape": list(OUTPUT_SHAPE),
        "output": "softmax",
        "dataset_doi": "10.17632/44vkdvrn67.1",
        "dataset_license": "CC BY 4.0",
        "scope_note": "Single-site Brixton tower camera dataset; not a universal lightning taxonomy.",
    }
