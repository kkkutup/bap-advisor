import pytest
from pathlib import Path
from app.db.local_store import resolve_regulations_dir
from app.rag.chunker import TurkishLegalChunker
from app.rag.pdf_reader import read_document_pages


@pytest.fixture
def chunker():
    return TurkishLegalChunker(max_chunk_chars=1200, min_chunk_chars=50)


@pytest.fixture
def reg_dir():
    return resolve_regulations_dir()


def test_chunk_seed_markdown_articles(chunker, reg_dir):
    seed_path = reg_dir / "meu_uygulama_esaslari_seed.md"
    assert seed_path.exists(), f"Seed markdown missing at {seed_path}"

    pages = read_document_pages(str(seed_path))
    chunks = chunker.chunk_pages(pages, "Mersin Üniversitesi BAP Uygulama Usul ve Esasları")

    assert len(chunks) >= 14, f"Expected at least 14 chunks, got {len(chunks)}"

    article_numbers = [c.article_number for c in chunks if c.article_number]
    assert "MADDE 1" in article_numbers
    assert "MADDE 4" in article_numbers
    assert "MADDE 8" in article_numbers
    assert "MADDE 14" in article_numbers

    # Verify section titles
    section_titles = [c.section_title for c in chunks if c.section_title]
    assert any("BÖLÜM I" in st or "AMAÇ" in st for st in section_titles)
    assert any("BÖLÜM II" in st or "PROJE TÜRLERİ" in st for st in section_titles)


def test_chunk_extracts_project_codes(chunker, reg_dir):
    seed_path = reg_dir / "meu_uygulama_esaslari_seed.md"
    pages = read_document_pages(str(seed_path))
    chunks = chunker.chunk_pages(pages, "Mersin Üniversitesi BAP Uygulama Usul ve Esasları")

    # Locate MADDE 4 chunk (Tez Projeleri)
    madde_4_chunks = [c for c in chunks if c.article_number == "MADDE 4"]
    assert len(madde_4_chunks) >= 1
    m4 = madde_4_chunks[0]

    codes = m4.metadata_json.get("project_codes", [])
    assert "TP1" in codes
    assert "TP2" in codes
    assert "TP3" in codes

    # Locate MADDE 5 chunk (Araştırma Projeleri)
    madde_5_chunks = [c for c in chunks if c.article_number == "MADDE 5"]
    assert len(madde_5_chunks) >= 1
    m5 = madde_5_chunks[0]
    m5_codes = m5.metadata_json.get("project_codes", [])
    assert "AP1" in m5_codes
    assert "AP2" in m5_codes
    assert "AP4" in m5_codes


def test_chunk_extracts_numerical_limits(chunker, reg_dir):
    seed_path = reg_dir / "meu_uygulama_esaslari_seed.md"
    pages = read_document_pages(str(seed_path))
    chunks = chunker.chunk_pages(pages, "Mersin Üniversitesi BAP Uygulama Usul ve Esasları")

    # Locate MADDE 8 (Proje Süresi ve Ek Süre Sınırı)
    madde_8_chunks = [c for c in chunks if c.article_number == "MADDE 8"]
    assert len(madde_8_chunks) >= 1
    m8 = madde_8_chunks[0]

    limits = m8.metadata_json.get("detected_limits", [])
    assert any("36" in str(lim) or "ay" in str(lim) for lim in limits)
    assert "36" in m8.content_text


def test_chunk_sub_clause_fıkra(chunker):
    sample_text = """
## BÖLÜM I: GENEL HÜKÜMLER

### MADDE 10 – Denetim ve Yaptırımlar
(1) Proje harcamaları altı aylık dönemler halinde BAP Komisyonunca denetlenir. Yürütücü ara raporu zamanında sunmakla yükümlüdür.
(2) Mücbir sebep olmaksızın ara raporu iki ay geciktiren yürütücünün proje alımları durdurulur ve sonraki dönemde proje başvurusu kabul edilmez.
(3) Satın alınan teçhizat ve donanım üniversite demirbaşına kaydedilir.
"""
    pages = [{"page_number": 1, "text": sample_text}]
    chunks = chunker.chunk_pages(pages, "Test Yönetmeliği")

    assert len(chunks) == 3
    for idx, c in enumerate(chunks, start=1):
        assert c.article_number == "MADDE 10"
        assert c.sub_clause == f"({idx})"
        assert f"({idx})" in c.content_text


def test_chunk_pdf_fallback_and_tables(chunker, reg_dir):
    h_cetveli = reg_dir / "8-H-Cetveli_2026Butcesi.pdf"
    if not h_cetveli.exists():
        pytest.skip("8-H-Cetveli_2026Butcesi.pdf not found in data/regulations")

    pages = read_document_pages(str(h_cetveli))
    chunks = chunker.chunk_pages(pages, "2026 Yılı H Cetveli")

    assert len(chunks) >= 1
    assert any("GÜNDELİK" in c.content_text or "Harcırah" in c.content_text for c in chunks)


def test_chunk_unformatted_text_fallback(chunker):
    unformatted = (
        "Bu kılavuz Mersin Üniversitesi araştırmacılarına rehberlik etmek üzere hazırlanmıştır.\n\n"
        "Araştırmacılar harcama pusulalarını ve faturaları mevzuata uygun olarak teslim etmelidir.\n\n"
        "BAP Koordinasyon Birimi mesai saatleri içerisinde danışmanlık hizmeti sunmaktadır."
    )
    pages = [{"page_number": 1, "text": unformatted}]
    chunks = chunker.chunk_pages(pages, "Genel Bilgilendirme Kılavuzu")

    assert len(chunks) >= 1
    for c in chunks:
        assert "[Mevzuat: Genel Bilgilendirme Kılavuzu]" in c.content_text
