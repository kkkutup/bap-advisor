package tr.edu.mersin.bapadvisor.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import tr.edu.mersin.bapadvisor.common.BudgetLineItemDto;
import tr.edu.mersin.bapadvisor.common.DocumentRole;
import tr.edu.mersin.bapadvisor.common.FindingDto;
import tr.edu.mersin.bapadvisor.common.FindingSeverity;
import tr.edu.mersin.bapadvisor.common.RuleCategory;
import tr.edu.mersin.bapadvisor.common.StructuredSubmissionDto;
import tr.edu.mersin.bapadvisor.common.SubmissionType;

public class RequiredAttachmentsEvaluator implements RuleEvaluator {

    @Override
    public boolean supports(StructuredSubmissionDto submission) {
        return submission != null;
    }

    @Override
    public List<FindingDto> evaluate(StructuredSubmissionDto submission, RuleLimitsContext context) {
        List<FindingDto> findings = new ArrayList<>();
        if (submission == null) {
            return findings;
        }

        List<DocumentRole> attachments = submission.detectedAttachments() != null
                ? submission.detectedAttachments()
                : List.of();
        SubmissionType type = submission.detectedSubmissionType();
        String code = submission.detectedProjectCode();
        boolean hasMissingDoc = false;

        // 1. Check primary application forms by submission type
        if (type == SubmissionType.NEW_PROJECT) {
            if (!attachments.contains(DocumentRole.APPLICATION_FORM)) {
                hasMissingDoc = true;
                findings.add(FindingBuilder.create(
                        DeterministicRuleConstants.RULE_REQUIRED_ATTACHMENTS,
                        RuleCategory.DOCUMENTS,
                        FindingSeverity.MISSING_DOCUMENT,
                        "Zorunlu Başvuru Formu Eksik",
                        "Yeni proje başvuru paketi içerisinde onaylı Başvuru Formu (APPLICATION_FORM) bulunamadı.",
                        DeterministicRuleConstants.DOC_USUL_ESASLAR,
                        "Madde 7/1",
                        "Tüm başvurular BAP Otomasyon Sistemi üzerinden üretilen başvuru formu ile sunulur.",
                        "APPLICATION_FORM mevcut olmalıdır",
                        "Eksik"
                ));
            }

            // TP2 and TP3 require THESIS_PROPOSAL
            if (code != null && (code.equalsIgnoreCase("TP2") || code.equalsIgnoreCase("TP3"))) {
                if (!attachments.contains(DocumentRole.THESIS_PROPOSAL)) {
                    hasMissingDoc = true;
                    findings.add(FindingBuilder.create(
                            DeterministicRuleConstants.RULE_REQUIRED_ATTACHMENTS,
                            RuleCategory.DOCUMENTS,
                            FindingSeverity.MISSING_DOCUMENT,
                            "Enstitü Tez Önerisi / Jürisi Kararı Eksik (" + code + ")",
                            code + " tez projesi başvurusunda Enstitü Yönetim Kurulu tez önerisi ve jüri kabul tutanağı (THESIS_PROPOSAL) zorunludur.",
                            DeterministicRuleConstants.DOC_USUL_ESASLAR,
                            "Madde 4/1-b,c",
                            "Enstitü tez konusu kabul kararı ve tez izleme komitesi / jüri onay tutanağının bulunması zorunludur.",
                            "THESIS_PROPOSAL mevcut olmalıdır",
                            "Eksik"
                    ));
                }
            }

            // Ethics approval check
            if (Boolean.TRUE.equals(submission.ethicsRequiredKeywordsDetected())) {
                if (!attachments.contains(DocumentRole.ETHICS_APPROVAL)) {
                    hasMissingDoc = true;
                    findings.add(FindingBuilder.create(
                            DeterministicRuleConstants.RULE_REQUIRED_ATTACHMENTS,
                            RuleCategory.DOCUMENTS,
                            FindingSeverity.MISSING_DOCUMENT,
                            "Etik Kurul Onay Belgesi Eksik",
                            "Başvuruda etik kurul izni gerektiren faaliyet veya anahtar kelimeler tespit edildiği hâlde geçerli Etik Kurul Onay Belgesi (ETHICS_APPROVAL) bulunamadı.",
                            DeterministicRuleConstants.DOC_USUL_ESASLAR,
                            "Madde 9/1",
                            "İnsan ve hayvan üzerinde deney veya kişisel veri toplanması öngörülen projelerde Etik Kurul Onay Belgesi zorunludur.",
                            "ETHICS_APPROVAL mevcut olmalıdır",
                            "Eksik"
                    ));
                }
            }
        } else if (type == SubmissionType.OPEN_ACCESS) {
            if (!attachments.contains(DocumentRole.PUBLICATION_PROOF)) {
                hasMissingDoc = true;
                findings.add(FindingBuilder.create(
                        DeterministicRuleConstants.RULE_REQUIRED_ATTACHMENTS,
                        RuleCategory.DOCUMENTS,
                        FindingSeverity.MISSING_DOCUMENT,
                        "Makale Kabul / Yayın Kanıt Belgesi Eksik",
                        "Açık erişim yayın desteği başvurusunda makalenin kabul edildiğine dair resmi yazı veya yayın kanıtı (PUBLICATION_PROOF) bulunamadı.",
                        DeterministicRuleConstants.DOC_USUL_ESASLAR,
                        "Madde 6/3",
                        "Başvuru dosyasında makalenin kabul edildiğine dair resmi yazı ve dergi çeyreklik dilim belgesi bulunmak zorundadır.",
                        "PUBLICATION_PROOF mevcut olmalıdır",
                        "Eksik"
                ));
            }
            if (!attachments.contains(DocumentRole.PROFORMA_INVOICE)) {
                hasMissingDoc = true;
                findings.add(FindingBuilder.create(
                        DeterministicRuleConstants.RULE_REQUIRED_ATTACHMENTS,
                        RuleCategory.DOCUMENTS,
                        FindingSeverity.MISSING_DOCUMENT,
                        "Yayıncı Fatura / Proforma Eksik",
                        "Açık erişim makale desteği başvurusunda yayıncının fatura veya proforma belgesi (PROFORMA_INVOICE) bulunamadı.",
                        DeterministicRuleConstants.DOC_USUL_ESASLAR,
                        "Madde 6/3",
                        "Başvuru dosyasında yayıncının fatura/proforması bulunmak zorundadır.",
                        "PROFORMA_INVOICE mevcut olmalıdır",
                        "Eksik"
                ));
            }
        } else if (type == SubmissionType.ADDITIONAL_BUDGET || type == SubmissionType.ADDITIONAL_TIME || type == SubmissionType.PROCUREMENT_ADVANCE) {
            boolean hasPetitionOrForm = attachments.contains(DocumentRole.SIGNED_PETITION) || attachments.contains(DocumentRole.APPLICATION_FORM);
            if (!hasPetitionOrForm) {
                hasMissingDoc = true;
                findings.add(FindingBuilder.create(
                        DeterministicRuleConstants.RULE_REQUIRED_ATTACHMENTS,
                        RuleCategory.DOCUMENTS,
                        FindingSeverity.MISSING_DOCUMENT,
                        "Gerekçeli Talep Dilekçesi / Formu Eksik",
                        type + " başvurusunda gerekçeli ve imzalı talep dilekçesi veya başvuru formu (SIGNED_PETITION / APPLICATION_FORM) bulunamadı.",
                        DeterministicRuleConstants.DOC_USUL_ESASLAR,
                        "Madde 7/2 ve Madde 13/3",
                        "Ek bütçe, ek süre ve satın alma/avans taleplerinde ayrıntılı gerekçe formu ve imzalı dilekçe zorunludur.",
                        "SIGNED_PETITION veya APPLICATION_FORM",
                        "Eksik"
                ));
            }
        }

        // 2. Cross-check budget line items for PROFORMA_INVOICE and TECHNICAL_SPEC
        List<BudgetLineItemDto> items = submission.budgetLineItems() != null ? submission.budgetLineItems() : List.of();
        boolean hasPurchaseItem = false;

        for (BudgetLineItemDto item : items) {
            String cat = item.category() != null ? item.category().toUpperCase() : "";
            boolean isPurchaseCategory = cat.equals("EQUIPMENT") || cat.equals("CONSUMABLE") || cat.equals("SERVICE_PURCHASE");
            if (isPurchaseCategory) {
                hasPurchaseItem = true;
            }

            BigDecimal itemTotal = item.totalTl() != null ? item.totalTl() : BigDecimal.ZERO;
            String itemName = item.name() != null ? item.name() : "Belirtilmemiş Kalem";

            // If item exceeds 25,000 TL, verify minimum 3 proformas
            if (itemTotal.compareTo(DeterministicRuleConstants.PROFORMA_THRESHOLD_TL) > 0) {
                if (item.proformaCount() != null && item.proformaCount() < DeterministicRuleConstants.PROFORMA_MIN_COUNT) {
                    hasMissingDoc = true;
                    findings.add(FindingBuilder.create(
                            DeterministicRuleConstants.RULE_REQUIRED_ATTACHMENTS,
                            RuleCategory.DOCUMENTS,
                            FindingSeverity.VIOLATION,
                            "Yetersiz Proforma Fatura Sayısı: " + itemName,
                            "Toplam tutarı 25.000 TL'yi aşan '" + itemName + "' (" + DeterministicRuleConstants.formatTl(itemTotal)
                                    + ") alımı için en az 3 adet proforma fatura zorunludur (Mevcut: " + item.proformaCount() + ").",
                            DeterministicRuleConstants.DOC_USUL_ESASLAR,
                            "Madde 11/1-a",
                            "Toplam tutarı 25.000,00 TL'yi aşan her alım kalemi için en az 3 (üç) adet proforma fatura veya piyasa teklif mektubu dosyaya eklenmelidir.",
                            ">= 3 Adet Proforma",
                            item.proformaCount() + " Adet"
                    ));
                }
            }

            // If equipment or service purchase exceeds 50,000 TL, or hasTechnicalSpec is false, check technical spec
            boolean needsSpec = (cat.equals("EQUIPMENT") || cat.equals("SERVICE_PURCHASE"))
                    && itemTotal.compareTo(DeterministicRuleConstants.TECHNICAL_SPEC_THRESHOLD_TL) > 0;
            if (needsSpec) {
                if (Boolean.FALSE.equals(item.hasTechnicalSpec()) || !attachments.contains(DocumentRole.TECHNICAL_SPEC)) {
                    hasMissingDoc = true;
                    findings.add(FindingBuilder.create(
                            DeterministicRuleConstants.RULE_REQUIRED_ATTACHMENTS,
                            RuleCategory.DOCUMENTS,
                            FindingSeverity.MISSING_DOCUMENT,
                            "Teknik Şartname Belgesi Eksik: " + itemName,
                            "Toplam tutarı 50.000 TL'yi aşan '" + itemName + "' (" + DeterministicRuleConstants.formatTl(itemTotal)
                                    + ") kalemi için ayrıntılı Teknik Şartname belgesi (TECHNICAL_SPEC) zorunludur.",
                            DeterministicRuleConstants.DOC_USUL_ESASLAR,
                            "Madde 11/1-b",
                            "Toplam tutarı 50.000,00 TL'yi aşan her cihaz, donanım veya özel hizmet alımı için ayrıntılı ve tarafsız Teknik Şartname belgesi zorunludur.",
                            "TECHNICAL_SPEC belgesi zorunludur",
                            "Eksik"
                    ));
                }
            }
        }

        // If purchase items exist, require PROFORMA_INVOICE in detectedAttachments
        if (hasPurchaseItem && !attachments.contains(DocumentRole.PROFORMA_INVOICE)) {
            hasMissingDoc = true;
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_REQUIRED_ATTACHMENTS,
                    RuleCategory.DOCUMENTS,
                    FindingSeverity.MISSING_DOCUMENT,
                    "Piyasa Fiyat Araştırması / Proforma Fatura Eksik",
                    "Bütçe tablosundaki mal/cihaz/hizmet alım kalemleri için piyasa fiyat araştırmasını tevsik eden proforma fatura (PROFORMA_INVOICE) pakette tespit edilemedi.",
                    DeterministicRuleConstants.DOC_USUL_ESASLAR,
                    "Madde 11/1",
                    "BAP bütçesinden talep edilen mal, malzeme, cihaz ve hizmet alımlarında piyasa fiyat araştırmasını teminen proforma faturalar dosyaya eklenmelidir.",
                    "PROFORMA_INVOICE mevcut olmalıdır",
                    "Eksik"
            ));
        }

        // If all required attachments and conditions are satisfied
        if (!hasMissingDoc) {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_REQUIRED_ATTACHMENTS,
                    RuleCategory.DOCUMENTS,
                    FindingSeverity.COMPLIANT,
                    "Zorunlu Başvuru Ek Belgeleri Eksiksiz",
                    "Başvuru türü ve bütçe kalemleri için mevzuatta öngörülen tüm zorunlu ek belgeler eksiksiz olarak sunulmuştur.",
                    DeterministicRuleConstants.DOC_USUL_ESASLAR,
                    "Madde 7 ve Madde 11",
                    "Tüm belgeler eksiksiz ve mevzuata uygun şekilde dosyalanmalıdır.",
                    "Tüm zorunlu belgeler mevcut",
                    "Eksiksiz (" + attachments.size() + " belge)"
            ));
        }

        return findings;
    }
}
