#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <thread>
#include <mutex>
#include <atomic>
#include <chrono>
#include <cstring>
#include <algorithm>
#include <unordered_map>
#include <memory>
#include <fstream>
#include <sstream>
#include <sys/stat.h>
#include <unistd.h>

#include "llama.h"
#include "common/chat.h"
#include "ggml.h"
#ifdef LLAMA_MTMD_AVAILABLE
#include "mtmd.h"
#include "mtmd-helper.h"
#endif

#define LOG_TAG "LlamaBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::atomic<bool> g_stopRequested{false};
static std::atomic<bool> g_initialized{false};
static std::mutex g_backendMutex;
static std::mutex g_modelLoadMutex;

struct ModelContext {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    int64_t memoryUsage = -1;
    std::string modelPath;
#ifdef LLAMA_MTMD_AVAILABLE
    mtmd_context* mtmd = nullptr;
#else
    void* mtmd = nullptr;
#endif
    std::string mmprojPath;
    std::mutex inferenceMutex;
};

static std::unordered_map<int64_t, std::shared_ptr<ModelContext>> g_contexts;
static std::mutex g_mutex;
static int64_t g_nextHandle = 1;
static thread_local std::string g_lastError;

static void setLastError(const std::string & message) {
    g_lastError = message;
    LOGE("%s", message.c_str());
}

static int64_t processResidentBytes() {
    std::ifstream in("/proc/self/statm");
    long pages = 0;
    long rss = 0;
    if (!(in >> pages >> rss)) return -1;
    const long pageSize = sysconf(_SC_PAGESIZE);
    return pageSize > 0 ? static_cast<int64_t>(rss) * pageSize : -1;
}

static bool looksLikeGGUF(const char * path) {
    std::ifstream in(path, std::ios::binary);
    char magic[4]{};
    return static_cast<bool>(in.read(magic, sizeof(magic))) &&
           magic[0] == 'G' && magic[1] == 'G' && magic[2] == 'U' && magic[3] == 'F';
}

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeGetVersion(JNIEnv* env, jobject) {
    return env->NewStringUTF(llama_print_system_info());
}

