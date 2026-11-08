import logging
from typing import Any, Dict, List, Optional
from uuid import UUID
import numpy as np
from sqlalchemy import delete, select, text
from app.db.local_store import LocalRegulationStore
from app.db.models import RegulationChunkModel, RegulationDocumentModel
from app.db.session import (
    get_db_session,
    init_postgres_schema_if_needed,
    is_postgres_available,
)

logger = logging.getLogger(__name__)


class DualModeRegulationStore:
    """
    Seamless Dual Persistence Store for BAP Regulations.
    Automatically uses PostgreSQL + pgvector when available;
    otherwise falls back to LocalRegulationStore (.json + .npz).
    """

    def __init__(self, local_store: Optional[LocalRegulationStore] = None):
        self.local_store = local_store or LocalRegulationStore()
        self._checked_init = False

    def is_using_postgres(self) -> bool:
        if not self._checked_init:
            init_postgres_schema_if_needed()
            self._checked_init = True
        return is_postgres_available()

    def replace_document_chunks(
        self,
        doc_data: Dict[str, Any],
        chunks_data: List[Dict[str, Any]],
        embeddings: np.ndarray,
    ) -> None:
        """
        Store document and chunks into the active store.
        Always also mirrors to local_store as backup so zero-container transitions are instant.
        """
        # Always mirror to local store
        self.local_store.replace_document_chunks(doc_data, chunks_data, embeddings)

        if not self.is_using_postgres():
            logger.info(f"Persisted doc '{doc_data['slug']}' with {len(chunks_data)} chunks to LocalStore.")
            return

        # Persist to PostgreSQL ai_rag schema
        with get_db_session() as session:
            if session is None:
                return

            slug = doc_data["slug"]
            # Remove previous if exists
            existing_doc = session.execute(
                select(RegulationDocumentModel).where(RegulationDocumentModel.slug == slug)
            ).scalar_one_or_none()

            if existing_doc:
                session.delete(existing_doc)
                session.flush()

            # Insert document
            doc_model = RegulationDocumentModel(
                id=doc_data["id"] if isinstance(doc_data["id"], UUID) else UUID(str(doc_data["id"])),
                slug=slug,
                title_tr=doc_data["title_tr"],
                doc_type=doc_data["doc_type"],
                version_label=doc_data.get("version_label"),
                source_url=doc_data.get("source_url"),
                storage_path=doc_data["storage_path"],
                sha256_hash=doc_data.get("sha256_hash"),
                is_active=doc_data.get("is_active", True),
            )
            session.add(doc_model)
            session.flush()

            # Insert chunks
            for idx, ch in enumerate(chunks_data):
                emb_list = (
                    embeddings[idx].tolist()
                    if embeddings is not None and idx < len(embeddings)
                    else None
                )
                chunk_id = (
                    ch["id"] if isinstance(ch["id"], UUID) else UUID(str(ch["id"]))
                )
                chunk_model = RegulationChunkModel(
                    id=chunk_id,
                    regulation_doc_id=doc_model.id,
                    chunk_index=ch["chunk_index"],
                    section_title=ch.get("section_title"),
                    article_number=ch.get("article_number"),
                    sub_clause=ch.get("sub_clause"),
                    content_text=ch["content_text"],
                    embedding=emb_list,
                    metadata_json=ch.get("metadata_json", {}),
                )
                session.add(chunk_model)

            session.commit()
            # Update content_tsv via postgres function
            try:
                session.execute(
                    text(
                        "UPDATE ai_rag.regulation_chunks SET content_tsv = to_tsvector('turkish', content_text) "
                        "WHERE regulation_doc_id = :doc_id"
                    ),
                    {"doc_id": doc_model.id},
                )
                session.commit()
            except Exception as e:
                logger.warning(f"Could not compute turkish tsvector in PostgreSQL: {e}")

            logger.info(f"Persisted doc '{slug}' with {len(chunks_data)} chunks to PostgreSQL ai_rag.")

    def get_document_by_slug(self, slug: str) -> Optional[Dict[str, Any]]:
        if self.is_using_postgres():
            with get_db_session() as session:
                if session is not None:
                    doc = session.execute(
                        select(RegulationDocumentModel).where(
                            RegulationDocumentModel.slug == slug
                        )
                    ).scalar_one_or_none()
                    if doc:
                        return {
                            "id": doc.id,
                            "slug": doc.slug,
                            "title_tr": doc.title_tr,
                            "doc_type": doc.doc_type,
                            "version_label": doc.version_label,
                            "source_url": doc.source_url,
                            "storage_path": doc.storage_path,
                            "sha256_hash": doc.sha256_hash,
                            "is_active": doc.is_active,
                            "ingested_at": doc.ingested_at,
                        }
        return self.local_store.get_document_by_slug(slug)

    def get_document_by_id(self, doc_id: Any) -> Optional[Dict[str, Any]]:
        if self.is_using_postgres():
            with get_db_session() as session:
                if session is not None:
                    uid = doc_id if isinstance(doc_id, UUID) else UUID(str(doc_id))
                    doc = session.get(RegulationDocumentModel, uid)
                    if doc:
                        return {
                            "id": doc.id,
                            "slug": doc.slug,
                            "title_tr": doc.title_tr,
                            "doc_type": doc.doc_type,
                            "version_label": doc.version_label,
                            "source_url": doc.source_url,
                            "storage_path": doc.storage_path,
                            "sha256_hash": doc.sha256_hash,
                            "is_active": doc.is_active,
                            "ingested_at": doc.ingested_at,
                        }
        return self.local_store.get_document_by_id(doc_id)

    def delete_document_by_slug(self, slug: str) -> None:
        self.local_store.delete_document_by_slug(slug)
        if self.is_using_postgres():
            with get_db_session() as session:
                if session is not None:
                    session.execute(
                        delete(RegulationDocumentModel).where(
                            RegulationDocumentModel.slug == slug
                        )
                    )
                    session.commit()

    def get_all_chunks(self) -> List[Dict[str, Any]]:
        """
        Returns all chunks with their embeddings for hybrid search.
        Falls back to local_store if postgres is unavailable or empty.
        """
        if self.is_using_postgres():
            with get_db_session() as session:
                if session is not None:
                    try:
                        chunks = session.execute(
                            select(RegulationChunkModel).order_by(
                                RegulationChunkModel.regulation_doc_id,
                                RegulationChunkModel.chunk_index,
                            )
                        ).scalars().all()
                        if chunks:
                            result = []
                            for ch in chunks:
                                emb = (
                                    np.array(ch.embedding, dtype=np.float32)
                                    if ch.embedding is not None
                                    else np.zeros(1024, dtype=np.float32)
                                )
                                result.append(
                                    {
                                        "id": str(ch.id),
                                        "regulation_doc_id": str(ch.regulation_doc_id),
                                        "chunk_index": ch.chunk_index,
                                        "section_title": ch.section_title,
                                        "article_number": ch.article_number,
                                        "sub_clause": ch.sub_clause,
                                        "content_text": ch.content_text,
                                        "metadata_json": ch.metadata_json or {},
                                        "embedding": emb,
                                    }
                                )
                            return result
                    except Exception as e:
                        logger.warning(f"Error fetching chunks from postgres: {e}; falling back to local store.")
        return self.local_store.get_all_chunks()

    def count_chunks(self) -> int:
        if self.is_using_postgres():
            with get_db_session() as session:
                if session is not None:
                    try:
                        res = session.execute(
                            text("SELECT count(*) FROM ai_rag.regulation_chunks")
                        ).scalar()
                        if res is not None and res > 0:
                            return res
                    except Exception:
                        pass
        return self.local_store.count_chunks()


_default_store: Optional[DualModeRegulationStore] = None


def get_regulation_store() -> DualModeRegulationStore:
    global _default_store
    if _default_store is None:
        _default_store = DualModeRegulationStore()
    return _default_store
