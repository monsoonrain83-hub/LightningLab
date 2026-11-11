import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from model_contract import LABELS, decode_probabilities, model_metadata, validate_manifest_rows


class LightningModelContractTests(unittest.TestCase):
    def test_contract_metadata_has_fixed_temporal_input_and_seven_labels(self):
        metadata = model_metadata()
        self.assertEqual(7, len(metadata["labels"]))
        self.assertEqual([1, 8, 224, 224, 3], metadata["input_shape"])
        self.assertEqual([1, 7], metadata["output_shape"])
        self.assertEqual("CC BY 4.0", metadata["dataset_license"])

    def test_manifest_rejects_event_group_split_leakage(self):
        rows = [
            {"label": LABELS[0], "split": "train", "event_group": "same-flash"},
            {"label": LABELS[1], "split": "test", "event_group": "same-flash"},
        ]
        with self.assertRaises(ValueError):
            validate_manifest_rows(rows)

    def test_manifest_counts_valid_partitions(self):
        rows = [
            {"label": LABELS[0], "split": "train", "event_group": "a"},
            {"label": LABELS[1], "split": "validation", "event_group": "b"},
            {"label": LABELS[2], "split": "test", "event_group": "c"},
        ]
        total, counts = validate_manifest_rows(rows)
        self.assertEqual(3, total)
        self.assertEqual({"train": 1, "validation": 1, "test": 1}, counts)

    def test_probabilities_are_normalized_and_ranked(self):
        decoded = decode_probabilities([1, 2, 3, 4, 5, 6, 7])
        self.assertEqual("CC", decoded[0][0])
        self.assertAlmostEqual(1.0, sum(score for _, score in decoded), places=6)

    def test_malformed_probabilities_are_rejected(self):
        self.assertIsNone(decode_probabilities([1, 2]))
        self.assertIsNone(decode_probabilities([1, 2, 3, 4, 5, 6, float("nan")]))
        self.assertIsNone(decode_probabilities([0] * 7))


if __name__ == "__main__":
    unittest.main()
