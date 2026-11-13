import os
from functools import lru_cache
from typing import Literal
from pydantic_settings import BaseSettings, SettingsConfigDict

_ROOT_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
_ROOT_ENV = os.path.join(_ROOT_DIR, ".env")


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=(_ROOT_ENV, ".env", "../.env", "../../.env"),
        env_file_encoding="utf-8",
        extra="ignore"
    )

    # Security & Tokens
    internal_service_token: str = "bap_internal_service_secure_token_secret_key_meu_2026"

    # Database
    database_url: str = "postgresql://bap_user:bap_password_change_me@localhost:5432/bap_advisor"

    # Server binding
    ai_engine_host: str = "0.0.0.0"
    ai_engine_port: int = 8001

    # Dual-Mode LLM Gateway
    llm_provider: Literal["nanogpt", "ollama", "vllm"] = "nanogpt"
    nanogpt_base_url: str = "https://api.nano-gpt.com/api/v1"
    nanogpt_api_key: str = ""
    nanogpt_model: str = "gpt-4o-mini"

    ollama_base_url: str = "http://localhost:11434/v1"
    ollama_model: str = "qwen2.5:14b-instruct"

    vllm_base_url: str = "http://localhost:8000/v1"
    vllm_model: str = "qwen2.5:14b-instruct"

    airgapped_mode: bool = False

    # Embedding & Reranker
    embedding_model: str = "BAAI/bge-m3"
    embedding_dimension: int = 1024
    reranker_model: str = "BAAI/bge-reranker-v2-m3"

    # File Storage
    storage_dir: str = "./data/uploads"
    regulations_dir: str = "./data/regulations"

    # Vision OCR Fallback
    enable_vision_ocr_fallback: bool = False


@lru_cache
def get_settings() -> Settings:
    return Settings()
