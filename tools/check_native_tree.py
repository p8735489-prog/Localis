#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
cpp = root / "app/src/main/cpp"
vendor = cpp / "vendor"
mtmd = cpp / "llama_src/tools/mtmd"

required_vendor = [
    vendor / "CMakeLists.txt",
    vendor / "cpp-httplib/CMakeLists.txt",
    vendor / "cpp-httplib/httplib.h",
    vendor / "cpp-httplib/httplib.cpp",
    vendor / "nlohmann/json.hpp",
    vendor / "nlohmann/json_fwd.hpp",
]
required_mtmd = [
    mtmd / "CMakeLists.txt",
    # CMakeLists.txt alone is not proof the dependency is populated -- it can
    # be committed/updated (e.g. bumping to a newer llama.cpp ref) without the
    # matching source files being fetched, which used to let this check pass
    # while CMake later failed with "Cannot find source file: mtmd.cpp".
    mtmd / "mtmd.cpp",
    mtmd / "mtmd.h",
    mtmd / "clip.cpp",
]

missing = [str(p.relative_to(root)) for p in required_vendor + required_mtmd if not p.exists()]
if missing:
    print("NATIVE TREE INCOMPLETE")
    for p in missing:
        print(" -", p)
    print("Run tools/fetch_mtmd.sh to populate the pinned llama.cpp dependency tree.")
    sys.exit(1)

cmake = (cpp / "CMakeLists.txt").read_text(errors="ignore")
if "add_subdirectory(vendor)" not in cmake:
    print("ERROR: vendor/ subdirectory is not added before common/")
    sys.exit(1)
if "add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/llama_src/tools/mtmd" not in cmake:
    print("ERROR: pinned llama.cpp MTMD target is not added")
    sys.exit(1)

# Verify the src -> llama_src symlink exists so ../src/llama-ext.h resolves.
# Some Git clients (GitHub's web upload UI, Windows checkouts without
# core.symlinks=true, some zip-based CI actions) do not preserve real
# symlinks, so self-heal here instead of hard-failing when llama_src is
# present but the symlink got flattened/dropped.
src_link = cpp / "src"
llama_src_dir = cpp / "llama_src"
if not src_link.is_symlink() or not src_link.exists():
    if src_link.exists() and not src_link.is_symlink():
        print(f"WARNING: {src_link} exists but is not a symlink; replacing it")
        if src_link.is_dir():
            import shutil
            shutil.rmtree(src_link)
        else:
            src_link.unlink()
    if llama_src_dir.is_dir():
        try:
            src_link.symlink_to("llama_src")
            print("Recreated src -> llama_src symlink")
        except OSError as e:
            print(f"ERROR: could not create src -> llama_src symlink: {e}")
            sys.exit(1)
    else:
        print("ERROR: src -> llama_src symlink is missing and llama_src/ does not exist")
        sys.exit(1)

if not src_link.is_symlink() or not src_link.exists():
    print("ERROR: src -> llama_src symlink is missing")
    sys.exit(1)

print("NATIVE TREE OK: vendor + mtmd + symlink present")
