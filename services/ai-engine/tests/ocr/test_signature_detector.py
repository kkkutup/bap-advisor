import os
import pytest
from app.ocr.signature_detector import SignatureDetector
from app.parsers.pdf_parser import PdfParser

SAMPLE_DIR = "/Users/kutup/Documents/bap-advisor/data/sample-submissions"


def test_text_signature_detection():
    text = "Proje Yürütücüsü: Doç. Dr. Ahmet Yılmaz\n[İMZA] /İmzalıdır/ (Islak İmza İle Teslim Edilmiştir)"
    has_sig, conf, methods = SignatureDetector.detect_signature(text, page_images=None)
    assert has_sig is True
    assert conf >= 0.90
    assert any("text_marker" in m for m in methods)


def test_scanned_signed_pdf_detection():
    signed_pdf = os.path.join(SAMPLE_DIR, "05b_ek_sure_scanned_signed", "01_ek_sure_talep_formu_taranmis_imzali.pdf")
    parser = PdfParser()
    doc = parser.parse(signed_pdf)

    assert doc.is_scanned is True
    assert len(doc.page_images) >= 1

    has_sig, conf, methods = SignatureDetector.detect_signature(doc.text, doc.page_images)
    assert has_sig is True
    assert conf >= 0.90
    assert any("blue_ink" in m or "stroke" in m for m in methods)


def test_scanned_unsigned_pdf_detection():
    unsigned_pdf = os.path.join(SAMPLE_DIR, "05_ek_sure_scanned_unsigned", "01_ek_sure_talep_formu_taranmis_imzasiz.pdf")
    parser = PdfParser()
    doc = parser.parse(unsigned_pdf)

    assert doc.is_scanned is True
    assert len(doc.page_images) >= 1

    has_sig, conf, methods = SignatureDetector.detect_signature(doc.text, doc.page_images)
    # The signature box is present, but NO ink strokes exist -> MUST detect unsigned!
    assert has_sig is False
