#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <string>
#include <vector>
#include <thread>
#include <exception>
#include <stdexcept>
#include "llama.h"

#define GI_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "GIKOKO_LLAMA", __VA_ARGS__)

static void throwJava(JNIEnv *env, const std::string &message) {
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    if (cls) env->ThrowNew(cls, message.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_gi_koko_voiceassistant_NativeLlmBridge_nativeGenerate(
        JNIEnv *env, jobject,
        jstring modelPathValue, jstring promptValue,
        jint contextSize, jint maxTokens, jint threads) {
    const char *modelPathChars = env->GetStringUTFChars(modelPathValue, nullptr);
    const char *promptChars = env->GetStringUTFChars(promptValue, nullptr);
    if (!modelPathChars || !promptChars) {
        if (modelPathChars) env->ReleaseStringUTFChars(modelPathValue, modelPathChars);
        if (promptChars) env->ReleaseStringUTFChars(promptValue, promptChars);
        throwJava(env, "Native input မရပါ");
        return nullptr;
    }

    const std::string modelPath(modelPathChars);
    const std::string userPrompt(promptChars);
    env->ReleaseStringUTFChars(modelPathValue, modelPathChars);
    env->ReleaseStringUTFChars(promptValue, promptChars);

    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    llama_sampler *sampler = nullptr;

    try {
        llama_backend_init();

        llama_model_params modelParams = llama_model_default_params();
        modelParams.n_gpu_layers = 0;
        model = llama_model_load_from_file(modelPath.c_str(), modelParams);
        if (!model) throw std::runtime_error("GGUF model ကို load မလုပ်နိုင်ပါ။ Q4 quantized text model ဖြစ်ကြောင်း စစ်ပါ။");

        llama_context_params ctxParams = llama_context_default_params();
        ctxParams.n_ctx = static_cast<uint32_t>(std::max(256, std::min(1024, static_cast<int>(contextSize))));
        ctxParams.n_batch = 128;
        ctxParams.n_ubatch = 128;
        const int cpuThreads = std::max(1, std::min(4, static_cast<int>(threads)));
        ctxParams.n_threads = cpuThreads;
        ctxParams.n_threads_batch = cpuThreads;
        ctx = llama_init_from_model(model, ctxParams);
        if (!ctx) throw std::runtime_error("LLM context မဖန်တီးနိုင်ပါ။ Context size လျှော့ပြီး စမ်းပါ။");

        const llama_vocab *vocab = llama_model_get_vocab(model);
        const char *chatTemplate = llama_model_chat_template(model, nullptr);
        const std::string systemText = "You are G.I KO KO, a helpful offline assistant. Reply in the user's language. Be accurate and concise.";
        const std::string userText = userPrompt;
        llama_chat_message chat[2] = {
            {"system", systemText.c_str()},
            {"user", userText.c_str()}
        };

        std::string formatted;
        if (chatTemplate != nullptr) {
            size_t capacity = std::max<size_t>(2048, (systemText.size() + userText.size()) * 4 + 512);
            std::vector<char> buffer(capacity);
            int32_t length = llama_chat_apply_template(chatTemplate, chat, 2, true, buffer.data(), static_cast<int32_t>(buffer.size()));
            if (length < 0) throw std::runtime_error("Model chat template ကို apply မလုပ်နိုင်ပါ");
            if (static_cast<size_t>(length) >= buffer.size()) {
                buffer.resize(static_cast<size_t>(length) + 1);
                length = llama_chat_apply_template(chatTemplate, chat, 2, true, buffer.data(), static_cast<int32_t>(buffer.size()));
                if (length < 0 || static_cast<size_t>(length) >= buffer.size()) throw std::runtime_error("Prompt အလွန်ရှည်နေပါသည်");
            }
            formatted.assign(buffer.data(), static_cast<size_t>(length));
        } else {
            formatted = "### System:\n" + systemText + "\n### User:\n" + userText + "\n### Assistant:\n";
        }

        std::vector<llama_token> tokens(std::max<size_t>(256, formatted.size() + 16));
        int32_t tokenCount = llama_tokenize(vocab, formatted.c_str(), static_cast<int32_t>(formatted.size()),
                                            tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
        if (tokenCount < 0) {
            tokens.resize(static_cast<size_t>(-tokenCount) + 8);
            tokenCount = llama_tokenize(vocab, formatted.c_str(), static_cast<int32_t>(formatted.size()),
                                        tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
        }
        if (tokenCount <= 0) throw std::runtime_error("Prompt tokenize မလုပ်နိုင်ပါ");
        tokens.resize(static_cast<size_t>(tokenCount));

        const int maxContext = static_cast<int>(llama_n_ctx(ctx));
        if (tokenCount >= maxContext - 8) {
            const int keep = std::max(32, maxContext - 96);
            tokens.erase(tokens.begin(), tokens.end() - keep);
            tokenCount = static_cast<int32_t>(tokens.size());
        }

        llama_batch batch = llama_batch_get_one(tokens.data(), tokenCount);
        if (llama_decode(ctx, batch) != 0) throw std::runtime_error("Prompt evaluation မအောင်မြင်ပါ။ Model/context ကို စစ်ပါ။");

        auto samplerParams = llama_sampler_chain_default_params();
        sampler = llama_sampler_chain_init(samplerParams);
        llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.7f));
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

        std::string output;
        std::vector<char> piece(512);
        const int limit = std::max(16, std::min(256, static_cast<int>(maxTokens)));
        for (int i = 0; i < limit; ++i) {
            const llama_token token = llama_sampler_sample(sampler, ctx, -1);
            if (llama_vocab_is_eog(vocab, token)) break;
            const int32_t pieceLength = llama_token_to_piece(vocab, token, piece.data(),
                                                              static_cast<int32_t>(piece.size()), 0, false);
            if (pieceLength > 0) output.append(piece.data(), static_cast<size_t>(pieceLength));
            llama_sampler_accept(sampler, token);
            batch = llama_batch_get_one(const_cast<llama_token *>(&token), 1);
            if (llama_decode(ctx, batch) != 0) break;
        }
        if (output.empty()) throw std::runtime_error("Model က token မထုတ်ပေးပါ");
        if (sampler) llama_sampler_free(sampler);
        if (ctx) llama_free(ctx);
        if (model) llama_model_free(model);
        return env->NewStringUTF(output.c_str());
    } catch (const std::exception &error) {
        if (sampler) llama_sampler_free(sampler);
        if (ctx) llama_free(ctx);
        if (model) llama_model_free(model);
        GI_LOGE("Offline inference failed: %s", error.what());
        throwJava(env, error.what());
        return nullptr;
    }
}
