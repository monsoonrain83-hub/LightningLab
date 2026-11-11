# r10.60 — Lightning video dataset pipeline

- Added a reproducible manifest builder for the published Brixton labelled MP4 collection.
- Parses seven documented labels and camera IDs from filenames.
- Hashes clips, groups same-timestamp camera views, joins byte-identical videos, and creates chronological train/validation/test splits.
- Adds summary counts and Python unit tests; CI now tests/compiles the lightning data utilities.
- Dataset files are not vendored; CC BY 4.0 attribution is documented.
- This is data preparation, not a trained Android classifier. Single-site and camera limitations remain explicit.
