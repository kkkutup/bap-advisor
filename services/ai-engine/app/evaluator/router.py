import logging
from fastapi import APIRouter, HTTPException, status
from app.evaluator.service import get_evaluation_service
from app.schemas import (
    EvaluateRequest,
    EvaluateResponse,
    QaRequest,
    QaResponse,
)

logger = logging.getLogger(__name__)

router = APIRouter(tags=["Evaluation & Q&A"])


@router.post("/evaluate", response_model=EvaluateResponse)
async def evaluate_submission(request: EvaluateRequest) -> EvaluateResponse:
    """
    Combine deterministic rule findings with RAG regulation analysis.
    Generate formal revision return draft or commission referral summary
    with strict adherence to domain guardrail (no scientific merit judgments).
    """
    service = get_evaluation_service()
    try:
        return await service.evaluate_submission(request)
    except Exception as e:
        logger.exception(f"Error during submission evaluation: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Evaluation execution failed: {str(e)}",
        )


@router.post("/qa/ask", response_model=QaResponse)
async def ask_question(request: QaRequest) -> QaResponse:
    """
    Contextual Q&A grounded strictly on retrieved regulation chunks and submission files.
    """
    service = get_evaluation_service()
    try:
        return await service.ask_question(request)
    except Exception as e:
        logger.exception(f"Error during Q&A processing: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Q&A execution failed: {str(e)}",
        )
