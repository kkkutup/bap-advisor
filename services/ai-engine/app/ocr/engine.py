from typing import Any, List, Optional, Tuple
import numpy as np
from PIL import Image

_OCR_ENGINE = None


def get_ocr_engine():
    """
    Lazy initialization of RapidOCR (100% local, runs on Mac CPU/MPS).
    Initialized only when first needed to minimize startup time.
    """
    global _OCR_ENGINE
    if _OCR_ENGINE is None:
        from rapidocr_onnxruntime import RapidOCR
        _OCR_ENGINE = RapidOCR()
    return _OCR_ENGINE


def run_local_ocr(image: Any) -> Tuple[List[Any], float]:
    """
    Runs RapidOCR on given image (PIL Image, numpy array, or file path).
    Returns (raw_ocr_results, average_confidence).
    """
    if isinstance(image, Image.Image):
        # Convert PIL to RGB numpy array
        if image.mode != "RGB":
            image = image.convert("RGB")
        np_img = np.array(image)
    elif isinstance(image, str):
        # File path
        with Image.open(image) as pil_img:
            if pil_img.mode != "RGB":
                pil_img = pil_img.convert("RGB")
            np_img = np.array(pil_img)
    elif isinstance(image, np.ndarray):
        np_img = image
    else:
        raise ValueError(f"Unsupported image type for OCR: {type(image)}")

    engine = get_ocr_engine()
    result, _ = engine(np_img)

    if not result:
        return [], 0.0

    scores = [float(item[2]) for item in result if len(item) >= 3 and item[2] is not None]
    avg_score = sum(scores) / len(scores) if scores else 0.0

    return result, round(avg_score, 4)
