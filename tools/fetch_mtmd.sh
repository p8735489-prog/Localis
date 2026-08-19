#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/app/src/main/cpp/llama_src/tools/mtmd"
REF="${LLAMA_CPP_MTMD_REF:-master}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
if [[ -f "$DEST/CMakeLists.txt" ]]; then
  echo "mtmd sources already present"
  exit 0
fi
URL="https://github.com/ggml-org/llama.cpp/archive/refs/heads/${REF}.tar.gz"
if [[ "$REF" != "master" ]]; then
  URL="https://github.com/ggml-org/llama.cpp/archive/refs/tags/${REF}.tar.gz"
fi
curl -L --fail --retry 3 --connect-timeout 15 "$URL" -o "$TMP/llama.tar.gz"
tar -xzf "$TMP/llama.tar.gz" -C "$TMP"
SRC="$(find "$TMP" -maxdepth 2 -type d -path '*/tools/mtmd' | head -1)"
test -n "$SRC"
mkdir -p "$(dirname "$DEST")"
cp -a "$SRC" "$DEST"
# mtmd's CMake links vendor-hash and expects upstream vendor headers. Copy only
# the small vendor tree it needs; the rest of llama.cpp remains the app snapshot.
UPROOT="$(dirname "$(dirname "$SRC")")"
if [[ -d "$UPROOT/vendor" && ! -d "$ROOT/app/src/main/cpp/vendor" ]]; then
  cp -a "$UPROOT/vendor" "$ROOT/app/src/main/cpp/vendor"
fi
echo "Fetched llama.cpp mtmd runtime: $REF"
