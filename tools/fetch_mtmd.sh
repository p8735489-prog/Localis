#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST_MTMD="$ROOT/app/src/main/cpp/llama_src/tools/mtmd"
DEST_VENDOR="$ROOT/app/src/main/cpp/vendor"
REF="${LLAMA_CPP_MTMD_REF:-master}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# --- Phase 1: Always ensure vendor headers exist (common/ needs nlohmann/json) ---
if [[ -f "$DEST_VENDOR/nlohmann/json_fwd.hpp" ]]; then
  echo "vendor headers already present"
else
  echo "Downloading llama.cpp ${REF} for vendor headers..."
  URL="https://github.com/ggml-org/llama.cpp/archive/refs/heads/${REF}.tar.gz"
  if [[ "$REF" != "master" ]]; then
    URL="https://github.com/ggml-org/llama.cpp/archive/refs/tags/${REF}.tar.gz"
  fi
  curl -L --fail --retry 3 --connect-timeout 15 "$URL" -o "$TMP/llama.tar.gz"
  tar -xzf "$TMP/llama.tar.gz" -C "$TMP"
  UPROOT="$(find "$TMP" -maxdepth 1 -type d -name 'llama.cpp-*' | head -1)"
  if [[ -z "$UPROOT" ]]; then
    UPROOT="$(find "$TMP" -maxdepth 1 -type d | tail -1)"
  fi

  # Copy vendor/ (contains nlohmann/json and other upstream vendor headers)
  if [[ -d "$UPROOT/vendor" ]]; then
    mkdir -p "$(dirname "$DEST_VENDOR")"
    cp -a "$UPROOT/vendor" "$DEST_VENDOR"
    echo "Copied vendor headers"
  else
    echo "::warning::vendor/ not found in llama.cpp ${REF}. Falling back to bundled nlohmann/json_fwd.hpp"
  fi

  # --- Phase 2: Also fetch mtmd if available ---
  if [[ -f "$DEST_MTMD/CMakeLists.txt" ]]; then
    echo "mtmd sources already present"
  else
    SRC="$(find "$TMP" -maxdepth 4 -type d -name mtmd -path '*/tools/mtmd' | head -1)"
    if [[ -z "$SRC" ]]; then
      SRC="$(find "$TMP" -maxdepth 5 -type d -name mtmd | head -1)"
    fi
    if [[ -n "$SRC" ]]; then
      echo "Found mtmd at: $SRC"
      mkdir -p "$(dirname "$DEST_MTMD")"
      cp -a "$SRC" "$DEST_MTMD"
      echo "Copied mtmd sources"
    else
      echo "::warning::Could not find tools/mtmd in llama.cpp ${REF}. Building without mtmd."
    fi
  fi
fi

echo "Fetch complete. vendor=$([[ -d "$DEST_VENDOR" ]] && echo OK || echo MISSING) mtmd=$([[ -f "$DEST_MTMD/CMakeLists.txt" ]] && echo OK || echo MISSING)"
