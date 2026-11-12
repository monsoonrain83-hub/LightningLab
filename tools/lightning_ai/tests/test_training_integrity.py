"""Regression tests for temporal lightning dataset integrity (stdlib only)."""
import csv
import hashlib
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from model_contract import LABELS, validate_manifest_rows
from train_brixton import read_manifest


class LightningTrainingIntegrityTests(unittest.TestCase):
    def test_same_hash_in_distinct_event_groups_is_rejected(self):
        rows = [
            {"label": LABELS[0], "split": "train", "event_group": "one", "sha256": "a" * 64},
            {"label": LABELS[1], "split": "test", "event_group": "two", "sha256": "a" * 64},
        ]
        with self.assertRaisesRegex(ValueError, "payload leaks"):
            validate_manifest_rows(rows)

    def test_same_clip_path_cross_split_is_rejected(self):
        rows = [
            {"label": LABELS[0], "split": "train", "event_group": "one", "relative_path": "a.mp4"},
            {"label": LABELS[1], "split": "test", "event_group": "two", "relative_path": "a.mp4"},
        ]
        with self.assertRaisesRegex(ValueError, "path leaks"):
            validate_manifest_rows(rows)

    def test_invalid_sha256_rejected(self):
        with self.assertRaisesRegex(ValueError, "Invalid sha256"):
            validate_manifest_rows([
                {"label": LABELS[0], "split": "train", "event_group": "a", "sha256": "BAD"},
            ])

    def test_corrupted_clip_rejected_before_training(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            labels = (LABELS[0], LABELS[1], LABELS[2])
            rows = []
            for i, split in enumerate(("train", "validation", "test")):
                name = f"clip{i}.mp4"
                data = f"clip{i}".encode()
                (root / name).write_bytes(data)
                rows.append({
                    "relative_path": name, "label": labels[i],
                    "split": split, "event_group": f"event{i}",
                    "sha256": hashlib.sha256(data).hexdigest(),
                })
            rows[1]["sha256"] = "f" * 64
            manifest = root / "manifest.csv"
            with manifest.open("w", newline="", encoding="utf-8") as stream:
                writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
                writer.writeheader()
                writer.writerows(rows)
            with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
                read_manifest(manifest, root)


if __name__ == "__main__":
    unittest.main()
