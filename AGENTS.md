# AGENTS.md — `bap-advisor` Çoklu Ajan Çalışma Kuralları

Bu çalışma alanında görev alan her ajan, herhangi bir dosya oluşturmadan veya değiştirmeden önce **`BUILD_PLAN.md`** dosyasını okumak zorundadır.

## 1. Altın Kurallar (Kesin Sınırlar)

1. **Dosya Sahipliği İzolasyonu:** Yalnızca size atanan ajan rolünün (`Ajan 0` .. `Ajan 5`) dizininde değişiklik yapın (`BUILD_PLAN.md` Bölüm 3.1). Başka bir ajanın servisinde değişiklik gerekiyorsa doğrudan dosyayı düzenlemeyin; önce `contracts/` altındaki şemayı kontrol edin veya orkestratöre bildirin.
2. **Kontrat Önceliği (Contract-First):**
   - `web-dashboard` $\leftrightarrow$ `core-api` iletişimi `contracts/openapi-core-public.yaml` şemasına birebir uymalıdır.
   - `core-api` $\leftrightarrow$ `ai-engine` iletişimi `contracts/openapi-ai-internal.yaml` ve `contracts/schemas/` JSON şemalarına birebir uymalıdır.
3. **Veritabanı Şema İzolasyonu (Tek PostgreSQL 16 + `pgvector`):**
   - Spring Boot (`Flyway`) yalnızca **`core`** şemasına yazar.
   - Python FastAPI (`Alembic`) yalnızca **`ai_rag`** şemasına yazar.
   - Servisler birbirlerinin veritabanı tablolarına doğrudan SQL ile yazamaz; iç API üzerinden konuşur.
4. **Alan Kuralı (Bilimsel Hakemlik Yasağı):**
   - Sistemin ürettiği tüm promptlar, değerlendirme kuralları ve UI metinleri **yalnızca idari, şekli ve mali BAP mevzuat uygunluğunu** denetler.
   - Projenin bilimsel kalitesi, hipotezi veya akademik özgünlüğü hakkında hüküm verilmez; takdir gerektiren durumlar `AMBIGUOUS_COMMISSION` / `COMMISSION_REVIEW` olarak Profesör Kuruluna yönlendirilir.
   - RAG çıktılarındaki her kural ihlali veya uygunluk bulgusu mutlaka somut mevzuat belgesi adı ve madde numarası (`regulation_article_ref`) içermelidir.
5. **Çift Modlu LLM ve Lokal Embedding Zorunluluğu:**
   - Embedding (`BAAI/bge-m3`) her zaman lokal çalışır; dış API'ye bağımlı embedding kodu yazmayın.
   - LLM çağrıları doğrudan sağlayıcı SDK'sına gömülmez; `OpenAI-compatible` soyutlama katmanı üzerinden `NANOGPT` (test) ve `OLLAMA` / `VLLM` (%100 lokal üretim + `AIRGAPPED_MODE=true`) desteklenecek şekilde yazılır.

## 2. Geliştirme Makinesi (macOS) Ortam Bilgileri

Komut çalıştırırken sistemde kurulu olan aşağıdaki araç yollarını dikkate alın:

- **Java 17 (`core-api`):**
  ```bash
  export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
  export PATH="$JAVA_HOME/bin:$PATH"
  ```
  Maven global kurulu olmadığı için her zaman `services/core-api/mvnw` (Maven Wrapper) kullanın.
- **Python 3.12 (`ai-engine`):**
  Sistem varsayılan `python3` sürümü `3.15` olduğundan kütüphane tekerlek (wheel) uyumsuzluğu yaşamamak için her zaman `/opt/homebrew/bin/uv` ile **Python 3.12** kullanın:
  ```bash
  cd services/ai-engine
  uv venv --python 3.12
  uv sync
  ```
- **Node.js (`web-dashboard`):**
  `/opt/homebrew/bin/node` (`v26+`) ve `npm` doğrudan kullanılabilir.
- **Gizli Anahtarlar:**
  `NANOGPT_API_KEY`, `JWT_SECRET` veya `INTERNAL_SERVICE_TOKEN` değerlerini asla kaynak koda yazmayın; `.env` üzerinden okuyun.
