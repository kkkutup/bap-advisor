import pytest
from app.ocr.classifier import DocumentClassifier
from app.schemas import DocumentRole


def test_classify_application_form():
    role = DocumentClassifier.classify_document(
        file_name="01_tp2_basvuru_formu.pdf",
        content_markdown="# T.C. MERSİN ÜNİVERSİTESİ\nBAP PROJE BAŞVURU FORMU (TP2)"
    )
    assert role == DocumentRole.APPLICATION_FORM


def test_classify_budget_table():
    role = DocumentClassifier.classify_document(
        file_name="02_butce_tablosu.xlsx",
        content_markdown="# Bütçe Tablosu ve Kalem Dağılımı",
        parsed_tables=[{"headers": ["Kalem", "Tutar"], "rows": [["Sarf", "5000"]]}]
    )
    assert role == DocumentRole.BUDGET_TABLE


def test_classify_thesis_proposal():
    role = DocumentClassifier.classify_document(
        file_name="03_enstitu_tez_oneri_karari.pdf",
        content_markdown="FEN BİLİMLERİ ENSTİTÜSÜ MÜDÜRLÜĞÜ\nENSTİTÜ YÖNETİM KURULU TEZ ÖNERİSİ KABUL KARARI"
    )
    assert role == DocumentRole.THESIS_PROPOSAL


def test_classify_proforma_invoice():
    role = DocumentClassifier.classify_document(
        file_name="04_proforma_fatura_kimyasallar.pdf",
        content_markdown="PROFORMA FATURA VE FİYAT TEKLİF MEKTUBU\nKDV Hariç Tutar"
    )
    assert role == DocumentRole.PROFORMA_INVOICE


def test_classify_ethics_approval():
    role = DocumentClassifier.classify_document(
        file_name="etik_kurul_karar.pdf",
        content_markdown="GİRİŞİMSEL OLMAYAN KLİNİK ARAŞTIRMALAR ETİK KURULU ONAY BELGESİ"
    )
    assert role == DocumentRole.ETHICS_APPROVAL


def test_classify_publication_proof():
    role = DocumentClassifier.classify_document(
        file_name="02_wos_jcr_q1_kanit_belgesi.pdf",
        content_markdown="WEB OF SCIENCE - JOURNAL CITATION REPORTS (JCR) QUARTILE SCORE: Q1 (%10'luk dilim)"
    )
    assert role == DocumentRole.PUBLICATION_PROOF


def test_classify_technical_spec():
    role = DocumentClassifier.classify_document(
        file_name="spektrofotometre_sartnamesi.pdf",
        content_markdown="TEKNİK ŞARTNAME\nAsgari Teknik Özellikler"
    )
    assert role == DocumentRole.TECHNICAL_SPEC
