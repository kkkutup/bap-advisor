import logging
import re
from typing import List
from app.schemas import FindingItem, FindingSeverity

logger = logging.getLogger(__name__)

SCIENTIFIC_JUDGMENT_KEYWORDS = [
    r'\bbilimsel\s+değer\b',
    r'\bbilimsel\s+kalite\b',
    r'\bözgün\s+değer\b',
    r'\bhipotez\s+yetersiz\b',
    r'\bakademik\s+özgünlük\b',
    r'\bmetodoloji\s+(?:zayıf|yetersiz|hatalı)\b',
    r'\byöntem\s+(?:zayıf|yetersiz|hatalı)\b',
    r'\baraştırma\s+konusu\s+önemsiz\b',
]


def check_and_sanitize_finding(item: FindingItem) -> FindingItem:
    """
    Post-validates a finding item against the strict domain guardrail.
    If scientific merit or academic quality is being judged, adjusts severity to AMBIGUOUS_COMMISSION
    and redirects the decision to the Professor Commission.
    Ensures regulation citations are present.
    """
    text_to_check = f"{item.title_tr} {item.explanation_tr}".lower()

    for pat in SCIENTIFIC_JUDGMENT_KEYWORDS:
        if re.search(pat, text_to_check):
            logger.warning(
                f"Domain guardrail triggered on finding {item.rule_code}: "
                f"Scientific judgment phrase detected. Converting to AMBIGUOUS_COMMISSION."
            )
            item.severity = FindingSeverity.AMBIGUOUS_COMMISSION
            item.title_tr = f"[Komisyon Takdiri] {item.title_tr}"
            item.explanation_tr = (
                f"{item.explanation_tr}\n"
                f"[ÖNEMLİ KURAL NOTU: Projenin bilimsel içeriği, metodolojisi veya akademik yeterliliği "
                f"ön inceleme memuru veya AI sistemi tarafından denetlenemez. Bu husustaki nihai takdir "
                f"BAP Komisyonuna (Profesör Kurulu) aittir.]"
            )
            break

    # Ensure regulation grounding
    if not item.regulation_doc_name:
        item.regulation_doc_name = "Mersin Üniversitesi BAP Uygulama Usul ve Esasları"
    if not item.regulation_article_ref:
        item.regulation_article_ref = "MADDE 1/3 (Ön İnceleme ve Komisyon Yetkisi)"

    return item


def sanitize_findings_list(findings: List[FindingItem]) -> List[FindingItem]:
    return [check_and_sanitize_finding(f) for f in findings]
