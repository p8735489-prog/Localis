# Localis — llama.cpp mtmd + mmproj native integration

Localis now uses the upstream llama.cpp multimodal runtime (`libmtmd`) instead of a Java-side vision stub.

Flow:

`Android image URI -> bytes -> JNI -> mtmd_helper_bitmap_init_from_buf -> mtmd_tokenize -> mmproj/CLIP encoder -> mtmd_helper_eval_chunk_single -> llama.cpp context -> sampler -> streamed answer`

The language model and projector remain separate GGUF files, as required by llama.cpp's multimodal design. The upstream documentation describes `libmtmd` as the modern replacement for the older `llava.cpp` path and explains the two-file model + `mmproj` arrangement. 

## Build

The repository contains `tools/fetch_mtmd.sh`. GitHub Actions runs it before Gradle so the upstream `tools/mtmd` tree and its vendor dependencies are present before CMake configures `libmtmd`.

The native bridge fails the build if mtmd sources are missing; it does not silently compile a fake vision implementation.

## Runtime safety

- A vision request requires a loaded language GGUF and a matching `mmproj`.
- The projector is loaded into the same native model context.
- Image bytes are held only for the request and decoded by mtmd.
- Native model operations share the existing per-model inference mutex.
- Projector/context cleanup occurs before model destruction.
- A text-only GGUF cannot receive pixels merely because the UI has an upload button.
- The current CPU build does not claim GPU/NPU acceleration for mtmd.

## Supported scope

Actual vision support follows the exact upstream llama.cpp/mtmd snapshot fetched during the build. `mmproj` compatibility is still architecture-specific; a mismatched projector is rejected by mtmd rather than producing fabricated image descriptions.
