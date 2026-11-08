import logging
from fastapi import APIRouter, HTTPException, status
from app.llm.airgap_guard import AirgapSecurityViolationError
from app.llm.gateway import get_llm_gateway
from app.schemas import (
    ProviderConfigRequest,
    ProviderStatusResponse,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/providers", tags=["LLM Provider Management"])


@router.get("/status", response_model=ProviderStatusResponse)
async def get_provider_status() -> ProviderStatusResponse:
    """
    Get active LLM provider, current model, airgap lock status, and embedding settings.
    """
    gateway = get_llm_gateway()
    return gateway.get_status()


@router.put("/config", response_model=ProviderStatusResponse)
async def update_provider_config(config: ProviderConfigRequest) -> ProviderStatusResponse:
    """
    Update LLM provider, switch between NanoGPT cloud and local Ollama/vLLM,
    or toggle hardware-level airgap lock.
    """
    gateway = get_llm_gateway()
    try:
        updated_status = gateway.update_config(config)
        return updated_status
    except AirgapSecurityViolationError as sec_err:
        logger.warning(f"Airgap lock rejected provider config update: {sec_err}")
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(sec_err),
        )
    except Exception as e:
        logger.exception(f"Error updating provider config: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to update provider configuration: {str(e)}",
        )
