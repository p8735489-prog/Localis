#!/usr/bin/env python3
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
FILES={
 'gguf':ROOT/'app/src/main/java/com/localaisearch/data/llm/GGUFEngine.kt',
 'chat':ROOT/'app/src/main/java/com/localaisearch/ui/viewmodel/ChatViewModel.kt',
 'repo':ROOT/'app/src/main/java/com/localaisearch/data/repository/AppModelRepository.kt',
 'jni':ROOT/'app/src/main/cpp/llama_bridge.cpp',
 'settings':ROOT/'app/src/main/java/com/localaisearch/ui/screens/SettingsAIScreen.kt',
}
errors=[]
def read(k):
    p=FILES[k]
    if not p.is_file(): errors.append(f'missing {p}'); return ''
    return p.read_text(encoding='utf-8')
gguf,chat,repo,jni,settings=[read(k) for k in FILES]
checks=[
 ('shared model repository','AppModelRepository.get(application)' in chat),
 ('shared GGUF engine','createProvider(LLMProviderType.LOCAL_GGUF' in repo),
 ('cancellation-safe generation','val generationHandle = synchronized(lock) { modelHandle }' in gguf),
 ('bounded image decode','loadImageBytesSafely' in chat and 'contentResolver.openInputStream(pendingImage)?.use { it.readBytes() }' not in chat and 'contentResolver.openInputStream(imageUri)?.use { it.readBytes() }' not in chat),
 ('validate before replacement',gguf.find('val modelFile = File(filePath)') < gguf.find('if (modelHandle > 0L) unloadModelInternal()')),
 ('native unload stop flag','nativeUnloadModel' in jni and 'g_stopRequested.store(true)' in jni[jni.find('nativeUnloadModel'):jni.find('nativeUnloadModel')+600]),
 ('native vision lock','nativeHasVision' in jni and 'inferenceMutex' in jni[jni.find('nativeHasVision'):jni.find('nativeHasVision')+1200]),
 ('native image bound','kMaxImageBytes' in jni),
 ('native batch allocation checks','could not allocate the prompt batch' in jni and 'could not allocate the multimodal decode batch' in jni),
 ('special-token parsing','llama_tokenize(vocab, prompt, strlen(prompt), nullptr, 0, true, true)' in jni),
 ('settings icon compile fix','KeyboardArrowDown' in settings),
 ('auto unload guarded','if (_isProcessing.value) return' in chat and 'fun checkAutoUnload()' in chat),
]
for name,ok in checks:
    if not ok: errors.append(name)
if errors:
    print('MODEL RUNTIME AUDIT FAILED')
    for e in errors: print(' -',e)
    sys.exit(1)
print('MODEL RUNTIME AUDIT PASSED')
for name,_ in checks: print(' -',name)
