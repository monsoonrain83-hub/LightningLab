import json
import csv
import math
import tempfile
import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from evaluate_predictions import (
    LIGHTNING_LABELS, CLOUD_HEAD_LABELS, evaluate, grouped_bootstrap,
    leakage_errors, metrics, read_predictions, audit_manifest,
)


def row(label="close", split="test", group="ev1", confidence=0.95, **kwargs):
    p = [(1.0 - confidence) / 6] * 7
    p[LIGHTNING_LABELS.index(label)] = confidence
    return dict(split=split, label=label, group_id=group, probabilities=p, **kwargs)


class ScientificEvaluationTests(unittest.TestCase):
    def test_perfect_predictions_have_perfect_macro_f1(self):
        rows = [row(label="close", group="a"), row(label="far", group="b")]
        result = metrics(rows, LIGHTNING_LABELS)
        self.assertEqual(result["macro_f1_on_accepted"], 1)
        self.assertAlmostEqual(result["accuracy_on_accepted"], 1)
        self.assertGreater(result["multiclass_brier"], 0)

    def test_event_group_leakage_is_blocked(self):
        rows = [row(split="train", group="duplicate"), row(group="duplicate")]
        with self.assertRaisesRegex(ValueError, "DATA LEAKAGE"):
            evaluate(rows, LIGHTNING_LABELS, bootstrap=0)

    def test_duplicate_hash_is_blocked(self):
        digest = "f" * 64
        rows = [row(split="train", group="a", sha256=digest),
                row(split="test", group="b", sha256=digest)]
        self.assertTrue(leakage_errors(rows))

    def test_independent_sites_must_be_disjoint(self):
        rows = [row(split="train", group="a", site_id="camera-place"),
                row(split="test", group="b", site_id="camera-place")]
        with self.assertRaisesRegex(ValueError, "site_id"):
            evaluate(rows, LIGHTNING_LABELS, independent_sites=True, bootstrap=0)

    def test_independent_sites_requires_site_id(self):
        with self.assertRaisesRegex(ValueError, "site_id"):
            evaluate([row()], LIGHTNING_LABELS, independent_sites=True, bootstrap=0)

    def test_abstention_is_not_counted_as_wrong(self):
        rows = [row(group="a", confidence=.95),
                row(group="b", confidence=.4)]
        result = metrics(rows, LIGHTNING_LABELS, abstain_below=.8)
        self.assertEqual(result["accepted"], 1)
        self.assertEqual(result["coverage"], .5)

    def test_all_abstained_do_not_claim_accuracy(self):
        result = metrics([row(confidence=.4)], LIGHTNING_LABELS, abstain_below=.9)
        self.assertIsNone(result["accuracy_on_accepted"])
        self.assertIsNone(result["macro_f1_on_accepted"])

    def test_bootstrap_is_deterministic_by_seed(self):
        rows = [row(group="a"), row(label="far", group="b")]
        self.assertEqual(grouped_bootstrap(rows, LIGHTNING_LABELS, 30, 12, 0),
                         grouped_bootstrap(rows, LIGHTNING_LABELS, 30, 12, 0))

    def test_unknown_label_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "rows.jsonl"
            obj = row()
            obj["label"] = "cumulonimbus"
            path.write_text(json.dumps(obj) + "\n")
            with self.assertRaisesRegex(ValueError, "unknown label"):
                read_predictions(path, LIGHTNING_LABELS)

    def test_nonfinite_probability_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "rows.jsonl"
            obj = row()
            obj["probabilities"][0] = float("nan")
            path.write_text(json.dumps(obj) + "\n")
            with self.assertRaisesRegex(ValueError, "non-finite"):
                read_predictions(path, LIGHTNING_LABELS)

    def test_duplicate_sample_id_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "rows.jsonl"
            path.write_text("\n".join(json.dumps(row(group=g, sample_id="x"))
                                      for g in ("a", "b")) + "\n")
            with self.assertRaisesRegex(ValueError, "duplicate sample_id"):
                read_predictions(path, LIGHTNING_LABELS)

    def test_cloud_head_filter(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "rows.jsonl"
            p = [0] * 11
            p[3] = 1
            path.write_text(json.dumps({"head": "CL", "split": "test",
                "group_id": "a", "label": "3", "probabilities": p}) + "\n")
            rows = read_predictions(path, CLOUD_HEAD_LABELS, head="CL")
            self.assertEqual(metrics(rows, CLOUD_HEAD_LABELS)["accuracy_on_accepted"], 1)
            with self.assertRaisesRegex(ValueError, "no evaluation rows"):
                read_predictions(path, CLOUD_HEAD_LABELS, head="CH")


    def test_manifest_protects_against_hidden_train_test_leakage(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "manifest.csv"
            with path.open("w", newline="") as f:
                writer = csv.DictWriter(f, fieldnames=["event_group", "split", "sha256"])
                writer.writeheader()
                writer.writerow({"event_group": "same", "split": "train", "sha256": "a" * 64})
                writer.writerow({"event_group": "same", "split": "test", "sha256": "b" * 64})
            with self.assertRaisesRegex(ValueError, "DATA LEAKAGE"):
                audit_manifest(path)

    def test_manifest_counts_clean_splits(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "manifest.csv"
            path.write_text("event_group,split,sha256\n"
                            "x,train," + "a" * 64 + "\n" +
                            "y,test," + "b" * 64 + "\n")
            result = audit_manifest(path)
            self.assertEqual(result["groups"], 2)
            self.assertEqual(result["split_counts"]["test"], 1)

    def test_brier_is_finite_and_ece_bounded(self):
        result = metrics([row(group="a"), row(group="b", confidence=.6)],
                         LIGHTNING_LABELS)
        self.assertTrue(math.isfinite(result["multiclass_brier"]))
        self.assertGreaterEqual(result["ece_10_bins"], 0)
        self.assertLessEqual(result["ece_10_bins"], 1)


    def test_independent_site_full_manifest_rejects_shared_site(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "manifest.csv"
            path.write_text("event_group,split,site_id\n"
                            "x,train,same\n"
                            "y,test,same\n")
            with self.assertRaisesRegex(ValueError, "site_id"):
                audit_manifest(path, require_independent_sites=True)

    def test_independent_site_full_manifest_rejects_missing_site(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "manifest.csv"
            path.write_text("event_group,split,site_id\n"
                            "x,train,\n"
                            "y,test,different\n")
            with self.assertRaisesRegex(ValueError, "site_id missing"):
                audit_manifest(path, require_independent_sites=True)


if __name__ == "__main__":
    unittest.main()
