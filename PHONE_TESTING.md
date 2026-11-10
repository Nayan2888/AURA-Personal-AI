# Galaxy M36 test procedure — run only after a real APK exists

Target: Samsung Galaxy M36 5G, Android 16/API 36, ARM64, 8 GB, unrooted.
Neither a green build nor these instructions mean this phone was tested.

1. Open the exact GitHub Actions run linked in the evidence report. If there is
   no `hermes-phase1-arm64-apks` artifact, the build did not produce a qualified
   installable package. Do not install an unrelated Hermes APK as a substitute.
2. Download and unzip the APK artifact. Compare `app-debug.apk` SHA-256 and byte
   size with `apk-evidence.json`. Install this independent application, package
   `dev.nayan.hermes.port`. No Termux installation is needed by the design.
3. Launch **Hermes · Phase 1**. Tap **Initialize original Hermes**. Save the actual
   JSON using **Copy result**. Required evidence: Python `3.14.x`, the approved
   upstream SHA, matching real dependency versions, `engine_imported: true`, and
   `engine_class: run_agent.AIAgent`. Any error is a failed gate, not a success.
4. Tap **Write, read and verify file** with Hindi and emoji. Confirm content and
   SHA-256 match. Close/reopen the app and inspect persisted files with adb below.
5. Run the default shell command. Expect real stdout, stderr and exit code 7.
   Run `sleep 60`: after the 10-second deadline expect `termination: timeout` and
   the actual signal-derived status. Run it again and press **Cancel**; inspect
   `termination: cancelled`. This shell does not promise Bash syntax compatibility.
6. Run the default Python script. Expect both streams and script exit code 7.
   Run `while True: pass`; expect timeout with a null exit code. Then run
   `print('worker restarted')` to verify a fresh worker works after cancellation.
7. Record screen-off/background/force-stop outcomes separately. The current bound
   service design promises no continued background work. Do not infer scheduling
   or future model/skill functionality from these Phase 1 probes.

## Optional PC/adb collection

Enable Developer options and USB debugging on your own phone, authorize the PC,
then use Android platform-tools. No root is required.

```bash
adb devices
adb shell getprop ro.build.fingerprint
adb shell getprop ro.product.cpu.abi
adb shell getconf PAGE_SIZE
adb install -r app-debug.apk
adb install -r app-debug-androidTest.apk
adb logcat -c
adb shell am instrument -w dev.nayan.hermes.port.test/androidx.test.runner.AndroidJUnitRunner
adb logcat -d > hermes-phone-logcat.txt
adb exec-out run-as dev.nayan.hermes.port cat files/latest-result.json > hermes-phone-result.json
adb shell run-as dev.nayan.hermes.port ls -R files/jobs
adb shell dumpsys meminfo dev.nayan.hermes.port > hermes-memory.txt
```

Instrumentation includes original-engine import, original file I/O, actual shell
streams/nonzero exit, timeout/cancel, Python execution and timeout/restart. Preserve
the complete instrumentation output, including failures and test counts. Redact
personal information from system-wide logcat before sharing.

An API 36 x86_64 emulator cannot install this ARM64-only build. Emulator results
must identify the actual ABI and cannot be relabeled physical Galaxy M36 results.
