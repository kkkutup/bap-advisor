import os
import pytest
from app.ocr.extractor import FormExtractor
from app.parsers.factory import get_parser_for_file
from app.schemas import DocumentRole, SubmissionType

SAMPLE_DIR = "/Users/kutup/Documents/bap-advisor/data/sample-submissions"


def test_extract_tp2_bundle_1():
    b1_dir = os.path.join(SAMPLE_DIR, "01_tp2_over_budget_v1")
    pdf_path = os.path.join(b1_dir, "01_tp2_basvuru_formu.pdf")
    xlsx_path = os.path.join(b1_dir, "02_butce_tablosu.xlsx")

    p1 = get_parser_for_file(pdf_path).parse(pdf_path)
    p2 = get_parser_for_file(xlsx_path).parse(xlsx_path)

    extractor = FormExtractor()
    data = extractor.extract_submission_data([
        {
            "doc_id": "1",
            "file_name": "01_tp2_basvuru_formu.pdf",
            "doc_role": DocumentRole.APPLICATION_FORM,
            "parsed_text": p1.text,
            "parsed_tables": p1.tables,
            "has_signature": True
        },
        {
            "doc_id": "2",
            "file_name": "02_butce_tablosu.xlsx",
            "doc_role": DocumentRole.BUDGET_TABLE,
            "parsed_text": p2.text,
            "parsed_tables": p2.tables,
            "has_signature": False
        }
    ])

    assert data.detected_submission_type == SubmissionType.NEW_PROJECT
    assert data.detected_project_code == "TP2"
    assert "Ahmet Yılmaz" in (data.applicant_name or "")
    assert data.requested_budget_total_tl == 142000.0
    assert data.requested_duration_months == 24
    assert data.wet_signature_present is True
    assert len(data.budget_line_items) >= 2


def test_extract_ap2_ethics_violation_bundle_3():
    b3_dir = os.path.join(SAMPLE_DIR, "03_ap2_ethics_and_duration_violation")
    docx_path = os.path.join(b3_dir, "01_ap2_proje_onerisi.docx")
    xlsx_path = os.path.join(b3_dir, "02_ap2_butce_ve_yolluk.xlsx")

    p1 = get_parser_for_file(docx_path).parse(docx_path)
    p2 = get_parser_for_file(xlsx_path).parse(xlsx_path)

    extractor = FormExtractor()
    data = extractor.extract_submission_data([
        {
            "doc_id": "1",
            "file_name": "01_ap2_proje_onerisi.docx",
            "doc_role": DocumentRole.APPLICATION_FORM,
            "parsed_text": p1.text,
            "parsed_tables": p1.tables,
            "has_signature": True
        },
        {
            "doc_id": "2",
            "file_name": "02_ap2_butce_ve_yolluk.xlsx",
            "doc_role": DocumentRole.BUDGET_TABLE,
            "parsed_text": p2.text,
            "parsed_tables": p2.tables,
            "has_signature": False
        }
    ])

    assert data.detected_submission_type == SubmissionType.NEW_PROJECT
    assert data.detected_project_code == "AP2"
    assert data.requested_duration_months == 42
    assert data.ethics_required_keywords_detected is True


def test_extract_open_access_bundle_4():
    b4_dir = os.path.join(SAMPLE_DIR, "04_open_access_wos_q1_valid")
    pdf_path = os.path.join(b4_dir, "01_acik_erisim_talep_formu.pdf")
    p1 = get_parser_for_file(pdf_path).parse(pdf_path)

    extractor = FormExtractor()
    data = extractor.extract_submission_data([
        {
            "doc_id": "1",
            "file_name": "01_acik_erisim_talep_formu.pdf",
            "doc_role": DocumentRole.APPLICATION_FORM,
            "parsed_text": p1.text,
            "parsed_tables": p1.tables,
            "has_signature": True
        }
    ])

    assert data.detected_submission_type == SubmissionType.OPEN_ACCESS
    assert data.journal_quartile == "Q1_TOP10"
    assert data.requested_budget_total_tl == 110000.0


def test_extract_ek_sure_scanned_unsigned_bundle_5():
    b5_dir = os.path.join(SAMPLE_DIR, "05_ek_sure_scanned_unsigned")
    pdf_path = os.path.join(b5_dir, "01_ek_sure_talep_formu_taranmis_imzasiz.pdf")
    p1 = get_parser_for_file(pdf_path).parse(pdf_path)

    extractor = FormExtractor()
    data = extractor.extract_submission_data([
        {
            "doc_id": "1",
            "file_name": "01_ek_sure_talep_formu_taranmis_imzasiz.pdf",
            "doc_role": DocumentRole.APPLICATION_FORM,
            "parsed_text": p1.text,
            "parsed_tables": p1.tables,
            "has_signature": False
        }
    ])

    assert data.detected_submission_type == SubmissionType.ADDITIONAL_TIME
    assert data.wet_signature_present is False
    assert (data.requested_duration_months == 18 or data.requested_duration_months == 42)
