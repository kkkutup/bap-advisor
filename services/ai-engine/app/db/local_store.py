import json
import logging
from pathlib import Path
from typing import Any, Dict, List, Optional
from uuid import UUID
import numpy as np
from app.config import get_settings

logger = logging.getLogger(__name__)


def resolve_regulations_dir() -> Path:
    settings = get_settings()
    configured = Path(settings.regulations_dir)

    # Candidate locations to find the regulations directory containing files
    candidates: List[Path] = [
        configured,
        configured.resolve(),
        Path.cwd() / "data" / "regulations",
    ]
    for parent in Path(__file__).resolve().parents:
        candidates.append(parent / "data" / "regulations")
        if not configured.is_absolute():
            candidates.append(parent / configured)

    for cand in candidates:
        try:
            if cand.exists() and cand.is_dir() and (any(cand.glob("*.md")) or any(cand.glob("*.pdf"))):
                return cand.resolve()
        except Exception:
            continue

    # Fallback to configured directory
    configured.mkdir(parents=True, exist_ok=True)
    return configured.resolve()


class LocalRegulationStore:
    """
    Disk-backed persistent JSON and NumPy vector store for zero-container local mode and unit tests.
    Stores chunks in .local_rag_index.json and embeddings in .local_rag_index.npz.
    """

    def __init__(self, data_dir: Optional[Path] = None):
        self.data_dir = data_dir or resolve_regulations_dir()
        self.json_path = self.data_dir / ".local_rag_index.json"
        self.npz_path = self.data_dir / ".local_rag_index.npz"

        self.documents: Dict[str, Dict[str, Any]] = {}  # slug/id -> doc dict
        self.chunks: List[Dict[str, Any]] = []  # list of chunk dicts
        self.embeddings: np.ndarray = np.empty((0, 1024), dtype=np.float32)
        self.load()

    def load(self) -> None:
        if self.json_path.exists():
            try:
                with open(self.json_path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    self.documents = data.get("documents", {})
                    self.chunks = data.get("chunks", [])
            except Exception as e:
                logger.warning(f"Failed to load local RAG index JSON: {e}")
                self.documents = {}
                self.chunks = []

        if self.npz_path.exists() and len(self.chunks) > 0:
            try:
                npz = np.load(self.npz_path)
                loaded_emb = npz["embeddings"]
                if loaded_emb.shape[0] == len(self.chunks):
                    self.embeddings = loaded_emb.astype(np.float32)
                else:
                    logger.warning("Embeddings count mismatch with chunks; will re-align.")
                    self.embeddings = np.zeros((len(self.chunks), 1024), dtype=np.float32)
            except Exception as e:
                logger.warning(f"Failed to load local RAG index NPZ: {e}")
                self.embeddings = np.zeros((len(self.chunks), 1024), dtype=np.float32)
        else:
            self.embeddings = np.zeros((len(self.chunks), 1024), dtype=np.float32)

    def save(self) -> None:
        self.data_dir.mkdir(parents=True, exist_ok=True)
        # Clean chunks of any non-serializable objects
        serializable_chunks = []
        for ch in self.chunks:
            item = dict(ch)
            item.pop("embedding", None)  # stored in npz
            serializable_chunks.append(item)

        data = {
            "documents": self.documents,
            "chunks": serializable_chunks,
        }
        with open(self.json_path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)

        if self.embeddings is not None and len(self.embeddings) == len(self.chunks):
            np.savez_compressed(self.npz_path, embeddings=self.embeddings.astype(np.float32))

    def upsert_document(self, doc_data: Dict[str, Any]) -> None:
        doc_id = str(doc_data["id"])
        slug = doc_data["slug"]
        self.documents[slug] = doc_data
        self.documents[doc_id] = doc_data

    def get_document_by_slug(self, slug: str) -> Optional[Dict[str, Any]]:
        return self.documents.get(slug)

    def get_document_by_id(self, doc_id: Any) -> Optional[Dict[str, Any]]:
        return self.documents.get(str(doc_id))

    def delete_document_by_slug(self, slug: str) -> None:
        target_doc = self.documents.get(slug)
        if not target_doc:
            return
        doc_id = str(target_doc["id"])
        self.documents.pop(slug, None)
        self.documents.pop(doc_id, None)

        # Filter out chunks
        keep_indices = []
        new_chunks = []
        for idx, ch in enumerate(self.chunks):
            if str(ch.get("regulation_doc_id")) != doc_id:
                keep_indices.append(idx)
                new_chunks.append(ch)

        self.chunks = new_chunks
        if len(keep_indices) > 0 and len(self.embeddings) > 0:
            self.embeddings = self.embeddings[keep_indices]
        else:
            self.embeddings = np.empty((0, 1024), dtype=np.float32)
        self.save()

    def replace_document_chunks(
        self,
        doc_data: Dict[str, Any],
        chunks_data: List[Dict[str, Any]],
        embeddings: np.ndarray,
    ) -> None:
        """
        Atomically replace all chunks and embeddings for a given document.
        """
        slug = doc_data["slug"]
        self.delete_document_by_slug(slug)
        self.upsert_document(doc_data)

        if not chunks_data:
            self.save()
            return

        new_chunks = list(chunks_data)
        embeddings_arr = np.asarray(embeddings, dtype=np.float32)

        if len(self.chunks) == 0:
            self.chunks = new_chunks
            self.embeddings = embeddings_arr
        else:
            self.chunks.extend(new_chunks)
            self.embeddings = np.vstack([self.embeddings, embeddings_arr])

        self.save()

    def get_all_chunks(self) -> List[Dict[str, Any]]:
        result = []
        for idx, ch in enumerate(self.chunks):
            item = dict(ch)
            if idx < len(self.embeddings):
                item["embedding"] = self.embeddings[idx]
            else:
                item["embedding"] = np.zeros(1024, dtype=np.float32)
            result.append(item)
        return result

    def count_chunks(self) -> int:
        return len(self.chunks)

    def count_documents(self) -> int:
        # unique docs by id
        unique_ids = set()
        for doc in self.documents.values():
            unique_ids.add(str(doc["id"]))
        return len(unique_ids)
