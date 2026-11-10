"""Run actual Python in a disposable Android service process.

The Android supervisor kills the entire process group on timeout/cancellation.
Redirect OS descriptors as well as Python streams to retain native stderr.
"""
import json
import os
from pathlib import Path
import runpy
import sys
import traceback


def execute(script_path, working_directory, stdout_path, stderr_path):
    os.chdir(working_directory)
    out_fd = os.open(stdout_path, os.O_CREAT | os.O_TRUNC | os.O_WRONLY, 0o600)
    err_fd = os.open(stderr_path, os.O_CREAT | os.O_TRUNC | os.O_WRONLY, 0o600)
    saved_out, saved_err = os.dup(1), os.dup(2)
    original_out, original_err, original_argv = sys.stdout, sys.stderr, sys.argv
    code = 0
    try:
        os.dup2(out_fd, 1)
        os.dup2(err_fd, 2)
        sys.stdout = open(os.dup(1), "w", encoding="utf-8", buffering=1)
        sys.stderr = open(os.dup(2), "w", encoding="utf-8", buffering=1)
        sys.argv = [str(script_path)]
        try:
            runpy.run_path(str(Path(script_path).resolve()), run_name="__main__")
        except SystemExit as exc:
            if exc.code is None:
                code = 0
            elif isinstance(exc.code, int):
                code = exc.code & 255
            else:
                print(exc.code, file=sys.stderr)
                code = 1
        except BaseException:
            traceback.print_exc()
            code = 1
        finally:
            sys.stdout.flush()
            sys.stderr.flush()
            sys.stdout.close()
            sys.stderr.close()
    finally:
        sys.stdout, sys.stderr, sys.argv = original_out, original_err, original_argv
        os.dup2(saved_out, 1)
        os.dup2(saved_err, 2)
        for fd in (out_fd, err_fd, saved_out, saved_err):
            os.close(fd)
    return json.dumps({"exit_code": code, "python": sys.version, "pid": os.getpid()})
