"""Tests aligned per-event sampling without requiring TensorFlow or video assets."""
import csv
import hashlib
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from model_contract import event_window_sample_times_ms
from train_brixton import attach_expert_event_windows


class ExpertTemporalSamplingTests(unittest.TestCase):
    def test_exact_android_time_schedule(self):
        self.assertEqual([500, 714, 928, 1142, 1357, 1571, 1785, 2000],
                         event_window_sample_times_ms(1000, 1500, 3000))

    def test_context_is_clamped_to_video(self):
        self.assertEqual(0, event_window_sample_times_ms(100, 150, 1000)[0])
        self.assertEqual(1000, event_window_sample_times_ms(900, 950, 1000)[-1])
        self.assertEqual(8, len(event_window_sample_times_ms(300, 300, 300)))

    def test_invalid_intervals_never_silently_reordered(self):
        with self.assertRaises(ValueError):
            event_window_sample_times_ms(500, 100, 1000)

    def rows(self):
        return [{"relative_path": "a.mp4", "sha256": "a" * 64},
                {"relative_path": "b.mp4", "sha256": "b" * 64}]

    def write_annotations(self, directory, entries):
        p = Path(directory) / "events.csv"
        with p.open("w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=["relative_path", "sha256", "start_ms", "end_ms", "reviewer"])
            writer.writeheader()
            writer.writerows(entries)
        return p

    def test_all_clips_require_expert_sha_verified_windows(self):
        with tempfile.TemporaryDirectory() as root:
            entries = [
                {"relative_path": "a.mp4", "sha256": "a" * 64, "start_ms": 200,
                 "end_ms": 400, "reviewer": "observer1"},
                {"relative_path": "b.mp4", "sha256": "b" * 64, "start_ms": 500,
                 "end_ms": 550, "reviewer": "observer2"},
            ]
            rows = self.rows()
            attach_expert_event_windows(rows, self.write_annotations(root, entries))
            self.assertEqual((200, 400), rows[0]["_event_window"])
            self.assertEqual((500, 550), rows[1]["_event_window"])

            incomplete = self.write_annotations(root, entries[:1])
            with self.assertRaisesRegex(ValueError, "exactly"):
                attach_expert_event_windows(self.rows(), incomplete)

    def test_annotation_hash_mismatch_blocks_training(self):
        with tempfile.TemporaryDirectory() as root:
            entries = [
                {"relative_path": "a.mp4", "sha256": "c" * 64, "start_ms": 200,
                 "end_ms": 400, "reviewer": "observer1"},
                {"relative_path": "b.mp4", "sha256": "b" * 64, "start_ms": 500,
                 "end_ms": 550, "reviewer": "observer2"},
            ]
            with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
                attach_expert_event_windows(self.rows(), self.write_annotations(root, entries))


if __name__ == "__main__":
    unittest.main()
