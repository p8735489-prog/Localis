#!/usr/bin/env python3
import re
import sys
from pathlib import Path

if len(sys.argv) != 2:
    raise SystemExit("usage: normalize_mtmd_cmake.py <CMakeLists.txt>")

p = Path(sys.argv[1])
s = p.read_text()

replacement = '''if (BUILD_SHARED_LIBS AND NOT ANDROID AND DEFINED LLAMA_INSTALL_VERSION AND NOT "${LLAMA_INSTALL_VERSION}" STREQUAL "")
    set_target_properties(mtmd PROPERTIES
        VERSION "${LLAMA_INSTALL_VERSION}"
        SOVERSION 0
        MACHO_CURRENT_VERSION 0 # keep macOS linker from seeing oversized version number
    )
endif()
'''

# Match an existing wrapper first. This deliberately consumes its matching
# endif so repeated normalization can never leave nested flow-control blocks.
wrapped = re.compile(
    r'(?ms)^[ \t]*if\s*\(\s*BUILD_SHARED_LIBS\b[^\n]*\)\s*\n'
    r'.*?^[ \t]*set_target_properties\s*\(\s*mtmd\s+PROPERTIES\s*\n'
    r'[ \t]*VERSION\s+\$\{LLAMA_INSTALL_VERSION\}\s*\n'
    r'[ \t]*SOVERSION\s+0\s*\n'
    r'[ \t]*MACHO_CURRENT_VERSION\s+0[^\n]*\n'
    r'[ \t]*\)\s*\n'
    r'[ \t]*endif\s*\(\s*\)\s*\n?'
)

unwrapped = re.compile(
    r'(?ms)^[ \t]*set_target_properties\s*\(\s*mtmd\s+PROPERTIES\s*\n'
    r'[ \t]*VERSION\s+\$\{LLAMA_INSTALL_VERSION\}\s*\n'
    r'[ \t]*SOVERSION\s+0\s*\n'
    r'[ \t]*MACHO_CURRENT_VERSION\s+0[^\n]*\n'
    r'[ \t]*\)\s*\n?'
)

if wrapped.search(s):
    s = wrapped.sub(replacement, s, count=1)
elif unwrapped.search(s):
    s = unwrapped.sub(replacement, s, count=1)
elif 'set_target_properties(mtmd' in s and 'VERSION ${LLAMA_INSTALL_VERSION}' in s:
    raise SystemExit('Unsupported mtmd CMake target-properties block; refusing unsafe rewrite')

p.write_text(s)
