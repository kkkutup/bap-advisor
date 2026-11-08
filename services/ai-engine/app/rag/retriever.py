import logging
import re
from pathlib import Path
from typing import Any, Dict, List, Optional
from uuid import uuid4
import numpy as np
from rank_bm25 import BM25Okapi
from app.db.local_store import resolve_regulations_dir
from app.db.store import DualModeRegulationStore, get_regulation_store
from app.rag.chunker import TurkishLegalChunker
from app.rag.embedder import LocalEmbedder, get_embedder, turkish_lower
from app.rag.pdf_reader import read_document_pages

logger = logging.getLogger(__name__)

STOPWORDS = {
    "ve", "veya", "ile", "için", "bir", "bu", "şu", "o", "de", "da", "ki",
    "ise", "ancak", "fakat", "lakin", "gibi", "kadar", "olan", "olarak", "tarafından",
    "üzerine", "göre", "tüm", "her", "bazı", "hiçbir", "nın", "nin", "nun", "nün"
}


def tokenize_turkish(text: str) -> List[str]:
    """
    Turkish legal text tokenizer.
    Lowercases using turkish_lower, extracts alphanumeric tokens, and keeps numbers and codes.
    """
    norm = turkish_lower(text)
    raw_tokens = re.findall(r'[a-zçğıöşü0-9]+', norm)
    return [t for t in raw_tokens if t not in STOPWORDS]