JNIEXPORT jstring JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeGetLastError(JNIEnv* env, jobject) {
    return env->NewStringUTF(g_lastError.empty() ? "" : g_lastError.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeSupportsGpu(JNIEnv*, jobject) {
    return llama_supports_gpu_offload() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeShutdown(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> backendLock(g_backendMutex);
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_contexts.empty()) {
        setLastError("Cannot shut down llama.cpp while models are still loaded");
        return JNI_FALSE;
    }
    if (g_initialized.exchange(false)) {
        llama_backend_free();
    }
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeInitialize(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> backendLock(g_backendMutex);
    if (!g_initialized.load()) {
        ggml_time_init();
        llama_backend_init();
        g_initialized.store(true);
        LOGI("llama.cpp initialized successfully");
    }
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeLoadModel(
        JNIEnv* env, jobject, jstring jFilePath, jint contextLength,
        jint threads, jboolean useGpu, jint gpuLayers) {

    // Only one language model is supported by the Android engine at a time.
    // Serialize native loads as well as Kotlin-side repository operations: the
    // latter can be bypassed by a second ViewModel or a stale UI event.
    std::unique_lock<std::mutex> loadLock(g_modelLoadMutex);
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        if (!g_contexts.empty()) {
            setLastError("A GGUF model is already loaded. Unload it before loading another model.");
            return 0;
        }
    }

    const char* filePath = env->GetStringUTFChars(jFilePath, nullptr);
    if (!filePath || !*filePath) {
        setLastError("Model path is empty");
        if (filePath) env->ReleaseStringUTFChars(jFilePath, filePath);
        return 0;
    }
    LOGI("Loading GGUF model: %s (ctx=%d, threads=%d, gpu=%d, gpuLayers=%d)",
         filePath, contextLength, threads, useGpu, gpuLayers);

    struct stat st{};
    if (stat(filePath, &st) != 0 || !S_ISREG(st.st_mode)) {
        setLastError(std::string("Model file is not accessible: ") + filePath);
        env->ReleaseStringUTFChars(jFilePath, filePath);
        return 0;
    }
    if (!looksLikeGGUF(filePath)) {
        setLastError(std::string("Not a GGUF file (invalid GGUF magic): ") + filePath);
        env->ReleaseStringUTFChars(jFilePath, filePath);
        return 0;
    }
    if (contextLength <= 0 || threads <= 0) {
        setLastError("Invalid context length or thread count");
        env->ReleaseStringUTFChars(jFilePath, filePath);
        return 0;
    }

    llama_model_params model_params = llama_model_default_params();
    // This build intentionally ships the CPU GGML backend. Do not pass GPU
    // layers to a CPU-only binary: doing so can make a valid GGUF fail during
    // initialization and look like a native-library/model compatibility bug.
    model_params.n_gpu_layers = (useGpu && llama_supports_gpu_offload())
            ? std::max(0, gpuLayers) : 0;

    g_lastError.clear();
    llama_model* model = llama_model_load_from_file(filePath, model_params);
    if (!model) {
        setLastError(std::string("llama.cpp rejected the GGUF model. The file may use an unsupported architecture, be incomplete/corrupt, or exceed available memory: ") + filePath);
        env->ReleaseStringUTFChars(jFilePath, filePath);
        return 0;
    }

    const int32_t trainContext = llama_model_n_ctx_train(model);
    int32_t effectiveContext = std::max(512, contextLength);
    if (trainContext > 0) {
        effectiveContext = std::min(effectiveContext, trainContext);
    }
    effectiveContext = std::min(effectiveContext, 32768);
    const int32_t effectiveThreads = std::clamp(threads, 1, 64);

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = static_cast<uint32_t>(effectiveContext);
    ctx_params.n_threads = effectiveThreads;
    ctx_params.n_threads_batch = effectiveThreads;

    llama_context* ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        setLastError(std::string("GGUF loaded, but llama.cpp could not allocate the requested context. Reduce context length or close other apps: ") + filePath);
        llama_model_free(model);
        env->ReleaseStringUTFChars(jFilePath, filePath);
        return 0;
    }

    std::lock_guard<std::mutex> lock(g_mutex);
    int64_t handle = g_nextHandle++;
    auto mc = std::make_shared<ModelContext>();
    mc->model = model;
    mc->ctx = ctx;
    mc->modelPath = std::string(filePath);
    mc->memoryUsage = processResidentBytes();
    g_contexts[handle] = mc;

    LOGI("Model loaded successfully. Handle=%ld, n_ctx=%d, train_ctx=%d, threads=%d", handle,
         llama_n_ctx(ctx), trainContext, effectiveThreads);
    env->ReleaseStringUTFChars(jFilePath, filePath);
    return handle;
}

JNIEXPORT jboolean JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeUnloadModel(JNIEnv*, jobject, jlong handle) {
    std::shared_ptr<ModelContext> mc;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        auto it = g_contexts.find(handle);
        if (it == g_contexts.end()) return JNI_FALSE;
        mc = it->second;
    }

    // Wait for any in-flight tokenize/chat/generation operation before freeing
    // the model. This closes a native use-after-free race that could crash the
    // whole Android process during model switching.
    std::lock_guard<std::mutex> inferenceLock(mc->inferenceMutex);
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        auto it = g_contexts.find(handle);
        if (it == g_contexts.end() || it->second != mc) return JNI_FALSE;
#ifdef LLAMA_MTMD_AVAILABLE
        if (mc->mtmd) { mtmd_free(mc->mtmd); mc->mtmd = nullptr; }
#endif
        if (mc->ctx) { llama_free(mc->ctx); mc->ctx = nullptr; }
        if (mc->model) { llama_model_free(mc->model); mc->model = nullptr; }
        g_contexts.erase(it);
    }
    // shared_ptr keeps the native context alive for any operation that raced
    // with unload after taking a snapshot from g_contexts.
    LOGI("Model unloaded. Handle=%ld", handle);
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeGetMemoryUsage(JNIEnv*, jobject, jlong handle) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_contexts.find(handle);
    if (it == g_contexts.end()) return -1;
    const int64_t rss = processResidentBytes();
    return rss > 0 ? rss : it->second->memoryUsage;
}

JNIEXPORT jboolean JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeStopGeneration(JNIEnv*, jobject) {
    g_stopRequested.store(true);
    LOGI("Stop generation requested");
    return JNI_TRUE;
}


