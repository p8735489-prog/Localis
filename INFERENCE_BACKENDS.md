# Localis inference backend architecture

## Policy

Localis uses **llama.cpp as the compatibility-first GGUF runtime**. The app does not claim GPU/NPU support merely because a phone exposes an accelerator; a native backend must actually be compiled and report availability before it is selected.

### Primary: llama.cpp
- GGUF is the primary local model format.
- Model architecture and quantization support follow the bundled llama.cpp build.
- CPU is the reliable baseline.
- GPU/NPU flags are reported only when the corresponding native backend is really present.

### Optional: MNN
MNN is kept as an optional acceleration adapter rather than replacing the GGUF path. MNN currently has Android LLM support and multiple backends; the adapter should only be enabled after its native libraries are bundled and compatibility-tested. The project currently does **not** pretend that MNN is already inside the APK.

### Optional: Cactus
Cactus is another on-device inference runtime with Kotlin/Android bindings. Its native model format/runtime is not the same as GGUF, so Localis keeps it behind a separate adapter boundary rather than converting every GGUF model just to use Cactus.

### React Native / Flutter
Onde Inference and Quaynor are not included in this Android-native app because they solve a framework-specific integration problem. Adding them here would increase runtime size without improving the native Android path.

## Routing

`AUTO` currently routes GGUF to llama.cpp. MNN/Cactus adapters become eligible only after their native runtime reports availability and the model has been validated for that runtime.

This avoids a dangerous false promise such as "GPU/NPU supported" when only a UI switch exists.
