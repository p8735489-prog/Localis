cmake_minimum_required(VERSION 3.22.1)
project(localaisearch_native LANGUAGES C CXX ASM)

# Build the complete upstream GGML backend instead of a hand-picked subset.
# The previous bridge omitted several GGML translation units/backends; that could
# make libllama_bridge.so fail to link and left Kotlin reporting "native library
# is unavailable" even though the llama.cpp sources were bundled in the project.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CPU ON CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_BLAS OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_HIP OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_WEBGPU OFF CACHE BOOL "" FORCE)
set(GGML_RPC OFF CACHE BOOL "" FORCE)

# Fail the native link if any llama/ggml symbol is missing. Without this, a
# broken .so can make the Kotlin layer report only "native library unavailable".
if(ANDROID)
    add_link_options(-Wl,--no-undefined)
endif()

# Android builds use the CPU backend by default. The llama.cpp model loader and
# every model implementation are compiled from the bundled source tree, so GGUF
# support follows the complete llama.cpp architecture/quantization support of
# this source snapshot rather than a hard-coded model allow-list.
add_subdirectory(ggml)

file(GLOB LLAMA_MODEL_SOURCES CONFIGURE_DEPENDS
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/models/*.cpp
)

set(LLAMA_CORE_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/llama.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/llama-adapter.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/llama-arch.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/llama-batch.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/llama-chat.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/llama-context.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/llama-cparams.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/llama-grammar.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/llama-graph.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/llama-hparams.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/llama-impl.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/llama-io.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/llama-kv-cache.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/llama-kv-cache-dsa.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/llama-kv-cache-dsv4.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/llama-kv-cache-iswa.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/llama-memory.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/llama-memory-hybrid.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/llama-memory-hybrid-iswa.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/llama-memory-recurrent.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/llama-mmap.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/llama-model-loader.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/llama-model-saver.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/llama-model.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/llama-quant.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/llama-sampler.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/llama-vocab.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/unicode.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/unicode-data.cpp
    ${LLAMA_MODEL_SOURCES}
)

add_library(local_llama STATIC ${LLAMA_CORE_SOURCES})
target_include_directories(local_llama PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src
)
target_compile_features(local_llama PRIVATE cxx_std_17)
target_link_libraries(local_llama PUBLIC ggml)
set_target_properties(local_llama PROPERTIES POSITION_INDEPENDENT_CODE ON)

add_library(llama_bridge SHARED llama_bridge.cpp)
target_include_directories(llama_bridge PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src
    ${CMAKE_CURRENT_SOURCE_DIR}/ggml/include
)
target_compile_features(llama_bridge PRIVATE cxx_std_17)
target_compile_options(llama_bridge PRIVATE -fexceptions -frtti)
target_link_libraries(llama_bridge PRIVATE local_llama ggml log android)
set_target_properties(llama_bridge PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
    POSITION_INDEPENDENT_CODE ON
)
