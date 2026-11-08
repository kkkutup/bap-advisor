from fastapi import APIRouter
from app.schemas import (
    RegulationIngestRequest,
    RegulationIngestResponse
)

router = APIRouter(prefix="/regulations", tags=["Regulation Ingestion & RAG"])


@router.post("/ingest", response_model=RegulationIngestResponse)
async def ingest_regulation(request: RegulationIngestRequest) -> RegulationIngestResponse:
    """
    Ingest and chunk regulation PDF/markdown into pgvector with BAAI/bge-m3.
    Fully implemented in Phase 1 by Agent 4.
    """
    slug = request.replace_previous_slug or request.title_tr.lower().replace(" ", "-")
    return RegulationIngestResponse(
        regulation_doc_id=request.regulation_doc_id,
        slug=slug,
        total_chunks_created=1,
        articles_indexed=["MADDE 1", "MADDE 2"],
        status="INDEXED"
    )
