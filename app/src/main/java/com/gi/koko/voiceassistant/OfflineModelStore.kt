package com.gi.koko.voiceassistant

import android.content.Context
import android.net.Uri
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object OfflineModelStore {
    private const val MAX_MODEL_BYTES = 700_000_000L

    suspend fun importModel(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val folder = File(context.filesDir, "offline_models")
        if (!folder.exists() && !folder.mkdirs()) {
            throw IllegalStateException("Model folder ဖန်တီးမရပါ")
        }
        val stat = StatFs(context.filesDir.absolutePath)
        require(stat.availableBytes > 150L * 1024L * 1024L) {
            "ဖုန်းထဲမှာ model ကူးယူရန် storage အလွတ် 150 MB မလုံလောက်ပါ"
        }
        val target = File(folder, "gi_local_model.gguf")
        val temp = File(folder, "gi_local_model.gguf.tmp")
        var total = 0L
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("ရွေးထားတဲ့ GGUF ဖိုင်ကို ဖတ်မရပါ")
            input.use { source ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_MODEL_BYTES) {
                            "ARMv7 ဖုန်းအတွက် model ဖိုင် 700 MB ထက်မကြီးစေရပါ"
                        }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            require(total > 1024L * 1024L) { "GGUF ဖိုင်အရွယ်အစား မမှန်ပါ" }
            val header = ByteArray(4)
            temp.inputStream().use { input ->
                require(input.read(header) == 4 && String(header, Charsets.US_ASCII) == "GGUF") {
                    "ဖိုင်သည် မှန်ကန်သော GGUF model မဟုတ်ပါ"
                }
            }
            if (target.exists() && !target.delete()) {
                throw IllegalStateException("အရင် model ဖိုင်ကို အစားထိုးမရပါ")
            }
            if (!temp.renameTo(target)) {
                throw IllegalStateException("Model ကို app storage သို့ ရွှေ့မရပါ")
            }
            target
        } catch (error: Exception) {
            temp.delete()
            throw error
        }
    }

    fun deleteModel(context: Context, path: String?): Boolean {
        if (path.isNullOrBlank()) return true
        val file = File(path).canonicalFile
        val folder = File(context.filesDir, "offline_models").canonicalFile
        if (file.parentFile != folder) return false
        return !file.exists() || file.delete()
    }
}
