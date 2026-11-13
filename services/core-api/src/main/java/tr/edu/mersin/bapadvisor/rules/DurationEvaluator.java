package tr.edu.mersin.bapadvisor.rules;

import java.util.ArrayList;
import java.util.List;
import tr.edu.mersin.bapadvisor.common.FindingDto;
import tr.edu.mersin.bapadvisor.common.FindingSeverity;
import tr.edu.mersin.bapadvisor.common.RuleCategory;
import tr.edu.mersin.bapadvisor.common.StructuredSubmissionDto;
import tr.edu.mersin.bapadvisor.common.SubmissionType;

public class DurationEvaluator implements RuleEvaluator {

    @Override
    public boolean supports(StructuredSubmissionDto submission) {
        if (submission == null) {
            return false;
        }
        SubmissionType type = submission.detectedSubmissionType();
        return type == SubmissionType.NEW_PROJECT
                || type == SubmissionType.ADDITIONAL_TIME
                || submission.requestedDurationMonths() != null;
    }

    @Override
    public List<FindingDto> evaluate(StructuredSubmissionDto submission, RuleLimitsContext context) {
        List<FindingDto> findings = new ArrayList<>();
        if (submission == null) {
            return findings;
        }

        SubmissionType type = submission.detectedSubmissionType();
        if (type == SubmissionType.ADDITIONAL_TIME) {
            evaluateAdditionalTime(submission, context, findings);
        } else {
            evaluateNewProjectDuration(submission, context, findings);
        }

        return findings;
    }

