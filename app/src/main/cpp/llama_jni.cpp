#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <thread>
#include <atomic>
#include <mutex>
#include <cstring>
#include <cstdint>
#include <algorithm>

#include "llama.h"

#define LOG_TAG "GliaNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static JavaVM* g_vm = nullptr;
static std::mutex g_mutex;
static std::mutex g_error_mutex;
static std::once_flag g_backend_once;
static std::atomic<bool> g_loaded{false};
static std::atomic<bool> g_generating{false};
static std::atomic<bool> g_stop{false};
static std::string g_last_error;

static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static const llama_vocab* g_vocab = nullptr;

static void set_error(const char* msg) {
    {
        std::lock_guard<std::mutex> lock(g_error_mutex);
        g_last_error = msg ? msg : "";
    }
    LOGE("%s", msg ? msg : "");
}

static void clear_error() {
    std::lock_guard<std::mutex> lock(g_error_mutex);
    g_last_error.clear();
}

static void backend_init_once() {
    std::call_once(g_backend_once, []() {
        llama_backend_init();
        LOGI("llama_backend_init ok");
    });
}

static void unload_locked() {
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    g_vocab = nullptr;
    g_loaded.store(false);
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_stop.store(true);
    unload_locked();
    llama_backend_free();
}

static std::vector<llama_token> tokenize(const std::string& text) {
    std::vector<llama_token> toks;
    if (!g_vocab || text.empty()) return toks;
    int n = llama_tokenize(g_vocab, text.c_str(), (int) text.size(),
                           nullptr, 0, /*add_special=*/false, /*parse_special=*/true);
    if (n < 0) n = -n;
    if (n <= 0) return toks;
    toks.resize((size_t) n);
    int written = llama_tokenize(g_vocab, text.c_str(), (int) text.size(),
                                 toks.data(), (int) toks.size(),
                                 /*add_special=*/false, /*parse_special=*/true);
    if (written < 0) {
        toks.clear();
    } else {
        toks.resize((size_t) written);
    }
    return toks;
}

static std::u16string utf8_to_utf16(const std::string& s) {
    std::u16string out;
    size_t i = 0;
    while (i < s.size()) {
        unsigned char c = (unsigned char) s[i];
        uint32_t cp = 0;
        int len = 0;
        if (c < 0x80) { cp = c; len = 1; }
        else if ((c & 0xE0) == 0xC0) { cp = c & 0x1F; len = 2; }
        else if ((c & 0xF0) == 0xE0) { cp = c & 0x0F; len = 3; }
        else if ((c & 0xF8) == 0xF0) { cp = c & 0x07; len = 4; }
        else { out.push_back((char16_t) 0xFFFD); i++; continue; }

        if (i + (size_t) len > s.size()) {
            out.push_back((char16_t) 0xFFFD);
            break;
        }
        bool ok = true;
        for (int k = 1; k < len; k++) {
            unsigned char cc = (unsigned char) s[i + (size_t) k];
            if ((cc & 0xC0) != 0x80) { ok = false; break; }
            cp = (cp << 6) | (cc & 0x3F);
        }
        if (!ok) {
            out.push_back((char16_t) 0xFFFD);
            i++;
            continue;
        }
        if (cp <= 0xFFFF) {
            out.push_back((char16_t) cp);
        } else {
            cp -= 0x10000;
            out.push_back((char16_t) (0xD800 + (cp >> 10)));
            out.push_back((char16_t) (0xDC00 + (cp & 0x3FF)));
        }
        i += (size_t) len;
    }
    return out;
}

static JNIEnv* attach_env(bool& attached) {
    attached = false;
    if (!g_vm) return nullptr;
    JNIEnv* env = nullptr;
    jint rc = g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (rc == JNI_EDETACHED) {
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return nullptr;
        attached = true;
    } else if (rc != JNI_OK) {
        return nullptr;
    }
    return env;
}

static void call_string(JNIEnv* env, jobject cb, jmethodID mid, const std::string& text) {
    if (!env || !cb || !mid) return;
    std::u16string u = utf8_to_utf16(text);
    jstring js = env->NewString(reinterpret_cast<const jchar*>(u.data()), (jsize) u.size());
    if (js) {
        env->CallVoidMethod(cb, mid, js);
        env->DeleteLocalRef(js);
    }
    if (env->ExceptionCheck()) env->ExceptionClear();
}

static void call_error(JNIEnv* env, jobject cb, jmethodID mid, const std::string& text) {
    if (!env || !cb || !mid) return;
    std::u16string u = utf8_to_utf16(text);
    jstring js = env->NewString(reinterpret_cast<const jchar*>(u.data()), (jsize) u.size());
    if (js) {
        env->CallVoidMethod(cb, mid, js);
        env->DeleteLocalRef(js);
    }
    if (env->ExceptionCheck()) env->ExceptionClear();
}

