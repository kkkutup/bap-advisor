import hashlib
import logging
import math
import os
import re
from typing import List, Optional, Union
import numpy as np
from app.config import get_settings

logger = logging.getLogger(__name__)


def turkish_lower(text: str) -> str:
    """
    Handles Turkish-specific character lowercasing (İ -> i, I -> ı).
    """
    translation = str.maketrans({
        "İ": "i",
        "I": "ı",
        "Ğ": "ğ",
        "Ü": "ü",
        "Ş": "ş",
        "Ö": "ö",
        "Ç": "ç",
    })
    return text.translate(translation).lower()


class LocalEmbedder:
    """
    Local Embedding Service for Turkish BAP Regulations.
    Supports:
    1. BAAI/bge-m3 via SentenceTransformer (with auto device selection: mps/cuda/cpu).
    2. Fast deterministic hash/TF-IDF embedder when BAP_FAST_EMBEDDINGS=true,
       or in AIRGAPPED_MODE when HuggingFace weights are not yet cached.
    """

    def __init__(self, dimension: int = 1024):
        self.dimension = dimension
        self._st_model = None
        self._st_init_attempted = False
        self._use_fast_mode = (
            os.environ.get("BAP_FAST_EMBEDDINGS", "").lower() in ("true", "1", "yes")
        )

    @property
    def is_fast_mode(self) -> bool:
        return self._use_fast_mode

    def _init_sentence_transformer(self) -> None:
        if self._st_init_attempted:
            return
        self._st_init_attempted = True

        if self._use_fast_mode:
            logger.info("BAP_FAST_EMBEDDINGS=true; using deterministic fast embedder.")
            return

        settings = get_settings()
        if settings.airgapped_mode:
            logger.info("AIRGAPPED_MODE=true; attempting only cached local weights.")

        try:
            import torch
            from sentence_transformers import SentenceTransformer

            device = "cpu"
            if torch.backends.mps.is_available():
                device = "mps"
            elif torch.cuda.is_available():
                device = "cuda"

            # First attempt: load only from local cache to avoid hanging on 2.2GB downloads during HTTP requests
            try:
                self._st_model = SentenceTransformer(
                    settings.embedding_model,
                    device=device,
                    local_files_only=True,
                )
                logger.info(f"Loaded cached {settings.embedding_model} on {device}.")
                return
            except Exception:
                if os.environ.get("BAP_DOWNLOAD_HF_WEIGHTS", "").lower() not in ("true", "1", "yes"):
                    logger.info("Local HF weights not cached; using fast deterministic embedder to prevent HTTP timeout.")
                    self._use_fast_mode = True
                    return

            # If not in test/airgap mode, attempt standard load
            self._st_model = SentenceTransformer(
                settings.embedding_model,
                device=device,
            )
            logger.info("SentenceTransformer loaded successfully.")
        except Exception as e:
            logger.warning(
                f"SentenceTransformer ({settings.embedding_model}) unavailable: {e}. "
                "Falling back to high-fidelity deterministic embedder."
            )
            self._st_model = None
            self._use_fast_mode = True

    def embed_texts(self, texts: List[str]) -> np.ndarray:
        """
        Embeds a list of texts and returns a 2D float32 array of shape (len(texts), 1024).
        Vectors are L2-normalized.
        """
        if not texts:
            return np.empty((0, self.dimension), dtype=np.float32)

        self._init_sentence_transformer()

        if self._st_model is not None and not self._use_fast_mode:
            try:
                embeddings = self._st_model.encode(
                    texts,
                    normalize_embeddings=True,
                    batch_size=16,
                    show_progress_bar=False,
                )
                return np.asarray(embeddings, dtype=np.float32)
            except Exception as e:
                logger.warning(f"SentenceTransformer encoding error: {e}; falling back to fast embedder.")

        # Deterministic Fast Mode
        embeddings = np.zeros((len(texts), self.dimension), dtype=np.float32)
        for i, text in enumerate(texts):
            embeddings[i] = self._deterministic_embed(text)
        return embeddings

    def embed_query(self, query: str) -> np.ndarray:
        """
        Embeds a single query string and returns a 1D float32 array of shape (1024,).
        """
        arr = self.embed_texts([query])
        return arr[0]

    def _deterministic_embed(self, text: str) -> np.ndarray:
        """
        High-fidelity deterministic Turkish Legal hashing & subword embedder.
        Maps Turkish vocabulary, regulation articles, project codes, and n-grams
        into a 1024-dimensional normalized vector space.
        """
        vec = np.zeros(self.dimension, dtype=np.float32)
        norm_text = turkish_lower(text)
        tokens = re.findall(r'[a-zçğıöşü0-9]+', norm_text)

        if not tokens:
            vec[0] = 1.0
            return vec

        # Weighting
        for token in tokens:
            weight = 1.0
            # Boost legal and numerical terms
            if token.startswith("madde") or token in ("ek", "gecici"):
                weight = 3.0
            elif re.match(r'^(tp|ap)[1-9]$', token) or token in ("q1", "q2", "wos"):
                weight = 3.5
            elif re.match(r'^\d+$', token):
                weight = 1.8
            elif len(token) > 5:
                weight = 1.2

            # Hash token to dimension indices
            h = int(hashlib.md5(token.encode("utf-8")).hexdigest(), 16)
            idx1 = h % self.dimension
            sign1 = 1.0 if ((h >> 16) & 1) else -1.0
            vec[idx1] += sign1 * weight

            # Secondary index for token pair collision spread
            idx2 = (h >> 8) % self.dimension
            sign2 = -1.0 if ((h >> 24) & 1) else 1.0
            vec[idx2] += sign2 * (weight * 0.5)

            # Character 3-grams for morphological resilience in Turkish
            if len(token) >= 3:
                for j in range(len(token) - 2):
                    ngram = token[j:j + 3]
                    nh = int(hashlib.sha256(ngram.encode("utf-8")).hexdigest(), 16)
                    n_idx = nh % self.dimension
                    n_sign = 1.0 if ((nh >> 16) & 1) else -1.0
                    vec[n_idx] += n_sign * 0.3

        # L2 Normalize
        norm = np.linalg.norm(vec)
        if norm > 1e-8:
            vec = vec / norm
        else:
            vec[0] = 1.0

        return vec.astype(np.float32)


_global_embedder: Optional[LocalEmbedder] = None


def get_embedder() -> LocalEmbedder:
    global _global_embedder
    if _global_embedder is None:
        settings = get_settings()
        _global_embedder = LocalEmbedder(dimension=settings.embedding_dimension)
    return _global_embedder
