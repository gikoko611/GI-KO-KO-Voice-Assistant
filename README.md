# G.I KO KO Voice Assistant

Native Android voice assistant for Android 10+ with ARMv7 targeting, English speech output with Android TTS, local GGUF model import, guarded tools, and GitHub Actions APK builds.

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


## Speech output

The main screen and floating assistant use the installed Android Text-to-Speech engine with the English (United States) voice (`en-US`). No Myanmar TTS model or Python runtime is bundled or required. Voice availability depends on the English voice data installed in Android TTS settings.
