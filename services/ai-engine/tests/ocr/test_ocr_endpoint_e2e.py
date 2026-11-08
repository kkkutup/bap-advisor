import os
import tempfile
import zipfile
from uuid import uuid4
import pytest
from fastapi.testclient import TestClient
from app.config import get_settings
from app.main import app
from app.schemas import DocumentRole, SubmissionType

client = TestClient(app)
VALID_TOKEN = get_settings().internal_service_token
SAMPLE_DIR = "/Users/kutup/Documents/bap-advisor/data/sample-submissions"


def test_ocr_extract_endpoint_bundle_1():
    b1_dir = os.path.join(SAMPLE_DIR, "01_tp2_over_budget_v1")
    pdf_path = os.path.join(b1_dir, "01_tp2_basvuru_formu.pdf")
    xlsx_path = os.path.join(b1_dir, "02_butce_tablosu.xlsx")

    version_id = str(uuid4())
    doc1_id = str(uuid4())
    doc2_id = str(uuid4())

    payload = {
        "version_id": version_id,
        "files": [
            {
                "doc_id": doc1_id,
                "storage_path": pdf_path,
                "mime_type": "application/pdf",
                "file_name": "01_tp2_basvuru_formu.pdf"
            },
            {
                "doc_id": doc2_id,
                "storage_path": xlsx_path,
                "mime_type": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "file_name": "02_butce_tablosu.xlsx"
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
    assert len(data["documents"]) == 2

    # Check document classifications
    doc_roles = [d["detected_doc_role"] for d in data["documents"]]
    assert "APPLICATION_FORM" in doc_roles
    assert "BUDGET_TABLE" in doc_roles

    # Check extracted structured data
    st = data["structured_data"]
    assert st["detected_submission_type"] == "NEW_PROJECT"
    assert st["detected_project_code"] == "TP2"
    assert st["requested_budget_total_tl"] == 142000.0
    assert st["requested_duration_months"] == 24
    assert st["wet_signature_present"] is True
    assert len(st["budget_line_items"]) >= 2


def test_ocr_extract_endpoint_bundle_2_compliant():
    b2_dir = os.path.join(SAMPLE_DIR, "02_tp2_compliant_v2")
    files_payload = []
    for fname in os.listdir(b2_dir):
        if fname.endswith((".pdf", ".xlsx")):
            fpath = os.path.join(b2_dir, fname)
            mime = "application/pdf" if fname.endswith(".pdf") else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            files_payload.append({
                "doc_id": str(uuid4()),
                "storage_path": fpath,
                "mime_type": mime,
                "file_name": fname
            })

    version_id = str(uuid4())
    payload = {
        "version_id": version_id,
        "files": files_payload,
        "enable_vision_fallback": False
    }

    response = client.post(
        "/internal/v1/ocr/extract",
        headers={"X-Internal-Service-Token": VALID_TOKEN},
        json=payload
    )

    assert response.status_code == 200
    data = response.json()
    assert len(data["documents"]) == 4

    roles = {d["detected_doc_role"] for d in data["documents"]}
    assert "APPLICATION_FORM" in roles
    assert "BUDGET_TABLE" in roles
    assert "THESIS_PROPOSAL" in roles
    assert "PROFORMA_INVOICE" in roles

    st = data["structured_data"]
    assert st["detected_submission_type"] == "NEW_PROJECT"
    assert st["requested_budget_total_tl"] == 118500.0


def test_ocr_extract_endpoint_scanned_unsigned_bundle_5():
    b5_dir = os.path.join(SAMPLE_DIR, "05_ek_sure_scanned_unsigned")
    pdf_path = os.path.join(b5_dir, "01_ek_sure_talep_formu_taranmis_imzasiz.pdf")

    version_id = str(uuid4())
    payload = {
        "version_id": version_id,
        "files": [
            {
                "doc_id": str(uuid4()),
                "storage_path": pdf_path,
                "mime_type": "application/pdf",
                "file_name": "01_ek_sure_talep_formu_taranmis_imzasiz.pdf"
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
    st = data["structured_data"]
    assert st["detected_submission_type"] == "ADDITIONAL_TIME"
    assert st["wet_signature_present"] is False


def test_ocr_extract_endpoint_scanned_signed_bundle_5b():
    b5b_dir = os.path.join(SAMPLE_DIR, "05b_ek_sure_scanned_signed")
    pdf_path = os.path.join(b5b_dir, "01_ek_sure_talep_formu_taranmis_imzali.pdf")

    version_id = str(uuid4())
    payload = {
        "version_id": version_id,
        "files": [
            {
                "doc_id": str(uuid4()),
                "storage_path": pdf_path,
                "mime_type": "application/pdf",
                "file_name": "01_ek_sure_talep_formu_taranmis_imzali.pdf"
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
    st = data["structured_data"]
    assert st["detected_submission_type"] == "ADDITIONAL_TIME"
    assert st["wet_signature_present"] is True


def test_ocr_extract_endpoint_with_zip_archive():
    b1_dir = os.path.join(SAMPLE_DIR, "01_tp2_over_budget_v1")
    pdf_path = os.path.join(b1_dir, "01_tp2_basvuru_formu.pdf")
    xlsx_path = os.path.join(b1_dir, "02_butce_tablosu.xlsx")

    with tempfile.NamedTemporaryFile(suffix=".zip", delete=False) as tmp_zip:
        zip_path = tmp_zip.name

    try:
        with zipfile.ZipFile(zip_path, "w") as zf:
            zf.write(pdf_path, arcname="01_tp2_basvuru_formu.pdf")
            zf.write(xlsx_path, arcname="02_butce_tablosu.xlsx")

        version_id = str(uuid4())
        payload = {
            "version_id": version_id,
            "files": [
                {
                    "doc_id": str(uuid4()),
                    "storage_path": zip_path,
                    "mime_type": "application/zip",
                    "file_name": "submission_bundle.zip"
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
        assert len(data["documents"]) == 2
        st = data["structured_data"]
        assert st["detected_submission_type"] == "NEW_PROJECT"
        assert st["detected_project_code"] == "TP2"
        assert st["requested_budget_total_tl"] == 142000.0

    finally:
        if os.path.exists(zip_path):
            os.remove(zip_path)
