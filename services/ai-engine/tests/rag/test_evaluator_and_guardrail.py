import os
from uuid import uuid4
import pytest
from app.evaluator.guardrail import check_and_sanitize_finding
from app.evaluator.synthesizer import GroundedSynthesizer
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


@pytest.fixture
def synthesizer():
    return GroundedSynthesizer()


def test_evaluator_domain_guardrail_sanitizes_scientific_judgment():
    # If a rule or LLM accidentally judges scientific merit, guardrail must redirect to commission
    unsafe_finding = FindingItem(
        source_type=FindingSource.RAG_LLM,
        rule_code="TEST_SCIENTIFIC_JUDGMENT",
        category=RuleCategory.ELIGIBILITY,
        severity=FindingSeverity.VIOLATION,
        title_tr="Bilimsel Kalite Yetersiz",
        explanation_tr="Projenin bilimsel değeri ve özgün değer hipotezi zayıf bulunmuştur.",
        regulation_doc_name="Mersin Üniversitesi BAP Uygulama Usul ve Esasları",
        regulation_article_ref="MADDE 1",
    )
    sanitized = check_and_sanitize_finding(unsafe_finding)

    assert sanitized.severity == FindingSeverity.AMBIGUOUS_COMMISSION
    assert "[Komisyon Takdiri]" in sanitized.title_tr
    assert "Profesör Kurulu" in sanitized.explanation_tr


def test_evaluator_grounded_missing_signature(synthesizer):
    submission = StructuredSubmissionData(
        detected_submission_type=SubmissionType.NEW_PROJECT,
        detected_project_code="TP2",
        applicant_name="Dr. Ali Kaya",
        applicant_title="Doç. Dr.",
        wet_signature_present=False,
    )
    findings = synthesizer.synthesize_rag_findings(
        submission_type=SubmissionType.NEW_PROJECT,
        project_code="TP2",
        structured_data=submission,
        relevant_chunks=[],
        deterministic_findings=[],
    )

    sig_findings = [f for f in findings if f.rule_code == "RAG_WET_SIGNATURE_MANDATORY"]
    assert len(sig_findings) == 1
    f = sig_findings[0]
    assert f.severity == FindingSeverity.MISSING_DOCUMENT
    assert f.regulation_article_ref == "MADDE 7/2"
    assert "ıslak imza" in f.title_tr.lower() or "mavi" in f.title_tr.lower()
    assert "yazıcıdan döküm alınarak" in f.verbatim_quote


def test_evaluator_grounded_missing_ethics(synthesizer):
    submission = StructuredSubmissionData(
        detected_submission_type=SubmissionType.NEW_PROJECT,
        detected_project_code="AP2",
        ethics_required_keywords_detected=True,
        wet_signature_present=True,
        detected_attachments=[DocumentRole.APPLICATION_FORM],
    )
    findings = synthesizer.synthesize_rag_findings(
        submission_type=SubmissionType.NEW_PROJECT,
        project_code="AP2",
        structured_data=submission,
        relevant_chunks=[],
        deterministic_findings=[],
    )

    ethics_findings = [f for f in findings if f.rule_code == "RAG_ETHICS_APPROVAL_MISSING"]
    assert len(ethics_findings) == 1
    f = ethics_findings[0]
    assert f.severity == FindingSeverity.MISSING_DOCUMENT
    assert f.regulation_article_ref == "MADDE 9/1"
    assert "Etik Kurul" in f.title_tr


def test_evaluator_proforma_count_rule(synthesizer):
    # Total > 25,000 TL with 1 proforma (< 3)
    item = BudgetLineItem(
        category=BudgetCategory.EQUIPMENT,
        name="Optik Spektrometre Cihazı",
        total_tl=38000.0,
        proforma_count=1,
    )
    submission = StructuredSubmissionData(
        detected_submission_type=SubmissionType.NEW_PROJECT,
        detected_project_code="AP2",
        budget_line_items=[item],
        wet_signature_present=True,
    )
    findings = synthesizer.synthesize_rag_findings(
        submission_type=SubmissionType.NEW_PROJECT,
        project_code="AP2",
        structured_data=submission,
        relevant_chunks=[],
        deterministic_findings=[],
    )

    p_findings = [f for f in findings if f.rule_code == "RAG_PROFORMA_COUNT_INSUFFICIENT"]
    assert len(p_findings) == 1
    assert p_findings[0].severity == FindingSeverity.VIOLATION
    assert p_findings[0].regulation_article_ref == "MADDE 11/1-a"
    assert "25.000,00 TL" in p_findings[0].verbatim_quote


