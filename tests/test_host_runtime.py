"""Real Linux process/filesystem tests. These do not establish Android support."""
import ctypes
import hashlib
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import threading
import time
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "app/src/main/python"))
sys.path.insert(0, str(ROOT / "app/build/staged/hermes"))
from hermes_android.workspace import Workspace


class FileOperationsTests(unittest.TestCase):
    def test_original_io_roundtrip_and_unicode(self):
        with tempfile.TemporaryDirectory() as directory:
            text = "नमस्ते Hermes ✅\n"
            result = Workspace(directory).write_and_read("nested/file.txt", text)
            self.assertEqual(Path(directory, "nested/file.txt").read_text(), text)
            self.assertEqual(result["sha256"], hashlib.sha256(text.encode()).hexdigest())

    def test_path_traversal_and_existing_symlink_are_refused(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory, "workspace"); root.mkdir()
            other = Path(directory, "outside"); other.mkdir()
            (root / "escape").symlink_to(other, target_is_directory=True)
            workspace = Workspace(root)
            for path in ("../outside/file", "/tmp/absolute", "escape/file"):
                with self.assertRaises(ValueError):
                    workspace.write_and_read(path, "must not be written")
            self.assertFalse((other / "file").exists())


class PackagedBootstrapTests(unittest.TestCase):
    def test_bootstrap_keeps_current_interpreter_and_blocks_mutation(self):
        with tempfile.TemporaryDirectory() as directory:
            code = """
import sys
before = sys.executable
import hermes_bootstrap
from hermes_apk_runtime import refuse_mutation
assert hermes_bootstrap._apk_packaged
assert sys.executable == before
try:
    refuse_mutation('sync_venv')
except RuntimeError as error:
    assert 'APK' in str(error)
else:
    raise AssertionError('mutation was not rejected')
print('bootstrap completed in the original host interpreter')
"""
            env = {**os.environ, "HERMES_HOME": directory, "HERMES_DISABLE_LAZY_INSTALLS": "1",
                   "PYTHONPATH": str(ROOT / "app/build/staged/hermes")}
            result = subprocess.run([sys.executable, "-c", code], env=env, cwd=directory,
                                    capture_output=True, text=True, timeout=15)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertFalse(Path(directory, "installs").exists())


class PythonWorkerTests(unittest.TestCase):
    def test_real_worker_stdout_stderr_file_and_exit_status(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            (path / "script.py").write_text("import os,sys\nfrom pathlib import Path\n"
                "Path('created.txt').write_text('worker executed')\n"
                "print('नमस्ते ✅')\nos.write(2, b'native stderr\\n')\nsys.exit(7)\n")
            code = "from hermes_android.worker import execute; print(execute(*__import__('sys').argv[1:]))"
            env = {**os.environ, "PYTHONPATH": str(ROOT / "app/src/main/python")}
            result = subprocess.run([sys.executable, "-c", code, str(path / "script.py"), directory,
                str(path / "stdout.txt"), str(path / "stderr.txt")], env=env, capture_output=True, text=True, timeout=15)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(json.loads(result.stdout)["exit_code"], 7)
            self.assertEqual((path / "stdout.txt").read_text(), "नमस्ते ✅\n")
            self.assertEqual((path / "stderr.txt").read_text(), "native stderr\n")
            self.assertEqual((path / "created.txt").read_text(), "worker executed")


class NativeProcessTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.build = tempfile.TemporaryDirectory()
        library = Path(cls.build.name, "libprocess.so")
        subprocess.run(["cc", "-shared", "-fPIC", "-Wall", "-Wextra", "-Werror", "-o", str(library),
                        str(ROOT / "app/src/main/cpp/process.c")], check=True)
        cls.native = ctypes.CDLL(str(library))
        cls.native.hermes_spawn.argtypes = [ctypes.c_char_p] * 5
        cls.native.hermes_spawn.restype = ctypes.c_int
        cls.native.hermes_poll.argtypes = [ctypes.c_int]
        cls.native.hermes_kill_group.argtypes = [ctypes.c_int, ctypes.c_int]

    @classmethod
    def tearDownClass(cls):
        cls.build.cleanup()

    def spawn(self, directory, command, shell="/bin/sh"):
        values = [shell, directory, command, str(Path(directory, "stdout")), str(Path(directory, "stderr"))]
        pid = self.native.hermes_spawn(*(v.encode() for v in values))
        self.assertGreater(pid, 1)
        self.addCleanup(self.native.hermes_kill_group, pid, signal.SIGKILL)
        return pid

    def wait(self, pid):
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            status = self.native.hermes_poll(pid)
            if status != -1000:
                return status
            threading.Event().wait(0.01)
        self.fail("native child did not terminate within 10 seconds")

    def test_stdout_stderr_and_nonzero_exit(self):
        with tempfile.TemporaryDirectory() as directory:
            pid = self.spawn(directory, "printf 'नमस्ते ✅'; printf 'err' >&2; exit 7")
            self.assertEqual(self.wait(pid), 7)
            self.assertEqual(Path(directory, "stdout").read_text(), "नमस्ते ✅")
            self.assertEqual(Path(directory, "stderr").read_text(), "err")

    def test_spawn_failure_is_nonzero(self):
        with tempfile.TemporaryDirectory() as directory:
            pid = self.spawn(directory, "true", shell="/nonexistent/hermes-shell")
            self.assertEqual(self.wait(pid), 127)
            self.assertIn("exec failed", Path(directory, "stderr").read_text())

    def test_cancel_kills_process_group_including_descendant(self):
        with tempfile.TemporaryDirectory() as directory:
            # A real child waits on a FIFO. Signal readiness via its pid file.
            command = "sleep 60 & child=$!; printf '%s' $child > child.pid; wait"
            pid = self.spawn(directory, command)
            ready = Path(directory, "child.pid")
            deadline = time.monotonic() + 5
            while not ready.exists() and time.monotonic() < deadline:
                threading.Event().wait(0.01)
            self.assertTrue(ready.exists())
            child = int(ready.read_text())
            self.assertEqual(self.native.hermes_kill_group(pid, signal.SIGKILL), 0)
            self.assertEqual(self.wait(pid), 137)
            deadline = time.monotonic() + 5
            while time.monotonic() < deadline:
                stat = Path(f"/proc/{child}/stat")
                if not stat.exists() or stat.read_text().split(") ", 1)[1].startswith("Z"):
                    break
                threading.Event().wait(0.01)
            else:
                self.fail("descendant remained running after group cancellation")


if __name__ == "__main__":
    unittest.main()
