#!/usr/bin/env python3
"""Sanity checks for the bundled AtmosphericKnowledge.db."""
import sqlite3
from pathlib import Path

DB = Path(__file__).parents[0] / "app/src/main/assets/knowledge/AtmosphericKnowledge.db"
con = sqlite3.connect(DB)
assert con.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
assert con.execute("SELECT COUNT(*) FROM cloud_genera").fetchone()[0] == 10
assert con.execute("SELECT COUNT(*) FROM phenomena").fetchone()[0] >= 20
assert con.execute("SELECT COUNT(*) FROM cloud_species").fetchone()[0] >= 15
assert con.execute("SELECT COUNT(*) FROM cloud_varieties").fetchone()[0] == 9
assert con.execute("SELECT COUNT(*) FROM cloud_features").fetchone()[0] == 11
assert con.execute("SELECT COUNT(*) FROM cloud_accessory_clouds").fetchone()[0] == 4
assert con.execute("SELECT genus_codes FROM cloud_species WHERE code='volutus'").fetchone()[0] == "Ac,Sc"
assert con.execute("SELECT genus_codes FROM cloud_varieties WHERE code='intortus'").fetchone()[0] == "Ci"
assert con.execute("SELECT COUNT(*) FROM cloud_varieties WHERE code='cavum'").fetchone()[0] == 0
assert con.execute("SELECT COUNT(*) FROM reference_images").fetchone()[0] >= 20
assert con.execute("SELECT COUNT(*) FROM sources").fetchone()[0] >= 4
assert con.execute("SELECT COUNT(*) FROM cloud_genera g JOIN phenomena p ON p.id=g.phenomenon_id WHERE p.code='Cb'").fetchone()[0] == 1
assert con.execute("SELECT COUNT(*) FROM cloud_species WHERE (',' || genus_codes || ',') LIKE '%,Cu,%'").fetchone()[0] >= 3
assert con.execute("SELECT COUNT(*) FROM reference_images WHERE verified=1").fetchone()[0] == 0
print("AtmosphericKnowledge.db checks passed.")
