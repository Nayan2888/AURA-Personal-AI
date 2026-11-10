#!/usr/bin/env python3
"""Package pinned, tracked upstream runtime sources and explicit small patches."""
import hashlib
import json
import pathlib
import shutil
import subprocess
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = ROOT / "vendor/hermes"
SHA = "fc86fb39a1ffb2071e0bcda4e7f837e7d79e05b8"
EXCLUDED = {".git", ".github", "apps", "website", "tests", "docs", "ui-tui", "web", "benchmarks", "node_modules"}


def patch_once(text, old, new, name):
    if text.count(old) != 1:
        raise ValueError(f"Upstream boundary changed: {name}")
    return text.replace(old, new, 1)


def apply_patches(destination):
    changes = []
    # Preserve the original modules and execution path. Only desktop startup/PM
    # operations are gated; the original dependency imports must still succeed.
    boundaries = {
        "hermes_bootstrap.py": [
            ("_settle_interrupted_update()\n\n", "if not _apk_packaged:\n    _settle_interrupted_update()\n\n"),
            ("if _legacy_post_swap is not None:", "if _legacy_post_swap is not None and not _apk_packaged:"),
            ("if not _pm_repair:", "if not _pm_repair and not _apk_packaged:"),
        ],
        "run_agent.py": [
            ('if "hermes_cli.main" not in sys.modules:', 'if "hermes_cli.main" not in sys.modules and not hermes_bootstrap._apk_packaged:'),
        ],
        "pm/client.py": [
            ('    from pm import receipt\n    from pm.registry import package_definitions',
             '    from hermes_apk_runtime import refuse_mutation\n    refuse_mutation(operation)\n    from pm import receipt\n    from pm.registry import package_definitions'),
        ],
    }
    for name, replacements in boundaries.items():
        path = destination / name
        original = path.read_text(encoding="utf-8")
        modified = original
        if name == "hermes_bootstrap.py":
            modified = patch_once(modified, '_IS_WINDOWS = sys.platform == "win32"',
                'from hermes_apk_runtime import is_packaged_runtime\n\n_apk_packaged = is_packaged_runtime()\n_IS_WINDOWS = sys.platform == "win32"', name)
        for old, new in replacements:
            modified = patch_once(modified, old, new, name)
        path.write_text(modified, encoding="utf-8")
        changes.append({"path": name, "original_sha256": hashlib.sha256(original.encode()).hexdigest(),
                        "packaged_sha256": hashlib.sha256(modified.encode()).hexdigest()})
    shutil.copy2(ROOT / "patches/hermes_apk_runtime.py", destination)
    return changes


def main():
    actual = subprocess.check_output(["git", "-C", str(SOURCE), "rev-parse", "HEAD"], text=True).strip()
    if actual != SHA:
        raise RuntimeError(f"Refusing different upstream commit: {actual}")
    if subprocess.check_output(["git", "-C", str(SOURCE), "status", "--porcelain"], text=True).strip():
        raise RuntimeError("Upstream submodule is modified; keep changes in the patch ledger")
    generated = ROOT / "app/build/generated"
    destination = ROOT / "app/build/staged/hermes"
    if destination.exists():
        shutil.rmtree(destination)
    destination.mkdir(parents=True)
    paths = subprocess.check_output(["git", "-C", str(SOURCE), "ls-files", "-z"]).decode().split("\0")
    for name in paths:
        if not name or pathlib.PurePosixPath(name).parts[0] in EXCLUDED:
            continue
        source = SOURCE / name
        if source.is_symlink():
            raise RuntimeError(f"Symlink requires explicit packaging review: {name}")
        if source.is_file():
            target = destination / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
    changes = apply_patches(destination)
    shutil.copy2(ROOT / "dependencies.json", destination / "hermes_android_dependencies.json")
    manifest = {"format": 1, "upstream_commit": SHA, "patches": changes, "files": {}}
    for path in sorted(destination.rglob("*")):
        if path.is_file():
            manifest["files"][str(path.relative_to(destination))] = hashlib.sha256(path.read_bytes()).hexdigest()
    (destination / "hermes_android_runtime.json").write_text(json.dumps(manifest, indent=2) + "\n")
    assets = generated / "assets"
    assets.mkdir(parents=True, exist_ok=True)
    shutil.copy2(ROOT / "LICENSE-HERMES", assets)
    shutil.copy2(destination / "hermes_android_runtime.json", assets / "source-manifest.json")
    with zipfile.ZipFile(assets / "hermes-runtime.zip", "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(destination.rglob("*")):
            if path.is_file():
                info = zipfile.ZipInfo(str(path.relative_to(destination)), (2026, 11, 9, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(info, path.read_bytes())
    print(json.dumps({"upstream_commit": SHA, "packaged_files": len(manifest["files"]), "patched": changes}, indent=2))


if __name__ == "__main__":
    main()
