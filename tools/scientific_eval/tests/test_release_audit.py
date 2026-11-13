"""Synthetic release-gate fixtures only; never publish them as scientific measurements."""
import hashlib
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from classification import BRIXTON
from release_audit import audit


class ScientificReleaseAuditTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name) / "candidate.tflite"
        self.path.write_bytes(b"synthetic test bytes - not an actual model")
        self.metadata = {
            "tflite_sha256": hashlib.sha256(self.path.read_bytes()).hexdigest(),
            "manifest_sha256": "b"*64, "dataset_license": "CC BY 4.0",
            "training_clips": 1000, "test_clips": 200,
            "labels": list(BRIXTON), "input_shape": [1, 8, 224, 224, 3],
            "output_shape": [1, 7],
        }
        per_class = {v: {"support": 20} for v in BRIXTON}
        self.evaluation = {
            "schema": "lightninglab-scientific-classification-v1",
            "task": "brixton", "labels": list(BRIXTON), "fingerprint_sha256": "a"*64,
            "results": {"external": {
                "evaluable": 140, "by_domain": {"A": {}, "B": {}, "C": {}},
                "metrics": {"per_class": per_class, "macro_f1_supported_classes": 7,
                            "macro_f1": 0.90, "ece": 0.08}
            }}
        }

    def tearDown(self):
        self.temp.cleanup()

    def test_candidate_never_means_automatically_operational(self):
        result = audit(self.path, self.metadata, self.evaluation, "brixton")
        self.assertEqual("candidate_for_expert_review", result["status"])
        self.assertIn("human review", result["caveat"])

    def test_corrupt_weights_block_release(self):
        self.path.write_bytes(b"corrupted")
        result = audit(self.path, self.metadata, self.evaluation, "brixton")
        self.assertEqual("blocked", result["status"])
        self.assertTrue(any("SHA-256" in item for item in result["reasons"]))

    def test_no_external_validation_blocks(self):
        self.evaluation["results"].pop("external")
        result = audit(self.path, self.metadata, self.evaluation, "brixton")
        self.assertEqual("blocked", result["status"])
        self.assertIn("No independent external test report", result["reasons"])

    def test_sparse_domains_or_classes_block(self):
        ext = self.evaluation["results"]["external"]
        ext["by_domain"] = {"same-station": {}}
        ext["metrics"]["per_class"]["CC"]["support"] = 0
        result = audit(self.path, self.metadata, self.evaluation, "brixton")
        self.assertEqual("blocked", result["status"])
        self.assertTrue(any("External evidence too sparse" in x for x in result["reasons"]))
        self.assertTrue(any("external domains" in x for x in result["reasons"]))

    def test_tensor_label_order_mismatch_blocks(self):
        self.metadata["labels"] = list(reversed(BRIXTON))
        result = audit(self.path, self.metadata, self.evaluation, "brixton")
        self.assertEqual("blocked", result["status"])
        self.assertTrue(any("label order" in x for x in result["reasons"]))


if __name__ == "__main__":
    unittest.main()
