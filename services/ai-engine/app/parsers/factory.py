import os
from typing import Optional
from app.parsers.base import BaseParser
from app.parsers.docx_parser import DocxParser
from app.parsers.image_parser import ImageParser
from app.parsers.pdf_parser import PdfParser
from app.parsers.text_parser import TextParser
from app.parsers.xlsx_parser import XlsxParser

_PARSER_CACHE = {
    "pdf": PdfParser(),
    "docx": DocxParser(),
    "xlsx": XlsxParser(),
    "text": TextParser(),
    "image": ImageParser()
}


def get_parser_for_file(file_path: str, mime_type: Optional[str] = None) -> BaseParser:
    ext = os.path.splitext(file_path)[1].lower()

    if ext == ".pdf" or (mime_type and "pdf" in mime_type.lower()):
        return _PARSER_CACHE["pdf"]

    if ext in (".docx", ".doc") or (mime_type and "wordprocessingml" in mime_type.lower()):
        return _PARSER_CACHE["docx"]

    if ext in (".xlsx", ".xls") or (mime_type and "spreadsheetml" in mime_type.lower()):
        return _PARSER_CACHE["xlsx"]

    if ext in ImageParser.SUPPORTED_EXTENSIONS or (mime_type and mime_type.lower().startswith("image/")):
        return _PARSER_CACHE["image"]

    if ext in (".txt", ".md", ".log", ".csv") or (mime_type and mime_type.lower().startswith("text/")):
        return _PARSER_CACHE["text"]

    # Fallback default parser
    return _PARSER_CACHE["text"]
