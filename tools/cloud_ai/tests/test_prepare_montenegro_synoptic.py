import importlib.util
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).resolve().parents[1] / "prepare_montenegro_synoptic.py"
spec = importlib.util.spec_from_file_location("prepare_montenegro_synoptic", MODULE_PATH)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class MontenegroPrepTests(unittest.TestCase):
    def test_chronological_split_keeps_latest_days_for_test(self):
        days = [f"2026-01-{i:02d}" for i in range(1, 21)]
        split = module.chronological_day_split(days)
        self.assertEqual("train", split[days[0]])
        self.assertEqual("validation", split[days[14]])
        self.assertEqual("test", split[days[-1]])

    def test_soft_targets_preserve_observer_disagreement(self):
        dist, counts = module.soft_distribution(["1", "1", "2", "/", ""])
        self.assertEqual({"1": 2, "2": 1, "/": 1}, counts)
        self.assertAlmostEqual(.5, dist["1"])
        self.assertAlmostEqual(.25, dist["2"])
        self.assertAlmostEqual(.25, dist["/"])

    def test_empty_targets_are_empty(self):
        self.assertEqual(({}, {}), module.soft_distribution(["", None]))

    def test_path_traversal_is_rejected(self):
        with self.assertRaises(ValueError):
            module.safe_image_name("../secret.jpg")

if __name__ == "__main__":
    unittest.main()
