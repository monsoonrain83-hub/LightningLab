import csv
import hashlib
import tempfile
import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from binary_verifier_manifest import load_manifest


def dataset(folder):
    root = Path(folder)
    rows = []
    for split in ("train", "validation", "test"):
        for label in ("lightning", "non_lightning"):
            name = f"{split}_{label}.mp4"
            path = root / name
            data = name.encode()
            path.write_bytes(data)
            rows.append(dict(relative_path=name, sha256=hashlib.sha256(data).hexdigest(),
                             split=split, group_id=name, label=label))
    manifest = root / "binary.csv"
    def write():
        with manifest.open("w", newline="") as out:
            writer = csv.DictWriter(out, fieldnames=list(rows[0]))
            writer.writeheader()
            writer.writerows(rows)
    write()
    return root, manifest, rows, write


class BinaryVerifierManifestTests(unittest.TestCase):
    def test_valid_holdout_has_both_labels_per_split(self):
        with tempfile.TemporaryDirectory() as td:
            root, path, _, _ = dataset(td)
            rows, stats = load_manifest(path, root)
            self.assertEqual(len(rows), 6)
            self.assertEqual(stats["counts"]["test/lightning"], 1)

    def test_rejects_cross_partition_group(self):
        with tempfile.TemporaryDirectory() as td:
            root, path, rows, write = dataset(td)
            rows[-1]["group_id"] = rows[0]["group_id"]
            write()
            with self.assertRaisesRegex(ValueError, "DATA LEAKAGE"):
                load_manifest(path, root)

    def test_rejects_content_mutation(self):
        with tempfile.TemporaryDirectory() as td:
            root, path, rows, write = dataset(td)
            (root / rows[0]["relative_path"]).write_bytes(b"replaced")
            with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
                load_manifest(path, root)

    def test_rejects_missing_negative_label(self):
        with tempfile.TemporaryDirectory() as td:
            root, path, rows, write = dataset(td)
            rows[:] = [r for r in rows if not
                       (r["label"] == "non_lightning" and r["split"] == "test")]
            write()
            with self.assertRaisesRegex(ValueError, "lacks non_lightning"):
                load_manifest(path, root)

    def test_rejects_path_traversal(self):
        with tempfile.TemporaryDirectory() as td:
            root, path, rows, write = dataset(td)
            rows[0]["relative_path"] = "../outside.mp4"
            write()
            with self.assertRaisesRegex(ValueError, "unsafe/missing"):
                load_manifest(path, root)


if __name__ == "__main__":
    unittest.main()
