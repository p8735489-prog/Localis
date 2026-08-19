# llama.cpp Native / GGUF audit

## What was checked

- JNI declaration/implementation parity for every `LlamaBridge.native*` method.
- Android CMake -> GGML -> llama.cpp -> `libllama_bridge.so` dependency chain.
- Complete bundled llama.cpp model source glob (`llama_src/models/*.cpp`).
- CPU GGML backend configuration for Android.
- GGUF magic validation and app-private model paths.
- Model load failure reporting.
- Context allocation failure reporting.
- Native model lifetime / unload races.
- Native generation lifetime / unload races.
- Token-piece buffer growth.
- Sampler initialization failure handling.
- Runtime GPU capability reporting.

## Important result

The project contains the llama.cpp model implementations and GGML sources, but the previous native layer had several reliability gaps. The main one was not the presence of llama.cpp: it was the path from CMake to a valid `libllama_bridge.so` and the lack of actionable native errors.

## Changes in this revision

1. Native link now uses `--no-undefined` on Android so missing native symbols fail the build instead of producing a broken runtime library.
2. The bridge validates that the selected file exists and starts with the GGUF magic before calling llama.cpp.
3. Native load failures now expose the last native error to Kotlin.
4. CPU-only builds no longer pass GPU layers to llama.cpp. GPU capability is queried from the actual native backend.
5. Model unload waits for in-flight native work, closing a use-after-free path during model switching.
6. Tokenize, detokenize and chat-template operations use the same per-model lifetime lock.
7. Token-piece output dynamically grows when a token needs more than the initial buffer.
8. Sampler creation is checked at every stage.
9. Native process RSS is exposed as a practical memory-usage signal after model load.
10. JNI method parity was checked: every `external fun native*` declaration has a corresponding JNI implementation.

## GGUF scope

The loader delegates model interpretation to the bundled llama.cpp snapshot rather than maintaining an app-side architecture allow-list. That means supported architectures and quantization types follow the bundled llama.cpp source. It does **not** mean every GGUF ever produced is guaranteed to load: unsupported/new architectures, corrupt files, incompatible metadata, insufficient memory, or required multimodal components can still prevent loading.

## Current backend limitation

This Android build intentionally enables the GGML CPU backend. The model/GGUF support is broad, but GPU offload is not enabled by this native build yet. The Kotlin layer now detects that honestly instead of pretending GPU support exists.

## Build verification

The source-level audit passed. A final Android/NDK build could not be executed in this environment because the Gradle wrapper requires downloading Gradle 8.9 and external DNS/network access is unavailable. No APK build success is claimed.
