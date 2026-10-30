# G.I KO KO Voice Assistant

Native Android voice assistant for Redmi 9A / Android 10 with an ARMv7 (armeabi-v7a) release target.

## v0.2.0 features
- OpenAI-compatible online LLM chat (Groq and other compatible providers).
- Provider connection test using the provider's /models endpoint.
- API key encrypted at rest with Android Keystore AES-GCM.
- AI function calling for calculator, app launch, and restricted Termux commands. Every tool action requires explicit user confirmation.
- Local conversation history (up to 200 messages) with clear-history control.
- Burmese speech recognition input and English (US) Android Text-to-Speech output.
- Expanded, scrollable conversation area and floating answer panel.
- ARMv7 debug and signed release APKs built by GitHub Actions.

## Target device
- Redmi 9A / Android 10 (API 29)
- armeabi-v7a
- Kotlin + Jetpack Compose

## Configure online LLM
1. Open LLM Setup.
2. Enter an OpenAI-compatible Base URL, exact Model ID, and API key.
3. Tap TEST CONNECTION. The app checks the provider's /models endpoint.
4. Save, then send a message.

Example Groq Base URL: https://api.groq.com/openai/v1.
The model ID and API key must be obtained from the selected provider. No API key is bundled in the APK.

## Offline capabilities and GGUF limitation
The app works offline for its built-in calculator and local chat-history access. Android speech recognition may require an installed offline language pack. GGUF import is currently a file-selection feature only; native GGUF inference is not included in this ARMv7 release. Current upstream llama.cpp Android documentation demonstrates its NDK cross-build with arm64-v8a; a compatible and tested 32-bit ARMv7 inference library is not bundled here. Do not treat selecting a GGUF file as proof that local model inference works.

## Build and download
Open Actions → Android CI and Release → latest successful run → Artifacts.
- GI-KO-KO-Voice-Assistant-v0.2.0-release-armv7 is the signed APK for direct installation.
- GI-KO-KO-Voice-Assistant-debug-armv7 is the debug APK.

The CI release APK uses the Android debug signing key so it can be installed directly for device testing. It is not Play Store upload signing. Use a private upload keystore before publishing through Google Play. Uninstalling a build signed with a different key may be required before installing this one.

## Safety
- App launching is restricted to a valid installed package name.
- Termux commands are restricted to pwd, ls, and python --version.
- AI-requested tools are never executed without the confirmation dialog.
- No root or Accessibility permission is required.
- Floating assistant requires Android's Display over other apps permission.