// Models should not expose chat-control markers in the visible answer. This is
// a defensive last line for templates whose stop tokens are represented as
// ordinary text pieces by an unusual tokenizer/runtime combination.
static std::string sanitizeGeneratedPiece(const std::string & piece) {
    static const char* const controlTags[] = {
        "<|im_end|>", "<|im_start|>", "<|endoftext|>", "<|eot_id|>",
        "<|eom_id|>", "<|assistant|>", "<|user|>", "<|system|>",
        "<|end|>", "</s>", "<s>"
    };
    std::string out = piece;
    for (const char* tag : controlTags) {
        size_t pos = 0;
        while ((pos = out.find(tag, pos)) != std::string::npos) {
            out.erase(pos, std::strlen(tag));
        }
    }
    return out;
}


JNIEXPORT jboolean JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeLoadVisionProjector(
        JNIEnv* env, jobject, jlong handle, jstring jMmprojPath, jint threads, jboolean useGpu) {
#ifndef LLAMA_MTMD_AVAILABLE
    setLastError("Multimodal runtime (mtmd) was not compiled into this build");
    return JNI_FALSE;
#else
    std::shared_ptr<ModelContext> mc;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        auto it = g_contexts.find(handle);
        if (it == g_contexts.end() || !it->second) return JNI_FALSE;
        mc = it->second;
    }
    std::lock_guard<std::mutex> inferenceLock(mc->inferenceMutex);
    if (!mc->model || !mc->ctx) {
        setLastError("Cannot load vision projector before the GGUF model is loaded");
        return JNI_FALSE;
    }
    const char* path = env->GetStringUTFChars(jMmprojPath, nullptr);
    if (!path || !*path) {
        if (path) env->ReleaseStringUTFChars(jMmprojPath, path);
        setLastError("Vision projector path is empty");
        return JNI_FALSE;
    }
    struct stat st{};
    if (stat(path, &st) != 0 || !S_ISREG(st.st_mode)) {
        setLastError(std::string("Vision projector is not accessible: ") + path);
        env->ReleaseStringUTFChars(jMmprojPath, path);
        return JNI_FALSE;
    }
    if (mc->mtmd) {
        mtmd_free(mc->mtmd);
        mc->mtmd = nullptr;
    }
    mtmd_context_params params = mtmd_context_params_default();
    params.use_gpu = useGpu && llama_supports_gpu_offload();
    params.print_timings = false;
    params.n_threads = std::max(1, (int)threads);
    params.warmup = true;
    params.batch_max_tokens = 1024;
    mc->mtmd = mtmd_init_from_file(path, mc->model, params);
    if (!mc->mtmd) {
        setLastError(std::string("llama.cpp mtmd could not load the vision projector: ") + path +
                     ". Make sure the mmproj matches the language model architecture and embedding size.");
        env->ReleaseStringUTFChars(jMmprojPath, path);
        return JNI_FALSE;
    }
    if (!mtmd_support_vision(mc->mtmd)) {
        mtmd_free(mc->mtmd);
        mc->mtmd = nullptr;
        setLastError("The supplied mmproj does not expose image/vision input support");
        env->ReleaseStringUTFChars(jMmprojPath, path);
        return JNI_FALSE;
    }
    mc->mmprojPath = path;
    env->ReleaseStringUTFChars(jMmprojPath, path);
    LOGI("mtmd vision projector loaded successfully");
    return JNI_TRUE;
#endif
}

JNIEXPORT jboolean JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeHasVision(JNIEnv*, jobject, jlong handle) {
#ifndef LLAMA_MTMD_AVAILABLE
    return JNI_FALSE;
#else
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_contexts.find(handle);
    if (it == g_contexts.end() || !it->second || !it->second->mtmd) return JNI_FALSE;
    return mtmd_support_vision(it->second->mtmd) ? JNI_TRUE : JNI_FALSE;
#endif
}

