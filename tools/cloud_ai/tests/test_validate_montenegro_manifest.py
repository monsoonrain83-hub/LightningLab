import csv
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from validate_montenegro_manifest import audit_manifest


def make_manifest(root, *, shared=False, corrupt=False, bad_soft=False):
    root.mkdir(exist_ok=True)
    folder = root / "images"
    folder.mkdir(exist_ok=True)
    rows = []
    for i, split in enumerate(["train", "validation", "test"]):
        data = b"identical" if shared else f"image{i}".encode()
        image = folder / f"sky_{i}.jpg"
        image.write_bytes(data)
        digest = hashlib.sha256(data).hexdigest()
        if corrupt and i == 2:
            digest = "0" * 64
        rows.append(dict(
            image_id=str(i), image_path=f"images/sky_{i}.jpg",
            split=split, sha256=digest,
            cl_soft_targets=json.dumps({"1": .6, "2": .4}) if not bad_soft else '{"1": 3}',
            cm_soft_targets=json.dumps({"0": 1}),
            ch_soft_targets=json.dumps({"/": 1}),
        ))
    path = root / "manifest.csv"
    with path.open("w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0]))
        w.writeheader()
        w.writerows(rows)
    return path


class CloudDataIntegrityTests(unittest.TestCase):
    def test_valid_dataset(self):
        with tempfile.TemporaryDirectory() as td:
            report = audit_manifest(make_manifest(Path(td)))
            self.assertEqual(report["images"], 3)
            self.assertFalse(report["site_generalization_validated"])

    def test_byte_duplicate_leakage_blocked(self):
        with tempfile.TemporaryDirectory() as td:
            with self.assertRaisesRegex(ValueError, "DATA LEAKAGE"):
                audit_manifest(make_manifest(Path(td), shared=True))

    def test_corrupt_image_hash_blocked(self):
        with tempfile.TemporaryDirectory() as td:
            with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
                audit_manifest(make_manifest(Path(td), corrupt=True))

    def test_invalid_soft_target_probability_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            with self.assertRaisesRegex(ValueError, "probability"):
                audit_manifest(make_manifest(Path(td), bad_soft=True))

    def test_path_escape_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            path = make_manifest(Path(td))
            rows = list(csv.DictReader(path.open()))
            rows[0]["image_path"] = "../outside.jpg"
            with path.open("w", newline="") as f:
                w = csv.DictWriter(f, fieldnames=list(rows[0]))
                w.writeheader()
                w.writerows(rows)
            with self.assertRaisesRegex(ValueError, "unsafe image_path"):
                audit_manifest(path)

    def test_legacy_without_sha_is_audited(self):
        with tempfile.TemporaryDirectory() as td:
            path = make_manifest(Path(td))
            rows = list(csv.DictReader(path.open()))
            for row in rows:
                row.pop("sha256")
            with path.open("w", newline="") as f:
                w = csv.DictWriter(f, fieldnames=list(rows[0]))
                w.writeheader()
                w.writerows(rows)
            self.assertEqual(audit_manifest(path)["unique_sha256"], 3)


if __name__ == "__main__":
    unittest.main()
