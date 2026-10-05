#!/usr/bin/env python3
"""Pack every sample set in sample-sets/ into <id>.zip beside it.

The zips are what the app's file picker installs from (sample-sets/README.md),
so they are committed. This writes the same bytes every time it is run on the
same folders — fixed timestamps, sorted entries, no host metadata — so a zip
shows up in `git status` only when its set has changed, and
`SampleArchivesTest` in dicesets/install can hold each zip to its folder.

    python3 sample-sets/build-archives.py

Needs nothing but Python.
"""
import sys
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent

# A fixed date, because zip stores one per entry and "now" would change the
# bytes on every run. 1980-01-01 is the earliest zip can say.
STAMP = (1980, 1, 1, 0, 0, 0)

# Owner read/write, everyone read, as a regular file: what an extractor
# expects, and nothing that says anything about this machine.
MODE = 0o100644 << 16


def pack(folder: Path) -> Path:
    archive = folder.with_suffix(".zip")
    files = sorted(p for p in folder.rglob("*") if p.is_file())
    with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as out:
        for path in files:
            # One folder deep, named after the set, the way a downloaded
            # archive usually arrives; the app finds diceset.toml wherever it is.
            entry = zipfile.ZipInfo(f"{folder.name}/{path.relative_to(folder).as_posix()}", STAMP)
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = MODE
            entry.create_system = 3  # unix, so the mode above is read as one
            out.writestr(entry, path.read_bytes())
    return archive


def main() -> int:
    sets = sorted(p for p in HERE.iterdir() if p.is_dir() and (p / "diceset.toml").is_file())
    if not sets:
        print("no sets found next to this script", file=sys.stderr)
        return 1
    for folder in sets:
        archive = pack(folder)
        print(f"{archive.relative_to(HERE)}  {archive.stat().st_size:>9,} bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
