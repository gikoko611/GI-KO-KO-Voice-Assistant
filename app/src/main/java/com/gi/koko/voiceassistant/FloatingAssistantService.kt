package com.gi.koko.voiceassistant

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.runBlocking
import android.speech.tts.TextToSpeech
import android.provider.Settings
import android.speech.*
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.app.NotificationCompat
import java.util.Locale
import kotlin.math.abs

class FloatingAssistantService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: View? = null
    private var panel: View? = null
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var expanded = false
    private val gold = Color.rgb(255, 200, 87)
    private val dark = Color.rgb(12, 14, 22)
    private val panelColor = Color.rgb(25, 29, 42)
    private var input: EditText? = null
    private var answerView: TextView? = null
    private var statusView: TextView? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(7301, notification())
        tts = TextToSpeech(this) { result ->
            if (result == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }
        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    private fun params(width: Int, height: Int, gravity: Int = Gravity.TOP or Gravity.START): WindowManager.LayoutParams =
        WindowManager.LayoutParams(width, height, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { this.gravity = gravity; x = 18; y = 210 }

    private fun rounded(color: Int, radius: Float, stroke: Int = gold): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            setStroke(dp(1), stroke)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showBubble() {
        if (bubble != null) return
        val b = TextView(this).apply {
            text = "G"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(dark)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(gold, Color.rgb(173, 111, 23))).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(2), Color.WHITE)
            }
            elevation = dp(10).toFloat()
        }
        val p = params(dp(58), dp(58))
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        b.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = p.x; startY = p.y; moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > dp(5) || abs(dy) > dp(5)) moved = true
                    p.x = startX + dx; p.y = startY + dy
                    try { windowManager.updateViewLayout(b, p) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) togglePanel()
                    true
                }
                else -> true
            }
        }
        bubble = b
        windowManager.addView(b, p)
    }

    private fun togglePanel() {
        if (expanded) closePanel() else openPanel()
    }

    private fun openPanel() {
        if (panel != null) return
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(dark, dp(20).toFloat(), gold)
            elevation = dp(14).toFloat()
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "G.I KO KO  •  VOICE ASSISTANT"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(gold)
        }
        val close = TextView(this).apply {
            text = "  ✕  "
            textSize = 18f
            setTextColor(Color.WHITE)
            setOnClickListener { closePanel() }
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(38), 1f))
        header.addView(close)
        root.addView(header)

        statusView = TextView(this).apply {
            text = "Ready • Tap mic or type below"
            textSize = 11f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(4), 0, dp(8))
        }
        root.addView(statusView)

        answerView = TextView(this).apply {
            text = "မင်္ဂလာပါ။ ဘာကူညီပေးရမလဲ။"
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(panelColor, dp(12).toFloat(), Color.DKGRAY)
            textIsSelectable = true
        }
        val answerScroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            background = rounded(panelColor, dp(12).toFloat(), Color.DKGRAY)
            addView(answerView, ScrollView.LayoutParams(-1, -2))
        }
        root.addView(answerScroll, LinearLayout.LayoutParams(-1, dp(230)))

        input = EditText(this).apply {
            hint = "မြန်မာ / English ဖြင့် ရိုက်မေးပါ…"
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setSingleLine(false)
            maxLines = 3
            background = rounded(panelColor, dp(12).toFloat(), Color.GRAY)
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        val inputLp = LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(10) }
        root.addView(input, inputLp)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val mic = actionButton("🎙 MIC")
        mic.setOnClickListener { startVoiceInput() }
        val send = actionButton("SEND")
        send.setOnClickListener { submitQuestion() }
        val speak = actionButton("🔊 SPEAK")
        speak.setOnClickListener {
            val text = answerView?.text?.toString().orEmpty()
            if (text.isNotBlank()) speak(text)
        }
        actions.addView(mic, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(4) })
        actions.addView(send, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(4) })
        actions.addView(speak, LinearLayout.LayoutParams(0, dp(44), 1f))
        root.addView(actions)

        val foot = TextView(this).apply {
            text = "Powered By I KO KO  •  Drag G bubble to move"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(gold)
            setPadding(0, dp(8), 0, 0)
        }
        root.addView(foot)

        val panelWidth = (resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(dp(260))
        val lp = params(panelWidth, WindowManager.LayoutParams.WRAP_CONTENT)
        lp.x = dp(12)
        lp.y = dp(120)
        lp.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        panel = root
        try {
            windowManager.addView(root, lp)
            expanded = true
        } catch (_: Exception) {
            panel = null
            statusView = null
        }
    }

    private fun actionButton(label: String): TextView = TextView(this).apply {
        text = label
        textSize = 12f
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(dark)
        background = rounded(gold, dp(10).toFloat(), gold)
    }

    private fun closePanel() {
        panel?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        panel = null
        input = null
        answerView = null
        statusView = null
        expanded = false
        try { recognizer?.cancel() } catch (_: Exception) {}
    }

    private fun submitQuestion() {
        val question = input?.text?.toString()?.trim().orEmpty()
        if (question.isBlank()) {
            statusView?.text = "စာမေးခွန်း ရိုက်ထည့်ပါ"
            return
        }
        input?.setText("")
        statusView?.text = "LLM is thinking…"
        if (question.startsWith("calc:", true)) {
            val reply = try { "ရလဒ်: " + SafeToolEngine.calculate(question.substringAfter(":").trim()) }
                catch (e: Exception) { "တွက်ချက်မှု မအောင်မြင်ပါ: ${e.message}" }
            answerView?.text = reply
            statusView?.text = "Calculator complete"
            speak(reply)
            return
        }
        Thread {
            val reply = runCatching {
                runBlocking { LlmClient.reply(applicationContext, emptyList(), question) }
            }.getOrElse { "LLM မဖြေဆိုနိုင်ပါ။ ${it.message ?: "Connection error"}" }
            Handler(Looper.getMainLooper()).post {
                answerView?.text = reply
                statusView?.text = if (reply.startsWith("LLM မဖြေဆိုနိုင်ပါ")) "LLM connection failed" else "LLM response ready"
                if (!reply.startsWith("LLM မဖြေဆိုနိုင်ပါ")) speak(reply)
            }
        }.start()
    }

    private fun startVoiceInput() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            statusView?.text = "Microphone permission ကို Main App မှာ အရင်ခွင့်ပြုပါ"
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusView?.text = "Speech recognition service မရှိပါ"
            return
        }
        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { statusView?.text = "Listening…" }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { statusView?.text = "Processing speech…" }
                override fun onError(error: Int) { statusView?.text = "Speech error $error • language pack စစ်ပါ" }
                override fun onResults(results: Bundle?) {
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    input?.setText(spoken)
                    statusView?.text = if (spoken.isBlank()) "အသံမကြားရပါ" else "Voice captured • Send ကိုနှိပ်ပါ"
                    if (spoken.isNotBlank()) submitQuestion()
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!partial.isNullOrBlank()) input?.setText(partial)
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "my-MM")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "my-MM")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            recognizer?.startListening(speechIntent)
        } catch (e: Exception) {
            statusView?.text = "Mic unavailable: ${e.message}"
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gi-floating-response")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("gi_floating_assistant", "G.I KO KO Assistant",
                NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps the floating assistant available over other apps"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0,
            packageManager.getLaunchIntentForPackage(packageName) ?: Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val stopIntent = Intent(this, FloatingAssistantService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        return NotificationCompat.Builder(this, "gi_floating_assistant")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("G.I KO KO Voice Assistant")
            .setContentText("Floating assistant is active • Tap G bubble to ask")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop floating", stopPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        closePanel()
        bubble?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        bubble = null
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val ACTION_STOP = "com.gi.koko.voiceassistant.action.STOP_FLOATING"
    }
}
