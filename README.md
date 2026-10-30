# G.I KO KO Voice Assistant

Native Android voice assistant for Android 10+ with ARMv7 targeting, Burmese/English speech UI, local GGUF model import, guarded tools, and GitHub Actions APK builds.

## Target device
- Redmi 9A / Android 10 (API 29)
- `armeabi-v7a` primary ABI
- Kotlin + Jetpack Compose

## Build
GitHub Actions builds a debug APK on push. Open **Actions → Android CI → latest run → Artifacts** to download it.

## Current implementation scope
- Voice input through Android `SpeechRecognizer` (offline availability depends on installed recognition service and language packs).
- Spoken output through installed Android Text-to-Speech voices (Burmese voice availability depends on the device engine).
- GGUF file import through Android Storage Access Framework.
- Safe built-in tools with confirmation before execution.
- Draggable always-on-top floating G bubble with expandable text + voice mini-panel (requires Android 'Display over other apps' permission).
- Foreground notification with a Stop Floating action.
- Termux hand-off is optional and requires Termux to be installed and explicitly enabled.

The imported GGUF model is not bundled in the APK. A native inference backend is a separate integration step; this starter does not claim that model inference is already wired up. The floating panel currently provides voice capture, text input, text display, TTS output, and a basic calculator route; it does not yet route questions through the GGUF model.

## Safety
Destructive file operations and external command execution are not run automatically. Tool requests are validated and require user confirmation. The app does not require root or Accessibility permission.


## Myanmar Offline TTS investigation

The upstream [freococo/MyanmarTTS](https://huggingface.co/freococo/MyanmarTTS) project is a Burmese StableTTS + Vocos PyTorch model. Its published quick start uses the Python package and PyTorch; the published checkpoint is not an Android-ready ONNX/TFLite package. Therefore, this repository does **not** claim that Burmese offline speech already works inside the APK.

### Reproducible CPU smoke test

1. Open **Actions → Myanmar TTS Model Smoke Test**.
2. Choose **Run workflow**.
3. Wait for the CPU inference job to finish.
4. Download the `myanmar-tts-cpu-smoke-test` artifact and listen to `myanmar_sample.wav`.

The smoke test verifies upstream Python CPU inference and stores a JSON report. It runs in GitHub Actions; it does not run on the phone and does not establish Android ARMv7 compatibility.

### Android integration gate

Before bundling an offline Burmese voice into the APK, a separate conversion/runtime proof is required:
- Export the StableTTS and Vocos stages to a mobile-supported format without unsupported operators.
- Verify Burmese text frontend, vocabulary, acoustic model, and vocoder end-to-end.
- Run on Android 10 `armeabi-v7a`, measure peak RAM, latency, and APK/model storage.
- Only then add model download/import, offline voice selection, speed/pitch controls, and shared playback for the main screen and floating assistant.

Until that gate passes, the app continues to use installed Android TTS voices as its fallback. The MyanmarTTS smoke-test workflow is intentionally manual and does not change the normal APK build.
