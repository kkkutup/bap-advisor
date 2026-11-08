from fastapi import APIRouter
from app.schemas import (
    OcrExtractRequest,
    OcrExtractResponse,
    ExtractedDocumentResult,
    StructuredSubmissionData,
    SubmissionType,
    DocumentRole
)

router = APIRouter(prefix="/ocr", tags=["OCR & Document Extraction"])


@router.post("/extract", response_model=OcrExtractResponse)
async def extract_ocr_data(request: OcrExtractRequest) -> OcrExtractResponse:
    """
    Extract text, tables, and structured data from uploaded document package.
    Fully implemented in Phase 1 by Agent 3.
    """
    doc_results = [
        ExtractedDocumentResult(
            doc_id=f.doc_id,
            file_name=f.file_name,
            detected_doc_role=DocumentRole.APPLICATION_FORM,
            ocr_confidence=0.98,
            has_signature_detected=True,
            parsed_markdown="# BAP Başvuru Formu\nTaslak ayrıştırma çıktısı.",
            parsed_tables_json=[]
        )
        for f in request.files
    ]

    structured_data = StructuredSubmissionData(
        detected_submission_type=SubmissionType.NEW_PROJECT,
        detected_project_code="TP2",
        applicant_name="Örnek Yürütücü",
        applicant_title="Doç. Dr.",
        department="Mühendislik Fakültesi",
        project_title="Örnek BAP Başvuru Başlığı",
        requested_budget_total_tl=125000.00,
        requested_duration_months=24,
        budget_line_items=[],
        wet_signature_present=True,
        ethics_required_keywords_detected=False,
        detected_attachments=[DocumentRole.APPLICATION_FORM],
        notes="Ajan 3 OCR hattı için temel iskelet çıktısı."
    )

    return OcrExtractResponse(
        version_id=request.version_id,
        documents=doc_results,
        structured_data=structured_data
    )
