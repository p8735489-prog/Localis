# v39 Model Engine Audit

## Root cause fixed
The previous chat path used `llama_chat_apply_template()` directly and, when it returned an empty result,
fell back to a generic `<system>/<user>/<assistant>` prompt. That fallback is not a universal GGUF
chat format and can cause Qwen/Llama/Gemma-family models to echo control markers such as
`<|im_start|>` / `<|im_end|>` or otherwise expose the prompt.

## v39 fix
- Bundles and links llama.cpp `common/chat.cpp` and its Jinja chat-template implementation.
- Uses `common_chat_templates_init(model, "")` + `common_chat_templates_apply()` so the GGUF metadata
  and upstream llama.cpp template implementation determine the prompt format.
- If no usable template can be produced, inference fails clearly instead of sending a broken fallback
  prompt to the model.
- Final web-search answers now use `chatStream()` too, so search mode cannot bypass the GGUF chat template.
- Adds a defensive output filter for common control markers before they reach the UI.
- Keeps EOS/BOS stopping in native generation.
- Keeps the existing native inference mutex and single-generation guard.

## Expected behavior
For a Qwen3 GGUF with a valid chat template, the visible answer should contain only the assistant
content. Template/control tokens are not displayed as ordinary answer text.

## Verification boundary
This package was statically audited. A real Android Release build still needs to run on the GitHub
Actions runner with the configured Android SDK/NDK and the project's signing secrets.
