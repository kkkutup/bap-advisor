import os
import tempfile
import zipfile
import pytest
from PIL import Image, ImageDraw
from app.parsers.archive_parser import ArchiveParser, ArchiveSecurityException
from app.parsers.docx_parser import DocxParser
from app.parsers.factory import get_parser_for_file
from app.parsers.image_parser import ImageParser
from app.parsers.pdf_parser import PdfParser
from app.parsers.text_parser import TextParser
from app.parsers.xlsx_parser import XlsxParser

SAMPLE_DIR = "/Users/kutup/Documents/bap-advisor/data/sample-submissions"


def test_pdf_digital_parser():
    pdf_path = os.path.join(SAMPLE_DIR, "01_tp2_over_budget_v1", "01_tp2_basvuru_formu.pdf")
    parser = PdfParser()
    doc = parser.parse(pdf_path)

    assert doc.page_count >= 1
    assert "TP2" in doc.text or "Ahmet Yılmaz" in doc.text
    assert doc.ocr_confidence >= 0.95
    assert doc.is_scanned is False
    assert len(doc.page_images) >= 1


def test_xlsx_parser():
    xlsx_path = os.path.join(SAMPLE_DIR, "01_tp2_over_budget_v1", "02_butce_tablosu.xlsx")
    parser = XlsxParser()
    doc = parser.parse(xlsx_path)

    assert len(doc.tables) >= 1
    assert "Kimyasal" in doc.text
    assert doc.ocr_confidence == 0.99
    assert doc.tables[0]["col_count"] >= 5


def test_docx_parser():
    docx_path = os.path.join(SAMPLE_DIR, "03_ap2_ethics_and_duration_violation", "01_ap2_proje_onerisi.docx")
    parser = DocxParser()
    doc = parser.parse(docx_path)

    assert "Mehmet Kaya" in doc.text
    assert "AP2" in doc.text
    assert "42 Ay" in doc.text
    assert doc.ocr_confidence >= 0.95


def test_image_parser_with_rapidocr():
    with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tmp:
        tmp_path = tmp.name

    try:
        # Create image with text
        img = Image.new("RGB", (600, 150), color=(255, 255, 255))
        draw = ImageDraw.Draw(img)
        draw.text((20, 20), "MERSIN UNIVERSITESI BAP BIRIMI", fill=(0, 0, 0))
        draw.text((20, 60), "Talep Edilen Butce: 125000 TL", fill=(0, 0, 0))
        img.save(tmp_path)

        parser = ImageParser()
        doc = parser.parse(tmp_path)

        assert doc.is_scanned is True
        assert "MERSIN" in doc.text or "BAP" in doc.text or "125000" in doc.text
        assert doc.ocr_confidence > 0.5
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)


def test_text_parser():
    with tempfile.NamedTemporaryFile(suffix=".md", mode="w", encoding="utf-8", delete=False) as tmp:
        tmp.write("# BAP Proje Raporu\nTürkçe karakter desteği: ığüşöçİĞÜŞÖÇ")
        tmp_path = tmp.name

    try:
        parser = TextParser()
        doc = parser.parse(tmp_path)
        assert "BAP Proje Raporu" in doc.text
        assert "Türkçe" in doc.text
        assert doc.ocr_confidence == 0.99
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)


def test_archive_parser_safe_extraction():
    with tempfile.TemporaryDirectory() as temp_dir:
        zip_path = os.path.join(temp_dir, "test_bundle.zip")
        with zipfile.ZipFile(zip_path, "w") as zf:
            zf.writestr("test1.txt", "Dosya 1 içeriği")
            zf.writestr("subfolder/test2.txt", "Dosya 2 içeriği")

        extract_dir, extracted_files = ArchiveParser.extract_safely(zip_path)
        assert len(extracted_files) == 2
        assert any("test1.txt" in f for f in extracted_files)
        assert any("test2.txt" in f for f in extracted_files)


def test_archive_parser_zip_slip_prevention():
    with tempfile.TemporaryDirectory() as temp_dir:
        malicious_zip = os.path.join(temp_dir, "malicious.zip")
        with zipfile.ZipFile(malicious_zip, "w") as zf:
            # Craft unsafe relative path
            zf.writestr("../../etc/evil.txt", "malicious payload")

        with pytest.raises(ArchiveSecurityException):
            ArchiveParser.extract_safely(malicious_zip)


def test_parser_factory():
    assert isinstance(get_parser_for_file("doc.pdf"), PdfParser)
    assert isinstance(get_parser_for_file("doc.docx"), DocxParser)
    assert isinstance(get_parser_for_file("doc.xlsx"), XlsxParser)
    assert isinstance(get_parser_for_file("image.jpg"), ImageParser)
    assert isinstance(get_parser_for_file("report.md"), TextParser)
