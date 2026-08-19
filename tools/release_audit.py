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
for name in ('SIGNING_KEYSTORE_B64','SIGNING_STORE_PASSWORD','SIGNING_KEY_ALIAS','SIGNING_KEY_PASSWORD'):
    if name not in workflow:
        errors.append(f'Missing CI signing secret: {name}')
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

if errors:
    print('RELEASE AUDIT FAILED')
    for e in errors: print(' -',e)
    sys.exit(1)
print('RELEASE AUDIT PASSED')
print(f'JNI methods checked: {len(methods)}')
