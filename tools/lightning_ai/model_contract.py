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
