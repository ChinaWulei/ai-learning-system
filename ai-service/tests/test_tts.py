import io
import wave

from app.tts.service import GeminiTtsService


def test_pcm_to_wav_uses_gemini_audio_format():
    audio = GeminiTtsService._pcm_to_wav(b"\x00\x00" * 240)

    with wave.open(io.BytesIO(audio), "rb") as wav:
        assert wav.getnchannels() == 1
        assert wav.getsampwidth() == 2
        assert wav.getframerate() == 24000
        assert wav.getnframes() == 240
