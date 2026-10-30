package com.gi.koko.voiceassistant

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object WhisperModelStore {
    private const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin"
    private const val MIN_MODEL_BYTES = 50L * 1024L * 1024L
    private const val MAX_MODEL_BYTES = 120L * 1024L * 1024L

    fun modelFile(context: Context): File =
        File(File(context.filesDir, "whisper_models"), "ggml-tiny.bin")

    suspend fun download(context: Context, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val folder = modelFile(context).parentFile!!
            if (!folder.exists() && !folder.mkdirs()) {
                throw IllegalStateException("Whisper model folder ဖန်တီးမရပါ")
            }
            val target = modelFile(context)
            val temp = File(folder, "ggml-tiny.bin.part")
            val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 25_000
                readTimeout = 45_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "GIKOKO-VoiceAssistant/0.4")
            }
            try {
                connection.connect()
                require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                    "Model download မအောင်မြင်ပါ (HTTP ${connection.responseCode})"
                }
                val expected = connection.contentLengthLong
                if (expected > 0) require(expected <= MAX_MODEL_BYTES) { "Whisper Tiny model အရွယ်အစား မမျှော်လင့်ထားပါ" }
                var total = 0L
                var lastPercent = -1
                connection.inputStream.use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_MODEL_BYTES) { "Whisper model ဖိုင် အရွယ်အစားကန့်သတ်ချက်ကျော်နေပါသည်" }
                            output.write(buffer, 0, count)
                            if (expected > 0) {
                                val percent = ((total * 100) / expected).toInt().coerceIn(0, 100)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    withContext(Dispatchers.Main) { onProgress(percent) }
                                }
                            }
                        }
                        output.fd.sync()
                    }
                }
                require(total >= MIN_MODEL_BYTES) { "ဒေါင်းလုဒ်ရရှိသော model ဖိုင် မပြည့်စုံပါ (${total / (1024 * 1024)} MB)" }
                if (target.exists() && !target.delete()) throw IllegalStateException("အရင် Whisper model ကို အစားထိုးမရပါ")
                if (!temp.renameTo(target)) throw IllegalStateException("Whisper model ကို သိမ်းဆည်းမရပါ")
                withContext(Dispatchers.Main) { onProgress(100) }
                target
            } catch (error: Exception) {
                temp.delete()
                throw error
            } finally {
                connection.disconnect()
            }
        }
}

object NativeWhisperBridge {
    init {
        System.loadLibrary("gi_whisper")
    }

    private external fun nativeTranscribe(
        modelPath: String,
        samples: FloatArray,
        threads: Int,
        language: String
    ): String

    suspend fun transcribe(modelPath: String, samples: FloatArray): String =
        withContext(Dispatchers.IO) {
            require(samples.size >= 8_000) { "အသံဖမ်းချိန် အနည်းဆုံး 0.5 စက္ကန့် လိုအပ်ပါသည်" }
            nativeTranscribe(modelPath, samples, 2, "my").trim()
        }
}

object WhisperAudioRecorder {
    suspend fun record(context: Context, durationMs: Int = 10_000): FloatArray =
        withContext(Dispatchers.IO) {
            val sampleRate = 16_000
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            require(minBuffer > 0) { "ဖုန်း၏ microphone audio format ကို မထောက်ပံ့ပါ" }
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, sampleRate * 2)
            )
            try {
                require(recorder.state == AudioRecord.STATE_INITIALIZED) { "Microphone ကို စတင်မရပါ" }
                recorder.startRecording()
                val output = FloatArray(sampleRate * (durationMs / 1000) + sampleRate)
                var written = 0
                val buffer = ShortArray(4096)
                val deadline = SystemClock.elapsedRealtime() + durationMs
                while (SystemClock.elapsedRealtime() < deadline && written < output.size) {
                    val count = recorder.read(buffer, 0, minOf(buffer.size, output.size - written))
                    if (count > 0) {
                        for (i in 0 until count) output[written++] = buffer[i] / 32768.0f
                    } else if (count < 0) {
                        throw IllegalStateException("Microphone audio read error: $count")
                    }
                }
                require(written >= sampleRate / 2) { "အသံမရရှိပါ။ Microphone ကို စစ်ဆေးပါ" }
                output.copyOf(written)
            } finally {
                try { if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop() } catch (_: Exception) {}
                recorder.release()
            }
        }
}
