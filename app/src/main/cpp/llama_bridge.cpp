/**
 * llama_bridge.cpp - JNI bridge between Kotlin GGUFEngine and llama.cpp
 *
 * This file provides the native implementation for LlamaBridge.kt.
 *
 * To enable real GGUF inference:
 * 1. Build llama.cpp for Android (see CMakeLists.txt for instructions)
 * 2. Uncomment the llama.cpp API calls in the functions below
 * 3. Link the static libraries in CMakeLists.txt
 *
 * Until llama.cpp is linked, this file compiles as a stub that returns
 * safe default values, allowing the app to build and run in demo mode.
 */

#include <jni.h>
#include <android/log.h>
#include <string>
#include <atomic>

#define LOG_TAG "LlamaBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ─── llama.cpp headers (uncomment when integrated) ──────────────────
// #include "llama.h"
// #include "ggml.h"
// ────────────────────────────────────────────────────────────────────

static std::atomic<bool> g_stopRequested{false};

struct ModelContext {
    // llama_model* model = nullptr;
    // llama_context* ctx = nullptr;
    int64_t memoryUsage = 0;
};

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeGetVersion(JNIEnv* env, jobject) {
    // When llama.cpp is linked:
    // return env->NewStringUTF(llama_print_system_info());

    // Stub:
    return env->NewStringUTF("stub-1.0.0");
}

JNIEXPORT jlong JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeLoadModel(
        JNIEnv* env, jobject, jstring jFilePath, jint contextLength,
        jint threads, jboolean useGpu, jint gpuLayers) {

    const char* filePath = env->GetStringUTFChars(jFilePath, nullptr);
    LOGI("Loading GGUF model: %s (ctx=%d, threads=%d, gpu=%d, gpuLayers=%d)",
         filePath, contextLength, threads, useGpu, gpuLayers);

    // ─── Real implementation (uncomment when llama.cpp is linked) ──────
    // llama_model_params model_params = llama_model_default_params();
    // model_params.n_gpu_layers = useGpu ? gpuLayers : 0;
    //
    // llama_model* model = llama_load_model_from_file(filePath, model_params);
    // if (!model) {
    //     LOGE("Failed to load model: %s", filePath);
    //     env->ReleaseStringUTFChars(jFilePath, filePath);
    //     return 0;
    // }
    //
    // llama_context_params ctx_params = llama_context_default_params();
    // ctx_params.n_ctx = contextLength;
    // ctx_params.n_threads = threads;
    // ctx_params.n_threads_batch = threads;
    //
    // llama_context* ctx = llama_new_context_with_model(model, ctx_params);
    // if (!ctx) {
    //     LOGE("Failed to create context");
    //     llama_free_model(model);
    //     env->ReleaseStringUTFChars(jFilePath, filePath);
    //     return 0;
    // }
    //
    // auto* mc = new ModelContext();
    // mc->model = model;
    // mc->ctx = ctx;
    // env->ReleaseStringUTFChars(jFilePath, filePath);
    // return reinterpret_cast<jlong>(mc);
    // ────────────────────────────────────────────────────────────────────

    // Stub:
    auto* mc = new ModelContext();
    mc->memoryUsage = 0;
    env->ReleaseStringUTFChars(jFilePath, filePath);
    return reinterpret_cast<jlong>(mc);
}

JNIEXPORT void JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeFreeModel(JNIEnv*, jobject, jlong modelHandle) {
    auto* mc = reinterpret_cast<ModelContext*>(modelHandle);
    if (mc) {
        // if (mc->ctx) llama_free(mc->ctx);
        // if (mc->model) llama_free_model(mc->model);
        delete mc;
        LOGI("Model freed");
    }
}

JNIEXPORT jlong JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeInitContext(
        JNIEnv*, jobject, jlong modelHandle, jfloat temperature, jfloat topP,
        jint topK, jfloat repeatPenalty, jint maxTokens, jint seed) {

    auto* mc = reinterpret_cast<ModelContext*>(modelHandle);
    if (!mc) return 0;

    LOGI("Init context: temp=%.2f, topP=%.2f, topK=%d, repeatPenalty=%.2f, maxTokens=%d, seed=%d",
         temperature, topP, topK, repeatPenalty, maxTokens, seed);

    // ─── Real implementation ────────────────────────────────────────────
    // llama_context_params params = llama_context_default_params();
    // params.n_ctx = mc->ctx_size;
    // params.n_threads = mc->threads;
    // // Store sampling params for use in generateNext
    // llama_context* ctx = llama_new_context_with_model(mc->model, params);
    // return reinterpret_cast<jlong>(ctx);
    // ────────────────────────────────────────────────────────────────────

    // Stub: return model handle as context
    return reinterpret_cast<jlong>(new ModelContext());
}

