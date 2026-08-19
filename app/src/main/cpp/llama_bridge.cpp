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
#include <fstream>
#include <sstream>
#include <sys/stat.h>
#include <unistd.h>

#include "llama.h"
#include "ggml.h"

#define LOG_TAG "LlamaBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::atomic<bool> g_stopRequested{false};
static std::atomic<bool> g_initialized{false};

struct ModelContext {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    int64_t memoryUsage = -1;
    std::string modelPath;
    std::mutex inferenceMutex;
};

static std::unordered_map<int64_t, ModelContext*> g_contexts;
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

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = static_cast<uint32_t>(contextLength);
    ctx_params.n_threads = threads;
    ctx_params.n_threads_batch = threads;

    llama_context* ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        setLastError(std::string("GGUF loaded, but llama.cpp could not allocate the requested context. Reduce context length or close other apps: ") + filePath);
        llama_model_free(model);
        env->ReleaseStringUTFChars(jFilePath, filePath);
        return 0;
    }

    std::lock_guard<std::mutex> lock(g_mutex);
    int64_t handle = g_nextHandle++;
    ModelContext* mc = new ModelContext();
    mc->model = model;
    mc->ctx = ctx;
    mc->modelPath = std::string(filePath);
    mc->memoryUsage = processResidentBytes();
    g_contexts[handle] = mc;

    LOGI("Model loaded successfully. Handle=%ld, n_ctx=%d", handle,
         llama_n_ctx(ctx));
    env->ReleaseStringUTFChars(jFilePath, filePath);
    return handle;
}

JNIEXPORT jboolean JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeUnloadModel(JNIEnv*, jobject, jlong handle) {
    ModelContext* mc = nullptr;
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
        if (mc->ctx) { llama_free(mc->ctx); mc->ctx = nullptr; }
        if (mc->model) { llama_model_free(mc->model); mc->model = nullptr; }
        g_contexts.erase(it);
    }
    delete mc;
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

JNIEXPORT jboolean JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeGenerateStream(
        JNIEnv* env, jobject, jlong handle, jstring jPrompt,
        jfloat temperature, jint maxTokens, jint topK, jfloat topP,
        jfloat repeatPenalty, jfloat frequencyPenalty, jfloat presencePenalty, jobject callback) {
    std::unique_lock<std::mutex> mapLock(g_mutex);
    auto it = g_contexts.find(handle);
    if (it == g_contexts.end() || !it->second) return JNI_FALSE;
    ModelContext* mc = it->second;
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
    ModelContext* mc = nullptr;
    { std::lock_guard<std::mutex> lock(g_mutex); auto it = g_contexts.find(handle); if (it == g_contexts.end() || !it->second || !it->second->model) return env->NewStringUTF(""); mc = it->second; }
    std::lock_guard<std::mutex> inferenceLock(mc->inferenceMutex);

    const jsize count = env->GetArrayLength(jRoles);
    if (count != env->GetArrayLength(jContents) || count == 0) return env->NewStringUTF("");

    const char* tmpl = llama_model_chat_template(mc->model, nullptr);
    if (!tmpl || !*tmpl) return env->NewStringUTF("");

    std::vector<std::string> roles(count), contents(count);
    std::vector<llama_chat_message> messages(count);
    for (jsize i = 0; i < count; ++i) {
        jstring jr = (jstring) env->GetObjectArrayElement(jRoles, i);
        jstring jc = (jstring) env->GetObjectArrayElement(jContents, i);
        const char* r = env->GetStringUTFChars(jr, nullptr);
        const char* c = env->GetStringUTFChars(jc, nullptr);
        roles[i] = r ? r : "user";
        contents[i] = c ? c : "";
        env->ReleaseStringUTFChars(jr, r);
        env->ReleaseStringUTFChars(jc, c);
        env->DeleteLocalRef(jr);
        env->DeleteLocalRef(jc);
        messages[i] = { roles[i].c_str(), contents[i].c_str() };
    }

    int32_t required = llama_chat_apply_template(tmpl, messages.data(), messages.size(), true, nullptr, 0);
    if (required <= 0) return env->NewStringUTF("");
    std::string out((size_t)required + 1, '\0');
    int32_t written = llama_chat_apply_template(tmpl, messages.data(), messages.size(), true, out.data(), required + 1);
    if (written < 0) return env->NewStringUTF("");
    out.resize((size_t)written);
    return env->NewStringUTF(out.c_str());
}

JNIEXPORT jlong JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeTokenize(
        JNIEnv* env, jobject, jlong handle, jstring jText, jintArray jTokens) {

    ModelContext* mc = nullptr;
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

    ModelContext* mc = nullptr;
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
    ModelContext* mc = nullptr;
    { std::lock_guard<std::mutex> lock(g_mutex); auto it = g_contexts.find(handle); if (it == g_contexts.end()) return 0; mc = it->second; }
    std::lock_guard<std::mutex> inferenceLock(mc->inferenceMutex);
    return mc->ctx ? llama_n_ctx(mc->ctx) : 0;
}

JNIEXPORT jstring JNICALL
Java_com_localaisearch_data_llm_LlamaBridge_nativeGetModelInfo(
        JNIEnv* env, jobject, jlong handle) {
    ModelContext* mc = nullptr;
    { std::lock_guard<std::mutex> lock(g_mutex); auto it = g_contexts.find(handle); if (it == g_contexts.end()) return env->NewStringUTF("Invalid handle"); mc = it->second; }
    std::lock_guard<std::mutex> inferenceLock(mc->inferenceMutex);
    std::string info = "Path: " + mc->modelPath;
    if (mc->ctx) {
        info += ", n_ctx=" + std::to_string(llama_n_ctx(mc->ctx));
    }
    return env->NewStringUTF(info.c_str());
}

} // extern "C"
