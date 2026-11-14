import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from binary_temporal_window import event_sample_times, phone_like_dimensions


class BinaryTemporalWindowTests(unittest.TestCase):
    def test_android_aspect_preserving_pre_resize(self):
        self.assertEqual(phone_like_dimensions(1920, 1080), (224, 126))
        self.assertEqual(phone_like_dimensions(1080, 1920), (126, 224))
        self.assertEqual(phone_like_dimensions(150, 100), (150, 100))

    def test_invalid_pre_resize_dimensions(self):
        with self.assertRaises(ValueError):
            phone_like_dimensions(0, 1080)
        with self.assertRaises(ValueError):
            phone_like_dimensions(1080, -1)

    def test_matches_android_central_event(self):
        self.assertEqual(event_sample_times(1000, 1200, 2500),
                         [500, 671, 842, 1014, 1185, 1357, 1528, 1700])

    def test_clamps_to_video_boundaries(self):
        samples = event_sample_times(0, 100, 200)
        self.assertEqual(samples[0], 0)
        self.assertEqual(samples[-1], 200)
        self.assertEqual(len(samples), 8)

    def test_equal_timestamps_for_short_video_are_allowed(self):
        times = event_sample_times(0, 0, 0)
        self.assertEqual(times, [0] * 8)

    def test_no_overflow_of_large_temporal_windows(self):
        times = event_sample_times(2**63 - 150, 2**63 - 120, 2**63 - 1)
        self.assertTrue(all(0 <= x <= 2**63 - 1 for x in times))
        self.assertEqual(times[-1], 2**63 - 1)

    def test_out_of_bounds_event_is_rejected(self):
        with self.assertRaises(ValueError):
            event_sample_times(1200, 1800, 1500)

    def test_reversed_event_is_rejected(self):
        with self.assertRaises(ValueError):
            event_sample_times(200, 100, 1000)

    def test_negative_and_boolean_timestamps_rejected(self):
        with self.assertRaises(ValueError):
            event_sample_times(-1, 2, 500)
        with self.assertRaises(ValueError):
            event_sample_times(True, 2, 500)


if __name__ == "__main__":
    unittest.main()
