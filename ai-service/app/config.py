from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_env: str = "development"
    llm_provider: str = "mock"
    llm_model: str = "mock-learning-model"
    llm_api_key: str = ""
    llm_base_url: str = ""
    gemini_api_key: str = ""
    gemini_tts_model: str = "gemini-3.1-flash-preview-tts"
    gemini_tts_voice: str = "Sulafat"
    embedding_model: str = "BAAI/bge-small-zh-v1.5"
    use_bge: bool = False
    java_base_url: str = "http://localhost:8080"
    java_internal_token: str = "change-me"
    data_dir: Path = Path("data")
    chunk_size: int = 600
    chunk_overlap: int = 100
    retrieval_top_k: int = 5

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")


@lru_cache
def get_settings() -> Settings:
    settings = Settings()
    settings.data_dir.mkdir(parents=True, exist_ok=True)
    return settings

