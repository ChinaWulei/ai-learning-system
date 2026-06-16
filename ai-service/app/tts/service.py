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
            raise RuntimeError("Gemini TTS 未配置，请设置 GEMINI_API_KEY")

        instruction = self._language_instruction(language)
        payload = {
            "contents": [{
                "parts": [{
                    "text": (
                        f"{instruction}"
                        "保持原意，不添加或省略内容，知识点处适当停顿：\n"
                        f"{text}"
                    )
                }]
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
            raise RuntimeError("Gemini 未返回有效音频") from exception
        return self._pcm_to_wav(pcm)

    @staticmethod
    def _language_instruction(language: str) -> str:
        instructions = {
            "zh-CN": "请使用自然、温暖、耐心的普通话教师语气朗读以下内容。",
            "en-US": "Read the following content in clear, warm, patient American English teacher style. ",
            "ja-JP": "以下の内容を、自然で温かく、わかりやすい日本語の先生の口調で読み上げてください。",
            "yue-HK": "請用自然、親切、有耐性嘅粵語老師語氣朗讀以下內容。",
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
