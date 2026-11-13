"""Synthetic interval cases for regression only; not operational detector measurements."""
import csv
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from events import interval_iou, score_events, parse, evaluate


class EventEvalTests(unittest.TestCase):
    @staticmethod
    def clip(vid, group, split="test", duration=3_600_000, truth=None, predictions=None, domain="A"):
        return {"video_id": vid, "source_group": group, "split": split,
                "duration_ms": duration, "sha256": ("a" if vid == "a" else "b") * 64,
                "domain": domain, "truth": truth or [], "predictions": predictions or []}

    @staticmethod
    def event(start, end, score=None, event_id="1"):
        return {"start_ms": start, "end_ms": end, "score": score, "event_id": event_id}

    def test_iou_with_real_time_intervals(self):
        self.assertAlmostEqual(1 / 3, interval_iou(self.event(0, 100), self.event(50, 150)))
        self.assertEqual(0.0, interval_iou(self.event(0, 100), self.event(100, 200)))

    def test_one_to_one_matching_for_duplicate_predictions(self):
        t = self.event(100, 200)
        p1 = self.event(100, 200, .95, "1")
        p2 = self.event(100, 200, .90, "2")
        result = score_events([self.clip("a", "g1", truth=[t], predictions=[p1, p2])], .5)
        self.assertEqual((1, 1, 0), (result["tp"], result["fp"], result["fn"]))
        self.assertEqual(1.0, result["recall"])

    def test_negative_video_increases_false_alarm_exposure(self):
        t = self.event(100, 200)
        c1 = self.clip("a", "g1", truth=[t], predictions=[self.event(100, 200, .90)])
        c2 = self.clip("b", "g2", predictions=[self.event(1000, 1100, .90)])
        result = score_events([c1, c2], .5)
        self.assertEqual(0.5, result["false_positives_per_hour"])
        self.assertEqual(1, result["negative_videos"])
        self.assertEqual(0.5, result["precision"])

    def test_no_ground_truth_and_no_predictions_reports_null_precision(self):
        result = score_events([self.clip("a", "g1")], .5)
        self.assertIsNone(result["precision"])
        self.assertIsNone(result["recall"])
        self.assertIsNone(result["f1"])
        self.assertEqual(0.0, result["false_positives_per_hour"])

    def test_external_domain_stays_separate(self):
        a = self.clip("a", "g1", split="test", truth=[self.event(100, 200)])
        b = self.clip("b", "g2", split="external", domain="new-camera")
        result = evaluate([a, b], bootstraps=30)
        self.assertEqual(1, result["results"]["external"]["negative_videos"])
        self.assertIn("new-camera", result["results"]["external"]["by_domain"])

    def make_tables(self, clips, truths, predictions):
        directory = tempfile.TemporaryDirectory()
        root = Path(directory.name)
        tables = {
            "clips": (["video_id", "source_group", "split", "domain", "duration_ms", "sha256"], clips),
            "truth": (["video_id", "event_id", "start_ms", "end_ms"], truths),
            "predictions": (["video_id", "event_id", "start_ms", "end_ms", "score"], predictions),
        }
        for stem, (columns, rows) in tables.items():
            with (root / (stem + ".csv")).open("w", newline="", encoding="utf-8") as stream:
                writer = csv.DictWriter(stream, fieldnames=columns)
                writer.writeheader()
                writer.writerows(rows)
        return directory, root

    def test_group_leakage_rejected(self):
        clips = [
            {"video_id": "a", "source_group": "same", "split": "train",
             "domain": "cam1", "duration_ms": "1000", "sha256": "a"*64},
            {"video_id": "b", "source_group": "same", "split": "test",
             "domain": "cam1", "duration_ms": "1000", "sha256": "b"*64},
        ]
        tmp, root = self.make_tables(clips, [], [])
        try:
            with self.assertRaisesRegex(ValueError, "leakage"):
                parse(root/"clips.csv", root/"truth.csv", root/"predictions.csv")
        finally:
            tmp.cleanup()

    def test_event_outside_source_window_rejected(self):
        clips = [{"video_id": "a", "source_group": "g1", "split": "test",
                  "domain": "cam1", "duration_ms": "1000", "sha256": "a"*64}]
        truth = [{"video_id": "a", "event_id": "1", "start_ms": "900", "end_ms": "1100"}]
        tmp, root = self.make_tables(clips, truth, [])
        try:
            with self.assertRaisesRegex(ValueError, "outside"):
                parse(root/"clips.csv", root/"truth.csv", root/"predictions.csv")
        finally:
            tmp.cleanup()

    def test_score_threshold_preregistered(self):
        truth = self.event(100, 200)
        prediction = self.event(100, 200, .6)
        clip = self.clip("a", "g1", truth=[truth], predictions=[prediction])
        self.assertEqual(1, score_events([clip], 0.5)["tp"])
        self.assertEqual(0, score_events([clip], 0.7)["tp"])
        with self.assertRaises(ValueError):
            score_events([clip], -0.1)


if __name__ == "__main__":
    unittest.main()
