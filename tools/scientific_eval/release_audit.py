#!/usr/bin/env python3
"""Fail-closed model *candidate* evidence audit. Not a scientific certification."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from classification import TASK_LABELS


def sha256_file(path: Path) -> str:
    hasher = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def audit(model_file: Path, metadata: dict, evaluation: dict, task: str,
          min_external: int = 100, min_domains: int = 3,
          min_support_per_class: int = 5, min_macro_f1: float = 0.80,
          max_ece: float = 0.15) -> dict:
    if task not in TASK_LABELS:
        raise ValueError("Unsupported task")
    if min_external < 1 or min_domains < 2 or min_support_per_class < 1:
        raise ValueError("Invalid scientific evidence requirements")
    if not 0 <= min_macro_f1 <= 1 or not 0 <= max_ece <= 1:
        raise ValueError("Invalid quality thresholds")
    reasons = []
    actual_hash = sha256_file(model_file)
    expected = metadata.get("tflite_sha256", "")
    if not isinstance(expected, str) or expected.lower() != actual_hash:
        reasons.append("Model SHA-256 does not match training metadata")
    if not metadata.get("manifest_sha256") or not metadata.get("dataset_license"):
        reasons.append("Training manifest fingerprint and dataset license are required")
    if not isinstance(metadata.get("training_clips"), int) or metadata["training_clips"] < 1:
        reasons.append("No positive recorded training sample count")
    if not isinstance(metadata.get("test_clips"), int) or metadata["test_clips"] < 1:
        reasons.append("No held-out model test sample count")
    task_labels = list(TASK_LABELS[task])
    if task == "brixton":
        if metadata.get("labels") != task_labels:
            reasons.append("Model label order differs from runtime Brixton contract")
        if metadata.get("input_shape") != [1, 8, 224, 224, 3] or metadata.get("output_shape") != [1, 7]:
            reasons.append("Brixton tensor contract mismatch")
    elif task == "cloud-genus":
        if metadata.get("labels") != task_labels:
            reasons.append("Cloud genus label order mismatch")
    else:
        # Three-head WMO models have one classifier per level; require all
        # head-order metadata and evaluate each head separately.
        if metadata.get("head_order") != ["CL", "CM", "CH"] or metadata.get("class_order_per_head") != task_labels:
            reasons.append("WMO synoptic head contract mismatch")

    if evaluation.get("schema") != "lightninglab-scientific-classification-v1":
        reasons.append("Unknown evaluation report schema")
    if evaluation.get("task") != task or evaluation.get("labels") != task_labels:
        reasons.append("Evaluated task/label order does not match model")
    if not evaluation.get("fingerprint_sha256"):
        reasons.append("No evaluation dataset fingerprint")
    ext = evaluation.get("results", {}).get("external")
    if not isinstance(ext, dict):
        reasons.append("No independent external test report")
    else:
        observed = ext.get("evaluable", 0)
        domains = len(ext.get("by_domain", {}))
        supported = ext.get("metrics", {}).get("macro_f1_supported_classes", 0)
        per_class = ext.get("metrics", {}).get("per_class", {})
        if observed < min_external:
            reasons.append(f"Only {observed} external observations (minimum {min_external})")
        if domains < min_domains:
            reasons.append(f"Only {domains} external domains (minimum {min_domains})")
        # Exclude WMO observer "/" from support requirements, not a meteorological genus.
        physical_labels = [v for v in task_labels if v != "/"] if task.startswith("wmo-") else task_labels
        missing = [label for label in physical_labels
                   if per_class.get(label, {}).get("support", 0) < min_support_per_class]
        if missing:
            reasons.append("External evidence too sparse for classes: " + ", ".join(missing))
        if supported < len(physical_labels):
            reasons.append("External test does not cover all evaluable classes")
        macro = ext.get("metrics", {}).get("macro_f1")
        ece = ext.get("metrics", {}).get("ece")
        if macro is None or macro < min_macro_f1:
            reasons.append(f"External macro-F1 is unavailable/below {min_macro_f1}")
        if ece is None or ece > max_ece:
            reasons.append(f"External ECE is unavailable/above {max_ece}")
    return {
        "schema": "lightninglab-scientific-release-audit-v1",
        "task": task,
        "model_sha256": actual_hash,
        "status": "candidate_for_expert_review" if not reasons else "blocked",
        "reasons": reasons,
        "thresholds": {"min_external": min_external, "min_domains": min_domains,
                       "min_support_per_class": min_support_per_class,
                       "min_macro_f1": min_macro_f1, "max_ece": max_ece},
        "caveat": ("Candidate status is an automated evidence checklist only; human review, "
                   "external validation, metadata provenance and device testing are required. "
                   "No hardware-verified lightning polarity or cloud physical truth is asserted.")
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--evaluation", type=Path, required=True)
    parser.add_argument("--task", choices=sorted(TASK_LABELS), required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--min-external", type=int, default=100)
    parser.add_argument("--min-domains", type=int, default=3)
    parser.add_argument("--min-support-per-class", type=int, default=5)
    parser.add_argument("--min-macro-f1", type=float, default=0.8)
    parser.add_argument("--max-ece", type=float, default=0.15)
    args = parser.parse_args()
    try:
        result = audit(args.model,
                       json.loads(args.metadata.read_text(encoding="utf-8")),
                       json.loads(args.evaluation.read_text(encoding="utf-8")),
                       args.task, args.min_external, args.min_domains,
                       args.min_support_per_class, args.min_macro_f1, args.max_ece)
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(json.dumps({"status": result["status"], "reasons": result["reasons"]}, ensure_ascii=False))
        return 0 if result["status"] == "candidate_for_expert_review" else 3
    except (ValueError, OSError, KeyError, TypeError) as err:
        print("Scientific release audit failed:", err)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
