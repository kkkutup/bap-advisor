from datetime import datetime, timezone
from uuid import uuid4
from sqlalchemy import (
    Boolean,
    Column,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    String,
    Text,
)
from sqlalchemy.dialects.postgresql import JSONB, TSVECTOR, UUID
from sqlalchemy.orm import declarative_base, relationship
from pgvector.sqlalchemy import Vector

Base = declarative_base()


class RegulationDocumentModel(Base):
    __tablename__ = "regulation_documents"
    __table_args__ = (
        Index("ix_regulation_docs_slug", "slug", unique=True),
        Index("ix_regulation_docs_active", "is_active"),
        {"schema": "ai_rag"},
    )

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid4)
    slug = Column(String(255), nullable=False, unique=True)
    title_tr = Column(String(500), nullable=False)
    doc_type = Column(String(50), nullable=False)
    version_label = Column(String(100), nullable=True)
    source_url = Column(String(1000), nullable=True)
    storage_path = Column(String(1000), nullable=False)
    sha256_hash = Column(String(64), nullable=True)
    is_active = Column(Boolean, default=True, nullable=False)
    ingested_at = Column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),
        nullable=False,
    )

    chunks = relationship(
        "RegulationChunkModel",
        back_populates="document",
        cascade="all, delete-orphan",
        order_by="RegulationChunkModel.chunk_index",
    )


class RegulationChunkModel(Base):
    __tablename__ = "regulation_chunks"
    __table_args__ = (
        Index(
            "ix_regulation_chunks_embedding_cosine",
            "embedding",
            postgresql_using="hnsw",
            postgresql_ops={"embedding": "vector_cosine_ops"},
        ),
        Index(
            "ix_regulation_chunks_tsv",
            "content_tsv",
            postgresql_using="gin",
        ),
        Index("ix_regulation_chunks_article_number", "article_number"),
        Index("ix_regulation_chunks_doc_id", "regulation_doc_id"),
        {"schema": "ai_rag"},
    )

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid4)
    regulation_doc_id = Column(
        UUID(as_uuid=True),
        ForeignKey("ai_rag.regulation_documents.id", ondelete="CASCADE"),
        nullable=False,
    )
    chunk_index = Column(Integer, nullable=False)
    section_title = Column(String(500), nullable=True)
    article_number = Column(String(100), nullable=True)
    sub_clause = Column(String(100), nullable=True)
    content_text = Column(Text, nullable=False)
    content_tsv = Column(TSVECTOR, nullable=True)
    embedding = Column(Vector(1024), nullable=True)
    metadata_json = Column(JSONB, default=dict, nullable=False)

    document = relationship("RegulationDocumentModel", back_populates="chunks")
