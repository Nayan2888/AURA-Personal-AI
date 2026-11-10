"""App-owned file operations using the original Hermes native I/O primitives."""
import hashlib
from pathlib import Path


class Workspace:
    def __init__(self, root):
        self.root = Path(root).resolve()
        self.root.mkdir(parents=True, exist_ok=True)

    def resolve(self, relative):
        value = Path(relative)
        if value.is_absolute() or not relative or ".." in value.parts:
            raise ValueError("Use a relative workspace path without '..'")
        path = (self.root / value).resolve()
        if not path.is_relative_to(self.root) or path == self.root:
            raise ValueError("Path escapes the workspace")
        return path

    def write_and_read(self, relative, text):
        from tools.file_operations_native import atomic_write, probe, sha256_of
        path = self.resolve(relative)
        data = text.encode("utf-8")
        error = atomic_write(str(path), data)
        if error is not None:
            raise OSError(error)
        result = probe(str(path), want_text=True)
        if result is None or result[1] != data:
            raise OSError("Written file failed readback")
        digest = sha256_of(str(path))
        if digest != hashlib.sha256(data).hexdigest():
            raise OSError("Written file failed SHA-256 verification")
        return {"path": relative, "bytes": len(data), "sha256": digest,
                "content": result[1].decode("utf-8"), "implementation": "tools.file_operations_native"}
