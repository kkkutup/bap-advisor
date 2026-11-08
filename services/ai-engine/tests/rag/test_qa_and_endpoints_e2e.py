from uuid import uuid4
import pytest
from fastapi.testclient import TestClient
from app.config import get_settings
from app.db.local_store import resolve_regulations_dir
from app.main import app
from app.schemas import (
    BudgetCategory,
    BudgetLineItem,
    DocumentRole,
    FindingItem,
    FindingSeverity,
    FindingSource,
    OverallComplianceStatus,
    RuleCategory,
    StructuredSubmissionData,
    SubmissionType,
)

client = TestClient(app)
VALID_TOKEN = get_settings().internal_service_token


def test_e2e_unauthorized_token_rejection():
    # Calling evaluate without token header must return 401 or 422
    resp = client.post("/internal/v1/evaluate", json={})
    assert resp.status_code in (401, 422)

    # Calling with invalid token must return 401
    resp = client.post(
        "/internal/v1/evaluate",
        headers={"X-Internal-Service-Token": "invalid_wrong_token"},
        json={},
    )
    assert resp.status_code == 401


def test_e2e_regulations_ingest_endpoint():
    reg_dir = resolve_regulations_dir()
    seed_path = reg_dir / "meu_uygulama_esaslari_seed.md"
    assert seed_path.exists()

    doc_id = str(uuid4())
    payload = {
        "regulation_doc_id": doc_id,
        "storage_path": str(seed_path),
        "title_tr": "Mersin Üniversitesi BAP Uygulama Usul ve Esasları",
        "doc_type": "ESASLAR",
        "replace_previous_slug": "test-meu-esaslar-e2e",
        "version_label": "2026.1",
    }
    response = client.post(
        "/internal/v1/regulations/ingest",
        headers={"X-Internal-Service-Token": VALID_TOKEN},
        json=payload,
    )
    assert response.status_code == 200
    data = response.json()
    assert data["regulation_doc_id"] == doc_id
    assert data["slug"] == "test-meu-esaslar-e2e"
    assert data["total_chunks_created"] >= 14
    assert "MADDE 4" in data["articles_indexed"]
    assert data["status"] == "INDEXED"


def test_e2e_evaluate_endpoint_full():
    version_id = str(uuid4())
    structured = {
        "detected_submission_type": "NEW_PROJECT",
        "detected_project_code": "TP2",
        "applicant_name": "Fatma Şahin",
        "applicant_title": "Dr. Öğr. Üyesi",
        "department": "Kimya Bölümü",
        "project_title": "Polimer Sentez Projesi",
        "requested_budget_total_tl": 140000.0,
        "requested_duration_months": 24,
        "wet_signature_present": False,  # Will trigger RAG missing signature
        "ethics_required_keywords_detected": False,
        "detected_attachments": ["APPLICATION_FORM"],
        "budget_line_items": [
            {
                "category": "CONSUMABLE",
                "name": "Kimyasal Reaktifler",
                "total_tl": 30000.0,
                "proforma_count": 1,  # > 25k but only 1 proforma!
            }
        ],
    }

    deterministic_finding = {
        "source_type": "DETERMINISTIC_RULE",
        "rule_code": "RULE_BUDGET_CAP",
        "category": "BUDGET",
        "severity": "VIOLATION",
        "title_tr": "Bütçe Üst Limiti Aşıldı",
        "explanation_tr": "TP2 tavanı olan 125.000 TL aşılarak 140.000 TL talep edilmiştir.",
        "regulation_doc_name": "Mersin Üniversitesi BAP Uygulama Usul ve Esasları",
        "regulation_article_ref": "MADDE 4/1-b",
        "expected_value": "125.000,00 TL",
        "actual_value": "140.000,00 TL",
    }

    payload = {
        "version_id": version_id,
        "submission_type": "NEW_PROJECT",
        "project_code": "TP2",
        "structured_data": structured,
        "document_markdowns": [],
        "deterministic_findings": [deterministic_finding],
        "module_flags": {
            "run_deterministic_rules": True,
            "run_rag_checklist": True,
            "generate_action_drafts": True,
        },
    }

    response = client.post(
        "/internal/v1/evaluate",
        headers={"X-Internal-Service-Token": VALID_TOKEN},
        json=payload,
    )
    assert response.status_code == 200
    data = response.json()

    assert data["overall_compliance_status"] == "NEEDS_REVISION"
    assert len(data["rag_findings"]) >= 1
    assert len(data["combined_findings"]) >= 2

    # Check for revision draft
    assert data["revision_draft_tr"] is not None
    assert "Eksiklik Bildirimi" in data["revision_draft_tr"]
    assert "Fatma Şahin" in data["revision_draft_tr"]

    # Check commission referral summary
    assert data["commission_referral_summary_tr"] is not None
    assert "PROFESÖR KURULU" in data["commission_referral_summary_tr"]


def test_e2e_qa_ask_endpoint():
    payload = {
        "version_id": str(uuid4()),
        "question": "TP2 tez projesi için 2026 yılı bütçe tavanı ne kadardır ve enstitü onay belgesi zorunlu mudur?",
        "submission_context": {
            "detected_submission_type": "NEW_PROJECT",
            "detected_project_code": "TP2",
            "applicant_name": "Ayşe Yılmaz",
            "wet_signature_present": True,
            "detected_attachments": ["APPLICATION_FORM"],
            "budget_line_items": [],
        },
        "chat_history": [],
    }

    response = client.post(
        "/internal/v1/qa/ask",
        headers={"X-Internal-Service-Token": VALID_TOKEN},
        json=payload,
    )
    assert response.status_code == 200
    data = response.json()

    assert "answer_tr" in data
    assert len(data["answer_tr"]) > 30
    assert len(data["citations"]) >= 1

    citation = data["citations"][0]
    assert "regulation_doc_name" in citation
    assert "regulation_article_ref" in citation
    assert "verbatim_quote" in citation
    assert len(citation["verbatim_quote"]) > 10
