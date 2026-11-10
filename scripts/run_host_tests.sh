#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p evidence
python3.14 scripts/stage_upstream.py > evidence/stage-source.json
python3.14 -m unittest discover -s tests -v 2>&1 | tee evidence/host-tests.log
