package com.gi.koko.voiceassistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

private val Gold = Color(0xFFFFC857)
private val Dark = Color(0xFF090B12)
private val Panel = Color(0xFF171B27)
private val Muted = Color(0xFF9AA3B5)

data class ChatLine(val fromUser: Boolean, val text: String)

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.US
        }
        setContent { VoiceAssistantScreen(::speak) }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gikoko-response")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceAssistantScreen(speak: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("gi_voice_prefs", 0) }
    var prompt by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready • Offline-first interface") }
    var selectedModel by remember { mutableStateOf(prefs.getString("model_uri", null)) }
    var pendingAction by remember { mutableStateOf<String?>(null) }
    var toolRequest by remember { mutableStateOf("calc: 12 * (3 + 4)") }
    var showHistory by remember { mutableStateOf(false) }
    var showLlmSettings by remember { mutableStateOf(false) }
    var llmBusy by remember { mutableStateOf(false) }
    var llmTesting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val savedConfig = remember { LlmClient.loadConfig(context) }
    var llmBaseUrl by remember { mutableStateOf(savedConfig.first) }
    var llmModel by remember { mutableStateOf(savedConfig.second) }
    var llmApiKey by remember { mutableStateOf(savedConfig.third) }
    val chatListState = rememberLazyListState()
    var messages by remember {
        mutableStateOf(ChatStore.load(context).ifEmpty { listOf(ChatLine(false, "မင်္ဂလာပါ။ G.I KO KO Voice Assistant ပါ။\nModel ကို import လုပ်ပြီး voice သို့မဟုတ် text နဲ့ စတင်နိုင်ပါတယ်။")) })
    }
    LaunchedEffect(messages) { ChatStore.save(context, messages) }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) chatListState.animateScrollToItem(messages.lastIndex) }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            selectedModel = uri.toString()
            prefs.edit().putString("model_uri", uri.toString()).apply()
            status = "GGUF file selected • Local inference backend is not installed in this ARMv7 build"
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val name = uri.lastPathSegment ?: "selected file"
            messages = messages + ChatLine(false, "File attached: $name\nFile access is granted only to this selected document.")
            status = "Document selected"
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) status = "Microphone permission granted. Tap MIC again to start."
        else status = "Microphone permission denied"
    }

    val floatingMicPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (Settings.canDrawOverlays(context)) {
                ContextCompat.startForegroundService(context, Intent(context, FloatingAssistantService::class.java))
                status = "Floating Assistant started"
            } else {
                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")))
                status = "Allow display over other apps, then tap Enable Floating again"
            }
        } else {
            status = "Microphone permission is required for floating voice input"
        }
    }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }

    DisposableEffect(speechRecognizer) {
        onDispose { speechRecognizer?.destroy() }
    }

    fun startListening() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        val recognizer = speechRecognizer
        if (recognizer == null) {
            status = "Speech recognition service unavailable on this device"
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "my-MM")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "my-MM")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true; status = "Listening…" }
            override fun onBeginningOfSpeech() { }
            override fun onRmsChanged(rmsdB: Float) { }
            override fun onBufferReceived(buffer: ByteArray?) { }
            override fun onEndOfSpeech() { listening = false; status = "Processing speech…" }
            override fun onError(error: Int) { listening = false; status = "Speech error ($error) • Check installed language packs" }
            override fun onResults(results: Bundle?) {
                listening = false
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (spoken.isNotBlank()) prompt = spoken
                status = if (spoken.isBlank()) "No speech detected" else "Speech captured"
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!partial.isNullOrBlank()) prompt = partial
            }
            override fun onEvent(eventType: Int, params: Bundle?) { }
        })
        recognizer.startListening(intent)
    }

    fun runConfirmedTool(action: String) {
        try {
            val result = when {
                action.startsWith("calc:") -> "Result: ${SafeToolEngine.calculate(action.removePrefix("calc:").trim())}"
                action.startsWith("open:") -> SafeToolEngine.openApp(context, action.removePrefix("open:").trim())
                action.startsWith("termux:") -> {
                    SafeToolEngine.runTermuxAllowlisted(context, action.removePrefix("termux:").trim())
                    "Termux command sent. Termux must allow external app commands."
                }
                else -> "Unsupported tool request"
            }
            messages = messages + ChatLine(false, result)
            status = "Tool completed"
            speak(result)
        } catch (error: Exception) {
            val result = "Tool blocked: ${error.message ?: "unknown error"}"
            messages = messages + ChatLine(false, result)
            status = "Tool failed safely"
        }
    }

    fun sendMessage() {
        val text = prompt.trim()
        if (text.isEmpty() || llmBusy) return
        val priorHistory = messages.toList()
        messages = priorHistory + ChatLine(true, text)
        prompt = ""
        llmBusy = true
        status = "Connecting to LLM…"
        scope.launch {
            try {
                val answer = LlmClient.reply(context, priorHistory, text)
                messages = messages + ChatLine(false, answer)
                status = "LLM response received"
                speak(answer)
            } catch (error: Exception) {
                val answer = "LLM မဖြေဆိုနိုင်ပါ။ ${error.message ?: "Connection error"}"
                messages = messages + ChatLine(false, answer)
                status = "LLM connection failed"
            } finally { llmBusy = false }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Gold, secondary = Gold, background = Dark, surface = Panel,
        onPrimary = Dark, onBackground = Color.White, onSurface = Color.White
    )) {
        Scaffold(containerColor = Dark, topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("G.I KO KO", color = Gold, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text("VOICE ASSISTANT", color = Color.White, fontSize = 11.sp, letterSpacing = 2.sp)
                    }
                },
                actions = {
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Default.History, contentDescription = "History", tint = Gold)
                    }
                    IconButton(onClick = { context.startActivity(Intent(context, SkillCenterActivity::class.java)) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Gold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Dark)
            )
        }) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.fillMaxWidth().height(104.dp)
                        .background(Brush.radialGradient(listOf(Color(0xFF5B4218), Panel, Dark)), RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(if (listening) 82.dp else 70.dp)
                                .background(Brush.linearGradient(listOf(Gold, Color(0xFF7C4C10))), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(if (listening) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = "Voice state", tint = Dark, modifier = Modifier.size(34.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(if (listening) "LISTENING" else "MIDNIGHT GOLD", color = Gold, fontWeight = FontWeight.Bold)
                        Text(status, color = Muted, fontSize = 11.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AssistChip(onClick = { modelPicker.launch(arrayOf("*/*")) }, label = {
                        Text(if (selectedModel == null) "Import GGUF" else "GGUF selected")
                    }, leadingIcon = { Icon(Icons.Default.AttachFile, null) })
                    AssistChip(onClick = { filePicker.launch(arrayOf("*/*")) }, label = { Text("Attach file") })
                    AssistChip(onClick = { showLlmSettings = true }, label = { Text("LLM Setup") })
                }

                AssistChip(
                    onClick = {
                        when {
                            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ->
                                floatingMicPermission.launch(Manifest.permission.RECORD_AUDIO)
                            !Settings.canDrawOverlays(context) -> {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")))
                                status = "Allow display over other apps, then tap Enable Floating again"
                            }
                            else -> {
                                ContextCompat.startForegroundService(context,
                                    Intent(context, FloatingAssistantService::class.java))
                                status = "Floating Assistant active • Tap the G bubble over any app"
                            }
                        }
                    },
                    label = { Text(if (Settings.canDrawOverlays(context)) "Enable Floating Assistant" else "Grant Floating Permission") },
                    leadingIcon = { Icon(Icons.Default.Mic, contentDescription = null) }
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = toolRequest, onValueChange = { toolRequest = it },
                        modifier = Modifier.weight(1f), singleLine = true,
                        label = { Text("Safe tool request") },
                        placeholder = { Text("calc: 2+2 | open: package.name | termux: pwd") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Gold, unfocusedBorderColor = Muted,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White
                        )
                    )
                    Button(onClick = { pendingAction = toolRequest.trim() },
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Dark)) {
                        Text("Run")
                    }
                }
                Text("CONVERSATION", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                LazyColumn(
                    state = chatListState,
                    modifier = Modifier.weight(1f).fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { line ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement =
                            if (line.fromUser) Arrangement.End else Arrangement.Start) {
                            Surface(
                                color = if (line.fromUser) Color(0xFF3B301C) else Panel,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(0.90f)
                            ) {
                                Text(line.text, Modifier.padding(12.dp), color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = prompt, onValueChange = { prompt = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("မြန်မာ / English ဖြင့် မေးပါ…", color = Muted) },
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Gold, unfocusedBorderColor = Muted,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White
                        )
                    )
                    IconButton(onClick = { if (listening) speechRecognizer?.stopListening() else startListening() }) {
                        Icon(if (listening) Icons.Default.Stop else Icons.Default.Mic, null, tint = Gold)
                    }
                    IconButton(onClick = { sendMessage() }, enabled = !llmBusy) {
                        if (llmBusy) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Gold, strokeWidth = 2.dp)
                        else Icon(Icons.Default.Send, null, tint = Gold)
                    }
                }
                Text("Powered By I KO KO", Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
                    color = Gold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (showHistory) {
            AlertDialog(
                onDismissRequest = { showHistory = false },
                title = { Text("CHAT HISTORY", color = Gold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${messages.size} messages saved on this device (maximum 200).", color = Color.White)
                        Text("Conversation is restored automatically when the app is reopened.", color = Muted, fontSize = 12.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        messages = listOf(ChatLine(false, "မင်္ဂလာပါ။ G.I KO KO Voice Assistant ပါ။\nModel ကို import လုပ်ပြီး voice သို့မဟုတ် text နဲ့ စတင်နိုင်ပါတယ်။"))
                        ChatStore.clear(context)
                        showHistory = false
                    }) { Text("CLEAR HISTORY", color = Gold) }
                },
                dismissButton = { TextButton(onClick = { showHistory = false }) { Text("CLOSE", color = Muted) } },
                containerColor = Panel
            )
        }
        if (showLlmSettings) {
            AlertDialog(
                onDismissRequest = { showLlmSettings = false },
                title = { Text("LLM CONNECTION", color = Gold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("OpenAI-compatible API • API key ကို Android Keystore ဖြင့် encrypt လုပ်သိမ်းထားသည်။", color = Muted, fontSize = 12.sp)
                        OutlinedTextField(value = llmBaseUrl, onValueChange = { llmBaseUrl = it }, label = { Text("Base URL") }, singleLine = true)
                        OutlinedTextField(value = llmModel, onValueChange = { llmModel = it }, label = { Text("Model ID") }, singleLine = true)
                        OutlinedTextField(value = llmApiKey, onValueChange = { llmApiKey = it }, label = { Text("API Key") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                        Text("Example: https://api.groq.com/openai/v1 • Model ID ကို provider မှရယူပါ။", color = Muted, fontSize = 11.sp)
                        TextButton(
                            enabled = !llmTesting,
                            onClick = {
                                try {
                                    require(llmBaseUrl.startsWith("https://")) { "HTTPS Base URL သုံးပါ" }
                                    require(llmModel.isNotBlank() && llmApiKey.isNotBlank()) { "Model ID နဲ့ API Key ထည့်ပါ" }
                                    LlmClient.saveConfig(context, llmBaseUrl, llmModel, llmApiKey)
                                    llmTesting = true
                                    status = "Testing provider connection…"
                                    scope.launch {
                                        try { status = LlmClient.testConnection(context) }
                                        catch (e: Exception) { status = "Connection test failed: ${e.message}" }
                                        finally { llmTesting = false }
                                    }
                                } catch (e: Exception) { status = e.message ?: "Invalid settings" }
                            }
                        ) { Text(if (llmTesting) "TESTING…" else "TEST CONNECTION", color = Gold) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        try {
                            require(llmBaseUrl.startsWith("https://")) { "HTTPS Base URL သုံးပါ" }
                            require(llmModel.isNotBlank()) { "Model ID ထည့်ပါ" }
                            LlmClient.saveConfig(context, llmBaseUrl, llmModel, llmApiKey)
                            showLlmSettings = false
                            status = "LLM settings saved securely"
                        } catch (e: Exception) { status = e.message ?: "Invalid settings" }
                    }) { Text("SAVE", color = Gold) }
                },
                dismissButton = { TextButton(onClick = { showLlmSettings = false }) { Text("CANCEL", color = Muted) } },
                containerColor = Panel
            )
        }
        if (pendingAction != null) {
            AlertDialog(
                onDismissRequest = { pendingAction = null },
                title = { Text("Confirm tool execution", color = Gold) },
                text = { Text("Run this request?\\n\\n${pendingAction}\\n\\nOnly the built-in allowlisted actions are accepted.", color = Color.White) },
                confirmButton = {
                    TextButton(onClick = {
                        val action = pendingAction.orEmpty()
                        pendingAction = null
                        runConfirmedTool(action)
                    }) { Text("CONFIRM", color = Gold) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingAction = null }) { Text("CANCEL", color = Muted) }
                },
                containerColor = Panel
            )
        }
    }
}
