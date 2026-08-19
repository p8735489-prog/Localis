# LocalAISearch v28

UI and inference update:
- Added frequency penalty and presence penalty controls from -2.0 to 2.0.
- Zero is displayed as Unspecified and maps to no extra penalty.
- Penalties are persisted and passed to llama.cpp sampler.
- Sidebar now shows recent conversations inline instead of a separate history card; selecting one loads it directly.
- Added a larger neutral rounded empty-state card behind the model selector/orb to prevent clipped top-only backgrounds and improve GPT-like layout.
