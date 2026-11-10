"""Initialize and probe the real packaged engine without calling a model."""
import importlib
import importlib.metadata
import json
import os
from pathlib import Path
import platform
import sys
import traceback

_agent_class = None


def initialize(home, workspace, source_root):
    global _agent_class
    if sys.version_info[:2] != (3, 14):
        raise RuntimeError(f"CPython 3.14 required, received {sys.version}")
    home_path, workspace_path = Path(home).resolve(), Path(workspace).resolve()
    home_path.mkdir(parents=True, exist_ok=True)
    workspace_path.mkdir(parents=True, exist_ok=True)
    os.environ["HERMES_HOME"] = str(home_path)
    os.environ["HERMES_DISABLE_LAZY_INSTALLS"] = "1"
    os.environ["HOME"] = str(home_path.parent)
    os.chdir(workspace_path)
    source = Path(source_root).resolve()
    sys.path.insert(0, str(source))
    manifest = json.loads((source / "hermes_android_runtime.json").read_text())
    dependencies = json.loads((source / "hermes_android_dependencies.json").read_text())
    results = []
    for package in dependencies["packages"]:
        try:
            installed = importlib.metadata.version(package["name"])
            results.append({"name": package["name"], "expected": package["version"],
                            "actual": installed, "ok": installed == package["version"]})
        except importlib.metadata.PackageNotFoundError:
            results.append({"name": package["name"], "expected": package["version"],
                            "actual": None, "ok": False})
    report = {"python": sys.version, "platform": sys.platform, "machine": platform.machine(),
              "upstream_commit": manifest["upstream_commit"], "dependencies": results,
              "engine_imported": False, "model_calls": 0}
    if not all(p["ok"] for p in results):
        report["error"] = "Dependency qualification failed; engine initialization refused"
        return json.dumps(report)
    try:
        module = importlib.import_module("run_agent")
        _agent_class = module.AIAgent
        report["engine_imported"] = True
        report["engine_class"] = f"{_agent_class.__module__}.{_agent_class.__name__}"
        report["engine_file"] = module.__file__
    except (Exception, SystemExit):
        report["error"] = traceback.format_exc()
    return json.dumps(report)


def file_probe(workspace, relative_path, text):
    from hermes_android.workspace import Workspace
    return json.dumps(Workspace(workspace).write_and_read(relative_path, text))
