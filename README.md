# Mersin Üniversitesi BAP Mevzuat Uygunluk ve İdari Ön İnceleme Sistemi (`bap-advisor`)

[![Contract First](https://img.shields.io/badge/API_Contract-OpenAPI_3.1-blue.svg)](file:///Users/kutup/Documents/bap-advisor/contracts)
[![Java 17](https://img.shields.io/badge/Backend-Java_17_Spring_Boot_3.4-brightgreen.svg)](file:///Users/kutup/Documents/bap-advisor/services/core-api)
[![Python 3.12](https://img.shields.io/badge/AI_Engine-FastAPI_Python_3.12-yellowgreen.svg)](file:///Users/kutup/Documents/bap-advisor/services/ai-engine)
[![React 18](https://img.shields.io/badge/Frontend-React_18_TypeScript_Vite-61dafb.svg)](file:///Users/kutup/Documents/bap-advisor/apps/web-dashboard)
[![Test Suite](https://img.shields.io/badge/Tests-100%25_Passing-success.svg)](file:///Users/kutup/Documents/bap-advisor)

Mersin Üniversitesi Bilimsel Araştırma Projeleri Koordinasyon Birimi (MEÜ BAP) için geliştirilmiş, yüksek hassasiyetli mevzuat uygunluk denetimi, 3 katmanlı hibrit OCR, mevzuata dayalı RAG (Retrieval-Augmented Generation), otomatik BAP iade/onay tutanağı üretimi ve versiyon karşılaştırma (diff) platformudur.

---

## 1. Kesin Alan Kuralı ve Güvenlik Sınırları (Domain Guardrail)

> [!IMPORTANT]
> **BİLİMSEL VE AKADEMİK HAKEMLİK YASAĞI**
> Bu sistem **asla ve hiçbir koşulda** projelerin akademik özgünlüğü, araştırma kalitesi, hipotez geçerliliği veya metodoloji yeterliliği hakkında hüküm vermez.
> 
> - Sistemin yetki alanı **yalnızca idari, şekli, belgesel ve mali** MEÜ BAP mevzuat gereksinimlerini denetlemekle sınırlıdır.
> - Değerlendirme gerektiren akademik/içeriksel konular otomatik olarak `AMBIGUOUS_COMMISSION` severity seviyesiyle işaretlenir ve `COMMISSION_REVIEW` statüsünde MEÜ BAP Komisyonuna (Profesörler Kurulu) havale edilir.
> - RAG ve kural motoru tarafından üretilen istisnasız **her bulgu**, resmi mevzuat adı (`regulation_doc_name`) ve ilgili madde numarasını (`regulation_article_ref`) içermek zorundadır.

---

## 2. Sistem Mimarisi

Sistem, sözleşme öncelikli (Contract-First) prensiple tasarlanmış 3 ana bileşenden oluşur:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       React 18 Web Dashboard (:5173)                        │
│   (Vite + Tailwind CSS + TanStack Query + Split-Screen Review + Chatbot)    │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ Live REST / OpenAPI (Snake_Case)
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     Spring Boot 3 Core API (:8080)                          │
│  - JWT RBAC & Oturum Yönetimi (ADMIN, COORDINATOR, OFFICER, COMMISSION)     │
│  - Deterministik Kural Motoru (Tavan Bütçeler, Süreler, Islak İmza, Ekler)  │
│  - MEÜ BAP Resmi Tutanak İhracı (PDF & DOCX Şablon Üretimi)                │
│  - BAP2 Entegrasyon Webhook Katmanı                                         │
│  - PostgreSQL 16 (core şeması) / H2 Local Memory DB                         │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ Internal REST (X-Internal-Service-Token)
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     Python FastAPI AI Engine (:8001)                        │
│  - 3-Katmanlı Hibrit OCR (Native Digital PDF -> RapidOCR -> Vision LLM)     │
│  - Lokal BAAI/bge-m3 Embedding & Hukuki Madde Segmentasyonu                 │
│  - Mevzuat RAG & Etkileşimli Soru-Cevap (Citations Destekli)                │
│  - Çift Modlu LLM Ağ Geçidi (NanoGPT API test <-> %100 Lokal Ollama/vLLM)  │
│  - Fiziksel Airgap Guard (Üretimde dış ağa veri çıkışı engeli)             │
│  - PostgreSQL 16 pgvector (ai_rag şeması)                                   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Veritabanı İzolasyonu
- **Tek PostgreSQL 16 Veritabanı**: Spring Boot Flyway yalnızca `core` şemasına yazar. Python FastAPI Alembic yalnızca `ai_rag` şemasına yazar.
- Servisler asla birbirlerinin veritabanı tablolarına doğrudan SQL ile erişmez; iç HTTP API üzerinden haberleşir.

---

## 3. Hızlı Başlangıç (Geliştirici Makinesinde Docker'sız)

macOS üzerinde yerel geliştirme için Docker kurulu olmasına gerek yoktur (`SPRING_PROFILES_ACTIVE=local` ile Spring Boot H2 in-memory veritabanını kullanır).

### Gereksinimler
- **Java 17**: Homebrew OpenJDK 17
- **Python 3.12**: `/opt/homebrew/bin/uv`
- **Node.js**: `v20+` / `npm`

### Ortam Kurulumu ve Başlatma

1. **Ortam değişkenlerini hazırlayın:**
   ```bash
   cp .env.example .env
   # Geliştirme/test aşamasında harici LLM için NANOGPT_API_KEY değerini .env dosyasına girebilirsiniz.
   ```

2. **Üç servisi ayrı terminallerde başlatın:**

   - **Terminal 1 — AI Engine (:8001):**
     ```bash
     make run-ai
     # Alternatif:
     # cd services/ai-engine && uv run uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload
     ```

   - **Terminal 2 — Core API (:8080):**
     ```bash
     make run-core
     # Alternatif (Local H2 in-memory profil ile):
     # cd services/core-api && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
     ```

   - **Terminal 3 — Web Dashboard (:5173):**
     ```bash
     make run-web
     # Alternatif:
     # cd apps/web-dashboard && npm run dev
     ```

3. **Tarayıcıdan açın:**
   [http://localhost:5173](http://localhost:5173)

---

## 4. Docker Compose ile Başlatma (Üretim Ortamı)

Tüm yığını (PostgreSQL 16 + pgvector, Spring Boot Core API, FastAPI AI Engine ve Nginx Web Dashboard) tek komutla ayağa kaldırmak için:

```bash
docker compose up --build -d
```

Servis Durumları:
- Web Dashboard: `http://localhost:3000` veya `http://localhost:80`
- Core API: `http://localhost:8080`
- AI Engine: `http://localhost:8001`
- PostgreSQL: `localhost:5432` (`bap_advisor`)

---

## 5. Canlı Uçtan Uca Entegrasyon Testi (E2E Smoke Test)

Sistemin tüm servislerinin canlı HTTP çağrılarıyla birbirine bağlandığını ve gerçek MEÜ başvuru dosyalarıyla çalıştığını doğrulamak için:

```bash
make verify-e2e
```

Bu komut Python ile yazılmış `scripts/verify_e2e_live.py` betiğini çalıştırır ve şu 8 adımı test eder:
1. **Kimlik Doğrulama & JWT RBAC:** Koordinatör ve Memur kullanıcılarıyla giriş, `/auth/me` profil teyidi.
2. **Mevzuat İndeksleme & 2026 MEÜ Limitleri:** 8 resmi MEÜ mevzuat metninin RAG motoruna indekslenmesi ve 2026 yılı bütçe tavanlarının (TP1: 30k .. AP4: 800k, Q1: 120k) doğrulanması.
3. **Gerçek TP2 Başvurusu (v1 - Tavan Aşımı):** `01_tp2_basvuru_formu.pdf` ve `02_butce_tablosu.xlsx` dosyalarının hibrit OCR ile taranması, 142.000,00 TL bütçenin çıkarılması, `RULE_BUDGET_CAP` (125k limit aşımı) ve `MISSING_DOCUMENT` (Tez Önerisi) ihlallerinin tespiti ve Türkçe iade taslağının oluşturulması.
4. **Düzeltilmiş Başvuru (v2) & Versiyon Karşılaştırma (Diff):** Bütçenin 118.500,00 TL'ye düşürülüp tez belgesinin eklenmesi, diff çağrısıyla `-23.500,00 TL` farkın ve çözülen maddelerin doğrulanması.
5. **Taranmış Resim PDF (Tier-2 RapidOCR):** İmzası bulunmayan taranmış dilekçede `wet_signature_present=false` tespiti ve `RULE_WET_SIGNATURE` ihlalinin üretilmesi.
6. **Mevzuata Dayalı Etkileşimli Soru-Cevap (Q&A):** Proje bağlamında sorulan soruya MEÜ BAP Yönergesi ilgili madde alıntılarıyla Türkçe yanıt verilmesi.
7. **Resmi MEÜ BAP Ön İnceleme Tutanağı İhracı:** `%PDF-` başlıklı PDF ve `PK` zip başlıklı DOCX resmi tutanak belgelerinin indirilmesi.
8. **Yönetici LLM Sağlayıcı Değişimi & Airgap Kilidi:** Çalışma anında canlı olarak NanoGPT'den `%100 Lokal Ollama`ya (`AIRGAPPED_MODE=true`) geçilmesi ve geri dönülmesi.

---

## 6. Varsayılan Tanımlı Kullanıcı Hesapları

Veritabanı başlatıldığında test ve kullanım için aşağıdaki roller otomatik oluşturulur:

| E-Posta | Şifre | Rol | Yetkiler |
| :--- | :--- | :--- | :--- |
| `admin@mersin.edu.tr` | `Admin123!` | `ADMIN` | Sistem ayarları, kullanıcı yönetimi, LLM motoru & airgap yapılandırması |
| `koordinator@mersin.edu.tr` | `Koord123!` | `COORDINATOR` | Tüm başvuruları inceleme, tutanak onay/ihraç, mevzuat & tavan yönetimi |
| `memur@mersin.edu.tr` | `Memur123!` | `OFFICER` | Başvuru yükleme, OCR çalıştırma, mevzuat ön inceleme, iade taslağı düzenleme |
| `kurul@mersin.edu.tr` | `Kurul123!` | `COMMISSION_VIEWER` | Salt okunur kurul ekranı, hakemlik öncesi idari tutanak izleme |

---

## 7. Çift Modlu LLM ve %100 Yerel (Airgapped) Üretim Modu

`bap-advisor`, üniversite içi veri gizliliğini korumak amacıyla iki aşamalı çalışmayı destekler:

### Aşama 1: Geliştirme & Test (NanoGPT API)
`.env` dosyasında:
```env
LLM_PROVIDER=nanogpt
NANOGPT_API_KEY=your_nanogpt_api_key_here
NANOGPT_MODEL=chatgpt-4o-latest
AIRGAPPED_MODE=false
```
*Not: Embedding işlemi (`BAAI/bge-m3`) her zaman sunucuda yerel olarak çalıştırılır; harici API'ye metin embedding verisi gönderilmez.*

### Aşama 2: Üretim / %100 Yerel & Kapalı Ağ (Ollama / vLLM)
Üniversite sunucusunda dış internet bağlantısı tamamen kapatıldığında:
```env
LLM_PROVIDER=ollama
OLLAMA_BASE_URL=http://localhost:11434/v1
OLLAMA_MODEL=qwen2.5:14b-instruct
AIRGAPPED_MODE=true
```
- `AIRGAPPED_MODE=true` etkinleştirildiğinde `app.llm.airgap_guard.AirgapGuard` devreye girer ve harici adreslere yapılacak tüm ağ çıkışlarını `AirgapViolationError` ile fiziksel olarak engeller.
- Bu yapılandırma Dashboard üzerindeki **Sağlayıcı Yapılandırması** sayfasından (`/admin/providers`) çalışma anında da değiştirilebilir.

---

## 8. MEÜ BAP 2026 Destek Tavanları ve Mevzuat Yönetimi

### 2026 Yılı Tavan Bütçeleri (`contracts/seed-data/meu_2026_limits.json`)
Sistemde yer alan bazı tavan tutarlar:
- **TP1 (Yüksek Lisans Tez Projesi):** 30.000,00 TL
- **TP2 (Doktora Tez Projesi):** 125.000,00 TL
- **TP3 (Tıpta/Diş Hekimliğinde Uzmanlık):** 210.000,00 TL
- **AP1 (Kapsamlı Araştırma Projesi):** 250.000,00 TL
- **AP2 (Öncelikli Alan Araştırma Projesi):** 300.000,00 TL
- **AP3 (Güdümlü Araştırma Projesi):** 500.000,00 TL
- **AP4 (Çok Disiplinli Stratejik Araştırma):** 800.000,00 TL
- **WOS Q1 Top %10 Yayın Teşvik Desteği:** 120.000,00 TL

### Yeni Mevzuat ve Kılavuz Yükleme
Mersin Üniversitesi senatosunca yeni bir mevzuat, yönerge veya uygulama kılavuzu yayınlandığında:
1. **Web Dashboard Üzerinden:** Koordinatör hesabıyla `Mevzuat Belgeleri` menüsünden yeni PDF/Markdown belgesini yükleyin.
2. **Komut Satırından:**
   ```bash
   make fetch-regulations
   ```
   Belge otomatik olarak `data/regulations/` dizinine kaydedilir, `chunker.py` tarafından hukuki maddelerine (`MADDE 1`, `MADDE 2`...) bölünür ve `bge-m3` ile vektör indeksine alınır.

---

## 9. Test Paketleri ve Kalite Güvencesi

Tüm test paketlerini tek seferde yürütmek için:

```bash
make test-all
```

- **`make test-core`**: Spring Boot Controller, Service, Rule Engine, Export ve Auth testleri (86 test - %100 Başarılı).
- **`make test-ai`**: FastAPI Hybrid OCR, PDF/XLSX Parser, RAG, Airgap Guard ve Gateway testleri (65 test - %100 Başarılı).
- **`make test-web`**: TypeScript derlemesi (`tsc`) ve Vite üretim derlemesi (0 hata).
- **`make validate-contracts`**: OpenAPI 3.1 YAML dosyaları ve JSON şemalarının doğrulanması.
