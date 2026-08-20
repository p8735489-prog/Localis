#!/usr/bin/env python3
from pathlib import Path
import re, sys
root=Path(__file__).resolve().parents[1]
errors=[]

gradle=(root/'app/build.gradle.kts').read_text()
wrapper=(root/'gradle/wrapper/gradle-wrapper.properties').read_text()
workflow=(root/'.github/workflows/android-release.yml').read_text()
proguard=(root/'app/proguard-rules.pro').read_text()

if 'kotlinCompilerExtensionVersion' in gradle:
    errors.append('Kotlin 2.x Compose plugin is used; remove legacy kotlinCompilerExtensionVersion.')
if 'services.gradle.org/distributions/gradle-8.9-bin.zip' not in wrapper:
    errors.append('Gradle wrapper is not pinned to the official Gradle 8.9 distribution.')
for name in ('SIGNING_KEYSTORE_PATH','SIGNING_STORE_PASSWORD','SIGNING_KEY_ALIAS','SIGNING_KEY_PASSWORD'):
    if name not in workflow:
        errors.append(f'Missing CI signing env: {name}')
if 'SIGNING_KEYSTORE_B64' not in workflow:
    errors.append('CI must restore the signing keystore from SIGNING_KEYSTORE_B64 secret.')
for line in workflow.splitlines():
    stripped=line.strip()
    if re.match(r'SIGNING_(STORE_PASSWORD|KEY_PASSWORD)\s*:', stripped):
        value=stripped.split(':',1)[1].strip()
        if not value.startswith('${{ secrets.'):
            errors.append('Signing passwords must come from GitHub Actions secrets, not hard-coded workflow values.')
sensitive_ext={'.jks','.keystore','.p12','.pfx','.pkcs12'}
sensitive=[]
for p in root.rglob('*'):
    if p.is_file() and p.suffix.lower() in sensitive_ext:
        sensitive.append(str(p.relative_to(root)))
if sensitive:
    errors.append(f'Signing keystore/certificate files must not be committed: {sensitive}')
if 'com.localaisearch.data.llm.LlamaBridge' not in proguard or '-keepclasseswithmembers class *' not in proguard:
    errors.append('JNI keep rules for LlamaBridge are incomplete.')
if 'org.torproject.jni.TorService' not in proguard:
    errors.append('TorService R8 keep rule is missing.')
if 'versionName = "2.1.0"' not in gradle:
    errors.append('Release versionName is not 2.1.0.')
if 'assembleRelease' not in workflow:
    errors.append('CI does not assemble release APK.')

# Ensure every JNI external declaration has a corresponding C++ symbol.
kt=(root/'app/src/main/java/com/localaisearch/data/llm/LlamaBridge.kt').read_text()
cpp=(root/'app/src/main/cpp/llama_bridge.cpp').read_text()
methods=re.findall(r'external fun (native\w+)\s*\(', kt)
for m in methods:
    sym='Java_com_localaisearch_data_llm_LlamaBridge_'+m
    if sym not in cpp:
        errors.append(f'Missing JNI implementation: {m}')


# Security / CI duplication checks — audit only; never mutate repository files.
workflow_dir=root/'.github/workflows'
canonical='android-release.yml'
remaining=list(workflow_dir.glob('*.yml'))
extra=[]
for w in remaining:
    if w.name == canonical:
        continue
    # The legacy workflow is intentionally retained as a no-op migration shim
    # so uploading over an existing repository disables the old auto-build.
    if w.name == 'build-apk.yml' and 'workflow_dispatch:' in w.read_text(errors='ignore') and 'push:' not in w.read_text(errors='ignore'):
        continue
    extra.append(w.name)
if extra:
    errors.append(f'Stale/duplicate workflows present: {extra}; replace/remove them before release.')

# Native CMake helper must be loaded before llama-common.
cmake=root/'app/src/main/cpp/CMakeLists.txt'
cmake_text=cmake.read_text(errors='ignore')
if 'include("${LLAMA_CMAKE_DIR}/common.cmake")' not in cmake_text:
    errors.append('Bundled llama.cpp CMake helper is not loaded before common/.')
if not (root/'app/src/main/cpp/cmake/common.cmake').exists():
    errors.append('Missing app/src/main/cpp/cmake/common.cmake')

if errors:
    print('RELEASE AUDIT FAILED')
    for e in errors: print(' -',e)
    sys.exit(1)
print('RELEASE AUDIT PASSED')
print(f'JNI methods checked: {len(methods)}')
