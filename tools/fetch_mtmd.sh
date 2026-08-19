#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST_MTMD="$ROOT/app/src/main/cpp/llama_src/tools/mtmd"
DEST_VENDOR="$ROOT/app/src/main/cpp/vendor"
REF="${LLAMA_CPP_MTMD_REF:-master}"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

need_vendor=0
need_mtmd=0

if [[ ! -f "$DEST_VENDOR/nlohmann/json_fwd.hpp" || ! -f "$DEST_VENDOR/nlohmann/json.hpp" ]]; then
    need_vendor=1
fi

if [[ ! -f "$DEST_MTMD/CMakeLists.txt" ]]; then
    need_mtmd=1
fi

if (( need_vendor == 0 && need_mtmd == 0 )); then
    echo "llama.cpp vendor headers and mtmd sources already present"
    exit 0
fi

echo "Downloading llama.cpp ${REF}..."
URL="https://github.com/ggml-org/llama.cpp/archive/refs/heads/${REF}.tar.gz"
if [[ "$REF" != "master" ]]; then
    URL="https://github.com/ggml-org/llama.cpp/archive/refs/tags/${REF}.tar.gz"
fi

curl -L --fail --retry 3 --connect-timeout 15 "$URL" -o "$TMP/llama.tar.gz"
tar -xzf "$TMP/llama.tar.gz" -C "$TMP"

UPROOT="$(find "$TMP" -maxdepth 1 -type d -name 'llama.cpp-*' | head -1)"
if [[ -z "$UPROOT" ]]; then
    UPROOT="$(find "$TMP" -mindepth 1 -maxdepth 1 -type d | head -1)"
fi
test -n "$UPROOT"

if (( need_vendor == 1 )); then
    if [[ -d "$UPROOT/vendor" ]]; then
        rm -rf "$DEST_VENDOR"
        mkdir -p "$(dirname "$DEST_VENDOR")"
        cp -a "$UPROOT/vendor" "$DEST_VENDOR"
        echo "Copied vendor headers"
    else
        echo "::error::llama.cpp ${REF} has no vendor directory"
        exit 1
    fi
fi

if (( need_mtmd == 1 )); then
    SRC="$(find "$UPROOT" -type d -path '*/tools/mtmd' | head -1 || true)"
    if [[ -n "$SRC" && -f "$SRC/CMakeLists.txt" ]]; then
        rm -rf "$DEST_MTMD"
        mkdir -p "$(dirname "$DEST_MTMD")"
        cp -a "$SRC" "$DEST_MTMD"
        echo "Copied mtmd sources"
    else
        echo "::warning::Could not find tools/mtmd in llama.cpp ${REF}; building without mtmd."
    fi
fi

echo "Fetch complete. vendor=$([[ -d "$DEST_VENDOR" ]] && echo OK || echo MISSING) mtmd=$([[ -f "$DEST_MTMD/CMakeLists.txt" ]] && echo OK || echo MISSING)"
