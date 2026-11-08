import os
from app.parsers.base import BaseParser, ParsedDocument


class TextParser(BaseParser):
    def parse(self, file_path: str, **kwargs) -> ParsedDocument:
        if not os.path.exists(file_path):
            raise FileNotFoundError(f"File not found: {file_path}")

        encodings = ["utf-8", "utf-8-sig", "latin-5", "iso-8859-9", "cp1254", "latin-1"]
        text = ""
        used_encoding = "utf-8"

        for enc in encodings:
            try:
                with open(file_path, "r", encoding=enc) as f:
                    text = f.read()
                used_encoding = enc
                break
            except (UnicodeDecodeError, LookupError):
                continue

        return ParsedDocument(
            text=text,
            tables=[],
            page_count=1,
            ocr_confidence=0.99,
            is_scanned=False,
            metadata={"encoding": used_encoding, "file_size": os.path.getsize(file_path)}
        )
