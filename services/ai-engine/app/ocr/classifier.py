import re
from typing import Any, Dict, List, Optional
from app.schemas import DocumentRole


class DocumentClassifier:
    """
    Classifies documents into DocumentRole based on file name,
    content headings, and structural markers.
    """

    ROLE_PATTERNS = {
        DocumentRole.APPLICATION_FORM: {
            "filename": [
                r"ba[sş]vuru", r"proje_?oneri", r"ek_?s[uü]re", r"ek_?b[uü]t[cç]e",
                r"a[cç][iı]k_?eri[sş]im", r"application", r"form"
            ],
            "content": [
                r"bap (?:proje )?ba[sş]vuru formu",
                r"proje [oö]neri formu",
                r"ek s[uü]re talep formu",
                r"ek b[uü]t[cç]e talep formu",
                r"a[cç][iı]k eri[sş]im yay[iı]n bas[iı]m [uü]creti deste[gğ]i talep formu",
                r"mersin [uü]niversitesi bilimsel ara[sş]t[iı]rma projeleri",
                r"bap otomasyon sistemi",
                r"proje t[uü]r[uü] ve ba[sş]vuru bilgileri"
            ]
        },
        DocumentRole.BUDGET_TABLE: {
            "filename": [
                r"b[uü]t[cç]e", r"budget", r"harcama", r"kalemler", r"maliyet"
            ],
            "content": [
                r"b[uü]t[cç]e tablosu",
                r"b[uü]t[cç]e d[oö]k[uü]m[uü]",
                r"gider kalemleri",
                r"proje maliyet [cç]izelgesi",
                r"harcama kalemleri",
                r"sarf malzeme b[uü]t[cç]esi"
            ]
        },
        DocumentRole.PROFORMA_INVOICE: {
            "filename": [
                r"proforma", r"fatura", r"teklif", r"invoice", r"quote"
            ],
            "content": [
                r"proforma fatura",
                r"fiyat teklif mektubu",
                r"proforma invoice",
                r"fiyat teklifi",
                r"teklif veren firma",
                r"teklif no",
                r"kdv hari[cç] tutar"
            ]
        },
        DocumentRole.TECHNICAL_SPEC: {
            "filename": [
                r"[sş]artname", r"teknik", r"spec"
            ],
            "content": [
                r"teknik [sş]artname",
                r"teknik [oö]zellikler",
                r"malzeme teknik [sş]artnamesi",
                r"cihaz teknik [sş]artnamesi",
                r"asgari teknik [oö]zellikler"
            ]
        },
        DocumentRole.ETHICS_APPROVAL: {
            "filename": [
                r"etik", r"ethics", r"kurul_?onay"
            ],
            "content": [
                r"etik kurul(?:u)? onay[iı]",
                r"etik komisyonu karar[iı]",
                r"hayvan deneyleri yerel etik kurulu",
                r"giri[sş]imsel olmayan klinik ara[sş]t[iı]rmalar etik kurulu",
                r"etik kurul belgesi",
                r"etik onay no"
            ]
        },
        DocumentRole.THESIS_PROPOSAL: {
            "filename": [
                r"tez_?oneri", r"tez_?kabul", r"enstit[uü]", r"thesis"
            ],
            "content": [
                r"tez [oö]nerisi kabul",
                r"enstit[uü] y[oö]netim kurulu karar[iı]",
                r"tez j[uü]risi onay belgesi",
                r"y[uü]ksek lisans tez [oö]nerisi",
                r"doktora tez konusu kabul",
                r"tez ba[sş]l[iı][gğ][iı] onay"
            ]
        },
        DocumentRole.SIGNED_PETITION: {
            "filename": [
                r"dilek[cç]e", r"petition", r"talep_?yaz[iı]s[iı]"
            ],
            "content": [
                r"bap koordinat[oö]rl[uü][gğ][uü]ne",
                r"bap koordinasyon birimi makam[iı]na",
                r"dilek[cç]e",
                r"bilgilerinize ve gere[gğ]ine arz ederim",
                r"ek s[uü]re verilmesi hususunda"
            ]
        },
        DocumentRole.PUBLICATION_PROOF: {
            "filename": [
                r"yay[iı]n", r"publication", r"kabul_?mektup", r"acceptance",
                r"wos", r"jcr", r"doi"
            ],
            "content": [
                r"kabul mektubu",
                r"acceptance letter",
                r"web of science",
                r"journal citation reports",
                r"quartile score",
                r"dergi [cç]eyreklik",
                r"article processing charge",
                r"makale bas[iı]m [uü]creti kan[iı]t[iı]"
            ]
        }
    }

    @classmethod
    def classify_document(
        cls,
        file_name: str,
        content_markdown: str,
        parsed_tables: Optional[List[Dict[str, Any]]] = None
    ) -> DocumentRole:
        """
        Classifies document role based on file name, markdown text, and tables.
        """
        norm_filename = file_name.lower().replace("-", "_").replace(" ", "_")
        norm_content = content_markdown.lower()

        scores: Dict[DocumentRole, int] = {role: 0 for role in DocumentRole}

        # Check content first (higher weight)
        for role, rules in cls.ROLE_PATTERNS.items():
            for pat in rules["content"]:
                if re.search(pat, norm_content):
                    scores[role] += 5

            for pat in rules["filename"]:
                if re.search(pat, norm_filename):
                    scores[role] += 3

        # Extra table heuristics
        if parsed_tables and len(parsed_tables) > 0:
            if scores[DocumentRole.BUDGET_TABLE] > 0:
                scores[DocumentRole.BUDGET_TABLE] += 2
            # If Excel file with budget sheets
            if norm_filename.endswith((".xlsx", ".xls")):
                scores[DocumentRole.BUDGET_TABLE] += 4

        # Find max role
        best_role = DocumentRole.OTHER
        max_score = 0

        for role, score in scores.items():
            if score > max_score:
                max_score = score
                best_role = role

        return best_role if max_score >= 3 else DocumentRole.OTHER
