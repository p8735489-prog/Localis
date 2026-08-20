#!/usr/bin/env bash
set -euo pipefail
# Regression guard: fetch_mtmd.sh must not contain pipefail-sensitive tar/find pipes.
if grep -q 'tar -tzf "\$ARCHIVE" | grep -q' tools/fetch_mtmd.sh; then
  echo "::error::fetch_mtmd.sh contains unsafe tar|grep -q pipeline"
  exit 1
fi
if grep -q 'find "\$TMP".*| head -1' tools/fetch_mtmd.sh; then
  echo "::error::fetch_mtmd.sh contains unsafe find|head pipeline"
  exit 1
fi
grep -q 'ARCHIVE_LIST="$TMP/archive.list"' tools/fetch_mtmd.sh
echo "fetch_mtmd regression guard passed"
