import logging
import os
from pathlib import Path
from typing import Any, Dict, List
import pymupdf

logger = logging.getLogger(__name__)

_ocr_engine = None


def get_ocr_engine():
    global _ocr_engine
    if _ocr_engine is None:
        try:
            from rapidocr_onnxruntime import RapidOCR
            _ocr_engine = RapidOCR()
        except Exception as e:
            logger.warning(f"Could not initialize RapidOCR: {e}")
            _ocr_engine = None
    return _ocr_engine


def read_document_pages(file_path: str) -> List[Dict[str, Any]]:
    """
    Reads a document (PDF, Markdown, or text) and returns a list of page dicts:
    [{"page_number": 1, "text": "..."}]
    
    For PDFs, if page text is sparse (< 50 chars), it renders the page and OCRs it locally.
    """
    path = Path(file_path)
    if not path.exists():
        # Try resolving relative to repo root data/regulations or current dir
        repo_data = Path(__file__).resolve().parent.parent.parent.parent / "data" / "regulations" / path.name
        if repo_data.exists():
            path = repo_data
        else:
            raise FileNotFoundError(f"Regulation file not found at: {file_path}")

    suffix = path.suffix.lower()

    if suffix in [".md", ".markdown", ".txt"]:
        return _read_markdown_or_text(path)
    elif suffix == ".pdf":
        return _read_pdf_with_ocr_fallback(path)
    else:
        # Fallback to UTF-8 text read
        try:
            content = path.read_text(encoding="utf-8")
            return [{"page_number": 1, "text": content}]
        except Exception as e:
            raise ValueError(f"Unsupported regulation file format '{suffix}': {e}")


def _read_markdown_or_text(path: Path) -> List[Dict[str, Any]]:
    content = path.read_text(encoding="utf-8", errors="replace")
    # If the markdown has page markers like '---' or page comments, we can split;
    # otherwise treat as a single multi-section document on page 1.
    return [{"page_number": 1, "text": content}]


def _read_pdf_with_ocr_fallback(path: Path) -> List[Dict[str, Any]]:
    doc = pymupdf.open(str(path))
    pages: List[Dict[str, Any]] = []

    for page_idx in range(len(doc)):
        page = doc[page_idx]
        page_num = page_idx + 1
        text = page.get_text() or ""
        text_clean = text.strip()

        # If sparse (< 50 non-whitespace chars), run RapidOCR on rendered page
        if len(text_clean) < 50:
            ocr_text = _ocr_pdf_page(page)
            if len(ocr_text.strip()) > len(text_clean):
                text_clean = ocr_text.strip()

        pages.append({
            "page_number": page_num,
            "text": text_clean,
        })

    doc.close()
    return pages


def _ocr_pdf_page(page: pymupdf.Page) -> str:
    engine = get_ocr_engine()
    if engine is None:
        return ""

    try:
        # Render page at 150 DPI for OCR accuracy and speed
        pix = page.get_pixmap(dpi=150)
        img_bytes = pix.tobytes("png")
        result, _ = engine(img_bytes)
        if not result:
            return ""

        # RapidOCR returns list of [box, text, confidence]
        lines = [item[1] for item in result if len(item) > 1 and item[1]]
        return "\n".join(lines)
    except Exception as e:
        logger.warning(f"Error during OCR of PDF page {page.number + 1}: {e}")
        return ""
