import base64
import io
import wave

import httpx

from app.config import Settings


class GeminiTtsService:
    def __init__(self, settings: Settings):
        self.api_key = settings.gemini_api_key
        self.model = settings.gemini_tts_model
        self.voice = settings.gemini_tts_voice

    async def synthesize(self, text: str, language: str = "zh-CN") -> bytes:
        if not self.api_key:
            raise RuntimeError("Gemini TTS is not configured. Please set GEMINI_API_KEY.")

        instruction = self._language_instruction(language)
        prompt = (
            f"{instruction}\n"
            "Keep the original meaning and all key knowledge points. "
            "Do not add extra explanations. "
            "Read the final result directly as a teacher speaking to a student.\n"
            f"{text}"
        )
        payload = {
            "contents": [{
                "parts": [{"text": prompt}]
            }],
            "generationConfig": {
                "responseModalities": ["AUDIO"],
                "speechConfig": {
                    "voiceConfig": {
                        "prebuiltVoiceConfig": {"voiceName": self.voice}
                    }
                },
            },
        }
        url = (
            "https://generativelanguage.googleapis.com/v1beta/models/"
            f"{self.model}:generateContent"
        )
        async with httpx.AsyncClient(timeout=120) as client:
            response = await client.post(
                url,
                headers={
                    "x-goog-api-key": self.api_key,
                    "Content-Type": "application/json",
                },
                json=payload,
            )
        response.raise_for_status()
        data = response.json()
        try:
            inline_data = data["candidates"][0]["content"]["parts"][0]["inlineData"]
            pcm = base64.b64decode(inline_data["data"])
        except (KeyError, IndexError, TypeError, ValueError) as exception:
            raise RuntimeError("Gemini did not return valid audio data.") from exception
        return self._pcm_to_wav(pcm)

    @staticmethod
    def _language_instruction(language: str) -> str:
        instructions = {
            "zh-CN": (
                "If the content is not already Mandarin Chinese, translate it into "
                "natural Mandarin Chinese first. Then read it in a warm, patient "
                "Mandarin Chinese teacher voice."
            ),
            "en-US": (
                "Translate the content into natural American English first. "
                "Then read it in a clear, warm, patient American English teacher voice."
            ),
            "ja-JP": (
                "Translate the content into natural Japanese first. "
                "Then read it in a clear, warm, patient Japanese teacher voice."
            ),
            "yue-HK": (
                "Translate the content into natural Cantonese used in Hong Kong first. "
                "Then read it in a warm, patient Cantonese teacher voice."
            ),
        }
        return instructions.get(language, instructions["zh-CN"])

    @staticmethod
    def _pcm_to_wav(pcm: bytes) -> bytes:
        output = io.BytesIO()
        with wave.open(output, "wb") as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(24000)
            wav.writeframes(pcm)
        return output.getvalue()
