import json
import logging
import os
from pathlib import Path
from typing import Any, Dict, List, Literal, Optional, Tuple
import httpx
from openai import AsyncOpenAI
from app.config import get_settings
from app.llm.airgap_guard import assert_airgap_compliance
from app.schemas import ProviderConfigRequest, ProviderStatusResponse

logger = logging.getLogger(__name__)


class LLMGateway:
    """
    Dual-Mode OpenAI-Compatible LLM Gateway for BAP Advisor.
    Supports seamless runtime switching between NanoGPT cloud testing
    and 100% local Ollama / vLLM production with hardware-level airgap lock.
    """

    def __init__(self):
        settings = get_settings()
        self.provider: Literal["nanogpt", "ollama", "vllm"] = settings.llm_provider
        self.airgapped_mode: bool = settings.airgapped_mode

        self.nanogpt_base_url: str = settings.nanogpt_base_url
        self.nanogpt_api_key: str = (
            os.environ.get("NANOGPT_API_KEY") or settings.nanogpt_api_key or ""
        )
        self.nanogpt_model: str = settings.nanogpt_model

        self.ollama_base_url: str = settings.ollama_base_url
        self.ollama_model: str = settings.ollama_model

        self.vllm_base_url: str = settings.vllm_base_url
        self.vllm_model: str = settings.vllm_model

        self._config_file = Path(settings.storage_dir) / ".provider_config.json"
        self._load_persisted_config()

    def _load_persisted_config(self) -> None:
        try:
            if self._config_file.exists():
                data = json.loads(self._config_file.read_text(encoding="utf-8"))
                if data.get("provider") in ("nanogpt", "ollama", "vllm"):
                    self.provider = data["provider"]
                if "airgapped_mode" in data and isinstance(data["airgapped_mode"], bool):
                    self.airgapped_mode = data["airgapped_mode"]
                if data.get("nanogpt_model"):
                    self.nanogpt_model = data["nanogpt_model"]
                if data.get("nanogpt_api_key"):
                    self.nanogpt_api_key = data["nanogpt_api_key"]
                if data.get("ollama_base_url"):
                    self.ollama_base_url = data["ollama_base_url"]
                if data.get("ollama_model"):
                    self.ollama_model = data["ollama_model"]
                if data.get("vllm_base_url"):
                    self.vllm_base_url = data["vllm_base_url"]
                if data.get("vllm_model"):
                    self.vllm_model = data["vllm_model"]
        except Exception as e:
            logger.warning(f"Could not load persisted provider config: {e}")

    def _save_persisted_config(self) -> None:
        try:
            self._config_file.parent.mkdir(parents=True, exist_ok=True)
            data = {
                "provider": self.provider,
                "airgapped_mode": self.airgapped_mode,
                "nanogpt_model": self.nanogpt_model,
                "nanogpt_api_key": self.nanogpt_api_key,
                "ollama_base_url": self.ollama_base_url,
                "ollama_model": self.ollama_model,
                "vllm_base_url": self.vllm_base_url,
                "vllm_model": self.vllm_model,
            }
            self._config_file.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        except Exception as e:
            logger.warning(f"Could not save persisted provider config: {e}")

    def get_status(self) -> ProviderStatusResponse:
        settings = get_settings()
        active_model = self.get_active_model()
        return ProviderStatusResponse(
            active_provider=self.provider,
            active_model=active_model,
            available_providers=["nanogpt", "ollama", "vllm"],
            airgapped_mode=self.airgapped_mode,
            embedding_model=settings.embedding_model,
            embedding_dimension=settings.embedding_dimension,
            reranker_model=settings.reranker_model,
        )

    def update_config(self, req: ProviderConfigRequest) -> ProviderStatusResponse:
        """
        Updates runtime provider settings dynamically.
        Enforces that if airgapped_mode is active, public providers cannot be configured.
        """
        target_airgapped = (
            req.airgapped_mode if req.airgapped_mode is not None else self.airgapped_mode
        )

        if req.llm_provider:
            # Check airgap compatibility
            if target_airgapped and req.llm_provider == "nanogpt":
                assert_airgap_compliance(self.nanogpt_base_url, target_airgapped)
            self.provider = req.llm_provider

        if req.nanogpt_api_key is not None:
            self.nanogpt_api_key = req.nanogpt_api_key
        if req.nanogpt_model:
            self.nanogpt_model = req.nanogpt_model

        if req.ollama_base_url:
            self.ollama_base_url = req.ollama_base_url
        if req.ollama_model:
            self.ollama_model = req.ollama_model

        if req.vllm_base_url:
            self.vllm_base_url = req.vllm_base_url
        if req.vllm_model:
            self.vllm_model = req.vllm_model

        if req.airgapped_mode is not None:
            self.airgapped_mode = req.airgapped_mode
            # If airgap was turned ON, verify active provider
            if self.airgapped_mode and self.provider == "nanogpt":
                # Automatically fail or warn
                assert_airgap_compliance(self.nanogpt_base_url, self.airgapped_mode)

        self._save_persisted_config()
        return self.get_status()

    def get_active_model(self) -> str:
        if self.provider == "nanogpt":
            return self.nanogpt_model
        elif self.provider == "ollama":
            return self.ollama_model
        elif self.provider == "vllm":
            return self.vllm_model
        return self.nanogpt_model

    def get_active_endpoint(self) -> Tuple[str, str, str]:
        """
        Returns (base_url, api_key, model) for the current active provider.
        """
        if self.provider == "nanogpt":
            return (self.nanogpt_base_url, self.nanogpt_api_key, self.nanogpt_model)
        elif self.provider == "ollama":
            return (self.ollama_base_url, "ollama", self.ollama_model)
        elif self.provider == "vllm":
            return (self.vllm_base_url, "vllm-internal-key", self.vllm_model)
        return (self.nanogpt_base_url, self.nanogpt_api_key, self.nanogpt_model)

    async def probe_provider_connection(self) -> Dict[str, Any]:
        """
        Probes the active provider endpoint to check live connectivity.
        """
        base_url, api_key, model = self.get_active_endpoint()
        assert_airgap_compliance(base_url, self.airgapped_mode)

        if self.provider == "nanogpt" and not api_key:
            return {"connected": False, "reason": "NANOGPT_API_KEY is not configured"}

        try:
            client = AsyncOpenAI(
                base_url=base_url,
                api_key=api_key or "local-token",
                timeout=3.0,
            )
            # Lightweight probe: models list
            await client.models.list()
            return {"connected": True, "provider": self.provider, "model": model}
        except Exception as e:
            return {"connected": False, "provider": self.provider, "reason": str(e)}

    async def chat_completion(
        self,
        messages: List[Dict[str, str]],
        temperature: float = 0.1,
        response_format: Optional[Dict[str, Any]] = None,
        timeout: float = 25.0,
        provider_override: Optional[str] = None,
        model_override: Optional[str] = None,
    ) -> Optional[str]:
        """
        Sends an OpenAI-compatible chat completion request.
        Validates airgap compliance before sending.
        Returns the assistant's text response, or None if unavailable/offline.
        """
        active_provider = provider_override or self.provider

        if active_provider == "nanogpt":
            base_url = self.nanogpt_base_url
            api_key = self.nanogpt_api_key
            model = model_override or self.nanogpt_model
        elif active_provider == "ollama":
            base_url = self.ollama_base_url
            api_key = "ollama"
            model = model_override or self.ollama_model
        elif active_provider == "vllm":
            base_url = self.vllm_base_url
            api_key = "vllm"
            model = model_override or self.vllm_model
        else:
            base_url = self.nanogpt_base_url
            api_key = self.nanogpt_api_key
            model = model_override or self.nanogpt_model

        # Security check: Airgap compliance
        assert_airgap_compliance(base_url, self.airgapped_mode)

        # If NanoGPT has no key, gracefully return None so offline synthesis is triggered
        if active_provider == "nanogpt" and not api_key:
            logger.info("NanoGPT API key not configured; skipping remote call for fallback synthesis.")
            return None

        try:
            client = AsyncOpenAI(
                base_url=base_url,
                api_key=api_key or "no-key-required",
                timeout=timeout,
            )
            kwargs: Dict[str, Any] = {
                "model": model,
                "messages": messages,
                "temperature": temperature,
            }
            if response_format:
                kwargs["response_format"] = response_format

            resp = await client.chat.completions.create(**kwargs)
            if resp.choices and resp.choices[0].message:
                return resp.choices[0].message.content or ""
            return None
        except Exception as exc:
            logger.warning(
                f"LLM gateway request to {active_provider} ({model} at {base_url}) failed: {exc}. "
                "Caller will utilize grounded template synthesis."
            )
            return None


_global_gateway: Optional[LLMGateway] = None


def get_llm_gateway() -> LLMGateway:
    global _global_gateway
    if _global_gateway is None:
        _global_gateway = LLMGateway()
    return _global_gateway
