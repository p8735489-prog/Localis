cmake_minimum_required(VERSION 3.22.1)
project(llama_bridge)

# Android Gradle Plugin does not auto-inject LIBRARY_NAME for CMake builds,
# so we define it explicitly to match our project name.
set(LIBRARY_NAME "llama_bridge")

# ─── llama.cpp Integration ───────────────────────────────────────────
# To enable local GGUF inference, you must build llama.cpp as a static
# library and link it here. See README.md for detailed instructions.
#
# Quick steps:
# 1. Clone llama.cpp: git clone https://github.com/ggerganov/llama.cpp
# 2. Build for Android (arm64-v8a / x86_64) with Vulkan/OpenCL for GPU
# 3. Set LLAMA_CPP_DIR to the build output directory
# 4. Uncomment the lines below
#
# set(LLAMA_CPP_DIR "" CACHE PATH "Path to llama.cpp build directory")
#
# add_library(llama STATIC IMPORTED)
# set_target_properties(llama PROPERTIES
#     IMPORTED_LOCATION ${LLAMA_CPP_DIR}/libllama.a
#     INTERFACE_INCLUDE_DIRECTORIES ${LLAMA_CPP_DIR}/../include
# )
#
# add_library(ggml STATIC IMPORTED)
# set_target_properties(ggml PROPERTIES
#     IMPORTED_LOCATION ${LLAMA_CPP_DIR}/libggml.a
# )
# ────────────────────────────────────────────────────────────────────

add_library(${LIBRARY_NAME} SHARED
    llama_bridge.cpp
)

# Uncomment when llama.cpp is linked:
# target_link_libraries(${LIBRARY_NAME}
#     llama
#     ggml
#     log
#     android
# )

# Stub linkage (no llama.cpp):
target_link_libraries(${LIBRARY_NAME}
    log
    android
)