extern "C" {

JNIEXPORT jint JNICALL
Java_com_glia_assistant_llm_LlamaBridge_nativeLoadModel(JNIEnv* env, jobject thiz,
                                                       jstring modelPath, jint nCtx, jint nThreads) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_generating.load()) {
        set_error("Nao e possivel carregar durante geracao");
        return -3;
    }
    if (!modelPath) {
        set_error("Caminho do modelo nulo");
        return -1;
    }

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path) {
        set_error("Falha ao ler caminho do modelo");
        return -1;
    }
    std::string pathStr(path);
    env->ReleaseStringUTFChars(modelPath, path);
    LOGI("nativeLoadModel: %s (ctx=%d, threads=%d)", pathStr.c_str(), nCtx, nThreads);

    backend_init_once();
    unload_locked();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;
    mparams.load_mode = LLAMA_LOAD_MODE_MMAP;

    g_model = llama_model_load_from_file(pathStr.c_str(), mparams);
    if (!g_model) {
        set_error("llama_model_load_from_file falhou");
        return -1;
    }

    g_vocab = llama_model_get_vocab(g_model);
    if (!g_vocab) {
        set_error("Vocabulario do modelo indisponivel");
        unload_locked();
        return -2;
    }

    const int ctxSize = nCtx > 0 ? nCtx : 2048;
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = (uint32_t) ctxSize;
    cparams.n_batch = (uint32_t) std::max(512, ctxSize);
    cparams.n_ubatch = 512;
    cparams.n_threads = nThreads > 0 ? nThreads : 4;
    cparams.n_threads_batch = cparams.n_threads;
    cparams.offload_kqv = false;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        set_error("llama_init_from_model falhou");
        unload_locked();
        return -2;
    }

    g_loaded.store(true);
    clear_error();
    LOGI("Modelo carregado: ctx=%d threads=%d vocab=%d", ctxSize, cparams.n_threads,
         llama_vocab_n_tokens(g_vocab));
    return 0;
}

JNIEXPORT void JNICALL
Java_com_glia_assistant_llm_LlamaBridge_nativeUnloadModel(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_stop.store(true);
    unload_locked();
    g_generating.store(false);
    LOGI("nativeUnloadModel");
}

