# Localis llama.cpp dependency pin

Localis uses a bundled llama.cpp/ggml source snapshot and pulls only the matching
`vendor/` and `tools/mtmd/` trees when those trees are absent.

The CI dependency is pinned to **llama.cpp b10218**. Do not change this to `master`.
A floating upstream branch can introduce CMake/API drift and break the native build.

If the bundled llama.cpp snapshot is intentionally upgraded, update the bundled
source, vendor, mtmd, and this pin together, then run the native CI build.
