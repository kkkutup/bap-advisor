from fastapi import APIRouter
from app.config import get_settings
from app.schemas import (
    ProviderStatusResponse,
    ProviderConfigRequest
)

router = APIRouter(prefix="/providers", tags=["LLM Provider Management"])


@router.get("/status", response_model=ProviderStatusResponse)
async def get_provider_status() -> ProviderStatusResponse:
    """
    Get active LLM provider, current model, and airgap status.
    """
    settings = get_settings()
    active_model = settings.nanogpt_model if settings.llm_provider == "nanogpt" else settings.ollama_model
    return ProviderStatusResponse(
        active_provider=settings.llm_provider,
        active_model=active_model,
        available_providers=["nanogpt", "ollama", "vllm"],
        airgapped_mode=settings.airgapped_mode,
        embedding_model=settings.embedding_model,
        embedding_dimension=settings.embedding_dimension,
        reranker_model=settings.reranker_model
    )


@router.put("/config", response_model=ProviderStatusResponse)
async def update_provider_config(config: ProviderConfigRequest) -> ProviderStatusResponse:
    """
    Update LLM provider and runtime settings.
    """
    settings = get_settings()
    settings.llm_provider = config.llm_provider
    if config.nanogpt_model:
        settings.nanogpt_model = config.nanogpt_model
    if config.ollama_model:
        settings.ollama_model = config.ollama_model
    if config.vllm_model:
        settings.vllm_model = config.vllm_model
    if config.airgapped_mode is not None:
        settings.airgapped_mode = config.airgapped_mode

    return await get_provider_status()
