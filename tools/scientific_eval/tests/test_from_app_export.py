"""Export conversion checks; human review is NOT missed-flash ground truth."""
import csv
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from from_app_export import transform, convert
from events import parse, score_events


class AppEvidenceConverterTests(unittest.TestCase):
    def evidence(self, rows):
        return {"schema_version": 3, "source": "visual_heuristic",
                "events": rows, "scientific_caveat": "scores uncalibrated"}

    def test_uses_raw_nonprobability_score_and_not_review_as_truth(self):
        exported = self.evidence([{"id": 12, "start_ms": 700, "end_ms": 750,
                                   "algorithm_score": 3.75, "human_review": "CONFIRMED"}])
        rows = transform(exported, "camera-1")
        self.assertEqual("3.75", rows[0]["score"])
        self.assertEqual(["video_id", "event_id", "start_ms", "end_ms", "score"],
                         list(rows[0].keys()))
        self.assertNotIn("human_review", rows[0])

    def test_zero_duration_requires_explicit_bookkeeping_interval(self):
        exported = self.evidence([{"id": 1, "start_ms": 10, "end_ms": 10,
                                   "algorithm_score": 0.1}])
        self.assertEqual(11, transform(exported, "camera-1")[0]["end_ms"])

    def test_invalid_scores_not_capped_to_one(self):
        exported = self.evidence([{"id": 1, "start_ms": 0, "end_ms": 12,
                                   "algorithm_score": -1.0}])
        with self.assertRaisesRegex(ValueError, "ranking score"):
            transform(exported, "camera-1")

    def test_full_converter_evaluates_with_an_independent_truth_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root/"evidence.json").write_text(json.dumps(self.evidence([
                {"id": 2, "start_ms": 100, "end_ms": 200, "algorithm_score": 7.0}
            ])))
            self.assertEqual(1, convert(root/"evidence.json", "clip-one", root/"predicted.csv"))
            (root/"clips.csv").write_text(
                "video_id,source_group,split,domain,duration_ms,sha256\n"
                "clip-one,g1,test,station,1000," + "a"*64 + "\n", encoding="utf-8")
            (root/"truth.csv").write_text(
                "video_id,event_id,start_ms,end_ms\nclip-one,expert1,100,200\n", encoding="utf-8")
            clips = parse(root/"clips.csv", root/"truth.csv", root/"predicted.csv")
            result = score_events(clips, 1.0)
            self.assertEqual(1, result["tp"])
            self.assertEqual(1.0, result["recall"])


if __name__ == "__main__":
    unittest.main()
