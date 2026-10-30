"""CPU-only smoke test for the upstream MyanmarTTS package.

This runs on GitHub Actions to verify the upstream Python model and produce
an audio artifact. It does NOT convert the model to an Android/ARMv7 runtime.
"""
from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import soundfile as sf
from myanmar_tts import MyanmarTTS

OUTPUT_DIR = Path("build/myanmar-tts-smoke")
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

TEXT = "မင်္ဂလာပါ။ G.I KO KO Voice Assistant မှ ကြိုဆိုပါတယ်။"

print("Loading MyanmarTTS on CPU. First run downloads model weights from Hugging Face.")
tts = MyanmarTTS(device="cpu")
print("Generating short Burmese sample (Euler, 8 steps).")
audio = tts.tts(TEXT, solver="euler", step=8)

if hasattr(audio, "detach"):
    audio = audio.detach().cpu().numpy()
audio = np.asarray(audio, dtype=np.float32).squeeze()

if audio.size == 0 or not np.isfinite(audio).all():
    raise RuntimeError("MyanmarTTS returned empty or non-finite audio.")

wav_path = OUTPUT_DIR / "myanmar_sample.wav"
sf.write(wav_path, audio, 44100, subtype="PCM_16")

report = {
    "status": "passed",
    "device": "cpu",
    "text": TEXT,
    "sample_rate_hz": 44100,
    "samples": int(audio.size),
    "duration_seconds": round(float(audio.size / 44100), 3),
    "peak_amplitude": round(float(np.max(np.abs(audio))), 6),
    "wav_file": str(wav_path),
    "android_armv7_ready": False,
    "note": (
        "This validates upstream Python CPU inference only. "
        "It does not prove Android compatibility or offline playback inside the APK."
    ),
}
(OUTPUT_DIR / "report.json").write_text(
    json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8"
)
print(json.dumps(report, ensure_ascii=False, indent=2))
