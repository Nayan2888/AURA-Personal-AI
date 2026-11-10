#!/usr/bin/env python3
import hashlib
import json
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parents[1]
rows = []
for path in sorted((root / "app/build/outputs/apk").rglob("*.apk")):
    with zipfile.ZipFile(path) as archive:
        libraries = [name for name in archive.namelist() if name.startswith("lib/") and name.endswith(".so")]
    rows.append({"path": str(path.relative_to(root)), "size_bytes": path.stat().st_size,
                 "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "native_libraries": libraries,
                 "device_tested": False})
if not rows:
    raise SystemExit("No APK was produced; no size or checksum can be reported")
(root / "evidence/apk-evidence.json").write_text(json.dumps(rows, indent=2) + "\n")
print(json.dumps(rows, indent=2))
