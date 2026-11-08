import os
from typing import Any, Dict, List
import numpy as np
from PIL import Image, ImageEnhance, ImageOps
from app.ocr.engine import run_local_ocr
from app.ocr.table_reconstructor import TableReconstructor
from app.parsers.base import BaseParser, ParsedDocument


class ImageParser(BaseParser):
    SUPPORTED_EXTENSIONS = {".png", ".jpg", ".jpeg", ".tiff", ".bmp", ".webp"}

    def parse(self, file_path: str, **kwargs) -> ParsedDocument:
        if not os.path.exists(file_path):
            raise FileNotFoundError(f"File not found: {file_path}")

        with Image.open(file_path) as raw_img:
            # 1. Auto-orient using EXIF tag
            oriented_img = ImageOps.exif_transpose(raw_img)
            if oriented_img is None:
                oriented_img = raw_img.copy()

            # Ensure RGB mode
            if oriented_img.mode != "RGB":
                rgb_img = oriented_img.convert("RGB")
            else:
                rgb_img = oriented_img.copy()

            # 2. Pre-process for OCR: mild contrast enhancement
            enhancer = ImageEnhance.Contrast(rgb_img)
            enhanced_img = enhancer.enhance(1.3)

            # 3. Run Tier-2 Local OCR
            ocr_results, avg_confidence = run_local_ocr(enhanced_img)

            # 4. Reconstruct lines & tables
            markdown_text, structured_tables = TableReconstructor.process_ocr_results(ocr_results)

            return ParsedDocument(
                text=markdown_text,
                tables=structured_tables,
                page_count=1,
                ocr_confidence=avg_confidence if avg_confidence > 0 else 0.85,
                is_scanned=True,
                page_images=[rgb_img],
                metadata={
                    "width": rgb_img.width,
                    "height": rgb_img.height,
                    "ocr_boxes_detected": len(ocr_results)
                }
            )
