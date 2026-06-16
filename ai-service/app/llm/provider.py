import json
from abc import ABC, abstractmethod
from typing import Any

import httpx

from app.config import Settings


class LLMProvider(ABC):
    @abstractmethod
    async def generate_json(
        self, system_prompt: str, user_prompt: str, schema_hint: dict[str, Any]
    ) -> dict[str, Any]:
        raise NotImplementedError

    @abstractmethod
    async def chat(self, system_prompt: str, user_prompt: str) -> str:
        raise NotImplementedError


class OpenAICompatibleProvider(LLMProvider):
    def __init__(self, api_key: str, base_url: str, model: str):
        self.api_key = api_key
        self.base_url = base_url.rstrip("/")
        self.model = model

    async def _complete(self, system_prompt: str, user_prompt: str, json_mode: bool) -> str:
        payload: dict[str, Any] = {
            "model": self.model,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            "temperature": 0.3,
        }
        if json_mode:
            payload["response_format"] = {"type": "json_object"}
        async with httpx.AsyncClient(timeout=90) as client:
            response = await client.post(
                f"{self.base_url}/chat/completions",
                headers={"Authorization": f"Bearer {self.api_key}"},
                json=payload,
            )
            response.raise_for_status()
            return response.json()["choices"][0]["message"]["content"]

    async def generate_json(
        self, system_prompt: str, user_prompt: str, schema_hint: dict[str, Any]
    ) -> dict[str, Any]:
        prompt = f"{user_prompt}\n仅输出JSON，结构参考：{json.dumps(schema_hint, ensure_ascii=False)}"
        return json.loads(await self._complete(system_prompt, prompt, True))

    async def chat(self, system_prompt: str, user_prompt: str) -> str:
        return await self._complete(system_prompt, user_prompt, False)


class GeminiProvider(LLMProvider):
    def __init__(self, api_key: str, base_url: str, model: str):
        self.api_key = api_key
        self.base_url = base_url.rstrip("/")
        self.model = model

    async def _complete(self, system_prompt: str, user_prompt: str, json_mode: bool) -> str:
        generation_config: dict[str, Any] = {"temperature": 0.3}
        if json_mode:
            generation_config["responseMimeType"] = "application/json"

        payload: dict[str, Any] = {
            "systemInstruction": {
                "parts": [{"text": system_prompt}],
            },
            "contents": [{
                "role": "user",
                "parts": [{"text": user_prompt}],
            }],
            "generationConfig": generation_config,
        }
        async with httpx.AsyncClient(timeout=90) as client:
            response = await client.post(
                f"{self.base_url}/models/{self.model}:generateContent",
                headers={
                    "x-goog-api-key": self.api_key,
                    "Content-Type": "application/json",
                },
                json=payload,
            )
            response.raise_for_status()
            data = response.json()
        parts = data["candidates"][0]["content"]["parts"]
        return "".join(part.get("text", "") for part in parts).strip()

    async def generate_json(
        self, system_prompt: str, user_prompt: str, schema_hint: dict[str, Any]
    ) -> dict[str, Any]:
        prompt = (
            f"{user_prompt}\n"
            "Output JSON only. Do not wrap it in Markdown. "
            f"Use this structure as a reference: {json.dumps(schema_hint, ensure_ascii=False)}"
        )
        return json.loads(await self._complete(system_prompt, prompt, True))

    async def chat(self, system_prompt: str, user_prompt: str) -> str:
        return await self._complete(system_prompt, user_prompt, False)


class MockLLMProvider(LLMProvider):
    async def generate_json(
        self, system_prompt: str, user_prompt: str, schema_hint: dict[str, Any]
    ) -> dict[str, Any]:
        return schema_hint

    async def chat(self, system_prompt: str, user_prompt: str) -> str:
        return f"这是离线演示回答。根据当前学习上下文，建议先理解核心概念，再通过一个小例子验证：{user_prompt[:80]}"


def create_llm_provider(settings: Settings) -> LLMProvider:
    provider = settings.llm_provider.lower()
    if provider == "mock":
        return MockLLMProvider()

    if provider == "gemini":
        api_key = settings.llm_api_key or settings.gemini_api_key
        if not api_key:
            return MockLLMProvider()
        model = settings.llm_model
        if model == "mock-learning-model":
            model = "gemini-2.5-flash"
        return GeminiProvider(
            api_key=api_key,
            base_url=settings.llm_base_url or "https://generativelanguage.googleapis.com/v1beta",
            model=model,
        )

    if not settings.llm_api_key:
        return MockLLMProvider()

    defaults = {
        "openai": "https://api.openai.com/v1",
        "deepseek": "https://api.deepseek.com/v1",
        "qwen": "https://dashscope.aliyuncs.com/compatible-mode/v1",
    }
    base_url = settings.llm_base_url or defaults.get(provider)
    if not base_url:
        raise ValueError(f"Unsupported LLM provider: {provider}")
    return OpenAICompatibleProvider(
        api_key=settings.llm_api_key,
        base_url=base_url,
        model=settings.llm_model,
    )

