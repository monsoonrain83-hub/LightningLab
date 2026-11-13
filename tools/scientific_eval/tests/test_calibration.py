"""Synthetic calibration tests; not claims about LightningLab model quality."""
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from calibration import fit_temperature, scale_probabilities, write_calibrated_test_rows
from classification import BRIXTON


class TemperatureCalibrationTests(unittest.TestCase):
    @staticmethod
    def sample(i: int, split: str = "validation", scores=None):
        labels = list(BRIXTON)
        return {
            "sample_id": str(i), "source_group": "group" + str(i // 10),
            "split": split, "truth": labels[i % 2], "domain": "synthetic",
            "sha256": str(i % 10) * 64,
            "scores": scores if scores is not None else [.98, .02, 0, 0, 0, 0, 0],
        }

    def test_temperature_preserves_ranking_and_normalization(self):
        probabilities = [.60, .30, .10, 0, 0, 0, 0]
        out = scale_probabilities(probabilities, 2.0)
        self.assertEqual(0, max(range(7), key=lambda i: out[i]))
        self.assertAlmostEqual(1.0, sum(out), places=10)
        self.assertGreater(out[1], probabilities[1])

    def test_invalid_temperature_rejected(self):
        with self.assertRaises(ValueError):
            scale_probabilities([1.0, 0.0], 0)
        with self.assertRaises(ValueError):
            scale_probabilities([0.8, 0.8], 1.0)

    def test_overconfident_validation_is_softened_without_test_peeking(self):
        rows = [self.sample(i) for i in range(40)]
        candidate = fit_temperature(rows, "brixton")
        self.assertGreater(candidate["temperature"], 1.0)
        self.assertLessEqual(candidate["validation_nll_after"], candidate["validation_nll_before"])

        # Arbitrarily flip an entire untouched test set; fitted temperature MUST
        # remain exactly the same.
        heldout = [self.sample(100 + i, "test", [0, 1, 0, 0, 0, 0, 0]) for i in range(20)]
        same = fit_temperature(rows + heldout, "brixton")
        self.assertEqual(candidate["temperature"], same["temperature"])

    def test_sparse_validation_refused(self):
        with self.assertRaisesRegex(ValueError, "Insufficient"):
            fit_temperature([self.sample(i) for i in range(10)], "brixton")

    def test_predictions_export_contains_only_untouched_test_and_external(self):
        rows = [self.sample(100, "test"), self.sample(200, "external"), self.sample(1, "validation")]
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "calibrated.csv"
            self.assertEqual(2, write_calibrated_test_rows(rows, "brixton", 1.5, file))
            body = file.read_text()
            self.assertIn("external", body)
            self.assertNotIn(",validation,", body)


if __name__ == "__main__":
    unittest.main()
