package com.gi.koko.voiceassistant

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object LlmClient {
    private const val PREFS = "gi_voice_prefs"
    private const val KEY_ALIAS = "gi_voice_llm_key"

    fun saveConfig(context: Context, baseUrl: String, model: String, apiKey: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("llm_base_url", baseUrl.trim().trimEnd('/'))
            .putString("llm_model", model.trim())
            .putString("llm_api_key_enc", encrypt(apiKey.trim()))
            .apply()
    }

    fun loadConfig(context: Context): Triple<String, String, String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val base = prefs.getString("llm_base_url", "https://api.openai.com/v1") ?: "https://api.openai.com/v1"
        val model = prefs.getString("llm_model", "gpt-4o-mini") ?: "gpt-4o-mini"
        val key = runCatching { decrypt(prefs.getString("llm_api_key_enc", "") ?: "") }.getOrDefault("")
        return Triple(base, model, key)
    }

    suspend fun reply(context: Context, history: List<ChatLine>, userText: String): String = withContext(Dispatchers.IO) {
        val (base, model, key) = loadConfig(context)
        require(key.isNotBlank()) { "LLM API Key မထည့်ရသေးပါ။ LLM Settings မှာ ထည့်ပါ။" }
        require(model.isNotBlank()) { "Model ID ထည့်ပါ။" }
        val endpoint = base.trimEnd('/') + "/chat/completions"
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content",
            "You are G.I KO KO, a helpful voice assistant. Reply clearly in the language used by the user."))
        history.takeLast(20).forEach { line ->
            messages.put(JSONObject()
                .put("role", if (line.fromUser) "user" else "assistant")
                .put("content", line.text))
        }
        messages.put(JSONObject().put("role", "user").put("content", userText))
        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", 0.7)
            .put("stream", false)
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $key")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = JSONObject(raw.ifBlank { "{}" })
            if (code !in 200..299) {
                val message = json.optJSONObject("error")?.optString("message")
                    ?: json.optString("message", "HTTP $code")
                throw IllegalStateException("LLM API error ($code): $message")
            }
            json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                ?.optString("content")?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("LLM response ထဲမှာ answer မတွေ့ပါ")
        } finally {
            connection.disconnect()
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        if (value.isBlank()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        if (value.isBlank()) return ""
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, 12)
        val payload = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(payload), Charsets.UTF_8)
    }
}
