"""Synthetic files/mocked ffprobe only; no actual media verification in CI."""
import csv
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch, Mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from prepare_reference_pack import create_pack, probe_duration_ms
from events import parse


class ReferencePackTests(unittest.TestCase):
    def test_real_file_bytes_are_hashed_but_splits_are_not_invented(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder) / "videos"
            root.mkdir()
            video = root / "storm.mp4"
            video.write_bytes(b"synthetic bytes; mocked duration")
            with patch("prepare_reference_pack.probe_duration_ms", return_value=8000):
                count = create_pack(root, Path(folder) / "out")
            self.assertEqual(1, count)
            csv_path = Path(folder) / "out" / "clips_REVIEW_BEFORE_EVALUATION.csv"
            with csv_path.open(encoding="utf-8") as file:
                row = list(csv.DictReader(file))[0]
            self.assertEqual("UNASSIGNED", row["split"])
            self.assertEqual("UNVERIFIED_GROUP", row["source_group"])
            self.assertEqual("8000", row["duration_ms"])
            self.assertEqual(64, len(row["sha256"]))
            with self.assertRaisesRegex(ValueError, "invalid video metadata"):
                parse(csv_path, Path(folder)/"out"/"expert_intervals.csv",
                      Path(folder)/"out"/"detector_intervals.csv")

    def test_probe_fails_when_ffprobe_has_no_duration(self):
        with patch("prepare_reference_pack.subprocess.run",
                   return_value=Mock(stdout=json.dumps({"format": {}}))):
            with self.assertRaisesRegex(ValueError, "trustworthy"):
                probe_duration_ms(Path("/dummy.mp4"))

    def test_does_not_treat_symlink_as_independent_clip(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder) / "videos"
            root.mkdir()
            (root/"clip.mp4").write_bytes(b"clip")
            (root/"copy.mp4").symlink_to(root/"clip.mp4")
            with patch("prepare_reference_pack.probe_duration_ms", return_value=1000):
                self.assertEqual(1, create_pack(root, Path(folder)/"out"))


if __name__ == "__main__":
    unittest.main()