    private void evaluateNewProjectDuration(StructuredSubmissionDto submission, RuleLimitsContext context, List<FindingDto> findings) {
        Integer requestedMonths = submission.requestedDurationMonths();
        String code = submission.detectedProjectCode();
        int cap = context.getProjectDurationCapMonths(code);

        if (requestedMonths == null || requestedMonths <= 0) {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_DURATION_MAX_36M,
                    RuleCategory.DURATION,
                    FindingSeverity.MISSING_DOCUMENT,
                    "Talep Edilen Proje Süresi Eksik / Belirtilmemiş",
                    "Başvuru formunda geçerli bir talep edilen proje süresi (ay) tespit edilemedi"
                            + (requestedMonths != null && requestedMonths == 0 ? " (0 ay olarak okundu)." : "."),
                    DeterministicRuleConstants.DOC_USUL_ESASLAR,
                    "Madde 8/1",
                    "Projelerin başlangıç ve bitiş tarihleri ile talep edilen toplam süre formda açıkça belirtilmelidir.",
                    "1 - " + cap + " ay",
                    requestedMonths != null && requestedMonths == 0 ? "0 ay (Eksik)" : "Belirtilmemiş"
            ));
            return;
        }

        boolean isThesis = code != null && code.toUpperCase().startsWith("TP");

        if (requestedMonths > cap) {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_DURATION_MAX_36M,
                    RuleCategory.DURATION,
                    FindingSeverity.VIOLATION,
                    "Azami Proje Süresi Aşımı (" + (code != null ? code : "BAP") + ")",
                    (code != null ? code : "Proje") + " için talep edilen süre (" + requestedMonths
                            + " ay), mevzuatta izin verilen azami süreyi (" + cap + " ay) aşmaktadır.",
                    DeterministicRuleConstants.DOC_USUL_ESASLAR,
                    isThesis ? "Madde 4 ve Madde 8/1" : "Madde 5 ve Madde 8/1",
                    "BAP projelerinin süresi, tez projeleri hariç ek süreler dâhil en çok 36 aydır. TP1 azami 12 ay, TP2 azami 24 ay, TP3 azami 36 aydır.",
                    "<= " + cap + " ay",
                    requestedMonths + " ay"
            ));
        } else {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_DURATION_MAX_36M,
                    RuleCategory.DURATION,
                    FindingSeverity.COMPLIANT,
                    "Proje Süresi Mevzuata Uygun (" + (code != null ? code : "BAP") + ")",
                    "Talep edilen proje süresi (" + requestedMonths + " ay), " + (code != null ? code : "ilgili proje")
                            + " için mevzuatta belirlenen azami süre (" + cap + " ay) dâhilindedir.",
                    DeterministicRuleConstants.DOC_USUL_ESASLAR,
                    "Madde 8/1",
                    "Proje süreleri ilgili proje türü için belirlenen azami süreyi aşamaz.",
                    "<= " + cap + " ay",
                    requestedMonths + " ay"
            ));
        }
    }

    private void evaluateAdditionalTime(StructuredSubmissionDto submission, RuleLimitsContext context, List<FindingDto> findings) {
        Integer existingMonths = submission.existingDurationMonths();
        Integer requestedMonths = submission.requestedDurationMonths();
        String code = submission.detectedProjectCode();

        if (existingMonths == null || existingMonths <= 0 || requestedMonths == null || requestedMonths <= 0) {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_DURATION_MAX_36M,
                    RuleCategory.DURATION,
                    FindingSeverity.MISSING_DOCUMENT,
                    "Ek Süre Bilgileri Eksik",
                    "Ek süre değerlendirmesi için mevcut sözleşme süresi veya talep edilen ek süre bilgisi eksiktir.",
                    DeterministicRuleConstants.DOC_USUL_ESASLAR,
                    "Madde 8/2",
                    "Yürütücüler güncel sözleşme süresini ve talep edilen ek süreyi gerekçesiyle belirtmelidir.",
                    "Mevcut ve Ek Süre (> 0 ay)",
                    "Eksik"
            ));
            return;
        }

        int totalMonths = existingMonths + requestedMonths;
        boolean isThesis = code != null && code.toUpperCase().startsWith("TP");

        if (isThesis) {
            int normalThesisDuration = context.getProjectDurationCapMonths(code);
            if (totalMonths > normalThesisDuration) {
                findings.add(FindingBuilder.create(
                        DeterministicRuleConstants.RULE_DURATION_MAX_36M,
                        RuleCategory.DURATION,
                        FindingSeverity.AMBIGUOUS_COMMISSION,
                        "Tez Projesi Ek Süre - Öğrenim Durumu Komisyon İncelemesi",
                        "Tez projesinde toplam süre (" + totalMonths + " ay) normal program süresini ("
                                + normalThesisDuration + " ay) aşmaktadır. Lisansüstü öğrencinin aktif öğrencilik ve azami öğrenim süresi durumu Enstitü kararı ile BAP Komisyonu tarafından değerlendirilmelidir.",
                        DeterministicRuleConstants.DOC_USUL_ESASLAR,
                        "Madde 4 ve Madde 8",
                        "Tez projelerinde süre ve ek süreler öğrencinin lisansüstü azami öğrenim süresine ve Enstitü Yönetim Kurulu kararlarına bağlıdır.",
                        "Enstitü Kararı ve Komisyon İncelemesi",
                        totalMonths + " ay"
                ));
            } else {
                findings.add(FindingBuilder.create(
                        DeterministicRuleConstants.RULE_DURATION_MAX_36M,
                        RuleCategory.DURATION,
                        FindingSeverity.COMPLIANT,
                        "Tez Projesi Ek Süre Uygun",
                        "Ek süre dâhil toplam tez proje süresi (" + totalMonths + " ay) normal program süresi ("
                                + normalThesisDuration + " ay) dâhilindedir.",
                        DeterministicRuleConstants.DOC_USUL_ESASLAR,
                        "Madde 4 ve Madde 8",
                        "Tez projeleri azami öğrenim süresi dâhilinde yürütülür.",
                        "<= " + normalThesisDuration + " ay",
                        totalMonths + " ay"
                ));
            }
            return;
        }

        // Non-thesis projects
        if (totalMonths > DeterministicRuleConstants.MAX_DURATION_NON_THESIS_MONTHS) {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_DURATION_MAX_36M,
                    RuleCategory.DURATION,
                    FindingSeverity.VIOLATION,
                    "36 Ay Azami Süre Kuralı İhlali",
                    "Mevcut süre (" + existingMonths + " ay) ile talep edilen ek sürenin (" + requestedMonths
                            + " ay) toplamı (" + totalMonths + " ay), MEÜ BAP mevzuatında belirlenen 36 aylık kesin üst sınırı aşmaktadır.",
                    DeterministicRuleConstants.DOC_USUL_ESASLAR,
                    "Madde 8/1",
                    "BAP projelerinin süresi, tez projeleri hariç olmak üzere, ek süreler de dâhil en çok 36 aydır. Hiçbir proje 36 ayı aşamaz.",
                    "<= 36 ay",
                    totalMonths + " ay"
            ));
            return;
        }

        // Check 50% extension limit
        int maxAllowedExtension = (int) Math.ceil(existingMonths * 0.50);
        if (requestedMonths > maxAllowedExtension) {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_DURATION_MAX_36M,
                    RuleCategory.DURATION,
                    FindingSeverity.AMBIGUOUS_COMMISSION,
                    "Ek Süre Yüzde 50 Sınırı İncelemesi",
                    "Talep edilen ek süre (" + requestedMonths + " ay), orijinal proje süresinin (" + existingMonths
                            + " ay) yüzde 50'sini (" + maxAllowedExtension + " ay) aşmaktadır; BAP Komisyonu özel takdirine sunulmalıdır.",
                    DeterministicRuleConstants.DOC_USUL_ESASLAR,
                    "Madde 8/3",
                    "Verilecek toplam ek süre, projenin ilk sözleşmesinde kabul edilen orijinal sürenin %50'sini aşamaz.",
                    "<= " + maxAllowedExtension + " ay",
                    requestedMonths + " ay"
            ));
        } else {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_DURATION_MAX_36M,
                    RuleCategory.DURATION,
                    FindingSeverity.COMPLIANT,
                    "Ek Süre Talebi Mevzuata Uygun",
                    "Toplam süre (" + totalMonths + " ay) ve talep edilen ek süre (" + requestedMonths
                            + " ay), azami 36 ay ve %50 yasal sınırlarına uygundur.",
                    DeterministicRuleConstants.DOC_USUL_ESASLAR,
                    "Madde 8/1 ve 8/3",
                    "Toplam süre 36 ayı ve orijinal sürenin %50'sini aşamaz.",
                    "<= 36 ay (ve <= %50)",
                    totalMonths + " ay"
            ));
        }
    }
}
