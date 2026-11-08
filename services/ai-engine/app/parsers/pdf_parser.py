import io
import os
import re
from typing import Any, Dict, List, Tuple
import fitz  # PyMuPDF
import pdfplumber
from PIL import Image
from app.ocr.engine import run_local_ocr
from app.ocr.table_reconstructor import TableReconstructor
from app.parsers.base import BaseParser, ParsedDocument


class PdfParser(BaseParser):
    def __init__(
        self,
        min_char_count_per_page: int = 50,
        garbled_ratio_threshold: float = 0.35,
        render_dpi: int = 200
    ):
        self.min_char_count_per_page = min_char_count_per_page
        self.garbled_ratio_threshold = garbled_ratio_threshold
        self.render_dpi = render_dpi

    def parse(self, file_path: str, **kwargs) -> ParsedDocument:
        if not os.path.exists(file_path):
            raise FileNotFoundError(f"File not found: {file_path}")

        doc = fitz.open(file_path)
        page_count = len(doc)
        page_markdowns: List[str] = []
        all_structured_tables: List[Dict[str, Any]] = []
        page_images: List[Image.Image] = []
        confidences: List[float] = []
        has_any_scanned_page = False

        # Open with pdfplumber for digital table extraction
        plumber_doc = None
        try:
            plumber_doc = pdfplumber.open(file_path)
        except Exception:
            plumber_doc = None

        try:
            for page_idx in range(page_count):
                page = doc[page_idx]
                page_text = page.get_text("text").strip()
                char_count = len(page_text)
                garbled_ratio = self._calculate_garbled_ratio(page_text)

                # Render page image (needed for visual analysis and OCR if scanned)
                pix = page.get_pixmap(dpi=self.render_dpi)
                page_img = Image.open(io.BytesIO(pix.tobytes("png"))).convert("RGB")
                page_images.append(page_img)

                # Check if page is scanned or low text
                is_page_scanned = (
                    char_count < self.min_char_count_per_page
                    or garbled_ratio > self.garbled_ratio_threshold
                )

                if is_page_scanned:
                    has_any_scanned_page = True
                    # Route to Tier-2 Local OCR
                    ocr_results, avg_conf = run_local_ocr(page_img)
                    confidences.append(avg_conf if avg_conf > 0 else 0.85)

                    page_md, page_tables = TableReconstructor.process_ocr_results(ocr_results)
                    page_header = f"## Sayfa {page_idx + 1} (OCR)"
                    page_markdowns.append(f"{page_header}\n\n{page_md}" if page_md else page_header)
                    all_structured_tables.extend(page_tables)
                else:
                    # Clean digital text page
                    confidences.append(0.98)
                    page_header = f"## Sayfa {page_idx + 1}"

                    # Extract digital tables via pdfplumber if available
                    digital_tables: List[Dict[str, Any]] = []
                    md_table_strings: List[str] = []

                    if plumber_doc and page_idx < len(plumber_doc.pages):
                        plumber_page = plumber_doc.pages[page_idx]
                        try:
                            raw_tables = plumber_page.extract_tables() or []
                            for tbl in raw_tables:
                                clean_rows = []
                                for row in tbl:
                                    if not row:
                                        continue
                                    clean_cells = [
                                        str(c).strip().replace("\n", " ") if c is not None else ""
                                        for c in row
                                    ]
                                    if any(clean_cells):
                                        clean_rows.append(clean_cells)
                                if len(clean_rows) >= 2:
                                    headers = clean_rows[0]
                                    data_rows = clean_rows[1:]
                                    md_tbl = self._format_markdown_table(headers, data_rows)
                                    md_table_strings.append(md_tbl)
                                    digital_tables.append({
                                        "page": page_idx + 1,
                                        "headers": headers,
                                        "rows": data_rows,
                                        "row_count": len(data_rows),
                                        "col_count": len(headers)
                                    })
                        except Exception:
                            digital_tables = []

                    content_parts = [page_header, page_text]
                    if md_table_strings:
                        content_parts.append("\n\n### Sayfa Tabloları:\n" + "\n\n".join(md_table_strings))

                    page_markdowns.append("\n\n".join(content_parts))
                    all_structured_tables.extend(digital_tables)

        finally:
            doc.close()
            if plumber_doc:
                try:
                    plumber_doc.close()
                except Exception:
                    pass

        overall_confidence = (
            sum(confidences) / len(confidences) if confidences else 0.98
        )
        full_markdown = "\n\n---\n\n".join(page_markdowns)

        return ParsedDocument(
            text=full_markdown,
            tables=all_structured_tables,
            page_count=page_count,
            ocr_confidence=round(overall_confidence, 4),
            is_scanned=has_any_scanned_page,
            page_images=page_images,
            metadata={
                "page_count": page_count,
                "scanned_pages_detected": has_any_scanned_page,
                "table_count": len(all_structured_tables)
            }
        )

    def _calculate_garbled_ratio(self, text: str) -> float:
        """
        Measures ratio of replacement chars, control chars or unmapped glyphs.
        """
        if not text:
            return 1.0

        # Non-printable, replacement characters \ufffd, and odd ASCII controls
        garbled_chars = 0
        total_chars = len(text)

        for ch in text:
            code = ord(ch)
            if ch == "\ufffd" or (code < 32 and ch not in "\n\r\t") or (127 <= code < 160):
                garbled_chars += 1

        return garbled_chars / total_chars if total_chars > 0 else 0.0

    def _format_markdown_table(self, headers: List[str], rows: List[List[str]]) -> str:
        col_count = len(headers)
        if col_count == 0:
            return ""

        header_line = "| " + " | ".join(h or " " for h in headers) + " |"
        sep_line = "| " + " | ".join("---" for _ in range(col_count)) + " |"
        row_lines = []
        for r in rows:
            cells = (r + [""] * col_count)[:col_count]
            row_lines.append("| " + " | ".join(c or " " for c in cells) + " |")

        return "\n".join([header_line, sep_line] + row_lines)