def test_evaluator_technical_spec_rule(synthesizer):
    # Total > 50,000 TL without technical spec
    item = BudgetLineItem(
        category=BudgetCategory.EQUIPMENT,
        name="Lazer Kesim İstasyonu",
        total_tl=65000.0,
        proforma_count=3,
        has_technical_spec=False,
    )
    submission = StructuredSubmissionData(
        detected_submission_type=SubmissionType.NEW_PROJECT,
        detected_project_code="AP2",
        budget_line_items=[item],
        wet_signature_present=True,
    )
    findings = synthesizer.synthesize_rag_findings(
        submission_type=SubmissionType.NEW_PROJECT,
        project_code="AP2",
        structured_data=submission,
        relevant_chunks=[],
        deterministic_findings=[],
    )

    spec_findings = [f for f in findings if f.rule_code == "RAG_TECHNICAL_SPEC_REQUIRED"]
    assert len(spec_findings) == 1
    assert spec_findings[0].severity == FindingSeverity.MISSING_DOCUMENT
    assert spec_findings[0].regulation_article_ref == "MADDE 11/1-b"


def test_evaluator_open_access_quartile_rule(synthesizer):
    submission = StructuredSubmissionData(
        detected_submission_type=SubmissionType.OPEN_ACCESS,
        journal_quartile="Q3",
        wet_signature_present=True,
    )
    findings = synthesizer.synthesize_rag_findings(
        submission_type=SubmissionType.OPEN_ACCESS,
        project_code="OPEN_ACCESS",
        structured_data=submission,
        relevant_chunks=[],
        deterministic_findings=[],
    )

    q_findings = [f for f in findings if f.rule_code == "RAG_OPEN_ACCESS_QUARTILE_INELIGIBLE"]
    assert len(q_findings) == 1
    assert q_findings[0].severity == FindingSeverity.VIOLATION
    assert q_findings[0].regulation_article_ref == "MADDE 6/2"


def test_evaluator_generates_formal_revision_letter(synthesizer):
    submission = StructuredSubmissionData(
        detected_submission_type=SubmissionType.NEW_PROJECT,
        detected_project_code="TP2",
        applicant_name="Zeynep Yılmaz",
        applicant_title="Dr. Öğr. Üyesi",
        department="Biyoloji Bölümü",
        project_title="Akdeniz Flora İncelemesi",
    )
    issue = FindingItem(
        source_type=FindingSource.RAG_LLM,
        rule_code="RAG_WET_SIGNATURE_MANDATORY",
        category=RuleCategory.FORMAL_SIGNATURE,
        severity=FindingSeverity.MISSING_DOCUMENT,
        title_tr="Mavi Islak İmza Eksikliği",
        explanation_tr="Evrakta mavi tükenmez kalemle imza bulunamamıştır.",
        regulation_doc_name="Mersin Üniversitesi BAP Uygulama Usul ve Esasları",
        regulation_article_ref="MADDE 7/2",
        verbatim_quote="proje yürütücüsü ve varsa araştırmacılar tarafından mavi tükenmez/dolma kalemle ıslak imzalı olarak imzalanmalıdır.",
        expected_value="Mavi ıslak imzalı taranmış form",
    )
    letter = synthesizer.generate_revision_draft(submission, "TP2", [issue])

    assert "MERSİN ÜNİVERSİTESİ" in letter
    assert "BAP Ön İnceleme Eksiklik Bildirimi" in letter
    assert "Zeynep Yılmaz" in letter
    assert "MADDE 7/2" in letter
    assert "15 (on beş) gün" in letter


def test_evaluator_generates_commission_referral_summary(synthesizer):
    submission = StructuredSubmissionData(
        detected_submission_type=SubmissionType.NEW_PROJECT,
        detected_project_code="AP1",
        applicant_name="Mehmet Demir",
        applicant_title="Doç. Dr.",
        department="Makine Mühendisliği",
        project_title="Termal Isı Geri Kazanım Projesi",
        requested_budget_total_tl=220000.0,
        requested_duration_months=24,
        wet_signature_present=True,
    )
    summary = synthesizer.generate_commission_referral_summary(submission, "AP1", [])

    assert "KOMİSYON BAŞKANLIĞINA (PROFESÖR KURULU)" in summary
    assert "Mehmet Demir" in summary
    assert "220,000.00 TL" in summary
    assert "24 Ay" in summary
    assert "sevk edilmesi uygundur" in summary
