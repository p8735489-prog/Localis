#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST_MTMD="$ROOT/app/src/main/cpp/llama_src/tools/mtmd"
DEST_VENDOR="$ROOT/app/src/main/cpp/vendor/cpp-httplib"
DEST_VENDOR_ROOT="$ROOT/app/src/main/cpp/vendor"
REF="${LLAMA_CPP_MTMD_REF:-b10218}"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

need_vendor=0
need_mtmd=0

if [[ ! -f "$DEST_VENDOR/CMakeLists.txt" ||
      ! -f "$DEST_VENDOR/httplib.h" ||
      ! -f "$ROOT/app/src/main/cpp/vendor/nlohmann/json_fwd.hpp" ||
      ! -f "$ROOT/app/src/main/cpp/vendor/nlohmann/json.hpp" ]]; then
    need_vendor=1
fi

if [[ ! -f "$DEST_MTMD/CMakeLists.txt" ]]; then
    need_mtmd=1
fi

if (( need_vendor == 0 && need_mtmd == 0 )); then
    echo "llama.cpp vendor headers and mtmd sources already present"
    exit 0
fi

echo "Preparing llama.cpp ${REF} native dependency..."
ARCHIVE="$TMP/llama.tar.gz"
URL="https://github.com/ggml-org/llama.cpp/archive/refs/tags/${REF}.tar.gz"
FALLBACK_URL="https://codeload.github.com/ggml-org/llama.cpp/tar.gz/refs/tags/${REF}"

if ! curl -fL --retry 5 --retry-all-errors --connect-timeout 20 --max-time 180 "$URL" -o "$ARCHIVE"; then
    echo "Primary download failed; trying GitHub codeload fallback..."
    curl -fL --retry 5 --retry-all-errors --connect-timeout 20 --max-time 180 "$FALLBACK_URL" -o "$ARCHIVE"
fi

test -s "$ARCHIVE"
tar -tzf "$ARCHIVE" >/dev/null

# Refuse unexpected/incomplete archives before touching the project tree.
# Under "set -o pipefail", piping tar into grep -q is unsafe: grep may exit
# after the first match, tar gets SIGPIPE, and the successful check becomes
# exit code 2. Keep the archive listing in a file instead.
ARCHIVE_LIST="$TMP/archive.list"
tar -tzf "$ARCHIVE" > "$ARCHIVE_LIST"
grep -q "/vendor/cpp-httplib/CMakeLists.txt$" "$ARCHIVE_LIST"
grep -q "/vendor/cpp-httplib/httplib.h$" "$ARCHIVE_LIST"
grep -q "/tools/mtmd/CMakeLists.txt$" "$ARCHIVE_LIST"

tar -xzf "$ARCHIVE" -C "$TMP"
UPROOT="$(find "$TMP" -mindepth 1 -maxdepth 1 -type d -print -quit)"
test -n "$UPROOT"

if (( need_vendor == 1 )); then
    SRC_VENDOR="$UPROOT/vendor/cpp-httplib"
    if [[ ! -f "$SRC_VENDOR/CMakeLists.txt" || ! -f "$SRC_VENDOR/httplib.h" ]]; then
        echo "::error::llama.cpp ${REF} vendor/cpp-httplib is incomplete"
        exit 1
    fi
    rm -rf "$DEST_VENDOR"
    mkdir -p "$DEST_VENDOR_ROOT"
    cp -a "$SRC_VENDOR" "$DEST_VENDOR"
    echo "Copied llama.cpp vendor/cpp-httplib"
fi

if (( need_mtmd == 1 )); then
    SRC="$UPROOT/tools/mtmd"
    if [[ ! -f "$SRC/CMakeLists.txt" ]]; then
        echo "::error::llama.cpp ${REF} does not contain tools/mtmd/CMakeLists.txt"
        exit 1
    fi
    rm -rf "$DEST_MTMD"
    mkdir -p "$(dirname "$DEST_MTMD")"
    cp -a "$SRC" "$DEST_MTMD"
    echo "Copied llama.cpp MTMD sources"
fi

python3 "$ROOT/tools/check_native_tree.py"
echo "Fetch complete: llama.cpp ${REF} vendor + MTMD are ready"
