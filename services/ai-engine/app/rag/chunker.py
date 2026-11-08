import re
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional
from uuid import uuid4


@dataclass
class RegulationChunkData:
    id: str
    chunk_index: int
    section_title: Optional[str]
    article_number: Optional[str]
    article_title: Optional[str]
    sub_clause: Optional[str]
    page_number: int
    content_text: str
    raw_text: str
    metadata_json: Dict[str, Any] = field(default_factory=dict)


# Regex patterns for Turkish legal texts
SECTION_PATTERN = re.compile(
    r'(?:^|\n)(?:##?\s*)?'
    r'((?:(?:BİRİNCİ|İKİNCİ|ÜÇÜNCÜ|DÖRDÜNCÜ|BEŞİNCİ|ALTINCI|YEDİNCİ|SEKİZİNCİ|DOKUZUNCU|ONUNCU|[IVXLCDM]+|\d+)\s*(?:BÖLÜM|BÖLÜMÜ|KISIM|KAPİTÜL)|'
    r'(?:BÖLÜM|KISIM)\s+(?:[IVXLCDM]+|\d+|BİRİNCİ|İKİNCİ|ÜÇÜNCÜ|DÖRDÜNCÜ|BEŞİNCİ|ALTINCI|YEDİNCİ|SEKİZİNCİ|DOKUZUNCU|ONUNCU))'
    r'[\s:–-]*[^\n]*)',
    re.IGNORECASE,
)

ARTICLE_PATTERN = re.compile(
    r'(?:^|\n)(?:###?\s*)?'
    r'((?:MADDE|Madde|GEÇİCİ MADDE|Geçici Madde|EK MADDE|Ek Madde)\s*(\d+|[A-ZÇĞİÖŞÜ0-9]+))'
    r'[\s:–-]*([^\n(]*)',
    re.IGNORECASE,
)

SUB_CLAUSE_PATTERN = re.compile(
    r'(?:^|\n)\s*(\(\d+\))\s*',
    re.IGNORECASE,
)

PROJECT_CODE_PATTERN = re.compile(
    r'\b(TP[1-9]|AP[1-9]|WOS_Q[1-4]|Q[1-4]|WOS_Q1_TOP10|WOS_Q1_OTHER|WOS_Q2)\b',
    re.IGNORECASE,
)

LIMIT_PATTERN = re.compile(
    r'(\d{1,3}(?:\.\d{3})*(?:,\d{2})?\s*(?:TL|Türk Lirası|ay|gün|saat|%|yüzde))',
    re.IGNORECASE,
)


