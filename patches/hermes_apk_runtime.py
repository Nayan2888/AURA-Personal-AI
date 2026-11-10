"""Explicit immutable-runtime contract for the independently packaged Android port.

No platform spoofing, module stubs or replacement agent. The build supplies the
manifest beside this file; the app validates source digests before importing it.
"""
import json
from pathlib import Path

UPSTREAM = "fc86fb39a1ffb2071e0bcda4e7f837e7d79e05b8"


def is_packaged_runtime():
    marker = Path(__file__).with_name("hermes_android_runtime.json")
    if not marker.is_file():
        return False
    value = json.loads(marker.read_text(encoding="utf-8"))
    if value.get("upstream_commit") != UPSTREAM or value.get("format") != 1:
        raise RuntimeError("Invalid packaged Hermes runtime identity")
    return True


def refuse_mutation(operation):
    if is_packaged_runtime():
        raise RuntimeError(
            f"{operation} cannot change the APK's Python/native runtime. "
            "Install an APK containing the required real dependencies."
        )
