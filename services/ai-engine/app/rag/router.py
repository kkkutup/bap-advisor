import logging
from uuid import UUID
from fastapi import APIRouter, HTTPException, status
from app.rag.retriever import get_retriever
from app.schemas import (
    RegulationIngestRequest,
    RegulationIngestResponse,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/regulations", tags=["Regulation Ingestion & RAG"])


@router.post("/ingest", response_model=RegulationIngestResponse)
async def ingest_regulation(request: RegulationIngestRequest) -> RegulationIngestResponse:
    """
    Ingest, chunk, and index regulation PDF/Markdown into dual-mode store (pgvector / local store)
    with BAAI/bge-m3 embeddings and Turkish legal MADDE-aware chunking.
    """
    retriever = get_retriever()
    try:
        slug = request.replace_previous_slug or request.title_tr.lower().replace(" ", "-")
        result = retriever.ingest_file(
            file_path=request.storage_path,
            title_tr=request.title_tr,
            doc_type=request.doc_type,
            slug=slug,
            doc_id=str(request.regulation_doc_id),
            version_label=request.version_label,
        )

        return RegulationIngestResponse(
            regulation_doc_id=request.regulation_doc_id,
            slug=result["slug"],
            total_chunks_created=result["total_chunks_created"],
            articles_indexed=result["articles_indexed"],
            status="INDEXED",
        )
    except FileNotFoundError as fnf:
        logger.error(f"File not found during regulation ingest: {fnf}")
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Regulation document file not found: {request.storage_path}",
        )
    except Exception as e:
        logger.exception(f"Unexpected error during regulation ingestion: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to ingest regulation document: {str(e)}",
        )
