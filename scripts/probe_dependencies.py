#!/usr/bin/env python3
"""Observe available exact-version wheels. Candidates are not runtime proof."""
import concurrent.futures
import datetime
import html.parser
import json
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


class Links(html.parser.HTMLParser):
    def __init__(self):
        super().__init__()
        self.links = []

    def handle_starttag(self, tag, attrs):
        if tag == "a":
            self.links.extend(value for key, value in attrs if key == "href" and value)


def compatible_candidate(name):
    if not name.endswith(".whl"):
        return False
    if "-none-any.whl" in name:
        return True
    return ("android_" in name and "arm64_v8a" in name
            and ("-cp314-" in name or "-abi3-" in name))


def probe(package):
    result = dict(package, pypi_candidates=[], chaquopy_candidates=[], errors=[])
    if "git" in package["source"]:
        result["status"] = "requires_exact_git_source_build"
        return result
    name, version = package["name"], package["version"]
    try:
        with urllib.request.urlopen(f"https://pypi.org/pypi/{name}/{version}/json", timeout=30) as response:
            data = json.load(response)
        result["pypi_candidates"] = [
            {"filename": p["filename"], "url": p["url"], "sha256": p["digests"]["sha256"]}
            for p in data["urls"] if compatible_candidate(p["filename"])
        ]
    except (OSError, ValueError) as error:
        result["errors"].append(f"PyPI: {error}")
    if not result["pypi_candidates"]:
        base = f"https://chaquo.com/pypi-13.1/{name}/"
        try:
            with urllib.request.urlopen(base, timeout=30) as response:
                parser = Links(); parser.feed(response.read().decode())
            result["chaquopy_candidates"] = [urllib.parse.urljoin(base, link) for link in parser.links
                if compatible_candidate(link) and f"-{version}-" in link]
        except urllib.error.HTTPError as error:
            if error.code != 404:
                result["errors"].append(f"Chaquopy: {error}")
        except OSError as error:
            result["errors"].append(f"Chaquopy: {error}")
    result["status"] = "candidate_not_runtime_tested" if result["pypi_candidates"] or result["chaquopy_candidates"] else "no_candidate_found"
    if result["errors"] and result["status"] == "no_candidate_found":
        result["status"] = "lookup_incomplete"
    return result


def main():
    dependencies = json.loads((ROOT / "dependencies.json").read_text())
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        rows = list(pool.map(probe, dependencies["packages"]))
    report = {"checked_at_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "scope": "Index candidate search only; no native import or device test", "packages": rows}
    target = ROOT / "evidence/dependency-probe.json"
    target.parent.mkdir(exist_ok=True)
    target.write_text(json.dumps(report, indent=2) + "\n")
    for row in rows:
        print(row["name"], row["version"], row["status"])


if __name__ == "__main__":
    main()