JNIEXPORT jboolean JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeGenerateMultimodalStream(
        JNIEnv* env, jobject, jlong handle, jstring jPrompt, jbyteArray jImage,
        jfloat temperature, jint maxTokens, jint topK, jfloat topP,
        jfloat repeatPenalty, jfloat frequencyPenalty, jfloat presencePenalty, jobject callback) {
#ifndef LLAMA_MTMD_AVAILABLE
    setLastError("Multimodal runtime (mtmd) was not compiled into this build");
    return JNI_FALSE;
#else
    std::shared_ptr<ModelContext> mc;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        auto it = g_contexts.find(handle);
        if (it == g_contexts.end() || !it->second) return JNI_FALSE;
        mc = it->second;
    }
    std::unique_lock<std::mutex> inferenceLock(mc->inferenceMutex);
    if (!mc->ctx || !mc->model || !mc->mtmd || !callback) {
        setLastError("Multimodal runtime is not ready. Load a matching mmproj before sending an image.");
        return JNI_FALSE;
    }
    if (!mtmd_support_vision(mc->mtmd)) {
        setLastError("The loaded mmproj does not support vision input");
        return JNI_FALSE;
    }

    const char* prompt = env->GetStringUTFChars(jPrompt, nullptr);
    if (!prompt) return JNI_FALSE;
    jsize imageLen = env->GetArrayLength(jImage);
    if (imageLen <= 0) {
        env->ReleaseStringUTFChars(jPrompt, prompt);
        setLastError("Image data is empty");
        return JNI_FALSE;
    }
    std::vector<unsigned char> image((size_t) imageLen);
    env->GetByteArrayRegion(jImage, 0, imageLen, reinterpret_cast<jbyte*>(image.data()));

    auto bitmapResult = mtmd_helper_bitmap_init_from_buf(mc->mtmd, image.data(), image.size(), false);
    if (!bitmapResult.bitmap) {
        env->ReleaseStringUTFChars(jPrompt, prompt);
        setLastError("mtmd could not decode the supplied image");
        return JNI_FALSE;
    }
    mtmd_bitmap* bitmap = bitmapResult.bitmap;
    if (std::string(prompt).find(mtmd_default_marker()) == std::string::npos) {
        mtmd_bitmap_free(bitmap);
        env->ReleaseStringUTFChars(jPrompt, prompt);
        setLastError("The formatted chat template did not preserve llama.cpp's multimodal image marker");
        return JNI_FALSE;
    }
    mtmd_input_chunks* chunks = mtmd_input_chunks_init();
    mtmd_input_text inputText{prompt, strlen(prompt), true, true};
    const mtmd_bitmap* bitmaps[] = {bitmap};
    int32_t tokenizeResult = mtmd_tokenize(mc->mtmd, chunks, &inputText, bitmaps, 1);
    env->ReleaseStringUTFChars(jPrompt, prompt);
    mtmd_bitmap_free(bitmap);
    if (tokenizeResult != 0) {
        mtmd_input_chunks_free(chunks);
        setLastError("mtmd failed to tokenize the multimodal prompt. The prompt must contain the image marker.");
        return JNI_FALSE;
    }

    llama_memory_clear(llama_get_memory(mc->ctx), true);
    llama_pos nPast = 0;
    const size_t nChunks = mtmd_input_chunks_size(chunks);
    const int nBatch = std::max(1, std::min(1024, (int)llama_n_ctx(mc->ctx)));
    for (size_t i = 0; i < nChunks; ++i) {
        const mtmd_input_chunk* chunk = mtmd_input_chunks_get(chunks, i);
        llama_pos nextPast = nPast;
        int32_t res = mtmd_helper_eval_chunk_single(mc->mtmd, mc->ctx, chunk, nPast, 0, nBatch,
                                                     i == nChunks - 1, &nextPast);
        if (res != 0) {
            mtmd_input_chunks_free(chunks);
            setLastError("llama.cpp failed while evaluating the multimodal image/text chunk");
            return JNI_FALSE;
        }
        nPast = nextPast;
    }
    mtmd_input_chunks_free(chunks);

    const llama_vocab* vocab = llama_model_get_vocab(mc->model);
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
    if (!onToken) {
        setLastError("JNI TokenCallback.onToken(String) was not found");
        return JNI_FALSE;
    }
    g_lastError.clear();
    g_stopRequested.store(false);

    llama_batch batch = llama_batch_init(1, 0, 1);
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler* smpl = llama_sampler_chain_init(sparams);
    if (!smpl) {
        llama_batch_free(batch);
        setLastError("llama.cpp could not create the multimodal sampler");
        return JNI_FALSE;
    }
    auto addSampler = [&](llama_sampler* sampler) {
        if (!sampler) return false;
        llama_sampler_chain_add(smpl, sampler);
        return true;
    };
    if (!addSampler(llama_sampler_init_penalties(64, repeatPenalty, frequencyPenalty, presencePenalty)) ||
        !addSampler(llama_sampler_init_top_k(std::max(1, topK))) ||
        !addSampler(llama_sampler_init_top_p(std::clamp(topP, 0.0f, 1.0f), 1)) ||
        !addSampler(llama_sampler_init_temp(std::max(0.0f, temperature))) ||
        !addSampler(llama_sampler_init_dist((uint32_t)std::chrono::steady_clock::now().time_since_epoch().count()))) {
        llama_sampler_free(smpl);
        llama_batch_free(batch);
        setLastError("llama.cpp could not initialize the multimodal sampler");
        return JNI_FALSE;
    }

    const int nCtx = llama_n_ctx(mc->ctx);
    const llama_token eos = llama_vocab_eos(vocab);
    bool ok = true;
    for (int i = 0; i < maxTokens && nPast < nCtx; ++i) {
        if (g_stopRequested.load()) break;
        llama_token token = llama_sampler_sample(smpl, mc->ctx, -1);
        llama_sampler_accept(smpl, token);
        if (token == eos || llama_vocab_is_eog(vocab, token)) break;
        std::string piece(256, '\0');
        int n = llama_token_to_piece(vocab, token, piece.data(), (int)piece.size(), 0, false);
        if (n < 0) { piece.resize((size_t)(-n)); n = llama_token_to_piece(vocab, token, piece.data(), (int)piece.size(), 0, false); }
        if (n > 0) {
            piece.resize((size_t)n);
            piece = sanitizeGeneratedPiece(piece);
            jstring js = env->NewStringUTF(piece.c_str());
            env->CallVoidMethod(callback, onToken, js);
            env->DeleteLocalRef(js);
            if (env->ExceptionCheck()) { env->ExceptionClear(); ok = false; break; }
        }
        batch.n_tokens = 1;
        batch.token[0] = token;
        batch.pos[0] = nPast++;
        batch.n_seq_id[0] = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0] = 1;
        if (llama_decode(mc->ctx, batch) != 0) { setLastError("llama.cpp failed while decoding a multimodal response token"); ok = false; break; }
    }
    llama_sampler_free(smpl);
    llama_batch_free(batch);
    return ok ? JNI_TRUE : JNI_FALSE;
