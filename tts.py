from __future__ import annotations
import asyncio
import subprocess
from pathlib import Path

VOICES = {"en": "en-US-JennyNeural", "ar": "ar-EG-SalmaNeural"}

async def _edge_save(text: str, language: str, out_path: Path) -> None:
    import edge_tts
    voice = VOICES.get(language, VOICES["en"])
    await edge_tts.Communicate(text, voice=voice).save(str(out_path))


def synthesize(text: str, language: str, out_path: str | Path) -> Path:
    out = Path(out_path)
    out.parent.mkdir(parents=True, exist_ok=True)
    try:
        asyncio.run(_edge_save(text, language, out))
        return out
    except Exception:
        wav = out.with_suffix(".wav")
        voice = "en-us" if language == "en" else "ar"
        subprocess.run(["espeak-ng", "-v", voice, "-s", "155", "-w", str(wav), text], check=True)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), str(out)], check=True)
        wav.unlink(missing_ok=True)
        return out
