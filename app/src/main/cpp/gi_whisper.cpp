#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <string>
#include <vector>
#include <exception>
#include <stdexcept>
#include "whisper.h"

#define GI_WHISPER_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "GIKOKO_WHISPER", __VA_ARGS__)

static void throwWhisperError(JNIEnv *env, const std::string &message) {
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    if (cls) env->ThrowNew(cls, message.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_gi_koko_voiceassistant_NativeWhisperBridge_nativeTranscribe(
        JNIEnv *env, jobject,
        jstring modelPathValue, jfloatArray samplesValue,
        jint threads, jstring languageValue) {
    const char *modelPathChars = env->GetStringUTFChars(modelPathValue, nullptr);
    const char *languageChars = env->GetStringUTFChars(languageValue, nullptr);
    if (!modelPathChars || !languageChars) {
        if (modelPathChars) env->ReleaseStringUTFChars(modelPathValue, modelPathChars);
        if (languageChars) env->ReleaseStringUTFChars(languageValue, languageChars);
        throwWhisperError(env, "Whisper input path မရရှိပါ");
        return nullptr;
    }

    const std::string modelPath(modelPathChars);
    const std::string language(languageChars);
    env->ReleaseStringUTFChars(modelPathValue, modelPathChars);
    env->ReleaseStringUTFChars(languageValue, languageChars);

    const jsize sampleCount = env->GetArrayLength(samplesValue);
    if (sampleCount < 8000) {
        throwWhisperError(env, "အသံဖိုင်တိုလွန်းနေပါသည်");
        return nullptr;
    }
    jboolean isCopy = JNI_FALSE;
    jfloat *samples = env->GetFloatArrayElements(samplesValue, &isCopy);
    if (!samples) {
        throwWhisperError(env, "Microphone audio memory မရရှိပါ");
        return nullptr;
    }

    whisper_context *ctx = nullptr;
    try {
        whisper_context_params contextParams = whisper_context_default_params();
        contextParams.use_gpu = false;
        contextParams.flash_attn = false;
        ctx = whisper_init_from_file_with_params(modelPath.c_str(), contextParams);
        if (!ctx) throw std::runtime_error("Whisper model ကို load မလုပ်နိုင်ပါ။ Model download ပြည့်စုံမှုကို စစ်ပါ။");

        whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
        params.n_threads = std::max(1, std::min(2, static_cast<int>(threads)));
        params.translate = false;
        params.no_context = true;
        params.no_timestamps = true;
        params.print_special = false;
        params.print_progress = false;
        params.print_realtime = false;
        params.print_timestamps = false;
        params.detect_language = false;
        params.language = language.c_str();

        if (whisper_full(ctx, params, samples, static_cast<int>(sampleCount)) != 0) {
            throw std::runtime_error("Whisper transcription မအောင်မြင်ပါ။ အသံကို ပိုရှင်းအောင် ပြန်ဖမ်းပါ။");
        }

        std::string result;
        const int segments = whisper_full_n_segments(ctx);
        for (int i = 0; i < segments; ++i) {
            const char *segment = whisper_full_get_segment_text(ctx, i);
            if (segment) {
                if (!result.empty()) result += " ";
                result += segment;
            }
        }
        whisper_free(ctx);
        ctx = nullptr;
        env->ReleaseFloatArrayElements(samplesValue, samples, JNI_ABORT);
        if (result.empty()) throw std::runtime_error("အသံထဲမှ စာသားမတွေ့ပါ။ ထပ်မံပြောကြည့်ပါ။");
        return env->NewStringUTF(result.c_str());
    } catch (const std::exception &error) {
        if (ctx) whisper_free(ctx);
        env->ReleaseFloatArrayElements(samplesValue, samples, JNI_ABORT);
        GI_WHISPER_LOGE("Whisper transcription failed: %s", error.what());
        throwWhisperError(env, error.what());
        return nullptr;
    }
}
