#!/usr/bin/env python3
"""Fast source-level checks run before GitHub Release compilation."""
from pathlib import Path
import re, sys, xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java'
RES = ROOT / 'app/src/main/res'
errors=[]

# 1) XML resources parse and every locale contains every string key.
locale_maps={}
for p in sorted(RES.glob('values*/strings.xml')):
    try:
        root=ET.parse(p).getroot()
        
        keys=[e.attrib['name'] for e in root.findall('string')]
        if len(keys) != len(set(keys)):
            dup=sorted({k for k in keys if keys.count(k)>1})
            errors.append(f'{p.parent.name}: duplicate string keys: {dup}')
        locale_maps[p.parent.name]=set(keys)
    except Exception as e:
        errors.append(f'Invalid XML: {p}: {e}')
if locale_maps:
    base=locale_maps.get('values', set())
    for name, keys in locale_maps.items():
        missing=base-keys
        extra=keys-base
        if missing: errors.append(f'{name}: missing {len(missing)} string keys: {sorted(missing)[:10]}')
        if extra: errors.append(f'{name}: extra {len(extra)} string keys: {sorted(extra)[:10]}')

# 2) R.string references must exist in the base resource set.
refs=set()
for p in JAVA.rglob('*.kt'):
    s=p.read_text(errors='ignore')
    refs |= set(re.findall(r'R\.string\.([A-Za-z0-9_]+)', s))
missing=refs-locale_maps.get('values', set())
if missing: errors.append(f'Kotlin references missing R.string keys: {sorted(missing)}')

# 3) Files using app R must import it unless they are in the app package itself or use android.R.
for p in JAVA.rglob('*.kt'):
    s=p.read_text(errors='ignore')
    if re.search(r'(?<![A-Za-z0-9_])R\.', s) and 'android.R.' not in s and 'import com.localaisearch.R' not in s and not s.startswith('package com.localaisearch\n'):
        errors.append(f'Missing com.localaisearch.R import: {p.relative_to(ROOT)}')

# 4) Detect common hardcoded user-facing Compose strings.
ui_hardcoded=[]
for p in (ROOT/'app/src/main/java/com/localaisearch/ui').rglob('*.kt'):
    s=p.read_text(errors='ignore')
    for m in re.finditer(r'\bText\(\s*"([^"$\n]{2,})"\s*[,)]', s):
        value=m.group(1)
        if not value.startswith('Localis') and not value.startswith('!') and not value.startswith('\\u'):
            ui_hardcoded.append(f'{p.relative_to(ROOT)}: {value}')
    for m in re.finditer(r'contentDescription\s*=\s*"([^"$\n]{2,})"', s):
        ui_hardcoded.append(f'{p.relative_to(ROOT)}: contentDescription={m.group(1)}')
if ui_hardcoded:
    errors.append('Hardcoded user-facing UI strings: ' + '; '.join(ui_hardcoded[:12]))

# 5) Check every JNI external declaration has a corresponding exported JNI function.
bridge=JAVA/'com/localaisearch/data/llm/LlamaBridge.kt'
cpp=ROOT/'app/src/main/cpp/llama_bridge.cpp'
if bridge.exists() and cpp.exists():
    decls=re.findall(r'external fun (native[A-Za-z0-9_]+)\s*\(', bridge.read_text())
    native=cpp.read_text(errors='ignore')
    for d in decls:
        if f'Java_com_localaisearch_data_llm_LlamaBridge_{d}' not in native:
            errors.append(f'Missing JNI implementation: {d}')

# 6) Basic delimiter balance on Kotlin files catches accidental patch corruption early.
for p in JAVA.rglob('*.kt'):
    s=p.read_text(errors='ignore')
    if s.count('{') != s.count('}') or s.count('(') != s.count(')'):
        errors.append(f'Unbalanced Kotlin delimiters: {p.relative_to(ROOT)}')


# 7) Native CMake must load llama.cpp helper before common/mtmd.
cmake=ROOT/'app/src/main/cpp/CMakeLists.txt'
cmake_text=cmake.read_text(errors='ignore') if cmake.exists() else ''
if 'include("${LLAMA_CMAKE_DIR}/common.cmake")' not in cmake_text:
    errors.append('Native CMake does not load bundled llama.cpp cmake/common.cmake before common target.')
helper=ROOT/'app/src/main/cpp/cmake/common.cmake'
if not helper.exists() or 'function(llama_add_compile_flags)' not in helper.read_text(errors='ignore'):
    errors.append('Bundled llama CMake helper missing llama_add_compile_flags().')

if errors:
    print('CI PREFLIGHT FAILED')
    for e in errors: print(' -', e)
    sys.exit(1)
print('CI PREFLIGHT PASSED')
print(f'Locales checked: {len(locale_maps)}')
print(f'Kotlin files checked: {len(list(JAVA.rglob("*.kt")))}')
