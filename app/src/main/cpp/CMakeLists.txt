cmake_minimum_required(VERSION 3.22.1)
project(localaisearch_native LANGUAGES C CXX ASM)

# Build the complete upstream GGML backend instead of a hand-picked subset.
# Vulkan is enabled for Android builds; the runtime still falls back to CPU when
# the device has no usable Vulkan device.
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
set(GGML_VULKAN ON CACHE BOOL "" FORCE)
set(GGML_OPENCL OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_WEBGPU OFF CACHE BOOL "" FORCE)
set(GGML_RPC OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_MTMD ON CACHE BOOL "" FORCE)
set(MTMD_VIDEO OFF CACHE BOOL "" FORCE)

# Fail the native link if any llama/ggml symbol is missing. Without this, a
# broken .so can make the Kotlin layer report only "native library unavailable".
if(ANDROID)
    add_link_options(-Wl,--no-undefined)
endif()

# Android builds prefer Vulkan acceleration when available. The llama.cpp model
# loader and every model implementation are compiled from the bundled source tree, so GGUF
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

# llama.cpp's common helpers define llama_add_compile_flags(), which is required
# by both the bundled llama source and common/chat-template runtime. The app
# intentionally builds llama.cpp as a subproject, so load the same helper chain
# that upstream CMake loads before adding llama/common targets.
set(LLAMA_CMAKE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/cmake")
list(APPEND CMAKE_MODULE_PATH "${LLAMA_CMAKE_DIR}")
include("${LLAMA_CMAKE_DIR}/common.cmake")

# llama-common contains the current Jinja/chat-template engine used by upstream
# llama.cpp. Expose the app's bundled llama target under the name expected by
# common/ so GGUF-specific templates are formatted exactly as upstream does.
add_library(llama ALIAS local_llama)

# vendor/ must be added before common/ because common/CMakeLists.txt links
# against the cpp-httplib target defined inside vendor/cpp-httplib.
# license_add_file() is stubbed in vendor/CMakeLists.txt for upstream compat.
add_subdirectory(vendor)

add_subdirectory(common)

# libmtmd is the upstream llama.cpp multimodal runtime. The repository checkout
# is populated by tools/fetch_mtmd.sh before Android builds. If the fetch failed
# (upstream restructure, network issue), build without mtmd and emit a warning
# so the build can still succeed; the Kotlin layer falls back to text-only mode.
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(MTMD_AVAILABLE OFF)
if(NOT EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/tools/mtmd/CMakeLists.txt)
    message(FATAL_ERROR
        "Required llama.cpp MTMD sources are missing. "
        "Run tools/fetch_mtmd.sh before configuring the native build. "
        "A successful Localis build must include the real multimodal runtime."
    )
endif()
if(NOT EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/vendor/cpp-httplib/CMakeLists.txt)
    message(FATAL_ERROR
        "Required llama.cpp vendor/cpp-httplib CMakeLists.txt is missing. "
        "Run tools/fetch_mtmd.sh before configuring the native build."
    )
endif()

set(MTMD_AVAILABLE ON)
if(NOT DEFINED LLAMA_VERSION_BASE)
    set(LLAMA_VERSION_BASE 0)
endif()
if(NOT DEFINED LLAMA_VERSION_MAJOR)
    set(LLAMA_VERSION_MAJOR 0)
endif()

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/llama_src/tools/mtmd ${CMAKE_CURRENT_BINARY_DIR}/mtmd)

add_library(llama_bridge SHARED llama_bridge.cpp)
target_include_directories(llama_bridge PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_src
    ${CMAKE_CURRENT_SOURCE_DIR}/ggml/include
)
target_compile_features(llama_bridge PRIVATE cxx_std_17)
target_compile_options(llama_bridge PRIVATE -fexceptions -frtti)
target_link_libraries(llama_bridge PRIVATE local_llama llama-common ggml log android)
set_target_properties(llama_bridge PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
    POSITION_INDEPENDENT_CODE ON
)

if(MTMD_AVAILABLE)
    target_include_directories(llama_bridge PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/llama_src/tools/mtmd)
    target_link_libraries(llama_bridge PRIVATE mtmd)
    target_compile_definitions(llama_bridge PRIVATE LLAMA_MTMD_AVAILABLE=1)
endif()
