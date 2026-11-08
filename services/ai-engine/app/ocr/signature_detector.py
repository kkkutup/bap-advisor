import re
from typing import Any, Dict, List, Optional, Tuple
import numpy as np
from PIL import Image


class SignatureDetector:
    """
    Wet signature and official stamp detector combining textual cues and
    visual stroke/ink density analysis on rendered document pages.
    """

    TEXT_SIGNATURE_PATTERNS = [
        re.compile(r"\[[İI]MZA\]", re.IGNORECASE),
        re.compile(r"/[İI]mzal[ıi]d[ıi]r/", re.IGNORECASE),
        re.compile(r"e-[iİ]mzal[ıi]d[ıi]r", re.IGNORECASE),
        re.compile(r"g[üu]venli elektronik imzal[ıi]", re.IGNORECASE),
        re.compile(r"asl[ıi] gibidir", re.IGNORECASE)
    ]

    SIGNATURE_LABEL_PATTERNS = [
        re.compile(r"proje yürütücüsü\s*imza", re.IGNORECASE),
        re.compile(r"yürütücü\s*imza", re.IGNORECASE),
        re.compile(r"yetkili\s*imza", re.IGNORECASE),
        re.compile(r"danışman\s*imza", re.IGNORECASE),
        re.compile(r"ad[ıi] soyad[ıi]\s*/?\s*imza", re.IGNORECASE),
        re.compile(r"ıslak\s*imza", re.IGNORECASE),
        re.compile(r"\bimza\s*:", re.IGNORECASE)
    ]

    @classmethod
    def detect_signature(
        cls,
        text_content: str,
        page_images: Optional[List[Image.Image]] = None
    ) -> Tuple[bool, float, List[str]]:
        """
        Determines whether a document has a valid wet signature or verified digital stamp.
        Returns: (has_signature, confidence, detection_methods)
        """
        methods: List[str] = []
        confidence_scores: List[float] = []

        # 1. Textual explicit signed markers
        for pat in cls.TEXT_SIGNATURE_PATTERNS:
            if pat.search(text_content):
                methods.append(f"text_marker:{pat.pattern}")
                confidence_scores.append(0.95)
                break

        # Check if signature labels are present in text
        has_signature_label = any(pat.search(text_content) for pat in cls.SIGNATURE_LABEL_PATTERNS)

        # 2. Visual analysis on page images
        visual_detected = False
        if page_images:
            # We usually inspect the last page or pages containing signature labels
            for p_idx, img in enumerate(page_images):
                has_visual_sig, conf, vis_methods = cls._analyze_image_signatures(img)
                if has_visual_sig:
                    visual_detected = True
                    methods.extend([f"p{p_idx+1}_{m}" for m in vis_methods])
                    confidence_scores.append(conf)

        # If visual inspection was possible and no ink strokes were found in the document,
        # but the document has a signature label (like "İmza:"), this indicates an UNSIGNED form!
        if page_images and not visual_detected and not any("text_marker" in m for m in methods):
            if has_signature_label:
                # Scanned or rendered form with empty signature box
                return False, 0.90, ["visual_empty_signature_zone"]
            return False, 0.80, ["no_signature_found"]

        if methods and confidence_scores:
            max_conf = max(confidence_scores)
            return True, round(max_conf, 4), methods

        # If no page images provided, fall back to label check with lower confidence
        if has_signature_label and not page_images:
            return True, 0.70, ["text_label_only"]

        return False, 0.85, ["no_signature_detected"]

    @classmethod
    def _analyze_image_signatures(cls, img: Image.Image) -> Tuple[bool, float, List[str]]:
        """
        Analyzes the bottom 35% of an image (standard signature & stamp zone)
        for blue-ink strokes, stamp clusters, and dark handwriting strokes.
        """
        if img.mode != "RGB":
            img = img.convert("RGB")

        arr = np.array(img)
        h, w, _ = arr.shape
        if h < 50 or w < 50:
            return False, 0.0, []

        # Signature zone: bottom 35%
        crop_start_y = int(h * 0.65)
        sig_zone = arr[crop_start_y:, :, :]

        r = sig_zone[:, :, 0].astype(int)
        g = sig_zone[:, :, 1].astype(int)
        b = sig_zone[:, :, 2].astype(int)

        methods: List[str] = []
        confidences: List[float] = []

        # A. Blue Ink Detection
        # Blue ink has significantly higher blue component than red/green
        blue_mask = (b - np.maximum(r, g) > 25) & (b > 75) & (b < 240)
        blue_pixel_count = int(np.sum(blue_mask))

        # A handwritten signature typically occupies 150 - 15000 pixels depending on DPI
        if blue_pixel_count >= 120:
            methods.append(f"blue_ink_signature({blue_pixel_count}_px)")
            confidences.append(0.96)

        # B. Official Stamp Detection (Red or Purple circular/rectangular ink)
        # Red stamp: red is significantly higher than green and blue
        red_stamp_mask = (r - np.maximum(g, b) > 35) & (r > 120) & (b < 180)
        red_stamp_pixels = int(np.sum(red_stamp_mask))
        if red_stamp_pixels >= 250:
            methods.append(f"red_stamp({red_stamp_pixels}_px)")
            confidences.append(0.94)

        # C. Dark Pen Non-Typed Stroke Clusters in Signature Box
        # Look for connected dark stroke clusters in the lower right or lower half
        # Dark pixels: r, g, b all < 100
        dark_mask = (r < 100) & (g < 100) & (b < 100)
        dark_pixels = int(np.sum(dark_mask))

        # Check right half of signature zone (standard Turkish signature column)
        right_half = sig_zone[:, int(w * 0.45):, :]
        r_rh = right_half[:, :, 0].astype(int)
        g_rh = right_half[:, :, 1].astype(int)
        b_rh = right_half[:, :, 2].astype(int)
        right_dark_mask = (r_rh < 100) & (g_rh < 100) & (b_rh < 100)
        right_dark_pixels = int(np.sum(right_dark_mask))

        # If there's an active stroke cluster in the right half of the signature zone
        if right_dark_pixels >= 400 and not methods:
            methods.append(f"dark_pen_stroke_cluster({right_dark_pixels}_px)")
            confidences.append(0.88)

        if methods:
            return True, max(confidences), methods

        return False, 0.0, []
