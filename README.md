# Hermes Agent — independent Android port, Phase 1

Native Kotlin/Compose host for the **original Nous Research Hermes Python engine**.
This branch is an independent port project hosted in `Nayan2888/AURA-Personal-AI`;
the repository's existing `main` application is not this port. Do not merge this
branch over the AURA application.

**Status: implementation and qualification in progress. No Galaxy M36 validation.**
An APK build, if successful, does not establish engine or feature parity. Missing
dependencies are build failures; there are no dependency stubs or fallback agents.
Phase 2 (conversations/model workflows) is not authorized or implemented here.

## Source identity

`vendor/hermes` is a Git submodule pointing to the unmodified official source:

- URL: https://github.com/NousResearch/hermes-agent
- Commit: `fc86fb39a1ffb2071e0bcda4e7f837e7d79e05b8`
- Original license: [LICENSE-HERMES](LICENSE-HERMES)
- Packaging changes: [patch ledger](patches/README.md)

`scripts/stage_upstream.py` checks the commit and clean submodule, copies tracked
runtime sources, applies documented bootstrap changes, and generates source
digests. The app verifies these digests before importing the original engine.
Skills, memory, provider plugins, tool registry and the conversation loop remain
upstream code. Preservation is not a claim that every subsystem works on Android.

## Runtime architecture

The Compose activity binds to a non-exported `:engine` service. That process hosts
Chaquopy CPython 3.14 and imports `run_agent.AIAgent` after checking package versions.
It does not instantiate a substitute agent or make direct SDK chat calls.

Phase 1 exposes explicit qualification operations: engine import, original native
file I/O, native local shell execution, and a disposable `:python_worker` service.
The shell is Android's `/system/bin/sh`, **not Bash**. The native process adapter
creates a process group, captures actual file-backed stdout/stderr and wait status,
and kills descendants on timeout/cancel/completion. This backend is not yet wired
into Hermes's Bash session wrapper or original execute_code RPC protocol.

Python workers execute actual user scripts using CPython. A script's `SystemExit`
or exception status is returned. An Android service's OS process exit code is not
available to the sibling engine service; crash/kill outcomes therefore return a
null exit code and an explicit termination reason. This is not a general-purpose
replacement for a Python executable or proof that Python subprocess APIs work.

Services are bound to the foreground UI. There is no 24/7 service or scheduler
claim, no external Termux dependency and no remote agent server. Same-UID processes
are crash/lifecycle isolation, not a security sandbox against arbitrary Python.

## Build

Use JDK 21, Gradle 8.13, host Python 3.14, Android SDK 36, build tools 36.0.0,
NDK 28.2.13676358, CMake 3.22.1. Set `ANDROID_HOME`/`local.properties` as usual.

```bash
git clone --branch hermes-android/phase-1 --recurse-submodules https://github.com/Nayan2888/AURA-Personal-AI.git hermes-android
cd hermes-android
python3.14 scripts/probe_dependencies.py
bash scripts/run_host_tests.sh
gradle --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace
python3.14 scripts/apk_evidence.py
```

`requirements-android.txt` preserves the exact core+ACP lock closure. Chaquopy's
real Android wheels are required. `wheelhouse/` is reserved for provenance-checked
locally built wheels; it does not contain placeholders. Native missing-wheel
failures must be resolved with source builds, not version downgrades. The psutil
Git source is pinned to upstream's exact required commit.

The [Actions workflow](.github/workflows/android-phase1.yml) builds ARM64 debug and
test APKs and uploads them only if the build succeeds. Failure logs are uploaded
separately. It does **not** claim emulator or physical-device tests ran.

## Validation and phone testing

- Linux host tests: `bash scripts/run_host_tests.sh`; real filesystem/process work.
- Android instrumentation: `gradle :app:connectedDebugAndroidTest`, only with an
  attached, authorized ARM64 Android device or compatible emulator.
- Physical Galaxy M36: follow [PHONE_TESTING.md](PHONE_TESTING.md), record actual
  fingerprint/build hash/results. No device has been marked working by this code.

Keep app data: uninstalling erases its internal workspace, jobs and Hermes state.
Debug builds are for controlled testing, not a hardened release.
