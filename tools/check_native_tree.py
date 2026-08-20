#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
cpp = root / "app/src/main/cpp"
vendor = cpp / "vendor"
mtmd = cpp / "llama_src/tools/mtmd"

required_vendor = [
    vendor / "cpp-httplib/CMakeLists.txt",
    vendor / "cpp-httplib/httplib.h",
    vendor / "nlohmann/json.hpp",
    vendor / "nlohmann/json_fwd.hpp",
]
required_mtmd = [
    mtmd / "CMakeLists.txt",
]

missing = [str(p.relative_to(root)) for p in required_vendor + required_mtmd if not p.exists()]
if missing:
    print("NATIVE TREE INCOMPLETE")
    for p in missing:
        print(" -", p)
    print("Run tools/fetch_mtmd.sh to populate the pinned llama.cpp dependency tree.")
    sys.exit(1)

cmake = (cpp / "CMakeLists.txt").read_text(errors="ignore")
if "add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/llama_src/tools/mtmd" not in cmake:
    print("ERROR: pinned llama.cpp MTMD target is not added")
    sys.exit(1)

print("NATIVE TREE OK: vendor + mtmd present")
