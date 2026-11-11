# Lightning video training data

## Brixton tower labelled video dataset

- DOI: https://doi.org/10.17632/44vkdvrn67.1
- Repository: https://data.mendeley.com/datasets/44vkdvrn67/1
- Published article: Hunt et al., *Data in Brief* 30 (2020), 105630.
- License: CC BY 4.0 (cite the dataset/article and retain attribution).
- Published collection: 3,623 H.264 MP4 clips, 640×360, about 800 MB, average 7.25 fps.
- Labels: `towerupward`, `towerdownward`, `close`, `far`, `unclear`, `unclearbehind`, `CC`.

The collection is valuable labelled evidence, but it is a single-site dataset around one tower and its camera views. It does not represent all lightning types, all regions, all cameras, or high-speed leader/return-stroke dynamics. It must not be treated as a universal lightning taxonomy.

## Prepare a manifest

Download and extract the dataset separately (the video files are not committed to this repository), then run:

```bash
python3 tools/lightning_ai/prepare_brixton.py \
  --root /path/to/extracted/brixton \
  --out data/lightning/brixton_manifest.csv
```

The script:
- parses only documented labels and camera IDs from filenames;
- hashes every accepted clip and records file size;
- keeps all camera views sharing a timestamp in one event group;
- joins byte-identical clips across timestamps to avoid split leakage;
- creates deterministic chronological train/validation/test partitions;
- writes a CSV manifest and a JSON summary including skipped files and class counts.

No model weights are included at this stage. A trained classifier must be validated on independent cameras/sites before any general-purpose claim.
