import importlib.util
from pathlib import Path
import tempfile,unittest,zipfile,stat
spec=importlib.util.spec_from_file_location("importer",Path(__file__).with_name("apply_update.py"))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class ImportTest(unittest.TestCase):
 def build(self, folder, extras):
    archive=folder/"update.zip"
    with zipfile.ZipFile(archive,"w") as z:
        z.writestr("project/settings.gradle.kts","settings")
        z.writestr("project/app/build.gradle.kts","app")
        for k,v in extras:z.writestr(k,v)
    return archive
 def test_preserves_policy_and_applies_code(self):
    with tempfile.TemporaryDirectory() as t:
        d=Path(t);dest=d/"dest";dest.mkdir()
        (dest/"LICENSE").write_text("keep")
        p=self.build(d,[("project/LICENSE","replace"),("project/.github/workflows/a.yml","bad"),("project/app/src/main/test.txt","ok"),("project/tools/ci/apply_update.py","bad")])
        self.assertEqual(m.apply(p,dest),3)
        self.assertEqual((dest/"LICENSE").read_text(),"keep")
        self.assertFalse((dest/".github").exists())
        self.assertFalse((dest/"tools").exists())
        self.assertEqual((dest/"app/src/main/test.txt").read_text(),"ok")
 def test_traversal_and_key_rejected_before_writes(self):
    for name in ["../bad","project/app/../bad","project/.git/config","project/app/x.jks","project/tools/.env"]:
        with tempfile.TemporaryDirectory() as t:
            d=Path(t);dest=d/"dest";dest.mkdir()
            with self.assertRaises(ValueError):m.apply(self.build(d,[(name,"bad")]),dest)
            self.assertEqual(list(dest.iterdir()),[])
 def test_symlink_rejected(self):
    with tempfile.TemporaryDirectory() as t:
        d=Path(t);dest=d/"dest";dest.mkdir()
        p=self.build(d,[])
        i=zipfile.ZipInfo("project/app/link");i.create_system=3;i.external_attr=(stat.S_IFLNK|0o777)<<16
        with zipfile.ZipFile(p,"a") as z:z.writestr(i,"/tmp")
        with self.assertRaises(ValueError):m.apply(p,dest)
if __name__=="__main__":unittest.main()
