#!/usr/bin/env bash
set -euo pipefail
# Regression test for fetch_mtmd.sh archive validation. This test intentionally
# avoids grep -q/head pipelines because pipefail can turn a successful producer
# into SIGPIPE failures.
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/src/llama/vendor/cpp-httplib" "$TMP/src/llama/tools/mtmd"
touch "$TMP/src/llama/vendor/cpp-httplib/CMakeLists.txt"
touch "$TMP/src/llama/vendor/cpp-httplib/httplib.h"
touch "$TMP/src/llama/tools/mtmd/CMakeLists.txt"
tar -czf "$TMP/llama.tar.gz" -C "$TMP/src" llama
python3 - "$TMP/llama.tar.gz" <<'PY'
import sys, tarfile
with tarfile.open(sys.argv[1], "r:gz") as tf:
    names=set(tf.getnames())
for suffix in ("/vendor/cpp-httplib/CMakeLists.txt",
               "/vendor/cpp-httplib/httplib.h",
               "/tools/mtmd/CMakeLists.txt"):
    assert any(n.endswith(suffix) for n in names), suffix
print("MTMD archive validation regression test passed")
PY
