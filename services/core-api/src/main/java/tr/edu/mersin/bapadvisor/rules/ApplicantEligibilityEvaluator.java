package tr.edu.mersin.bapadvisor.rules;

import java.util.ArrayList;
import java.util.List;
import tr.edu.mersin.bapadvisor.common.FindingDto;
import tr.edu.mersin.bapadvisor.common.FindingSeverity;
import tr.edu.mersin.bapadvisor.common.RuleCategory;
import tr.edu.mersin.bapadvisor.common.StructuredSubmissionDto;

public class ApplicantEligibilityEvaluator implements RuleEvaluator {

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

        String title = submission.applicantTitle();
        String code = submission.detectedProjectCode();
        String codeUpper = code != null ? code.trim().toUpperCase() : "";

        if (title == null || title.isBlank()) {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_APPLICANT_ELIGIBILITY,
                    RuleCategory.ELIGIBILITY,
                    FindingSeverity.AMBIGUOUS_COMMISSION,
                    "Yürütücü Akademik Unvanı Belirtilmemiş",
                    "Yürütücü unvanı başvuru formunda tespit edilemediğinden yürütücülük şartları otomatik denetlenemedi; BAP Komisyonu incelemesine yönlendirildi. "
                            + DeterministicRuleConstants.GUARDRAIL_NOTICE,
                    DeterministicRuleConstants.DOC_USUL_ESASLAR,
                    "Madde 1/2",
                    "Mersin Üniversitesi kadrosundaki tüm öğretim üyeleri ve doktora derecesine sahip araştırmacılar başvuru yapabilir.",
                    "Yetkili Akademik Unvan",
                    "Belirtilmemiş"
            ));
            return findings;
        }

        String lowerTitle = title.trim().toLowerCase();
        boolean hasDoctorate = lowerTitle.contains("dr") || lowerTitle.contains("doktor");
        boolean isFaculty = lowerTitle.contains("prof") || lowerTitle.contains("doç") || lowerTitle.contains("doc") || lowerTitle.contains("dr. öğr");

        // Thesis projects TP2, TP3
        if (codeUpper.equals("TP2") || codeUpper.equals("TP3")) {
            if (!hasDoctorate && !isFaculty) {
                findings.add(FindingBuilder.create(
                        DeterministicRuleConstants.RULE_APPLICANT_ELIGIBILITY,
                        RuleCategory.ELIGIBILITY,
                        FindingSeverity.VIOLATION,
                        "Tez Danışmanı Yürütücülük Unvan Uygunsuzluğu (" + codeUpper + ")",
                        codeUpper + " tez projelerinde yürütücünün öğrencinin resmi tez danışmanı (doktora veya öğretim üyeliği unvanına sahip araştırmacı) olması zorunludur. Mevcut unvan: " + title,
                        DeterministicRuleConstants.DOC_USUL_ESASLAR,
                        "Madde 4/1-b,c",
                        "Tez projeleri lisansüstü öğrencinin tez danışmanı olan öğretim üyesi yürütücülüğünde sunulur.",
                        "Tez Danışmanı / Öğretim Üyesi / Dr.",
                        title
                ));
                return findings;
            }
        }

        // AP1 Career Start: Dr., Dr. Öğr. Üyesi, Doç.
        if (codeUpper.equals("AP1")) {
            if (!hasDoctorate && !isFaculty) {
                findings.add(FindingBuilder.create(
                        DeterministicRuleConstants.RULE_APPLICANT_ELIGIBILITY,
                        RuleCategory.ELIGIBILITY,
                        FindingSeverity.VIOLATION,
                        "Kariyer Başlangıç Projesi Unvan Uygunsuzluğu (AP1)",
                        "AP1 Kariyer Başlangıç Projesine doktora veya tıpta uzmanlık derecesine sahip araştırmacılar başvurabilir. Mevcut unvan: " + title,
                        DeterministicRuleConstants.DOC_USUL_ESASLAR,
                        "Madde 5/1-a",
                        "Doktora veya tıpta uzmanlık derecesini son 5 yıl içinde almış öğretim elemanları başvurabilir.",
                        "Doktora / Uzmanlık Derecesi",
                        title
                ));
                return findings;
            }
        }

        // AP2, AP3, AP4: Faculty Member required (Prof., Doç., Dr. Öğr. Üyesi)
        if (codeUpper.equals("AP2") || codeUpper.equals("AP3") || codeUpper.equals("AP4")) {
            if (!isFaculty) {
                findings.add(FindingBuilder.create(
                        DeterministicRuleConstants.RULE_APPLICANT_ELIGIBILITY,
                        RuleCategory.ELIGIBILITY,
                        FindingSeverity.VIOLATION,
                        "Öğretim Üyeliği Unvan Şartı Sağlanamadı (" + codeUpper + ")",
                        codeUpper + " bağımsız araştırma projelerinde yürütücünün Mersin Üniversitesi kadrosunda öğretim üyesi (Prof. Dr., Doç. Dr., Dr. Öğr. Üyesi) olması zorunludur. Mevcut unvan: " + title,
                        DeterministicRuleConstants.DOC_USUL_ESASLAR,
                        "Madde 5/1",
                        "Mersin Üniversitesi kadrosundaki tüm öğretim üyeleri bağımsız araştırma projelerine başvurabilir.",
                        "Öğretim Üyesi (Prof. / Doç. / Dr. Öğr. Üyesi)",
                        title
                ));
                return findings;
            }
        }

        // Compliant finding with domain guardrail
        findings.add(FindingBuilder.create(
                DeterministicRuleConstants.RULE_APPLICANT_ELIGIBILITY,
                RuleCategory.ELIGIBILITY,
                FindingSeverity.COMPLIANT,
                "Yürütücü Başvuru Koşulu Şeklen Uygun (" + title + ")",
                "Yürütücünün akademik unvanı (" + title + "), " + (!codeUpper.isEmpty() ? codeUpper : "ilgili başvuru")
                        + " koşullarına idari ve şekli bakımdan uygundur. " + DeterministicRuleConstants.GUARDRAIL_NOTICE,
                DeterministicRuleConstants.DOC_USUL_ESASLAR,
                "Madde 1/2, Madde 4 ve Madde 5",
                "Mersin Üniversitesi kadrosundaki öğretim elemanları ilgili proje türlerine mevzuata uygun şekilde başvurabilir.",
                "Yetkili Akademik Unvan",
                title
        ));

        return findings;
    }
}
