-- 2026 MEÜ BAP Proje Üst Limitleri, Açık Erişim Tavanları, Süre Sınırları ve İhale Eşikleri

INSERT INTO core.project_limit_configs (id, fiscal_year, rule_category, code, title_tr, max_amount_tl, max_duration_months, currency, vat_included, conditions_json, effective_from, is_active)
VALUES
    -- Proje Bütçe Limitleri
    ('a0000001-0000-0000-0000-000000000001', 2026, 'BUDGET', 'TP1', 'Lisans Öğrencisi Araştırma Projesi', 30000.00, 12, 'TRY', TRUE, '{"target_applicant":"UNDERGRADUATE_STUDENT_ADVISOR","description":"Lisans öğrencisi araştırma projesi üst limiti 30.000 TLdir."}', '2026-01-01', TRUE),
    ('a0000001-0000-0000-0000-000000000002', 2026, 'BUDGET', 'TP2', 'Yüksek Lisans Tez Projeleri', 125000.00, 24, 'TRY', TRUE, '{"target_applicant":"MASTER_STUDENT_ADVISOR","requires_thesis_approval":true,"description":"Enstitü tez önerisi kabul belgesi ve tez jürisi kararı zorunludur."}', '2026-01-01', TRUE),
    ('a0000001-0000-0000-0000-000000000003', 2026, 'BUDGET', 'TP3', 'Doktora / Tıpta-Diş-Eczacılık-Veteriner Uzmanlık / Sanatta Yeterlik', 210000.00, 36, 'TRY', TRUE, '{"target_applicant":"DOCTORATE_STUDENT_ADVISOR","requires_thesis_approval":true,"description":"Enstitü tez konusu kabul kararı ve tez izleme komitesi onayı zorunludur."}', '2026-01-01', TRUE),
    ('a0000001-0000-0000-0000-000000000004', 2026, 'BUDGET', 'AP1', 'Kariyer Başlangıç Araştırma Projeleri', 250000.00, 24, 'TRY', TRUE, '{"target_applicant":"EARLY_CAREER_RESEARCHER","description":"Doktorasını veya eşdeğer uzmanlığını son 5 yıl içinde almış araştırmacılar başvurabilir."}', '2026-01-01', TRUE),
    ('a0000001-0000-0000-0000-000000000005', 2026, 'BUDGET', 'AP2', 'Bireysel Araştırma Projeleri', 300000.00, 36, 'TRY', TRUE, '{"target_applicant":"FACULTY_MEMBER","description":"Mersin Üniversitesi öğretim üyeleri başvurabilir. Azami süre ek süreler dâhil 36 ayı geçemez."}', '2026-01-01', TRUE),
    ('a0000001-0000-0000-0000-000000000006', 2026, 'BUDGET', 'AP3', 'Öncelikli Alan Araştırma Projeleri', 500000.00, 36, 'TRY', TRUE, '{"target_applicant":"FACULTY_MEMBER","description":"YÖK ve Üniversite Senatosu tarafından belirlenen öncelikli araştırma alanları ile uyumlu olmalıdır."}', '2026-01-01', TRUE),
    ('a0000001-0000-0000-0000-000000000007', 2026, 'BUDGET', 'AP4', 'Kapsamlı Araştırma Projeleri', 800000.00, 36, 'TRY', TRUE, '{"target_applicant":"FACULTY_MEMBER","description":"Disiplinlerarası nitelikte, üniversite araştırma altyapısını geliştirmeye yönelik kapsamlı projelerdir."}', '2026-01-01', TRUE),
    ('a0000001-0000-0000-0000-000000000008', 2026, 'BUDGET', 'AP7', 'Hızlı Destek Araştırma Projeleri', 100000.00, 12, 'TRY', TRUE, '{"target_applicant":"FACULTY_MEMBER","description":"Acil veri toplama veya kısa süreli araştırma faaliyetleri için hızlı değerlendirilen desteklerdir."}', '2026-01-01', TRUE),
    ('a0000001-0000-0000-0000-000000000009', 2026, 'BUDGET', 'AP9', 'Ulusal / Uluslararası Yarışmalara Katılım', 5000.00, 6, 'TRY', TRUE, '{"target_applicant":"STUDENT_AND_ADVISOR","description":"TEKNOFEST ve benzeri yarışmalara kabul veya katılım belgesi ibrazı zorunludur."}', '2026-01-01', TRUE),

    -- Açık Erişim Yayın Destek Tavanları
    ('a0000002-0000-0000-0000-000000000001', 2026, 'OPEN_ACCESS', 'WOS_Q1_TOP10', 'WoS Q1 (%10luk Dilim) Araştırma Makalesi Açık Erişim Desteği', 120000.00, NULL, 'TRY', TRUE, '{"journal_tier":"Q1_TOP10","article_type":"RESEARCH_ARTICLE","meu_affiliation_required":true}', '2026-01-01', TRUE),
    ('a0000002-0000-0000-0000-000000000002', 2026, 'OPEN_ACCESS', 'WOS_Q1_OTHER', 'WoS Q1 (Diğer) Araştırma Makalesi Açık Erişim Desteği', 80000.00, NULL, 'TRY', TRUE, '{"journal_tier":"Q1_OTHER","article_type":"RESEARCH_ARTICLE","meu_affiliation_required":true}', '2026-01-01', TRUE),
    ('a0000002-0000-0000-0000-000000000003', 2026, 'OPEN_ACCESS', 'WOS_Q2', 'WoS Q2 Araştırma Makalesi Açık Erişim Desteği', 60000.00, NULL, 'TRY', TRUE, '{"journal_tier":"Q2","article_type":"RESEARCH_ARTICLE","meu_affiliation_required":true}', '2026-01-01', TRUE),

    -- Süre Kuralları
    ('a0000003-0000-0000-0000-000000000001', 2026, 'DURATION', 'MAX_MONTHS_NON_THESIS', 'Tez Dışı Projeler Azami Süresi', 0.00, 36, 'TRY', TRUE, '{"max_extension_percent":50,"description":"Ek süreler dahil tez dışı projelerde toplam süre 36 ayı geçemez."}', '2026-01-01', TRUE),

    -- 2026 Yılı H Cetveli Gündelik Limitleri
    ('a0000004-0000-0000-0000-000000000001', 2026, 'BUDGET', 'PER_DIEM_PROF_8000', 'H Cetveli Gündelik: Ek Gösterge 8000 ve Üzeri (Prof. Dr.)', 750.00, NULL, 'TRY', TRUE, '{"daily_rate_tl":750.00,"max_lodging_rate_tl":1125.00}', '2026-01-01', TRUE),
    ('a0000004-0000-0000-0000-000000000002', 2026, 'BUDGET', 'PER_DIEM_ASSOC_5800', 'H Cetveli Gündelik: Ek Gösterge 5800-8000 (Doç. Dr.)', 680.00, NULL, 'TRY', TRUE, '{"daily_rate_tl":680.00,"max_lodging_rate_tl":1020.00}', '2026-01-01', TRUE),
    ('a0000004-0000-0000-0000-000000000003', 2026, 'BUDGET', 'PER_DIEM_ASST_3000', 'H Cetveli Gündelik: Ek Gösterge 3000-5800 (Dr. Öğr. Üyesi)', 620.00, NULL, 'TRY', TRUE, '{"daily_rate_tl":620.00,"max_lodging_rate_tl":930.00}', '2026-01-01', TRUE),
    ('a0000004-0000-0000-0000-000000000004', 2026, 'BUDGET', 'PER_DIEM_INST_GRADE_1_4', 'H Cetveli Gündelik: Aylık Derecesi 1-4 (Öğr. Gör., Arş. Gör.)', 540.00, NULL, 'TRY', TRUE, '{"daily_rate_tl":540.00,"max_lodging_rate_tl":810.00}', '2026-01-01', TRUE),
    ('a0000004-0000-0000-0000-000000000005', 2026, 'BUDGET', 'PER_DIEM_OTHER_GRADE_5_15', 'H Cetveli Gündelik: Aylık Derecesi 5-15 ve Öğrenciler', 500.00, NULL, 'TRY', TRUE, '{"daily_rate_tl":500.00,"max_lodging_rate_tl":750.00}', '2026-01-01', TRUE),

    -- Satın Alma ve İhale Eşikleri
    ('a0000005-0000-0000-0000-000000000001', 2026, 'PROCUREMENT', 'PROCUREMENT_DIRECT_LIMIT', 'Doğrudan Temin Parasal Sınırı (4734 Madde 22/d)', 620000.00, NULL, 'TRY', TRUE, '{"threshold":620000.00}', '2026-01-01', TRUE),
    ('a0000005-0000-0000-0000-000000000002', 2026, 'PROCUREMENT', 'TECH_SPEC_THRESHOLD', 'Teknik Şartname Zorunluluk Eşiği', 50000.00, NULL, 'TRY', TRUE, '{"threshold":50000.00}', '2026-01-01', TRUE),
    ('a0000005-0000-0000-0000-000000000003', 2026, 'PROCUREMENT', 'PROFORMA_THRESHOLD', 'Asgari Proforma Fatura Eşiği', 25000.00, NULL, 'TRY', TRUE, '{"threshold":25000.00,"min_proforma_count":3}', '2026-01-01', TRUE)