class TurkishLegalChunker:
    """
    Turkish Legal MADDE-Aware Chunker.
    Splits Turkish university BAP directives, regulations, and guides cleanly along
    hierarchical boundaries (BÖLÜM, MADDE, GEÇİCİ MADDE, (1) / (2) fıkra clauses),
    preserving section titles, article numbers, sub-clauses, and project code tags.
    """

    def __init__(self, max_chunk_chars: int = 1500, min_chunk_chars: int = 80):
        self.max_chunk_chars = max_chunk_chars
        self.min_chunk_chars = min_chunk_chars

    def chunk_pages(
        self,
        pages: List[Dict[str, Any]],
        doc_title: str,
        doc_type: str = "YONERGE",
    ) -> List[RegulationChunkData]:
        """
        Chunks page-structured document into RegulationChunkData items.
        """
        # First combine text while keeping page map
        full_text, page_offsets = self._build_page_map(pages)

        # Split into articles
        raw_articles = self._split_by_articles(full_text)

        chunks: List[RegulationChunkData] = []
        chunk_idx = 0

        for art in raw_articles:
            sub_chunks = self._chunk_single_article(art, doc_title, page_offsets)
            for sc in sub_chunks:
                sc.chunk_index = chunk_idx
                chunk_idx += 1
                chunks.append(sc)

        # Fallback if no articles were found (e.g. unformatted guide or preamble)
        if not chunks and full_text.strip():
            chunks = self._fallback_paragraph_chunks(full_text, doc_title, pages)

        return chunks

    def _build_page_map(self, pages: List[Dict[str, Any]]) -> (str, List[tuple]):
        """
        Combines pages into one string and tracks (start_char, end_char, page_number).
        """
        full_text = []
        page_offsets = []
        current_offset = 0

        for p in pages:
            text = p.get("text", "")
            start = current_offset
            full_text.append(text)
            current_offset += len(text) + 2  # for "\n\n"
            end = current_offset
            page_offsets.append((start, end, p.get("page_number", 1)))

        return "\n\n".join(full_text), page_offsets

    def _get_page_number_for_offset(self, offset: int, page_offsets: List[tuple]) -> int:
        for start, end, pno in page_offsets:
            if start <= offset <= end:
                return pno
        return page_offsets[0][2] if page_offsets else 1

    def _split_by_articles(self, text: str) -> List[Dict[str, Any]]:
        """
        Finds all MADDE occurrences and slices text between them, tracking preceding BÖLÜM headers.
        """
        article_matches = list(ARTICLE_PATTERN.finditer(text))
        if not article_matches:
            return []

        articles = []
        current_section = None

        # Check preamble before first MADDE
        first_match = article_matches[0]
        if first_match.start() > 0:
            preamble = text[:first_match.start()].strip()
            # Check if there is a section header in preamble
            sec_matches = list(SECTION_PATTERN.finditer(preamble))
            if sec_matches:
                current_section = sec_matches[-1].group(1).strip()
            if len(preamble) > 100:
                articles.append({
                    "article_number": "BAŞLANGIÇ",
                    "article_title": "Genel Hükümler ve Giriş",
                    "section_title": current_section or "Giriş",
                    "body": preamble,
                    "offset": 0,
                })

        for i, match in enumerate(article_matches):
            art_raw = match.group(1).strip()  # e.g. "MADDE 4" or "GEÇİCİ MADDE 1"
            art_num = match.group(2).strip()
            art_title = match.group(3).strip() if match.group(3) else ""

            # Check if there was a SECTION header between previous and this match
            prev_pos = article_matches[i - 1].end() if i > 0 else 0
            intervening = text[prev_pos:match.start()]
            sec_matches = list(SECTION_PATTERN.finditer(intervening))
            if sec_matches:
                current_section = sec_matches[-1].group(1).strip()

            start_body = match.end()
            end_body = (
                article_matches[i + 1].start()
                if i + 1 < len(article_matches)
                else len(text)
            )
            body = text[start_body:end_body].strip()

            norm_art_number = f"MADDE {art_num}"
            if "GEÇİCİ" in art_raw.upper():
                norm_art_number = f"GEÇİCİ MADDE {art_num}"
            elif "EK" in art_raw.upper():
                norm_art_number = f"EK MADDE {art_num}"

            articles.append({
                "article_number": norm_art_number,
                "article_title": art_title.rstrip(".:- "),
                "section_title": current_section,
                "body": body,
                "offset": match.start(),
            })

        return articles

    def _chunk_single_article(
        self,
        art: Dict[str, Any],
        doc_title: str,
        page_offsets: List[tuple],
    ) -> List[RegulationChunkData]:
        art_number = art["article_number"]
        art_title = art["article_title"]
        section_title = art.get("section_title")
        body = art["body"]
        offset = art.get("offset", 0)
        page_num = self._get_page_number_for_offset(offset, page_offsets)

        # Detect sub-clauses like (1), (2), (3)
        fıkra_matches = list(SUB_CLAUSE_PATTERN.finditer(body))

        # If no numbered sub-clauses, or only 1 sub-clause in a short article, keep as single chunk
        if not fıkra_matches or (len(fıkra_matches) == 1 and len(body) <= self.max_chunk_chars):
            sub_c = fıkra_matches[0].group(1) if fıkra_matches else None
            return [self._create_chunk(
                doc_title=doc_title,
                section_title=section_title,
                article_number=art_number,
                article_title=art_title,
                sub_clause=sub_c,
                raw_text=body,
                page_number=page_num,
            )]

        chunks = []
        # If there is introductory text before (1)
        if fıkra_matches[0].start() > 0:
            intro_text = body[:fıkra_matches[0].start()].strip()
            if len(intro_text) >= self.min_chunk_chars:
                chunks.append(self._create_chunk(
                    doc_title=doc_title,
                    section_title=section_title,
                    article_number=art_number,
                    article_title=art_title,
                    sub_clause="Giriş",
                    raw_text=intro_text,
                    page_number=page_num,
                ))

        for idx, fm in enumerate(fıkra_matches):
            fıkra_tag = fm.group(1)  # e.g. "(1)"
            start_pos = fm.start()
            end_pos = (
                fıkra_matches[idx + 1].start()
                if idx + 1 < len(fıkra_matches)
                else len(body)
            )
            fıkra_text = body[start_pos:end_pos].strip()

            chunks.append(self._create_chunk(
                doc_title=doc_title,
                section_title=section_title,
                article_number=art_number,
                article_title=art_title,
                sub_clause=fıkra_tag,
                raw_text=fıkra_text,
                page_number=page_num,
            ))

        return chunks

    def _create_chunk(
        self,
        doc_title: str,
        section_title: Optional[str],
        article_number: Optional[str],
        article_title: Optional[str],
        sub_clause: Optional[str],
        raw_text: str,
        page_number: int,
    ) -> RegulationChunkData:
        # Contextual header prepended for dense & sparse search grounding
        header_parts = [f"[Mevzuat: {doc_title}]"]
        if section_title:
            header_parts.append(f"[{section_title}]")
        if article_number:
            title_suffix = f" – {article_title}" if article_title else ""
            header_parts.append(f"[{article_number}{title_suffix}]")
        if sub_clause:
            header_parts.append(f"[{sub_clause}]")

        header_prefix = " ".join(header_parts)
        content_text = f"{header_prefix}\n{raw_text.strip()}"

        # Extract project codes and numerical limits
        project_codes = sorted(list(set(PROJECT_CODE_PATTERN.findall(content_text.upper()))))
        limits_found = LIMIT_PATTERN.findall(raw_text)

        metadata = {
            "regulation_doc_name": doc_title,
            "section_title": section_title,
            "article_number": article_number,
            "article_title": article_title,
            "sub_clause": sub_clause,
            "page_number": page_number,
            "project_codes": project_codes,
            "detected_limits": limits_found[:5],
            "char_count": len(content_text),
        }

        return RegulationChunkData(
            id=str(uuid4()),
            chunk_index=0,  # assigned sequentially by caller
            section_title=section_title,
            article_number=article_number,
            article_title=article_title,
            sub_clause=sub_clause,
            page_number=page_number,
            content_text=content_text,
            raw_text=raw_text.strip(),
            metadata_json=metadata,
        )

    def _fallback_paragraph_chunks(
        self,
        text: str,
        doc_title: str,
        pages: List[Dict[str, Any]],
    ) -> List[RegulationChunkData]:
        raw_paragraphs = [p.strip() for p in text.split("\n\n") if p.strip()]
        paragraphs: List[str] = []
        for p in raw_paragraphs:
            if len(p) <= self.max_chunk_chars:
                if len(p) >= self.min_chunk_chars or not paragraphs:
                    paragraphs.append(p)
                elif paragraphs:
                    paragraphs[-1] = f"{paragraphs[-1]}\n{p}"
            else:
                # Split large paragraph by lines or sentences
                lines = [l.strip() for l in p.split("\n") if l.strip()]
                curr = []
                curr_len = 0
                for line in lines:
                    if curr_len + len(line) > self.max_chunk_chars and curr:
                        paragraphs.append("\n".join(curr))
                        curr = [line]
                        curr_len = len(line)
                    else:
                        curr.append(line)
                        curr_len += len(line) + 1
                if curr:
                    paragraphs.append("\n".join(curr))

        chunks = []
        for idx, p in enumerate(paragraphs):
            art_num = f"BÖLÜM/KAYIT {idx + 1}"
            chunks.append(RegulationChunkData(
                id=str(uuid4()),
                chunk_index=idx,
                section_title="Genel Mevzuat Metni",
                article_number=art_num,
                article_title="Mevzuat Hükmü",
                sub_clause=None,
                page_number=1,
                content_text=f"[Mevzuat: {doc_title}] [{art_num}]\n{p}",
                raw_text=p,
                metadata_json={
                    "regulation_doc_name": doc_title,
                    "article_number": art_num,
                    "page_number": 1,
                    "char_count": len(p),
                },
            ))
        return chunks
