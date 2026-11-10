# Source and dependency notices

This is an independent port, not an official Nous Research Android release.

- Original Hermes: MIT, copyright 2025 Nous Research. See LICENSE-HERMES and the
  unmodified upstream submodule's license and component-specific notices.
- CPython: Python Software Foundation license and bundled third-party licenses.
- Chaquopy: MIT; its packaged native dependencies retain their own licenses.
- Jetpack/Android components: their corresponding Apache-2.0 notices/licenses.
- Dependency identity: dependencies.json, requirements-android.txt, upstream uv.lock.
- This implementation does not redistribute Termux, Bash, a Linux distribution,
  or model weights. It executes the device-provided Android shell.

This source inventory is not a completed APK SBOM. Before distributing a successful
APK, inspect the actual bundled wheels/native libraries and retain their license
files. Missing native artifacts and unreviewed redistribution provenance remain
build/release blockers, not an invitation to copy an anonymous binary bundle.
