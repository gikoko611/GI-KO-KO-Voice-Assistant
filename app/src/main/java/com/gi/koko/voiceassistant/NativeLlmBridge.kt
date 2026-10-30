package com.gi.koko.voiceassistant

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NativeLlmBridge {
    init {
        System.loadLibrary("gi_llama")
    }

    private external fun nativeGenerate(
        modelPath: String,
        prompt: String,
        contextSize: Int,
        maxTokens: Int,
        threads: Int
    ): String

    suspend fun generate(
        modelPath: String,
        prompt: String,
        contextSize: Int = 512,
        maxTokens: Int = 160
    ): String = withContext(Dispatchers.IO) {
        require(modelPath.endsWith(".gguf", ignoreCase = true)) { "ရွေးထားတဲ့ဖိုင်က GGUF မဟုတ်ပါ" }
        require(contextSize in 256..1024) { "Context size မမှန်ပါ" }
        require(maxTokens in 16..256) { "Output token limit မမှန်ပါ" }
        nativeGenerate(modelPath, prompt, contextSize, maxTokens, 2).trim()
            .ifBlank { "Local model က အဖြေမထုတ်ပေးနိုင်ပါ။ အခြား GGUF model ဖြင့် စမ်းပါ။" }
    }
}

object OfflineLlmClient {
    suspend fun reply(
        modelPath: String,
        history: List<ChatLine>,
        userText: String
    ): String {
        val prompt = buildString {
            append("You are G.I KO KO, a concise and helpful offline assistant. ")
            append("Reply in the same language as the user. Do not claim internet access.\n\n")
            history.takeLast(6).forEach { line ->
                append(if (line.fromUser) "User: " else "Assistant: ")
                append(line.text.take(1200))
                append("\n")
            }
            append("User: ")
            append(userText)
            append("\nAssistant:")
        }
        return NativeLlmBridge.generate(modelPath, prompt, contextSize = 512, maxTokens = 160)
    }
}
