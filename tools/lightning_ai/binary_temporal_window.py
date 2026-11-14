#!/usr/bin/env python3
"""Single event-centered 8-frame sampling contract shared with Android.

The verifier is trained on detector-centered candidate windows, NOT arbitrary
eight frames spanning an entire source clip. start_ms/end_ms are relative to
the original clip. The identical integer time rule is used by
LightningTemporalModel.sampleTimes on Android.
"""


def event_sample_times(start_ms: int, end_ms: int, duration_ms: int) -> list[int]:
    for name, value in (("start_ms", start_ms), ("end_ms", end_ms),
                        ("duration_ms", duration_ms)):
        if type(value) is not int or value < 0:
            raise ValueError(f"{name} must be a non-negative integer")
    if end_ms < start_ms or end_ms > duration_ms:
        raise ValueError("candidate event must be within video duration")
    begin = max(0, start_ms - 500)
    end = min(duration_ms, end_ms + 500)
    return [begin + (end - begin) * i // 7 for i in range(8)]
