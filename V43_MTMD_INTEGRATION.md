# Localis v2.1.0 — real llama.cpp mtmd/mmproj vision path

## What changed

The previous image path stopped at capability detection. This version attaches the upstream llama.cpp `libmtmd` runtime to the loaded GGUF model and sends actual image bytes through the projector before decoding the answer.

### Runtime path

```text
Android Content URI
  -> ContentResolver bytes
  -> JNI byte[]
  -> mtmd_helper_bitmap_init_from_buf()
  -> mtmd_tokenize()
  -> mmproj / CLIP vision encoder
  -> mtmd_helper_eval_chunk_single()
  -> llama_context KV / logits
  -> llama.cpp sampler
  -> streamed assistant tokens
```

The upstream llama.cpp documentation describes this architecture as a separate language-model GGUF plus a matching `mmproj` projector, with `libmtmd` providing the multimodal interface. urlllama.cpp mtmd documentationhttps://github.com/ggml-org/llama.cpp/tree/master/tools/mtmd

## Build integration

The app source snapshot did not contain the upstream `tools/mtmd` tree. It now fails closed instead of silently compiling a fake vision adapter. `tools/fetch_mtmd.sh` retrieves the upstream `tools/mtmd` tree and required vendor dependencies before CMake configuration. GitHub Actions runs this step automatically.

The build then links:

`llama_bridge -> local_llama + llama-common + mtmd + ggml`

## Model compatibility

The projector is never treated as a generic image encoder. `mtmd_init_from_file()` validates it against the loaded language model and the native runtime rejects mismatches. This follows llama.cpp's own multimodal design rather than maintaining a separate hard-coded list of vision models.

## Current limits

- The Android build in this repository currently uses the CPU GGML backend, so the vision projector is not falsely reported as GPU/NPU accelerated.
- Video/audio are not enabled in this Android bridge; image input is the supported multimodal path here.
- A matching `mmproj` is required. A text-only GGUF cannot be made vision-capable by the UI alone.
- The upstream mtmd API is experimental and can change; the fetch step is intentionally explicit so CI failures identify an upstream API change instead of producing a partially working vision feature.
