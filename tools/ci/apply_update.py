"""Validate and apply source-only update.zip on a trusted main checkout.

Do not execute code from the archive here. Preserve repository governance files
and workflows. Refuse ambiguous roots, traversal, symlinks and local secrets.
"""
from pathlib import Path, PurePosixPath
import argparse, shutil, stat, tempfile, zipfile

ROOT_FILES={"build.gradle.kts","settings.gradle.kts","gradle.properties","gradlew","gradlew.bat","build.gradle","settings.gradle"}
ROOT_DIRS={"app","tools","gradle","docs"}
PROTECTED={"docs/ARCHITECTURE.md","docs/DATA_AND_LICENSING.md"}
DENIED={"local.properties","key.properties","keystore.properties",".env",".git"}
MAX_BYTES=512*1024*1024
MAX_FILES=20000

def apply(archive, dest):
    dest=Path(dest).resolve()
    with zipfile.ZipFile(archive) as z:
        infos=z.infolist()
        if len(infos)>MAX_FILES or sum(i.file_size for i in infos)>MAX_BYTES:
            raise ValueError("Archive exceeds limits")
        entries={}
        for i in infos:
            name=i.filename
            p=PurePosixPath(name)
            if not name or name.startswith("/") or "\\" in name or ":" in name or ".." in p.parts or ".git" in p.parts or stat.S_ISLNK(i.external_attr>>16):
                raise ValueError("Unsafe archive entry")
            if i.is_dir():continue
            if p in entries:raise ValueError("Duplicate archive entry")
            entries[p]=i
        roots={p.parent for p in entries if p.name in {"settings.gradle.kts","settings.gradle"} and p.parent/"app"/"build.gradle.kts" in entries}
        if len(roots)!=1:raise ValueError("Expected one Android project source root")
        root=roots.pop()
        selected=[]
        for p,i in entries.items():
            if not p.is_relative_to(root):continue
            rel=p.relative_to(root)
            if not rel.parts:continue
            if rel.parts[0] not in ROOT_DIRS and str(rel) not in ROOT_FILES:continue
            if str(rel) in PROTECTED or rel.parts[:2]==("tools","ci"):continue
            if any(v in DENIED or v.startswith(".env.") for v in rel.parts) or rel.suffix.lower() in {".jks",".keystore",".p12",".pem",".key"}:
                raise ValueError("Potential local credential in source archive")
            if any(v in {".gradle","build","__pycache__"} for v in rel.parts):continue
            target=dest.joinpath(*rel.parts)
            if not target.resolve().is_relative_to(dest) or any(x.is_symlink() for x in [target,*target.parents] if x!=dest.parent):
                raise ValueError("Unsafe destination")
            selected.append((i,rel))
        if not selected:raise ValueError("No source files")
        # Read and CRC-check every selected file before modifying the checkout.
        with tempfile.TemporaryDirectory() as td:
            stage=Path(td)
            for i,rel in selected:
                target=stage.joinpath(*rel.parts);target.parent.mkdir(parents=True,exist_ok=True)
                with z.open(i) as src,target.open("wb") as out:shutil.copyfileobj(src,out)
                if i.external_attr>>16 & 0o111:target.chmod(0o755)
            for _,rel in selected:
                target=dest.joinpath(*rel.parts);target.parent.mkdir(parents=True,exist_ok=True)
                shutil.copy2(stage.joinpath(*rel.parts),target)
    return len(selected)

if __name__=="__main__":
    p=argparse.ArgumentParser();p.add_argument("archive");p.add_argument("destination")
    a=p.parse_args();print("Validated source files applied:",apply(a.archive,a.destination))
