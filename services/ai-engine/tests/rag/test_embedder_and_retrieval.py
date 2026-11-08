import os
import shutil
import tempfile
from pathlib import Path
from uuid import uuid4
import numpy as np
import pytest
from app.db.local_store import LocalRegulationStore
from app.rag.embedder import LocalEmbedder, turkish_lower
from app.rag.retriever import TurkishLegalRetriever, tokenize_turkish


@pytest.fixture
def fast_embedder():
    os.environ["BAP_FAST_EMBEDDINGS"] = "true"
    return LocalEmbedder(dimension=1024)


def test_deterministic_embedder_dimension_and_norm(fast_embedder):
    v = fast_embedder.embed_query("TP2 tez projesi bütçe üst limiti 125.000 TL")
    assert isinstance(v, np.ndarray)
    assert v.shape == (1024,)
    assert v.dtype == np.float32
    norm = float(np.linalg.norm(v))
    assert abs(norm - 1.0) < 1e-4


def test_deterministic_embedder_semantic_similarity(fast_embedder):
    q = fast_embedder.embed_query("TP2 tez projesi bütçe üst limiti ve tez önerisi")
    doc_rel = fast_embedder.embed_query("MADDE 4 - Tez Projeleri: TP2 tavanı 125.000 TL enstitü kararı")
    doc_irrel = fast_embedder.embed_query("Bahçe tarımı ve seracılık faaliyetleri hakkında genel bilgiler")

    sim_rel = float(np.dot(q, doc_rel))
    sim_irrel = float(np.dot(q, doc_irrel))

    assert sim_rel > sim_irrel
    assert sim_rel > 0.4


def test_bm25_turkish_tokenization():
    tokens = tokenize_turkish("İdari ve ŞEKLİ Uygunluk Denetimi (MADDE 4/1-b) - TP2: 125.000,00 TL")
    assert "idari" in tokens
    assert "sekli" in tokens or "şekli" in tokens
    assert "denetimi" in tokens
    assert "madde" in tokens
    assert "tp2" in tokens
    assert "ve" not in tokens  # stopword filtered


def test_dual_persistence_local_store_save_load(fast_embedder):
    temp_dir = Path(tempfile.mkdtemp())
    try:
        store = LocalRegulationStore(data_dir=temp_dir)
        doc_id = str(uuid4())
        doc_data = {
            "id": doc_id,
            "slug": "test-yonerge",
            "title_tr": "Test Yönergesi",
            "doc_type": "YONERGE",
            "storage_path": "/tmp/test.md",
        }
        chunks = [
            {
                "id": str(uuid4()),
                "regulation_doc_id": doc_id,
                "chunk_index": 0,
                "section_title": "Bölüm 1",
                "article_number": "MADDE 1",
                "sub_clause": "(1)",
                "content_text": "Madde 1 metni",
                "metadata_json": {"project_codes": ["TP1"]},
            }
        ]
        emb = fast_embedder.embed_texts(["Madde 1 metni"])
        store.replace_document_chunks(doc_data, chunks, emb)

        # Reload in a new store instance pointing to same directory
        store2 = LocalRegulationStore(data_dir=temp_dir)
        assert store2.count_documents() >= 1
        assert store2.count_chunks() == 1
        retrieved_doc = store2.get_document_by_slug("test-yonerge")
        assert retrieved_doc is not None
        assert retrieved_doc["title_tr"] == "Test Yönergesi"

        all_chunks = store2.get_all_chunks()
        assert len(all_chunks) == 1
        assert all_chunks[0]["article_number"] == "MADDE 1"
        assert all_chunks[0]["embedding"].shape == (1024,)
    finally:
        shutil.rmtree(temp_dir, ignore_errors=True)


def test_retriever_auto_bootstrap_and_rrf_ranking(fast_embedder):
    retriever = TurkishLegalRetriever(embedder=fast_embedder)
    retriever.ensure_bootstrapped()

    # Query for TP2 limit
    results = retriever.retrieve("TP2 tez projesi 2026 yılı bütçe tavanı ve üst limiti", top_k=3)
    assert len(results) > 0
    top = results[0]

    # Top result should be MADDE 4 which defines TP2
    assert top["regulation_article_ref"] == "MADDE 4"
    assert "TP2" in top["content_text"]
    assert top["score"] > 0
    assert len(top["verbatim_quote"]) > 20