ON CONFLICT (id) DO NOTHING;

-- Seed Regulation Manifest Documents
INSERT INTO core.regulation_records (id, slug, title_tr, doc_type, version_label, source_url, storage_path, is_active, ingested_at)
VALUES
    ('b0000001-0000-0000-0000-000000000001', 'yok-bap-yonetmeligi', 'Yükseköğretim Kurumları Bilimsel Araştırma Projeleri Hakkında Yönetmelik', 'YONETMELIK', '2016-Resmi-Gazete-Guncel', 'https://www.mevzuat.gov.tr/File/GeneratePdf?mevzuatNo=22998&mevzuatTur=KurumVeKurulusYonetmeligi&mevzuatTertip=5', 'data/regulations/Yuksekoretim_Kurumlar_Bilimsel_Aratrma_Projeleri_Hakknda_Yonetmelik.pdf', TRUE, CURRENT_TIMESTAMP),
    ('b0000001-0000-0000-0000-000000000002', 'meu-bap-yonergesi-2025', '23.01.2025 Tarihli Mersin Üniversitesi BAP Yönergesi', 'YONERGE', '23.01.2025', 'https://bap.mersin.edu.tr/23.01.2025_Tarihli_MEU_BAP_Yonergesi.pdf', 'data/regulations/23.01.2025_Tarihli_MEU_BAP_Yonergesi.pdf', TRUE, CURRENT_TIMESTAMP),
    ('b0000001-0000-0000-0000-000000000003', 'meu-bap-uygulama-esaslari-2025', '18.02.2025 Tarihli Mersin Üniversitesi BAP Uygulama Usul ve Esasları', 'ESASLAR', '18.02.2025', 'https://bap.mersin.edu.tr/18.02.2025_tarihli_Mersin_Universitesi_BAP_Uygulama_Esaslar_Deiiklik_19.06.2026.pdf', 'data/regulations/meu_uygulama_esaslari_seed.md', TRUE, CURRENT_TIMESTAMP),
    ('b0000001-0000-0000-0000-000000000004', 'meu-acik-erisim-esaslari', 'Açık Erişim (Open Access) Yayın Basım Ücreti Desteği Uygulama Usul ve Esasları', 'ESASLAR', '2025-2026', 'https://bap.mersin.edu.tr/Ack_Eriim_Open_Access_Yayn_Basm_Ucreti_Destei_Uygulama_Usul_ve_Esaslar.pdf', 'data/regulations/Ack_Eriim_Open_Access_Yayn_Basm_Ucreti_Destei_Uygulama_Usul_ve_Esaslar.pdf', TRUE, CURRENT_TIMESTAMP),
    ('b0000001-0000-0000-0000-000000000005', 'meu-ihale-yonergesi', 'Mersin Üniversitesi Bilimsel Araştırma Projeleri Satın Alma ve İhale Yönergesi', 'YONERGE', 'Guncel', 'https://bap.mersin.edu.tr/MEU_BAP_Ihale_Yonergesi.pdf', 'data/regulations/MEU_BAP_Ihale_Yonergesi.pdf', TRUE, CURRENT_TIMESTAMP),
    ('b0000001-0000-0000-0000-000000000006', 'cb-butce-rehberi-2026', '2026-2028 Dönemi Bütçe Hazırlama Rehberi (Cumhurbaşkanlığı SBB)', 'REHBER', '2026-2028', 'https://www.sbb.gov.tr/wp-content/uploads/2025/10/2026-2028-Butce-Hazirlama-Rehberi.pdf', 'data/regulations/2026_2028_Butce_Hazirlama_Rehberi.pdf', TRUE, CURRENT_TIMESTAMP),
    ('b0000001-0000-0000-0000-000000000007', 'h-cetveli-2026', '2026 Yılı H Cetveli - Yurtiçi Gündelik Tutarları (7573 Sayılı Bütçe Kanunu)', 'CETVEL', '2026', 'https://www.mevzuat.gov.tr/MevzuatMetin/1.5.7573.pdf', 'data/regulations/2026_Yili_H_Cetveli.pdf', TRUE, CURRENT_TIMESTAMP),
    ('b0000001-0000-0000-0000-000000000008', 'yurtdisi-gundelik-2026', '2026 Yılı Yurtdışı Gündeliklerine Dair Cumhurbaşkanı Kararı', 'CETVEL', '2026', 'https://www.resmigazete.gov.tr/eskiler/2026/01/20260115-1.pdf', 'data/regulations/2026_Yurtdisi_Gundelik_Karari.pdf', TRUE, CURRENT_TIMESTAMP)
ON CONFLICT (slug) DO NOTHING;