JNIEXPORT jboolean JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativePrompt(
        JNIEnv* env, jobject, jlong contextHandle, jstring jPrompt) {

    const char* prompt = env->GetStringUTFChars(jPrompt, nullptr);
    LOGI("Prompt: %.100s", prompt);

    // ─── Real implementation ────────────────────────────────────────────
    // auto* ctx = reinterpret_cast<llama_context*>(contextHandle);
    // llama_model* model = llama_get_model(ctx);
    //
    // // Tokenize prompt
    // std::vector<llama_token> tokens = llama_tokenize(model, prompt, true);
    // llama_batch batch = llama_batch_init(tokens.size(), 0, 1);
    //
    // for (size_t i = 0; i < tokens.size(); i++) {
    //     batch.token[i] = tokens[i];
    //     batch.pos[i] = i;
    //     batch.n_seq_id[i] = 1;
    //     batch.seq_id[i][0] = 0;
    // }
    // batch.n_tokens = tokens.size();
    //
    // if (llama_decode(ctx, batch) != 0) {
    //     LOGE("Failed to decode prompt");
    //     llama_batch_free(batch);
    //     env->ReleaseStringUTFChars(jPrompt, prompt);
    //     return false;
    // }
    // llama_batch_free(batch);
    // ────────────────────────────────────────────────────────────────────

    g_stopRequested = false;
    env->ReleaseStringUTFChars(jPrompt, prompt);
    return true;
}

JNIEXPORT jstring JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeGenerateNext(JNIEnv* env, jobject, jlong contextHandle) {

    if (g_stopRequested.load()) {
        return nullptr;
    }

    // ─── Real implementation ────────────────────────────────────────────
    // auto* ctx = reinterpret_cast<llama_context*>(contextHandle);
    // llama_model* model = llama_get_model(ctx);
    //
    // llama_token new_token = llama_sampler_sample(sampler, ctx, -1);
    // if (new_token == llama_token_eos(model)) {
    //     return nullptr;
    // }
    //
    // char buf[128];
    // int n = llama_token_to_piece(model, new_token, buf, sizeof(buf));
    // if (n < 0) return nullptr;
    //
    // // Feed token back
    // llama_batch batch = llama_batch_init(1, 0, 1);
    // batch.token[0] = new_token;
    // batch.pos[0] = llama_kv_cache_pos(ctx);
    // batch.n_seq_id[0] = 1;
    // batch.seq_id[0][0] = 0;
    // batch.n_tokens = 1;
    // llama_decode(ctx, batch);
    // llama_batch_free(batch);
    //
    // return env->NewStringUTF(std::string(buf, n));
    // ────────────────────────────────────────────────────────────────────

    // Stub: return nullptr to end generation immediately
    return nullptr;
}

JNIEXPORT void JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeStopGeneration(JNIEnv*, jobject, jlong) {
    g_stopRequested.store(true);
    LOGI("Generation stop requested");
}

JNIEXPORT void JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeFreeContext(JNIEnv*, jobject, jlong contextHandle) {
    auto* ctx = reinterpret_cast<ModelContext*>(contextHandle);
    if (ctx) {
        // llama_free(ctx);
        delete ctx;
    }
}

JNIEXPORT jlong JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeGetMemoryUsage(JNIEnv*, jobject, jlong modelHandle) {
    auto* mc = reinterpret_cast<ModelContext*>(modelHandle);
    if (mc) return mc->memoryUsage;
    return 0;
}

JNIEXPORT jboolean JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeIsGpuAvailable(JNIEnv*, jobject) {
    // ─── Real implementation ────────────────────────────────────────────
    // const char* info = llama_print_system_info();
    // return strstr(info, "Vulkan") != nullptr || strstr(info, "OpenCL") != nullptr;
    // ────────────────────────────────────────────────────────────────────

    // Stub:
    return false;
}

} // extern "C"
