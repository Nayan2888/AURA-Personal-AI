# Patch ledger

Original submodule files are unchanged. Build-time changes are applied exactly
once to a staged copy and fail if a boundary no longer matches the approved source.
Both original and modified file SHA-256 hashes are included in the APK manifest.

| Boundary | Change | Purpose and evidence limit |
|---|---|---|
| `hermes_bootstrap.py` | Read the explicit packaged-runtime manifest; skip interrupted desktop update settlement, legacy update continuation, launch preparation/re-exec and PM dependency selection only in the packaged runtime | Keep APK-owned interpreter and dependency paths; actual Linux bootstrap import is tested, Android import remains an instrumentation gate |
| `run_agent.py` | Skip the second desktop interrupted-update recovery when bootstrapped as an APK | Prevent source replacement/relaunch; original AIAgent implementation and all mixins remain unchanged |
| `pm/client.py::_request` | Refuse PM worker operations for packaged runtime with a clear APK-update remedy | No desktop runtime mutation/download worker; direct mutation APIs outside this route are not certified safe for Android and remain a future review item |
| `hermes_apk_runtime.py` | Add a small manifest-backed runtime mode and explicit mutation rejection | Real packaging adapter, not a stub for an upstream dependency |

No fake `sys.platform`, import-success substitution, `sys.modules` dependency
injection or alternate Hermes fork is used. Original pyproject/uv.lock are unchanged.
The APK's dependencies must still match actual installed distribution versions.

Desktop source directories excluded from the runtime payload are listed in the
stager. Their omission is a packaging scope decision, not a claim of feature parity.
Original complete sources remain available through the pinned submodule.
