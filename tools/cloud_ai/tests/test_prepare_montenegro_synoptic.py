import csv
import sys
import importlib.util
import tempfile
import unittest
import zipfile
from pathlib import Path

MODULE_PATH = Path(__file__).resolve().parents[1] / "prepare_montenegro_synoptic.py"
sys.path.insert(0, str(MODULE_PATH.parent))
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


    def test_prepare_writes_soft_labels_and_chronological_manifest(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "record"
            out = Path(tmp) / "out"
            root.mkdir()
            items = []
            annotations = []
            with zipfile.ZipFile(root / "images.zip", "w") as archive:
                for index in range(1, 7):
                    name = f"sky_{index}.jpg"
                    items.append({"id": str(index), "name": name,
                                  "item_received": f"2026-10-{index:02d} 12:00:00"})
                    archive.writestr("images/" + name, f"synthetic-image-{index}".encode())
                    for observer, label in (("A", "1"), ("B", "2")):
                        annotations.append({
                            "annotation_item_id": str(index), "user_id": observer,
                            "CL - Low cloud type": label,
                            "CM - Middle cloud type": "0",
                            "CH - High cloud type": "/",
                        })
            for filename, rows, fields in (
                ("annotation_items.csv", items, ["id", "name", "item_received"]),
                ("annotations.csv", annotations, ["annotation_item_id", "user_id",
                    "CL - Low cloud type", "CM - Middle cloud type", "CH - High cloud type"]),
            ):
                with (root / filename).open("w", newline="", encoding="utf-8") as f:
                    writer = csv.DictWriter(f, fieldnames=fields)
                    writer.writeheader()
                    writer.writerows(rows)
            (root / "LICENSE.txt").write_text("CC BY 4.0", encoding="utf-8")
            report = module.prepare(root, out)
            with (out / "manifest.csv").open(encoding="utf-8", newline="") as f:
                rows = list(csv.DictReader(f))
            self.assertEqual(6, report["images"])
            self.assertEqual(64, len(rows[0]["sha256"]))
            self.assertEqual(6, report["integrity_audit"]["unique_sha256"])
            self.assertEqual(2, int(rows[0]["annotator_count"]))
            self.assertEqual({"1": 0.5, "2": 0.5},
                             __import__("json").loads(rows[0]["cl_soft_targets"]))
            self.assertEqual("train", rows[0]["split"])
            self.assertEqual("test", rows[-1]["split"])
            self.assertTrue((out / "images" / "sky_1.jpg").is_file())
            self.assertTrue((out / "ATTRIBUTION.txt").is_file())

    def test_path_traversal_is_rejected(self):
        with self.assertRaises(ValueError):
            module.safe_image_name("../secret.jpg")

if __name__ == "__main__":
    unittest.main()
