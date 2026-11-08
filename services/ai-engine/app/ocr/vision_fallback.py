import base64
import io
import logging
from typing import Optional, Tuple
import httpx
from PIL import Image
from app.config import get_settings

logger = logging.getLogger(__name__)


class VisionFallback:
    """
    Tier-3 Optional Vision-LLM Fallback for low-confidence or heavily damaged pages.
    Calls OpenAI-compatible vision endpoint (NanoGPT or Ollama) with base64 PNG image.
    Safely degrades to Tier-2 output if offline, airgapped, or endpoint fails.
    """

    @classmethod
    async def try_vision_fallback(
        cls,
        image: Image.Image,
        tier2_markdown: str,
        tier2_confidence: float
    ) -> Tuple[str, float]:
        """
        Attempts vision LLM recovery on low-confidence image.
        Returns: (recovered_markdown, new_confidence)
        """
        settings = get_settings()

        # Airgap guardrail: never make outbound network calls if airgapped
        if settings.airgapped_mode:
            logger.info("Vision fallback skipped: AIRGAPPED_MODE is enabled.")
            return tier2_markdown, tier2_confidence

        # Determine target endpoint & model
        provider = settings.llm_provider
        base_url = settings.nanogpt_base_url if provider == "nanogpt" else settings.ollama_base_url
        api_key = settings.nanogpt_api_key if provider == "nanogpt" else "ollama"
        model = settings.nanogpt_model if provider == "nanogpt" else settings.ollama_model

        if provider == "nanogpt" and not api_key:
            logger.info("Vision fallback skipped: NanoGPT API key is not configured.")
            return tier2_markdown, tier2_confidence

        # Convert image to base64 PNG
        buffered = io.BytesIO()
        image.save(buffered, format="PNG")
        img_b64 = base64.b64encode(buffered.getvalue()).decode("utf-8")
        data_uri = f"data:image/png;base64,{img_b64}"

        headers = {
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json"
        }

        payload = {
            "model": model,
            "messages": [
                {
                    "role": "system",
                    "content": (
                        "Sen Mersin Üniversitesi BAP resmi belge ve form OCR uzmanısın. "
                        "Verilen başvuru belgesi görselindeki metin ve tabloları birebir, "
                        "tam ve hatasız Markdown formatına dönüştür. Yorum yapma, sadece "
                        "belgedeki içeriği aktar."
                    )
                },
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": "Bu belgedeki metin ve bütçe tablolarını Markdown olarak çıkar:"},
                        {"type": "image_url", "image_url": {"url": data_uri}}
                    ]
                }
            ],
            "max_tokens": 2048,
            "temperature": 0.0
        }

        endpoint_url = f"{base_url.rstrip('/')}/chat/completions"

        try:
            async with httpx.AsyncClient(timeout=30.0) as client:
                resp = await client.post(endpoint_url, json=payload, headers=headers)
                if resp.status_code == 200:
                    data = resp.json()
                    choices = data.get("choices", [])
                    if choices:
                        vlm_text = choices[0].get("message", {}).get("content", "").strip()
                        if vlm_text:
                            logger.info("Vision fallback successful: recovered clean markdown.")
                            return vlm_text, 0.95
        except Exception as e:
            logger.warning(f"Vision fallback failed gracefully: {e}. Falling back to Tier-2 OCR.")

        return tier2_markdown, tier2_confidence
