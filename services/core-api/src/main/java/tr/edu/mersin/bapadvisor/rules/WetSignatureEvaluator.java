package tr.edu.mersin.bapadvisor.rules;

import java.util.ArrayList;
import java.util.List;
import tr.edu.mersin.bapadvisor.common.FindingDto;
import tr.edu.mersin.bapadvisor.common.FindingSeverity;
import tr.edu.mersin.bapadvisor.common.RuleCategory;
import tr.edu.mersin.bapadvisor.common.StructuredSubmissionDto;

public class WetSignatureEvaluator implements RuleEvaluator {

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

        if (Boolean.TRUE.equals(submission.wetSignaturePresent())) {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_WET_SIGNATURE,
                    RuleCategory.FORMAL_SIGNATURE,
                    FindingSeverity.COMPLIANT,
                    "Yürütücü Islak İmzası Doğrulandı",
                    "Başvuru form ve dilekçelerinde proje yürütücüsünün mavi mürekkepli ıslak imzası tespit edilmiştir.",
                    DeterministicRuleConstants.DOC_USUL_ESASLAR,
                    "Madde 7/2",
                    "Sistemden üretilen formlar yazıcıdan döküm alınarak proje yürütücüsü ve araştırmacılar tarafından mavi tükenmez/dolma kalemle ıslak imzalı olarak imzalanmalıdır.",
                    "Islak imza mevcut (true)",
                    "Mevcut (true)"
            ));
        } else {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_WET_SIGNATURE,
                    RuleCategory.FORMAL_SIGNATURE,
                    FindingSeverity.MISSING_DOCUMENT,
                    "Yürütücü Islak İmzası Eksik",
                    "MEÜ BAP Otomasyonu üzerinden döküm alınan talep belgesinde yürütücünün mavi ıslak imzası tespit edilememiştir. Islak imza taşımayan taranmış belgeler şekli eksiklik nedeniyle iade edilir.",
                    DeterministicRuleConstants.DOC_USUL_ESASLAR,
                    "Madde 7/2",
                    "Islak imza taşımayan taranmış belgeler şekli eksiklik nedeniyle iade edilir.",
                    "Islak imza mevcut (true)",
                    "Eksik (false)"
            ));
        }

        return findings;
    }
}
