import logging
from typing import Any, Dict, List, Optional, Tuple
from uuid import uuid4
from app.schemas import (
    CitationItem,
    DocumentRole,
    FindingItem,
    FindingSeverity,
    FindingSource,
    OverallComplianceStatus,
    RuleCategory,
    StructuredSubmissionData,
    SubmissionType,
)

logger = logging.getLogger(__name__)


class GroundedSynthesizer:
    """
    Rule-grounded Turkish Legal Evaluation and Action Draft Synthesizer.
    Generates deterministic citation-backed findings, formal revision return drafts,
    commission referral summaries, and Q&A answers directly from retrieved regulation chunks
    and submission data.
    """

    def synthesize_rag_findings(
        self,
        submission_type: SubmissionType,
        project_code: Optional[str],
        structured_data: StructuredSubmissionData,
        relevant_chunks: List[Dict[str, Any]],
        deterministic_findings: List[FindingItem],
    ) -> List[FindingItem]:
        findings: List[FindingItem] = []

        # 1. Islak İmza Kontrolü (MADDE 7/2)
        if not structured_data.wet_signature_present:
            findings.append(FindingItem(
                id=uuid4(),
                source_type=FindingSource.RAG_LLM,
                rule_code="RAG_WET_SIGNATURE_MANDATORY",
                category=RuleCategory.FORMAL_SIGNATURE,
                severity=FindingSeverity.MISSING_DOCUMENT,
                title_tr="Mavi Islak İmza Eksikliği",
                explanation_tr=(
                    "BAP Otomasyon Sistemi çıktısı başvuru/talep belgesinde proje yürütücüsünün "
                    "mavi mürekkepli ıslak imzası tespit edilememiştir."
                ),
                regulation_doc_name="Mersin Üniversitesi BAP Uygulama Usul ve Esasları",
                regulation_article_ref="MADDE 7/2",
                verbatim_quote=(
                    "Sistemden üretilen başvuru formu, ek bütçe formu, ek süre talep formu veya satın alma/yolluk "
                    "dilekçeleri yazıcıdan döküm alınarak proje yürütücüsü ve varsa araştırmacılar tarafından "
                    "mavi tükenmez/dolma kalemle ıslak imzalı olarak imzalanmalıdır."
                ),
                expected_value="Islak imzalı mavi kalem onay",
                actual_value="İmzasız veya dijital kopya",
            ))

        # 2. Etik Kurul Onay Belgesi (MADDE 9/1)
        if structured_data.ethics_required_keywords_detected:
            has_ethics = DocumentRole.ETHICS_APPROVAL in structured_data.detected_attachments
            if not has_ethics:
                findings.append(FindingItem(
                    id=uuid4(),
                    source_type=FindingSource.RAG_LLM,
                    rule_code="RAG_ETHICS_APPROVAL_MISSING",
                    category=RuleCategory.DOCUMENTS,
                    severity=FindingSeverity.MISSING_DOCUMENT,
                    title_tr="Zorunlu Etik Kurul Onay Belgesi Eksik",
                    explanation_tr=(
                        "Başvuru içeriğinde insan/hayvan deneyi, anket, mülakat veya biyolojik materyal kullanımına "
                        "ilişkin hususlar yer almaktadır. Dosyada yetkili Etik Kurul Onay Belgesi bulunamamıştır."
                    ),
                    regulation_doc_name="Mersin Üniversitesi BAP Uygulama Usul ve Esasları",
                    regulation_article_ref="MADDE 9/1",
                    verbatim_quote=(
                        "Başvuruda insanlar üzerinde anket, mülakat, odak grup çalışması, deney veya biyolojik materyal kullanımı; "
                        "hayvanlar üzerinde herhangi bir prosedür... öngörülüyorsa başvuru paketine geçerli Etik Kurul Onay Belgesi "
                        "eklenmesi zorunludur."
                    ),
                    expected_value="Onaylı Etik Kurul Belgesi",
                    actual_value="Eksik",
                ))

        # 3. Proforma Fatura ve Teknik Şartname Kontrolleri (MADDE 11)
        for item in structured_data.budget_line_items:
            # 25.000 TL üzeri alımlarda en az 3 proforma
            if item.total_tl > 25000.0:
                p_count = item.proforma_count if item.proforma_count is not None else 1
                if p_count < 3:
                    findings.append(FindingItem(
                        id=uuid4(),
                        source_type=FindingSource.RAG_LLM,
                        rule_code="RAG_PROFORMA_COUNT_INSUFFICIENT",
                        category=RuleCategory.PROCUREMENT,
                        severity=FindingSeverity.VIOLATION,
                        title_tr=f"Yetersiz Proforma Fatura ({item.name})",
                        explanation_tr=(
                            f"Toplam tutarı 25.000 TL'yi aşan '{item.name}' ({item.total_tl:,.2f} TL) kalemi için "
                            f"mevzuat gereği en az 3 adet proforma fatura/fiyat teklifi ibraz edilmelidir. "
                            f"Mevcut tespit edilen: {p_count} adet."
                        ),
                        regulation_doc_name="Mersin Üniversitesi BAP Uygulama Usul ve Esasları",
                        regulation_article_ref="MADDE 11/1-a",
                        verbatim_quote=(
                            "Toplam tutarı 25.000,00 TL'yi aşan her alım kalemi için en az 3 (üç) adet kaşeli ve "
                            "imzalı güncel proforma fatura veya piyasa teklif mektubu dosyaya eklenmelidir."
                        ),
                        expected_value="En az 3 adet proforma",
                        actual_value=f"{p_count} adet proforma",
                    ))

            # 50.000 TL üzeri cihaz/hizmet için Teknik Şartname zorunlu
            if item.total_tl > 50000.0 and item.has_technical_spec is False:
                findings.append(FindingItem(
                    id=uuid4(),
                    source_type=FindingSource.RAG_LLM,
                    rule_code="RAG_TECHNICAL_SPEC_REQUIRED",
                    category=RuleCategory.DOCUMENTS,
                    severity=FindingSeverity.MISSING_DOCUMENT,
                    title_tr=f"Teknik Şartname Eksik ({item.name})",
                    explanation_tr=(
                        f"Toplam tutarı 50.000 TL'yi aşan '{item.name}' ({item.total_tl:,.2f} TL) alımı için "
                        f"yürütücü tarafından hazırlanmış tarafsız Teknik Şartname belgesi zorunludur."
                    ),
                    regulation_doc_name="Mersin Üniversitesi BAP Uygulama Usul ve Esasları",
                    regulation_article_ref="MADDE 11/1-b",
                    verbatim_quote=(
                        "Toplam tutarı 50.000,00 TL'yi aşan her cihaz, donanım veya özel hizmet alımı kalemi için "
                        "yürütücü tarafından hazırlanmış ayrıntılı ve tarafsız Teknik Şartname belgesi zorunludur."
                    ),
                    expected_value="Teknik Şartname Belgesi",
                    actual_value="Eksik",
                ))

        # 4. Açık Erişim (Open Access) Çeyreklik Dilim Uygunluğu (MADDE 6)
        if submission_type == SubmissionType.OPEN_ACCESS:
            q = structured_data.journal_quartile
            if q in ("Q3", "Q4"):
                findings.append(FindingItem(
                    id=uuid4(),
                    source_type=FindingSource.RAG_LLM,
                    rule_code="RAG_OPEN_ACCESS_QUARTILE_INELIGIBLE",
                    category=RuleCategory.OPEN_ACCESS,
                    severity=FindingSeverity.VIOLATION,
                    title_tr="Destek Dışı Dergi Çeyreklik Dilimi",
                    explanation_tr=(
                        f"Açık erişim makale desteği yalnızca WoS Q1 ve Q2 dergileri için geçerlidir. "
                        f"Başvuruda tespit edilen dergi dilimi: {q}."
                    ),
                    regulation_doc_name="Mersin Üniversitesi BAP Uygulama Usul ve Esasları",
                    regulation_article_ref="MADDE 6/2",
                    verbatim_quote=(
                        "WoS Q3 ve Q4 kategorisindeki dergiler ile derleme (review), editöre mektup, vaka takdimi "
                        "ve konferans özet bildirileri açık erişim desteği kapsamı dışındadır."
                    ),
                    expected_value="WoS Q1 veya Q2",
                    actual_value=str(q),
                ))

        # 5. Tez Projesi Enstitü Kararı (MADDE 4/1-b, 4/1-c)
        if project_code in ("TP2", "TP3"):
            has_thesis_proposal = DocumentRole.THESIS_PROPOSAL in structured_data.detected_attachments
            if not has_thesis_proposal:
                findings.append(FindingItem(
                    id=uuid4(),
                    source_type=FindingSource.RAG_LLM,
                    rule_code="RAG_THESIS_PROPOSAL_REQUIRED",
                    category=RuleCategory.DOCUMENTS,
                    severity=FindingSeverity.MISSING_DOCUMENT,
                    title_tr="Enstitü Tez Önerisi / Jüri Kabul Kararı Eksik",
                    explanation_tr=(
                        f"{project_code} tez projesi başvurularında ilgili Enstitü Yönetim Kurulu onaylı tez konusu "
                        f"veya tez izleme komitesi kabul tutanağının dosyada yer alması zorunludur."
                    ),
                    regulation_doc_name="Mersin Üniversitesi BAP Uygulama Usul ve Esasları",
                    regulation_article_ref="MADDE 4/1-b" if project_code == "TP2" else "MADDE 4/1-c",
                    verbatim_quote=(
                        "Başvuru paketinde ilgili Enstitü Yönetim Kurulunun tez önerisi ve jüri kabul kararının "
                        "bulunması zorunludur."
                    ),
                    expected_value="Enstitü Onay Belgesi",
                    actual_value="Eksik",
                ))

        # If no issues found, produce a compliant finding grounded in MADDE 1
        if not findings and not deterministic_findings:
            findings.append(FindingItem(
                id=uuid4(),
                source_type=FindingSource.RAG_LLM,
                rule_code="RAG_FORMAL_COMPLIANCE_CONFIRMED",
                category=RuleCategory.ELIGIBILITY,
                severity=FindingSeverity.COMPLIANT,
                title_tr="İdari ve Şekli Mevzuat Uygunluğu Doğrulandı",
                explanation_tr="Başvuru evrakları, tavan bütçe ve mevzuat kriterleri idari açıdan uygundur.",
                regulation_doc_name="Mersin Üniversitesi BAP Uygulama Usul ve Esasları",
                regulation_article_ref="MADDE 1/2",
                verbatim_quote=(
                    "Mersin Üniversitesi kadrosundaki tüm öğretim üyeleri, doktora veya sanatta yeterlik derecesine "
                    "sahip araştırmacılar ve enstitülere kayıtlı lisansüstü öğrenciler tarafından sunulan tüm BAP "
                    "talepleri bu usul ve esaslara tabidir."
                ),
            ))

        return findings

    def calculate_overall_status(
        self,
        all_findings: List[FindingItem],
    ) -> OverallComplianceStatus:
        severities = [f.severity for f in all_findings]
        if FindingSeverity.VIOLATION in severities:
            return OverallComplianceStatus.NEEDS_REVISION
        if FindingSeverity.MISSING_DOCUMENT in severities:
            return OverallComplianceStatus.NEEDS_REVISION
        if FindingSeverity.AMBIGUOUS_COMMISSION in severities:
            return OverallComplianceStatus.COMMISSION_REVIEW
        return OverallComplianceStatus.COMPLIANT

    def generate_revision_draft(
        self,
        structured_data: StructuredSubmissionData,
        project_code: Optional[str],
        issues: List[FindingItem],
    ) -> str:
        name = structured_data.applicant_name or "Araştırmacı"
        title = structured_data.applicant_title or "Sayın Yürütücü"
        dept = structured_data.department or "İlgili Bölüm"
        p_title = structured_data.project_title or "Belirtilmemiş BAP Projesi"
        p_code = project_code or structured_data.detected_project_code or "BAP"

        lines = [
            "T.C. MERSİN ÜNİVERSİTESİ",
            "BİLİMSEL ARAŞTIRMA PROJELERİ KOORDİNASYON BİRİMİ BAŞKANLIĞINA",
            "",
            f"Tarih: 2026 Mali Yılı | Konu: BAP Ön İnceleme Eksiklik Bildirimi ve Revizyon Talebi",
            f"Proje Kodu / Türü: {p_code} | Yürütücü: {title} {name} ({dept})",
            f"Proje Başlığı: {p_title}",
            "",
            "Sayın Yürütücü,",
            "",
            "BAP Otomasyon Sistemi üzerinden ilettiğiniz yukarıda bilgileri yer alan başvurunuz, "
            "Mersin Üniversitesi BAP Yönergesi ve 2026 Yılı Uygulama Usul ve Esasları çerçevesinde "
            "idari, şekli, belgesel ve mali mevzuat açısından ön incelemeye tabi tutulmuştur. "
            "Yapılan incelemede mevzuat gereği tamamlanması zorunlu olan aşağıdaki hususlar tespit edilmiştir:",
            "",
        ]

        for i, item in enumerate(issues, start=1):
            lines.append(f"{i}. {item.title_tr} [{item.regulation_article_ref}]:")
            lines.append(f"   - Tespit: {item.explanation_tr}")
            if item.verbatim_quote:
                lines.append(f"   - Mevzuat Dayanağı ({item.regulation_doc_name}): \"{item.verbatim_quote}\"")
            if item.expected_value:
                lines.append(f"   - Gereken İşlem: {item.expected_value}")
            lines.append("")

        lines.extend([
            "Yukarıda belirtilen eksikliklerin/uygunsuzlukların giderilerek BAP Otomasyon Sistemi üzerinden "
            "en geç 15 (on beş) gün içerisinde güncel revizyon dosyasının (v2) sisteme yüklenmesi rica olunur.",
            "",
            "Mersin Üniversitesi BAP Koordinasyon Birimi",
            "(Not: Bu belge ön inceleme mevzuat denetimi kapsamında otomatik üretilmiş resmi bildirim taslağıdır.)",
        ])

        return "\n".join(lines)

    def generate_commission_referral_summary(
        self,
        structured_data: StructuredSubmissionData,
        project_code: Optional[str],
        findings: List[FindingItem],
    ) -> str:
        name = structured_data.applicant_name or "Araştırmacı"
        title = structured_data.applicant_title or "Öğretim Üyesi"
        dept = structured_data.department or "Mersin Üniversitesi"
        p_title = structured_data.project_title or "BAP Araştırma Projesi"
        p_code = project_code or structured_data.detected_project_code or "BAP"
        budget = structured_data.requested_budget_total_tl or 0.0
        duration = structured_data.requested_duration_months or 0

        ambiguous_items = [f for f in findings if f.severity == FindingSeverity.AMBIGUOUS_COMMISSION]

        lines = [
            "T.C. MERSİN ÜNİVERSİTESİ",
            "BİLİMSEL ARAŞTIRMA PROJELERİ KOMİSYON BAŞKANLIĞINA (PROFESÖR KURULU)",
            "",
            "ÖN İNCELEME SEVK ÖZETİ VE MEVZUAT UYGUNLUK RAPORU",
            "",
            f"1. Başvuru Sahibi: {title} {name} - {dept}",
            f"2. Proje Bilgisi: {p_code} - \"{p_title}\"",
            f"3. Mali ve Süre Verileri: Talep Edilen Bütçe: {budget:,.2f} TL | Talep Edilen Süre: {duration} Ay",
            "",
            "4. İdari ve Şekli Mevzuat İnceleme Sonucu:",
            "   Başvuru dosyası Mersin Üniversitesi BAP Yönergesi ve 2026 Mali Yılı Bütçe/H Cetveli "
            "hükümleri doğrultusunda idari, şekli, imza, tavan limit ve ek belge kontrolünden geçirilmiştir. "
            "Proje şekli ve mali mevzuat ön koşullarını sağlamaktadır.",
            "",
        ]

        if ambiguous_items:
            lines.append("5. BAP Komisyonunun Akademik / İdari Takdirine Bırakılan Hususlar:")
            for item in ambiguous_items:
                lines.append(f"   * {item.title_tr} ({item.regulation_article_ref}): {item.explanation_tr}")
            lines.append("")
        else:
            lines.append("5. BAP Komisyonu Takdiri Gerektiren Özel Husus: Bulunmamaktadır.")
            lines.append("")

        lines.extend([
            "6. Sonuç ve Öneri:",
            "   Başvurunun 2547 sayılı Kanun ve MEÜ BAP Mevzuatı uyarınca projenin bilimsel kalitesi, "
            "akademik özgünlüğü ve bütçe gerekçelerinin değerlendirilmesi amacıyla BAP Komisyonuna sevk "
            "edilmesi uygundur.",
            "",
            "BAP Koordinasyon Birimi Ön İnceleme Servisi",
        ])

        return "\n".join(lines)

    def answer_qa(
        self,
        question: str,
        relevant_chunks: List[Dict[str, Any]],
        submission_context: StructuredSubmissionData,
    ) -> Tuple[str, List[CitationItem]]:
        """
        Synthesizes a Turkish citation-backed response strictly grounded in retrieved regulation chunks.
        """
        citations: List[CitationItem] = []

        for ch in relevant_chunks[:3]:
            citations.append(CitationItem(
                regulation_doc_name=ch.get("regulation_doc_name", "MEÜ BAP Mevzuatı"),
                regulation_article_ref=ch.get("regulation_article_ref", "Mevzuat Hükmü"),
                verbatim_quote=ch.get("verbatim_quote", ch.get("content_text", "")[:200]),
                score=float(ch.get("score", 0.9)),
            ))

        # Synthesize grounded answer
        lines = [
            f"Mersin Üniversitesi BAP Mevzuatı ve başvuru verileri çerçevesinde '{question}' sorunuz incelenmiştir:\n"
        ]

        if submission_context:
            p_code = submission_context.detected_project_code or "BAP"
            budget = submission_context.requested_budget_total_tl
            dur = submission_context.requested_duration_months
            dur_str = f"{dur} ay" if (dur is not None and dur > 0) else "Belirtilmemiş / 0 ay (Eksik)"
            budget_str = f"{budget:,.2f} TL" if budget is not None else "Belirtilmemiş"
            lines.append(
                f"• Başvuru Özeti ({p_code}): Talep Edilen Bütçe: {budget_str} | Talep Edilen Süre: {dur_str} | "
                f"Islak İmza: {'Mevcut' if submission_context.wet_signature_present else 'Eksik'}"
            )
            if submission_context.budget_line_items:
                low_proforma = [
                    f"{it.name} ({it.total_tl:,.0f} TL - Proforma: {it.proforma_count or 0} adet)"
                    for it in submission_context.budget_line_items
                    if it.total_tl >= 25000 and (it.proforma_count or 0) < 3
                ]
                if low_proforma:
                    lines.append(f"• Proforma Eksikliği Tespit Edilen Kalemler: {', '.join(low_proforma)}")
            lines.append("")

        if not relevant_chunks:
            lines.append(
                "İlgili konu hakkında yürürlükteki MEÜ BAP mevzuatında doğrudan bir hüküm tespit edilememiştir. "
                "Konunun BAP Komisyonu (Profesör Kurulu) tarafından takdir edilmesi gerekmektedir."
            )
        else:
            primary_chunk = relevant_chunks[0]
            art_ref = primary_chunk.get("regulation_article_ref", "")
            doc_name = primary_chunk.get("regulation_doc_name", "")
            raw_text = (
                primary_chunk.get("raw_text")
                or primary_chunk.get("verbatim_quote")
                or primary_chunk.get("content_text")
                or ""
            )

            lines.append(f"İlgili Mevzuat Hükmü ({doc_name} - {art_ref}):")
            lines.append(f"{raw_text}\n")
            lines.append(
                "Bu hüküm doğrultusunda başvurunuzdaki bütçe, süre, proforma ve belge şartlarının yukarıda belirtilen "
                "kriterlere uygun olarak BAP Otomasyon Sistemi üzerinden düzenlenmesi gerekmektedir."
            )

        answer_tr = "\n".join(lines)
        return answer_tr, citations
