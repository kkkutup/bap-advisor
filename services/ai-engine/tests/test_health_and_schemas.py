import pytest
from uuid import uuid4
from fastapi.testclient import TestClient
from app.main import app
from app.config import get_settings
from app.schemas import (
    SubmissionType,
    DocumentRole,
    BudgetCategory,
    BudgetLineItem,
    StructuredSubmissionData,
    FindingSeverity,
    FindingSource,
    RuleCategory,
    FindingItem
)

client = TestClient(app)
VALID_TOKEN = get_settings().internal_service_token


def test_health_endpoint():
    response = client.get("/internal/v1/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "UP"
    assert data["airgapped_mode"] is False
    assert data["llm_provider"] == "nanogpt"
    assert "timestamp" in data


def test_unauthorized_access_rejected():
    # Calling OCR without header
    response = client.post(
        "/internal/v1/ocr/extract",
        json={"version_id": str(uuid4()), "files": []}
    )
    assert response.status_code in (401, 422)


def test_authorized_provider_status():
    response = client.get(
        "/internal/v1/providers/status",
        headers={"X-Internal-Service-Token": VALID_TOKEN}
    )
    assert response.status_code == 200
    data = response.json()
    assert data["active_provider"] == "nanogpt"
    assert data["embedding_dimension"] == 1024


def test_ocr_extract_stub():
    version_id = str(uuid4())
    doc_id = str(uuid4())
    payload = {
        "version_id": version_id,
        "files": [
            {
                "doc_id": doc_id,
                "storage_path": "/path/to/test.pdf",
                "mime_type": "application/pdf",
                "file_name": "test.pdf"
            }
        ],
        "enable_vision_fallback": False
    }
    response = client.post(
        "/internal/v1/ocr/extract",
        headers={"X-Internal-Service-Token": VALID_TOKEN},
        json=payload
    )
    assert response.status_code == 200
    data = response.json()
    assert data["version_id"] == version_id
    assert len(data["documents"]) == 1
    assert data["structured_data"]["detected_submission_type"] == "NEW_PROJECT"
    assert data["structured_data"]["detected_project_code"] == "TP2"


def test_schema_validations():
    item = BudgetLineItem(
        category=BudgetCategory.CONSUMABLE,
        name="Kimyasal Reaktif",
        quantity=5.0,
        unit="ŞİŞE",
        unit_price_tl=1000.0,
        total_tl=5000.0,
        vat_included=True
    )
    assert item.total_tl == 5000.0

    submission = StructuredSubmissionData(
        detected_submission_type=SubmissionType.NEW_PROJECT,
        detected_project_code="TP2",
        budget_line_items=[item],
        wet_signature_present=True,
        detected_attachments=[DocumentRole.APPLICATION_FORM]
    )
    assert submission.detected_submission_type == SubmissionType.NEW_PROJECT
    assert submission.wet_signature_present is True

    finding = FindingItem(
        source_type=FindingSource.DETERMINISTIC_RULE,
        rule_code="RULE_BUDGET_CAP",
        category=RuleCategory.BUDGET,
        severity=FindingSeverity.COMPLIANT,
        title_tr="Bütçe Tavan Kontrolü",
        explanation_tr="Talep edilen bütçe TP2 tavanı olan 125.000 TL sınırları içerisindedir.",
        regulation_doc_name="Mersin Üniversitesi BAP Uygulama Usul ve Esasları",
        regulation_article_ref="MADDE 4/1-b"
    )
    assert finding.severity == FindingSeverity.COMPLIANT
