"""initial ai_rag tables

Revision ID: 20260101_0001
Revises:
Create Date: 2026-01-01 00:00:00.000000

"""
from typing import Sequence, Union
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects.postgresql import JSONB, TSVECTOR, UUID
from pgvector.sqlalchemy import Vector

# revision identifiers, used by Alembic.
revision: str = "20260101_0001"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # Extensions and schema
    op.execute("CREATE SCHEMA IF NOT EXISTS ai_rag;")
    op.execute("CREATE EXTENSION IF NOT EXISTS vector;")

    # Table: regulation_documents
    op.create_table(
        "regulation_documents",
        sa.Column("id", UUID(as_uuid=True), primary_key=True),
        sa.Column("slug", sa.String(length=255), nullable=False),
        sa.Column("title_tr", sa.String(length=500), nullable=False),
        sa.Column("doc_type", sa.String(length=50), nullable=False),
        sa.Column("version_label", sa.String(length=100), nullable=True),
        sa.Column("source_url", sa.String(length=1000), nullable=True),
        sa.Column("storage_path", sa.String(length=1000), nullable=False),
        sa.Column("sha256_hash", sa.String(length=64), nullable=True),
        sa.Column("is_active", sa.Boolean(), server_default=sa.text("true"), nullable=False),
        sa.Column(
            "ingested_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
        schema="ai_rag",
    )
    op.create_index(
        "ix_regulation_docs_slug",
        "regulation_documents",
        ["slug"],
        unique=True,
        schema="ai_rag",
    )
    op.create_index(
        "ix_regulation_docs_active",
        "regulation_documents",
        ["is_active"],
        schema="ai_rag",
    )

    # Table: regulation_chunks
    op.create_table(
        "regulation_chunks",
        sa.Column("id", UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "regulation_doc_id",
            UUID(as_uuid=True),
            sa.ForeignKey("ai_rag.regulation_documents.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("chunk_index", sa.Integer(), nullable=False),
        sa.Column("section_title", sa.String(length=500), nullable=True),
        sa.Column("article_number", sa.String(length=100), nullable=True),
        sa.Column("sub_clause", sa.String(length=100), nullable=True),
        sa.Column("content_text", sa.Text(), nullable=False),
        sa.Column("content_tsv", TSVECTOR(), nullable=True),
        sa.Column("embedding", Vector(1024), nullable=True),
        sa.Column("metadata_json", JSONB(), server_default=sa.text("'{}'::jsonb"), nullable=False),
        schema="ai_rag",
    )
    op.create_index(
        "ix_regulation_chunks_doc_id",
        "regulation_chunks",
        ["regulation_doc_id"],
        schema="ai_rag",
    )
    op.create_index(
        "ix_regulation_chunks_article_number",
        "regulation_chunks",
        ["article_number"],
        schema="ai_rag",
    )
    op.create_index(
        "ix_regulation_chunks_tsv",
        "regulation_chunks",
        ["content_tsv"],
        postgresql_using="gin",
        schema="ai_rag",
    )
    op.execute(
        "CREATE INDEX IF NOT EXISTS ix_regulation_chunks_embedding_cosine "
        "ON ai_rag.regulation_chunks USING hnsw (embedding vector_cosine_ops);"
    )


def downgrade() -> None:
    op.drop_table("regulation_chunks", schema="ai_rag")
    op.drop_table("regulation_documents", schema="ai_rag")