#endif
}

JNIEXPORT jboolean JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeGenerateStream(
        JNIEnv* env, jobject, jlong handle, jstring jPrompt,
        jfloat temperature, jint maxTokens, jint topK, jfloat topP,
        jfloat repeatPenalty, jfloat frequencyPenalty, jfloat presencePenalty, jobject callback) {
    std::unique_lock<std::mutex> mapLock(g_mutex);
    auto it = g_contexts.find(handle);
    if (it == g_contexts.end() || !it->second) return JNI_FALSE;
    std::shared_ptr<ModelContext> mc = it->second;
    mapLock.unlock();

    std::unique_lock<std::mutex> inferenceLock(mc->inferenceMutex);
    if (!mc->ctx || !mc->model || !callback) return JNI_FALSE;

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
    if (!onToken) {
        setLastError("JNI TokenCallback.onToken(String) was not found");
        return JNI_FALSE;
    }

    g_lastError.clear();
    g_stopRequested.store(false);
    const char* prompt = env->GetStringUTFChars(jPrompt, nullptr);
    const llama_vocab* vocab = llama_model_get_vocab(mc->model);
    const int n_ctx = llama_n_ctx(mc->ctx);

    // Every request supplies the complete chat history, so start from a clean KV state.
    llama_memory_clear(llama_get_memory(mc->ctx), true);

    int n_tokens = llama_tokenize(vocab, prompt, strlen(prompt), nullptr, 0, true, false);
    if (n_tokens < 0) n_tokens = -n_tokens;
    if (n_tokens <= 0 || n_tokens >= n_ctx) {
        env->ReleaseStringUTFChars(jPrompt, prompt);
        setLastError("Prompt is empty or exceeds the loaded model context");
        return JNI_FALSE;
    }
    std::vector<llama_token> tokens(n_tokens);
    int written = llama_tokenize(vocab, prompt, strlen(prompt), tokens.data(), tokens.size(), true, false);
    env->ReleaseStringUTFChars(jPrompt, prompt);
    if (written < 0) return JNI_FALSE;
    tokens.resize(written);

    llama_batch batch = llama_batch_init(n_ctx, 0, 1);
    for (int i = 0; i < (int)tokens.size(); ++i) {
        batch.token[i] = tokens[i];
        batch.pos[i] = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = (i == (int)tokens.size() - 1) ? 1 : 0;
    }
    batch.n_tokens = (int32_t)tokens.size();

    if (llama_decode(mc->ctx, batch) != 0) {
        llama_batch_free(batch);
        setLastError("llama.cpp failed to evaluate the prompt");
        return JNI_FALSE;
    }

    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler* smpl = llama_sampler_chain_init(sparams);
    if (!smpl) {
        llama_batch_free(batch);
        setLastError("llama.cpp could not create the sampler chain");
        return JNI_FALSE;
    }
    auto addSampler = [&](llama_sampler * sampler) {
        if (!sampler) return false;
        llama_sampler_chain_add(smpl, sampler);
        return true;
    };
    if (!addSampler(llama_sampler_init_penalties(64, repeatPenalty, frequencyPenalty, presencePenalty)) ||
        !addSampler(llama_sampler_init_top_k(std::max(1, topK))) ||
        !addSampler(llama_sampler_init_top_p(std::clamp(topP, 0.0f, 1.0f), 1)) ||
        !addSampler(llama_sampler_init_temp(std::max(0.0f, temperature))) ||
        !addSampler(llama_sampler_init_dist((uint32_t)std::chrono::steady_clock::now().time_since_epoch().count()))) {
        llama_sampler_free(smpl);
        llama_batch_free(batch);
        setLastError("llama.cpp could not initialize one of the sampling stages");
        return JNI_FALSE;
    }

    const llama_token eos = llama_vocab_eos(vocab);
    bool ok = true;
    int n_cur = (int)tokens.size();
    for (int i = 0; i < maxTokens && n_cur < n_ctx; ++i) {
        if (g_stopRequested.load()) break;
        llama_token token = llama_sampler_sample(smpl, mc->ctx, -1);
        if (token == eos || token == llama_vocab_bos(vocab)) break;

        std::string piece(256, '\0');
        int n = llama_token_to_piece(vocab, token, piece.data(), (int)piece.size(), 0, false);
        if (n < 0) {
            piece.resize((size_t)(-n));
            n = llama_token_to_piece(vocab, token, piece.data(), (int)piece.size(), 0, false);
        }
        if (n > 0) {
            piece.resize((size_t)n);
            piece = sanitizeGeneratedPiece(piece);
            if (piece.empty()) {
                // Still advance/decode the token; only the visible control marker
                // is suppressed from the UI.
            }
            jstring js = env->NewStringUTF(piece.c_str());
            env->CallVoidMethod(callback, onToken, js);
            env->DeleteLocalRef(js);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                ok = false;
                break;
            }
        }

        batch.n_tokens = 1;
        batch.token[0] = token;
        batch.pos[0] = n_cur;
        batch.n_seq_id[0] = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0] = 1;
        if (llama_decode(mc->ctx, batch) != 0) {
            setLastError("llama.cpp failed while decoding generated token");
            ok = false;
            break;
        }
        ++n_cur;
    }

    llama_sampler_free(smpl);
    llama_batch_free(batch);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeFormatChat(
        JNIEnv* env, jobject, jlong handle, jobjectArray jRoles, jobjectArray jContents) {
    std::shared_ptr<ModelContext> mc;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        auto it = g_contexts.find(handle);
        if (it == g_contexts.end() || !it->second || !it->second->model) {
            return env->NewStringUTF("");
        }
        mc = it->second;
    }
    std::lock_guard<std::mutex> inferenceLock(mc->inferenceMutex);

    const jsize count = env->GetArrayLength(jRoles);
    if (count != env->GetArrayLength(jContents) || count == 0) {
        return env->NewStringUTF("");
    }

    // IMPORTANT: use llama.cpp's common chat-template implementation instead of
    // the legacy llama_chat_apply_template() API. The latter only understands a
    // fixed list of templates and can silently fail for newer GGUF templates
    // (for example Qwen3 variants). When it fails, the old Kotlin fallback used
    // <system>/<user>/<assistant>, which can make the model literally reproduce
    // its prompt/control markers in the answer.
    try {
        auto tmpls = common_chat_templates_init(mc->model, "");
        if (!tmpls) {
            setLastError("llama.cpp could not initialize the GGUF chat template");
            return env->NewStringUTF("");
        }

        common_chat_templates_inputs inputs;
        inputs.add_generation_prompt = true;
        inputs.messages.reserve((size_t) count);

        for (jsize i = 0; i < count; ++i) {
            jstring jr = (jstring) env->GetObjectArrayElement(jRoles, i);
            jstring jc = (jstring) env->GetObjectArrayElement(jContents, i);
            const char* r = env->GetStringUTFChars(jr, nullptr);
            const char* c = env->GetStringUTFChars(jc, nullptr);

            common_chat_msg msg;
            msg.role = r ? r : "user";
            msg.content = c ? c : "";
            inputs.messages.push_back(std::move(msg));

            env->ReleaseStringUTFChars(jr, r);
            env->ReleaseStringUTFChars(jc, c);
            env->DeleteLocalRef(jr);
            env->DeleteLocalRef(jc);
        }

        const common_chat_params params = common_chat_templates_apply(tmpls.get(), inputs);
        if (params.prompt.empty()) {
            setLastError("GGUF chat template produced an empty prompt");
            return env->NewStringUTF("");
        }
        return env->NewStringUTF(params.prompt.c_str());
    } catch (const std::exception& e) {
        setLastError(std::string("GGUF chat-template error: ") + e.what());
        return env->NewStringUTF("");
    } catch (...) {
        setLastError("Unknown GGUF chat-template error");
        return env->NewStringUTF("");
    }
}

