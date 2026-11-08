from uuid import uuid4
from fastapi import APIRouter
from app.config import get_settings
from app.schemas import (
    EvaluateRequest,
    EvaluateResponse,
    QaRequest,
    QaResponse,
    CitationItem,
    OverallComplianceStatus
)

router = APIRouter(tags=["Evaluation & Q&A"])


@router.post("/evaluate", response_model=EvaluateResponse)
async def evaluate_submission(request: EvaluateRequest) -> EvaluateResponse:
    """
    Combine deterministic rule findings with RAG regulation analysis.
    Generate revision draft or commission referral summary.
    Fully implemented in Phase 1/Phase 2 by Agent 4.
    """
    settings = get_settings()
    active_model = settings.nanogpt_model if settings.llm_provider == "nanogpt" else settings.ollama_model

    return EvaluateResponse(
        evaluation_id=uuid4(),
        overall_compliance_status=OverallComplianceStatus.COMPLIANT,
        rag_findings=[],
        combined_findings=request.deterministic_findings,
        revision_draft_tr=None,
        commission_referral_summary_tr="Başvuru şekli mevzuat kriterlerini sağlamaktadır. BAP Komisyonuna sevk edilebilir.",
        llm_provider_used=settings.llm_provider,
        llm_model_name=active_model
    )


@router.post("/qa/ask", response_model=QaResponse)
async def ask_question(request: QaRequest) -> QaResponse:
    """
    Contextual Q&A grounded on regulation chunks and submission files.
    Fully implemented in Phase 2 by Agent 4.
    """
    settings = get_settings()
    active_model = settings.nanogpt_model if settings.llm_provider == "nanogpt" else settings.ollama_model

    return QaResponse(
        answer_tr="Mersin Üniversitesi BAP Uygulama Usul ve Esasları uyarınca talebiniz incelenmiştir.",
        citations=[
            CitationItem(
                regulation_doc_name="Mersin Üniversitesi BAP Uygulama Usul ve Esasları",
                regulation_article_ref="MADDE 4/1-b",
                verbatim_quote="TP2 (Yüksek Lisans Tez Projeleri): 2026 yılı üst limiti 125.000,00 TL'dir.",
                score=0.92
            )
        ],
        llm_provider_used=settings.llm_provider,
        llm_model_name=active_model
    )