class TurkishLegalRetriever:
    """
    Turkish Legal Hybrid Retriever with RRF (Reciprocal Rank Fusion, k=60).
    Combines:
    1. Dense Vector Cosine Search (BAAI/bge-m3 or local deterministic embedder)
    2. BM25Okapi keyword search with Turkish lowercasing and tokenization
    3. Metadata Boosting for exact MADDE numbers, project codes (TP1..AP9), and keywords
    """

    def __init__(
        self,
        store: Optional[DualModeRegulationStore] = None,
        embedder: Optional[LocalEmbedder] = None,
        rrf_k: int = 60,
    ):
        self.store = store or get_regulation_store()
        self.embedder = embedder or get_embedder()
        self.rrf_k = rrf_k
        self.chunker = TurkishLegalChunker()
        self._bm25: Optional[BM25Okapi] = None
        self._cached_chunks: List[Dict[str, Any]] = []
        self._cached_embeddings: Optional[np.ndarray] = None
        self._bootstrapped = False

    def ensure_bootstrapped(self) -> None:
        """
        Auto-bootstrap: If store is empty, automatically indexes seed regulations from data/regulations.
        """
        if self._bootstrapped:
            return

        if self.store.count_chunks() > 0:
            self._bootstrapped = True
            return

        logger.info("Regulation store is empty; auto-bootstrapping standard MEU regulations...")
        reg_dir = resolve_regulations_dir()

        seed_files = [
            ("meu_uygulama_esaslari_seed.md", "Mersin Üniversitesi BAP Uygulama Usul ve Esasları", "ESASLAR"),
            ("8-H-Cetveli_2026Butcesi.pdf", "2026 Yılı H Cetveli (Yurtiçi Harcırah)", "CETVEL"),
            ("23.01.2025_Tarihli_MEU_BAP_Yonergesi.pdf", "23.01.2025 Tarihli MEÜ BAP Yönergesi", "YONERGE"),
            ("Ack_Eriim_Open_Access_Yayn_Basm_Ucreti_Destei_Uygulama_Usul_ve_Esaslar.pdf", "MEÜ Açık Erişim Yayın Desteği Esasları", "ESASLAR"),
            ("Satn_Alma_ve_hale_Yonetmelii.pdf", "Satın Alma ve İhale Yönetmeliği", "YONETMELIK"),
        ]

        indexed_any = False
        for filename, title_tr, doc_type in seed_files:
            file_path = reg_dir / filename
            if file_path.exists():
                try:
                    self.ingest_file(
                        file_path=str(file_path),
                        title_tr=title_tr,
                        doc_type=doc_type,
                        slug=filename.lower().replace(".", "-").replace("_", "-"),
                    )
                    indexed_any = True
                except Exception as e:
                    logger.warning(f"Could not auto-ingest {filename}: {e}")

        self._bootstrapped = True
        self.invalidate_cache()
        logger.info(f"Auto-bootstrap complete. Total chunks indexed: {self.store.count_chunks()}")

    def ingest_file(
        self,
        file_path: str,
        title_tr: str,
        doc_type: str = "YONERGE",
        slug: Optional[str] = None,
        doc_id: Optional[str] = None,
        version_label: Optional[str] = None,
    ) -> Dict[str, Any]:
        """
        Parses, chunks, embeds, and stores a regulation file.
        """
        path = Path(file_path)
        actual_slug = slug or path.stem.lower().replace(" ", "-").replace("_", "-")
        actual_doc_id = doc_id or str(uuid4())

        pages = read_document_pages(str(file_path))
        chunks = self.chunker.chunk_pages(pages, doc_title=title_tr, doc_type=doc_type)

        if not chunks:
            logger.warning(f"No chunks extracted from {file_path}")
            return {
                "regulation_doc_id": actual_doc_id,
                "slug": actual_slug,
                "total_chunks_created": 0,
                "articles_indexed": [],
            }

        texts_to_embed = [c.content_text for c in chunks]
        embeddings = self.embedder.embed_texts(texts_to_embed)

        doc_data = {
            "id": actual_doc_id,
            "slug": actual_slug,
            "title_tr": title_tr,
            "doc_type": doc_type,
            "version_label": version_label or "2026.1",
            "storage_path": str(file_path),
            "sha256_hash": None,
            "is_active": True,
        }

        chunks_data = []
        articles_indexed = set()
        for idx, ch in enumerate(chunks):
            if ch.article_number:
                articles_indexed.add(ch.article_number)
            chunks_data.append({
                "id": ch.id,
                "regulation_doc_id": actual_doc_id,
                "chunk_index": idx,
                "section_title": ch.section_title,
                "article_number": ch.article_number,
                "sub_clause": ch.sub_clause,
                "content_text": ch.content_text,
                "metadata_json": ch.metadata_json,
            })

        self.store.replace_document_chunks(doc_data, chunks_data, embeddings)
        self.invalidate_cache()

        return {
            "regulation_doc_id": actual_doc_id,
            "slug": actual_slug,
            "total_chunks_created": len(chunks_data),
            "articles_indexed": sorted(list(articles_indexed)),
        }

    def invalidate_cache(self) -> None:
        self._cached_chunks = []
        self._cached_embeddings = None
        self._bm25 = None

    def _load_corpus_and_indexes(self) -> None:
        if self._cached_chunks and self._bm25 is not None:
            return

        self.ensure_bootstrapped()
        self._cached_chunks = self.store.get_all_chunks()

        if not self._cached_chunks:
            self._cached_embeddings = np.empty((0, self.embedder.dimension), dtype=np.float32)
            self._bm25 = None
            return

        # Build embeddings array
        emb_list = [ch["embedding"] for ch in self._cached_chunks]
        self._cached_embeddings = np.vstack(emb_list).astype(np.float32)

        # Build BM25 index with Turkish tokenization
        tokenized_corpus = [
            tokenize_turkish(ch["content_text"]) for ch in self._cached_chunks
        ]
        self._bm25 = BM25Okapi(tokenized_corpus)

    def retrieve(
        self,
        query: str,
        top_k: int = 5,
        project_code: Optional[str] = None,
        submission_type: Optional[str] = None,
    ) -> List[Dict[str, Any]]:
        """
        Executes Turkish Legal Hybrid RRF Retrieval.
        Returns top_k chunks sorted by combined score.
        """
        self._load_corpus_and_indexes()

        if not self._cached_chunks or self._bm25 is None or self._cached_embeddings is None:
            return []

        N = len(self._cached_chunks)
        top_k = min(top_k, N)

        # 1. Dense Cosine Retrieval
        query_vec = self.embedder.embed_query(query)
        # Cosine similarity (both are normalized)
        dense_scores = np.dot(self._cached_embeddings, query_vec)
        dense_ranking = np.argsort(-dense_scores)  # indices from highest to lowest score
        dense_rank_map = {idx: rank for rank, idx in enumerate(dense_ranking)}

        # 2. BM25 Sparse Keyword Retrieval
        tokenized_query = tokenize_turkish(query)
        if tokenized_query:
            bm25_scores = self._bm25.get_scores(tokenized_query)
        else:
            bm25_scores = np.zeros(N, dtype=np.float32)
        bm25_ranking = np.argsort(-bm25_scores)
        bm25_rank_map = {idx: rank for rank, idx in enumerate(bm25_ranking)}

        # 3. Metadata and Exact Pattern Boosting
        norm_query = turkish_lower(query)
        madde_match = re.search(r'madde\s*(\d+|[a-zçğıöşü0-9]+)', norm_query)
        target_madde_num = f"MADDE {madde_match.group(1).upper()}" if madde_match else None

        detected_codes = set(re.findall(r'\b(TP[1-9]|AP[1-9]|WOS_Q[1-4]|Q[1-4])\b', query.upper()))
        if project_code:
            detected_codes.add(project_code.upper())

        # 4. RRF Score Aggregation
        rrf_scores = []
        for i, ch in enumerate(self._cached_chunks):
            r_dense = dense_rank_map.get(i, N)
            r_bm25 = bm25_rank_map.get(i, N)

            # RRF base formula (k=60)
            score = (1.0 / (self.rrf_k + r_dense)) + (1.0 / (self.rrf_k + r_bm25))

            # Metadata exact boost
            art_num = ch.get("article_number") or ""
            if target_madde_num and target_madde_num in art_num.upper():
                score += 0.05  # strong exact article boost

            # Project code boost
            ch_codes = set(ch.get("metadata_json", {}).get("project_codes", []))
            if detected_codes and (ch_codes.intersection(detected_codes) or any(c in ch["content_text"].upper() for c in detected_codes)):
                score += 0.03

            # Keyword topic boost
            for kw in ("ıslak imza", "ek bütçe", "ek süre", "etik kurul", "proforma", "h cetveli", "teknik şartname", "36 ay"):
                if kw in norm_query and kw in ch["content_text"].lower():
                    score += 0.015

            rrf_scores.append((i, score))

        # Sort by RRF score descending
        rrf_scores.sort(key=lambda x: x[1], reverse=True)

        results = []
        for idx, score in rrf_scores[:top_k]:
            ch = self._cached_chunks[idx]
            content = ch["content_text"]
            raw_text = content.split("\n", 1)[1] if "\n" in content else content

            # Extract first 2-3 sentences as verbatim quote
            sentences = [s.strip() for s in re.split(r'[.!?]\s+', raw_text) if s.strip()]
            verbatim_quote = ". ".join(sentences[:2]) + "." if sentences else raw_text[:200]

            results.append({
                "id": ch["id"],
                "regulation_doc_name": ch.get("metadata_json", {}).get("regulation_doc_name") or ch.get("section_title") or "Mersin Üniversitesi BAP Mevzuatı",
                "regulation_article_ref": ch.get("article_number") or "Genel Hüküm",
                "section_title": ch.get("section_title"),
                "sub_clause": ch.get("sub_clause"),
                "content_text": content,
                "raw_text": raw_text,
                "verbatim_quote": verbatim_quote,
                "score": float(round(score * 100, 3)),
                "metadata": ch.get("metadata_json", {}),
            })

        return results


_global_retriever: Optional[TurkishLegalRetriever] = None


def get_retriever() -> TurkishLegalRetriever:
    global _global_retriever
    if _global_retriever is None:
        _global_retriever = TurkishLegalRetriever()
    return _global_retriever