JNIEXPORT jlong JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeTokenize(
        JNIEnv* env, jobject, jlong handle, jstring jText, jintArray jTokens) {

    std::shared_ptr<ModelContext> mc;
    { std::lock_guard<std::mutex> lock(g_mutex); auto it = g_contexts.find(handle); if (it == g_contexts.end()) return -1; mc = it->second; }
    std::lock_guard<std::mutex> inferenceLock(mc->inferenceMutex);
    if (!mc->model) return -1;

    const char* text = env->GetStringUTFChars(jText, nullptr);
    const llama_vocab* vocab = llama_model_get_vocab(mc->model);

    jint* tokens = env->GetIntArrayElements(jTokens, nullptr);
    jint maxTokens = env->GetArrayLength(jTokens);

    int n = llama_tokenize(vocab, text, strlen(text),
                          (llama_token*)tokens, maxTokens, true, false);

    env->ReleaseIntArrayElements(jTokens, tokens, 0);
    env->ReleaseStringUTFChars(jText, text);

    return n;
}

JNIEXPORT jstring JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeDetokenize(
        JNIEnv* env, jobject, jlong handle, jintArray jTokens) {

    std::shared_ptr<ModelContext> mc;
    { std::lock_guard<std::mutex> lock(g_mutex); auto it = g_contexts.find(handle); if (it == g_contexts.end()) return env->NewStringUTF(""); mc = it->second; }
    std::lock_guard<std::mutex> inferenceLock(mc->inferenceMutex);
    if (!mc->model) return env->NewStringUTF("");

    jint* tokens = env->GetIntArrayElements(jTokens, nullptr);
    jint nTokens = env->GetArrayLength(jTokens);
    const llama_vocab* vocab = llama_model_get_vocab(mc->model);

    std::string result;
    for (int i = 0; i < nTokens; i++) {
        char piece[256];
        int n = llama_token_to_piece(vocab, tokens[i], piece, sizeof(piece), 0, false);
        if (n > 0) {
            result.append(piece, n);
        }
    }

    env->ReleaseIntArrayElements(jTokens, tokens, JNI_ABORT);
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jlong JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeGetModelContextSize(
        JNIEnv*, jobject, jlong handle) {
    std::shared_ptr<ModelContext> mc;
    { std::lock_guard<std::mutex> lock(g_mutex); auto it = g_contexts.find(handle); if (it == g_contexts.end()) return 0; mc = it->second; }
    std::lock_guard<std::mutex> inferenceLock(mc->inferenceMutex);
    return mc->ctx ? llama_n_ctx(mc->ctx) : 0;
}

JNIEXPORT jstring JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeGetModelInfo(
        JNIEnv* env, jobject, jlong handle) {
    std::shared_ptr<ModelContext> mc;
    { std::lock_guard<std::mutex> lock(g_mutex); auto it = g_contexts.find(handle); if (it == g_contexts.end()) return env->NewStringUTF("Invalid handle"); mc = it->second; }
    std::lock_guard<std::mutex> inferenceLock(mc->inferenceMutex);
    std::string info = "Path: " + mc->modelPath;
    if (mc->ctx) {
        info += ", n_ctx=" + std::to_string(llama_n_ctx(mc->ctx));
    }
    return env->NewStringUTF(info.c_str());
}

} // extern "C"
