"""Synthetic regression fixtures. These are NOT measured model accuracy."""
import csv
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from classification import BRIXTON, TASK_LABELS, parse_csv, evaluate
from metrics import classification_metrics, cluster_interval, selective_metrics


class ClassificationEvalTests(unittest.TestCase):
    def row(self, label, predicted=None, group="g1", split="test", domain="site-A"):
        labels = BRIXTON
        scores = [0.0] * len(labels)
        scores[labels.index(predicted or label)] = 1.0
        return {"sample_id": group + label, "source_group": group, "split": split,
                "domain": domain, "sha256": "a" * 64, "truth": label,
                "scores": scores}

    def test_perfect_synthetic_scores_have_zero_brier(self):
        rows = [self.row(BRIXTON[0]), self.row(BRIXTON[1], group="g2")]
        score = classification_metrics(rows, list(BRIXTON))
        self.assertEqual(1.0, score["accuracy"])
        self.assertEqual(1.0, score["macro_f1"])
        self.assertEqual(0.0, score["brier_multiclass"])
        self.assertEqual(0.0, score["ece"])
        self.assertEqual(2, score["macro_f1_supported_classes"])
        self.assertEqual(7, score["total_classes"])

    def test_no_classes_do_not_pretend_to_have_full_macro_support(self):
        score = classification_metrics([self.row(BRIXTON[0], BRIXTON[1])], list(BRIXTON))
        self.assertEqual(0.0, score["macro_f1"])
        self.assertEqual(1, score["macro_f1_supported_classes"])

    def test_model_scores_not_silently_renormalized(self):
        row = self.row(BRIXTON[0])
        row["scores"] = [0.2] * 7
        with self.assertRaisesRegex(ValueError, "normalized"):
            classification_metrics([row], list(BRIXTON))

    def test_empty_selective_result_has_null_metrics(self):
        row = self.row(BRIXTON[0])
        row["scores"] = [1 / 7] * 7
        score = selective_metrics([row], list(BRIXTON), 0.9)
        self.assertEqual(0, score["retained"])
        self.assertIsNone(score["accuracy_on_retained"])

    def test_sparse_groups_do_not_fabricate_ci(self):
        ci = cluster_interval([self.row(BRIXTON[0])], "source_group",
                              lambda sample: classification_metrics(sample, list(BRIXTON))["macro_f1"],
                              iterations=30)
        self.assertIsNone(ci["lower"])
        self.assertEqual(1, ci["clusters"])

    def write_csv(self, rows, labels=BRIXTON):
        folder = tempfile.TemporaryDirectory()
        path = Path(folder.name) / "predictions.csv"
        fields = ["sample_id", "source_group", "split", "truth", "domain", "sha256"] + [
            "p_" + label for label in labels
        ]
        with path.open("w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=fields)
            writer.writeheader()
            for row in rows:
                writer.writerow({**{k: row[k] for k in fields if not k.startswith("p_")},
                                 **{"p_" + label: row["scores"][i] for i, label in enumerate(labels)}})
        return folder, path

    def test_identical_payload_cannot_cross_split(self):
        x = self.row("CC", group="one", split="train")
        y = self.row("close", group="two", split="test")
        folder, path = self.write_csv([x, y])
        try:
            with self.assertRaisesRegex(ValueError, "leaks"):
                parse_csv(path, "brixton")
        finally:
            folder.cleanup()

    def test_group_cannot_cross_split_even_for_distinct_hash(self):
        x = self.row("CC", group="shared", split="train")
        y = self.row("close", group="shared", split="test")
        y["sha256"] = "b" * 64
        folder, path = self.write_csv([x, y])
        try:
            with self.assertRaisesRegex(ValueError, "group leakage"):
                parse_csv(path, "brixton")
        finally:
            folder.cleanup()

    def test_evaluator_discloses_separate_external_domain(self):
        a = self.row("CC", group="g1", split="test", domain="camera-A")
        b = self.row("close", group="g2", split="external", domain="camera-B")
        result = evaluate([a, b], "brixton", bootstraps=30)
        self.assertEqual(["external", "test"], sorted(result["results"]))
        self.assertIn("camera-B", result["results"]["external"]["by_domain"])

    def test_wmo_undeterminable_is_excluded_from_scores(self):
        labels = TASK_LABELS["wmo-cl"]
        good = {"sample_id": "1", "source_group": "a", "split": "test",
                "truth": "1", "domain": "camera-A", "sha256": "a" * 64,
                "scores": [0.0, 1.0] + [0.0] * 9}
        unknown = {**good, "sample_id": "2", "source_group": "b",
                   "truth": "/", "sha256": "b" * 64}
        result = evaluate([good, unknown], "wmo-cl", bootstraps=30)
        self.assertEqual(1, result["results"]["test"]["observer_undeterminable_excluded"])
        self.assertEqual(1, result["results"]["test"]["evaluable"])


if __name__ == "__main__":
    unittest.main()
