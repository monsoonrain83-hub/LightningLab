import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from prepare_brixton import build_manifest, chronological_splits, parse_filename, write_manifest


class BrixtonManifestTests(unittest.TestCase):
    def test_parses_documented_labels_and_camera(self):
        parsed = parse_filename(Path("2015-11-18_17_38_04_CC_Cam2.mp4"))
        self.assertIsNotNone(parsed)
        self.assertEqual("CC", parsed["label"])
        self.assertEqual(2, parsed["camera"])
        self.assertEqual("2015-11-18T17:38:04", parsed["timestamp"].isoformat())

    def test_rejects_unlabelled_and_unknown_files(self):
        self.assertIsNone(parse_filename(Path("2015-11-18_17_38_04_unknown_Cam2.mp4")))
        self.assertIsNone(parse_filename(Path("random.mp4")))

    def test_chronological_split_has_three_partitions_when_possible(self):
        from datetime import datetime, timedelta
        start = datetime(2015, 10, 1)
        mapping = chronological_splits([start + timedelta(days=i) for i in range(10)])
        self.assertEqual("train", mapping[start])
        self.assertEqual("validation", mapping[start + timedelta(days=7)])
        self.assertEqual("test", mapping[start + timedelta(days=9)])

    def test_same_timestamp_and_duplicate_payload_never_cross_splits(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            files = {
                "2015-10-01_10_00_00_CC_Cam1.mp4": b"same-event",
                "2015-10-01_10_00_00_CC_Cam2.mp4": b"camera-two",
                "2015-10-02_10_00_00_close_Cam1.mp4": b"duplicate-event",
                "2015-10-03_10_00_00_far_Cam1.mp4": b"duplicate-event",
                "2015-10-04_10_00_00_unclear_Cam1.mp4": b"last-event",
            }
            for name, content in files.items():
                (root / name).write_bytes(content)
            rows, skipped = build_manifest(root)
            self.assertEqual([], skipped)
            by_name = {row["relative_path"]: row for row in rows}
            self.assertEqual(by_name["2015-10-01_10_00_00_CC_Cam1.mp4"]["split"],
                             by_name["2015-10-01_10_00_00_CC_Cam2.mp4"]["split"])
            self.assertEqual(by_name["2015-10-02_10_00_00_close_Cam1.mp4"]["split"],
                             by_name["2015-10-03_10_00_00_far_Cam1.mp4"]["split"])
            self.assertEqual(4, len({row["event_group"] for row in rows}))

    def test_writes_manifest_and_skipped_file_summary(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "data"
            root.mkdir()
            (root / "2015-10-01_10_00_00_towerupward_Cam1.mp4").write_bytes(b"video")
            (root / "unlabelled.mp4").write_bytes(b"skip")
            out = Path(tmp) / "out" / "manifest.csv"
            summary = write_manifest(root, out)
            self.assertTrue(out.is_file())
            self.assertTrue(out.with_suffix(".summary.json").is_file())
            self.assertEqual(1, summary["clips"])
            self.assertEqual(1, summary["skipped_mp4"])


if __name__ == "__main__":
    unittest.main()
