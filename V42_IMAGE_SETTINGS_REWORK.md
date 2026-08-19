# v42 Image input / settings / engine coordination

- Image attachment is capability-gated by the active model and matching mmproj projector.
- Text-only models show a disabled gray attachment affordance and a download prompt.
- Settings hub is reorganized into AI, Network, Appearance, Data, General, and About.
- Chat capability state is centralized in ChatViewModel so model, image, memory/search and network UI can coordinate.
- The current native multimodal bridge remains the final gate for actual pixel inference; the UI does not fabricate vision output.
