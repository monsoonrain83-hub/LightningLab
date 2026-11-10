# Daylight lightning registration fix

The new JVM tests exposed repeated false vertical structures during small handheld jitter. The previous global NCC tracker used absolute gradients, which discarded edge polarity and made the synthetic block/line texture ambiguous. Registration now correlates signed half-resolution luma (still normalized NCC, ±5 half-resolution search and subpixel peak interpolation) before the existing temporal min/negative-pair suppression. This is a targeted algorithm change; the test suite must confirm both no-bolt rejection and thin-bolt retention before release.