JNIEXPORT jboolean JNICALL
Java_com_glia_assistant_llm_LlamaBridge_nativeIsLoaded(JNIEnv* env, jobject thiz) {
    return g_loaded.load() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_glia_assistant_llm_LlamaBridge_nativeGenerate(JNIEnv* env, jobject thiz,
                                                      jstring prompt, jobject callback,
                                                      jfloat temperature, jfloat topP, jint topK,
                                                      jfloat minP, jfloat presencePenalty,
                                                      jfloat repetitionPenalty, jint maxTokens) {
    std::unique_lock<std::mutex> lock(g_mutex);
    if (!g_loaded.load() || !g_ctx || !g_model || !g_vocab) {
        set_error("Modelo nao carregado");
        return JNI_FALSE;
    }
    if (!prompt || !callback) {
        set_error("Prompt ou callback nulo");
        return JNI_FALSE;
    }

    const char* p = env->GetStringUTFChars(prompt, nullptr);
    if (!p) {
        set_error("Falha ao ler prompt");
        return JNI_FALSE;
    }
    jsize pLen = env->GetStringUTFLength(prompt);
    std::string promptStr(p, (size_t) pLen);
    env->ReleaseStringUTFChars(prompt, p);

    g_stop.store(false);
    g_generating.store(true);
    struct GeneratingGuard {
        ~GeneratingGuard() { g_generating.store(false); }
    } guard;

    jobject cbGlobal = env->NewGlobalRef(callback);
    if (!cbGlobal) {
        set_error("Falha ao criar referencia do callback");
        return JNI_FALSE;
    }
    jclass cbClass = env->GetObjectClass(cbGlobal);
    jmethodID onToken = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onDone = env->GetMethodID(cbClass, "onDone", "()V");
    jmethodID onError = env->GetMethodID(cbClass, "onError", "(Ljava/lang/String;)V");
    if (env->ExceptionCheck()) env->ExceptionClear();
    env->DeleteLocalRef(cbClass);

    if (!onToken || !onDone) {
        env->DeleteGlobalRef(cbGlobal);
        set_error("Callback TokenCallback invalido");
        return JNI_FALSE;
    }

    std::vector<llama_token> tokens = tokenize(promptStr);
    if (tokens.empty()) {
        env->DeleteGlobalRef(cbGlobal);
        set_error("Tokenizacao do prompt retornou vazio");
        return JNI_FALSE;
    }
    if ((int) tokens.size() >= (int) llama_n_ctx(g_ctx)) {
        env->DeleteGlobalRef(cbGlobal);
        set_error("Prompt maior que o contexto do modelo");
        return JNI_FALSE;
    }

    llama_memory_clear(llama_get_memory(g_ctx), true);
    llama_batch batch = llama_batch_get_one(tokens.data(), (int) tokens.size());
    if (llama_decode(g_ctx, batch) != 0) {
        env->DeleteGlobalRef(cbGlobal);
        set_error("Falha no decode do prompt");
        return JNI_FALSE;
    }

    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler* sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(
        llama_vocab_n_tokens(g_vocab), 64, repetitionPenalty, 0.0f, presencePenalty));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(topK));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(topP, 1.0f));
    llama_sampler_chain_add(sampler, llama_sampler_init_min_p(minP, 1.0f));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(
        temperature > 0.0f ? temperature : 0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string error;
    int tokenCount = 0;
    const int max_new = maxTokens > 0 ? maxTokens : 256;
    LOGI("generate: start prompt_len=%zu max_new=%d", promptStr.size(), max_new);
    for (int i = 0; i < max_new; i++) {
        if (g_stop.load()) break;

        llama_token new_token = llama_sampler_sample(sampler, g_ctx, -1);
        bool eog = llama_vocab_is_eog(g_vocab, new_token);
        char tokenBuf[512];
        int tokenN = llama_token_to_piece(g_vocab, new_token, tokenBuf, (int) sizeof(tokenBuf), 0, true);
        std::string tokenPiece;
        if (tokenN > 0) {
            tokenPiece.assign(tokenBuf, (size_t) tokenN);
        } else if (tokenN < 0) {
            std::vector<char> big((size_t) -tokenN);
            int tokenN2 = llama_token_to_piece(g_vocab, new_token, big.data(), (int) big.size(), 0, true);
            if (tokenN2 > 0) tokenPiece.assign(big.data(), (size_t) tokenN2);
        }
        if (!tokenPiece.empty() && tokenPiece == "<|im_end|>") eog = true;
        if (eog) {
            LOGI("EOG em %d tokens", i);
            break;
        }

        char buf[512];
        int n = llama_token_to_piece(g_vocab, new_token, buf, (int) sizeof(buf), 0, true);
        std::string piece;
        if (n > 0) {
            piece.assign(buf, (size_t) n);
        } else if (n < 0) {
            std::vector<char> big((size_t) -n);
            int n2 = llama_token_to_piece(g_vocab, new_token, big.data(), (int) big.size(), 0, true);
            if (n2 > 0) piece.assign(big.data(), (size_t) n2);
        }

        tokenCount++;
        if (!piece.empty()) {
            JNIEnv* e = nullptr;
            bool attached = false;
            e = attach_env(attached);
            if (e) call_string(e, cbGlobal, onToken, piece);
            if (attached) g_vm->DetachCurrentThread();
        }

        llama_batch b = llama_batch_get_one(&new_token, 1);
        if (llama_decode(g_ctx, b) != 0) {
            error = "Falha no decode do token gerado";
            break;
        }
    }

    llama_sampler_free(sampler);
    LOGI("generate: loop ended tokens=%d error=%s", tokenCount, error.c_str());

    JNIEnv* e = nullptr;
    bool attached = false;
    e = attach_env(attached);
    if (e) {
        if (!error.empty() && onError) call_error(e, cbGlobal, onError, error);
        if (onDone) {
            e->CallVoidMethod(cbGlobal, onDone);
            if (e->ExceptionCheck()) e->ExceptionClear();
        }
    }
    if (attached) g_vm->DetachCurrentThread();

    env->DeleteGlobalRef(cbGlobal);
    if (!error.empty()) set_error(error.c_str());
    LOGI("generate done, tokens=%d", tokenCount);

    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_glia_assistant_llm_LlamaBridge_nativeStopGeneration(JNIEnv* env, jobject thiz) {
    g_stop.store(true);
}

JNIEXPORT jboolean JNICALL
Java_com_glia_assistant_llm_LlamaBridge_nativeIsGenerating(JNIEnv* env, jobject thiz) {
    return g_generating.load() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_glia_assistant_llm_LlamaBridge_nativeGetLastError(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_error_mutex);
    return env->NewStringUTF(g_last_error.c_str());
}

} // extern "C"
