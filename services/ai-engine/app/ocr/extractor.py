import json
import os
import re
from typing import Any, Dict, List, Optional, Tuple
from app.ocr.currency_parser import CurrencyParser
from app.schemas import (
    BudgetCategory,
    BudgetLineItem,
    DocumentRole,
    StructuredSubmissionData,
    SubmissionType
)


class FormExtractor:
    """
    Configurable MEÜ BAP Form and metadata extractor.
    Uses declarative regex/label aliases from form_field_aliases.json.
    """

    def __init__(self, aliases_path: Optional[str] = None):
        if aliases_path is None:
            aliases_path = os.path.join(os.path.dirname(__file__), "form_field_aliases.json")

        self.aliases: Dict[str, Any] = {}
        if os.path.exists(aliases_path):
            with open(aliases_path, "r", encoding="utf-8") as f:
                self.aliases = json.load(f)

    def extract_submission_data(
        self,
        documents: List[Dict[str, Any]]
    ) -> StructuredSubmissionData:
        """
        Synthesizes StructuredSubmissionData across all documents in a submission package.
        Each document dict has:
        {
            "doc_id": ...,
            "file_name": ...,
            "doc_role": DocumentRole,
            "parsed_text": str,
            "parsed_tables": List[Dict],
            "has_signature": bool,
            "ocr_confidence": float
        }
        """
        combined_text = "\n\n".join(doc.get("parsed_text", "") for doc in documents)

        # Collect tables prioritizing BUDGET_TABLE, then APPLICATION_FORM
        budget_tables: List[Dict[str, Any]] = []
        app_tables: List[Dict[str, Any]] = []
        other_tables: List[Dict[str, Any]] = []

        for doc in documents:
            role = doc.get("doc_role")
            tbls = doc.get("parsed_tables", [])
            if role == DocumentRole.BUDGET_TABLE:
                budget_tables.extend(tbls)
            elif role == DocumentRole.APPLICATION_FORM:
                app_tables.extend(tbls)
            elif role not in (DocumentRole.PROFORMA_INVOICE, DocumentRole.THESIS_PROPOSAL):
                other_tables.extend(tbls)

        primary_tables = budget_tables or app_tables or other_tables

        # 1. Detect Submission Type
        submission_type = self._detect_submission_type(documents, combined_text)

        # 2. Detect Project Code
        project_code = self._detect_project_code(combined_text)

        # 3. Detect Applicant Title & Name
        applicant_title = self._detect_applicant_title(combined_text)
        applicant_name = self._detect_applicant_name(combined_text, applicant_title)

        # 4. Detect Department
        department = self._detect_department(combined_text)

        # 5. Detect Project Title
        project_title = self._detect_project_title(combined_text)

        # 6. Detect Budgets
        budget_items = self._extract_budget_line_items(primary_tables, combined_text)
        requested_budget, existing_budget = self._detect_budgets(combined_text, budget_items)

        # 7. Detect Durations
        req_duration, exist_duration = self._detect_durations(combined_text, submission_type)

        # 8. Detect Journal Quartile
        journal_quartile = self._detect_journal_quartile(combined_text)

        # 9. Detect Ethics Keywords
        ethics_detected = self._detect_ethics_keywords(combined_text)

        # 10. Detected Attachments & Wet Signature
        detected_attachments = list({
            doc.get("doc_role") for doc in documents
            if doc.get("doc_role") and doc.get("doc_role") != DocumentRole.OTHER
        })

        # Signature is present if primary document (APPLICATION_FORM or SIGNED_PETITION) has signature
        wet_signature_present = False
        for doc in documents:
            if doc.get("has_signature", False):
                wet_signature_present = True
                break

        # Generate summary note
        notes = (
            f"Otomatik çıkarım tamamlandı: {len(documents)} belge incelendi, "
            f"{len(budget_items)} bütçe kalemi tespit edildi."
        )

        return StructuredSubmissionData(
            detected_submission_type=submission_type,
            detected_project_code=project_code,
            applicant_name=applicant_name,
            applicant_title=applicant_title,
            department=department,
            project_title=project_title,
            requested_budget_total_tl=requested_budget,
            existing_approved_budget_tl=existing_budget,
            requested_duration_months=req_duration,
            existing_duration_months=exist_duration,
            budget_line_items=budget_items,
            journal_quartile=journal_quartile,
            wet_signature_present=wet_signature_present,
            ethics_required_keywords_detected=ethics_detected,
            detected_attachments=detected_attachments,
            notes=notes
        )

    def _detect_submission_type(
        self,
        documents: List[Dict[str, Any]],
        combined_text: str
    ) -> SubmissionType:
        norm_text = re.sub(r"[\s_\-]+", " ", combined_text.lower())
        spaceless_text = re.sub(r"[\s_\-]+", "", combined_text.lower())
        filenames = " ".join(d.get("file_name", "").lower() for d in documents)
        norm_filenames = re.sub(r"[\s_\-]+", " ", filenames)
        spaceless_filenames = re.sub(r"[\s_\-]+", "", filenames)

        type_aliases = self.aliases.get("submission_types", {})

        def matches_keywords(kw_list):
            for kw in kw_list:
                kw_norm = re.sub(r"[\s_\-]+", " ", kw.lower())
                kw_spaceless = re.sub(r"[\s_\-]+", "", kw.lower())
                if (
                    kw_norm in norm_text
                    or kw_spaceless in spaceless_text
                    or kw_norm in norm_filenames
                    or kw_spaceless in spaceless_filenames
                ):
                    return True
            return False

        # Priority 1: Check Open Access
        if matches_keywords(type_aliases.get("OPEN_ACCESS", [])):
            return SubmissionType.OPEN_ACCESS

        # Priority 2: Additional Time
        if matches_keywords(type_aliases.get("ADDITIONAL_TIME", [])):
            return SubmissionType.ADDITIONAL_TIME

        # Priority 3: Additional Budget
        if matches_keywords(type_aliases.get("ADDITIONAL_BUDGET", [])):
            return SubmissionType.ADDITIONAL_BUDGET

        # Priority 4: Procurement / Advance
        if matches_keywords(type_aliases.get("PROCUREMENT_ADVANCE", [])):
            return SubmissionType.PROCUREMENT_ADVANCE

        # Priority 5: New Project
        if matches_keywords(type_aliases.get("NEW_PROJECT", [])):
            return SubmissionType.NEW_PROJECT

        return SubmissionType.NEW_PROJECT

    def _detect_project_code(self, text: str) -> Optional[str]:
        # Look for explicit project codes: TP1-TP3, AP1-AP4, AP7, AP9
        pattern = r"\b(TP[1-3]|AP[1-4]|AP[79])\b"
        match = re.search(pattern, text)
        if match:
            return match.group(1).upper()

        # Check descriptive project titles
        if "yüksek lisans tez" in text.lower():
            return "TP2"
        if "doktora" in text.lower():
            return "TP3"
        if "lisans öğrencisi" in text.lower():
            return "TP1"
        if "bireysel araştırma" in text.lower():
            return "AP2"
        if "öncelikli alan" in text.lower():
            return "AP3"
        if "kapsamlı araştırma" in text.lower():
            return "AP4"
        if "hızlı destek" in text.lower():
            return "AP7"

        return None

    def _detect_applicant_title(self, text: str) -> Optional[str]:
        titles = self.aliases.get("applicant_titles", [
            "Prof. Dr.", "Doç. Dr.", "Dr. Öğr. Üyesi", "Arş. Gör. Dr.", "Öğr. Gör. Dr."
        ])
        for title in titles:
            if title in text:
                return title
        return None

    def _detect_applicant_name(self, text: str, title: Optional[str]) -> Optional[str]:
        patterns = self.aliases.get("field_patterns", {}).get("applicant_name", [])
        for pat in patterns:
            match = re.search(pat, text, re.IGNORECASE)
            if match:
                raw_name = match.group(1).strip()
                raw_name = raw_name.split("\n")[0].split("|")[0].strip()
                # Clean up if title is included in matched name
                if title and raw_name.startswith(title):
                    raw_name = raw_name[len(title):].strip()
                raw_name = raw_name.replace("YIlmaz", "Yılmaz").replace("I", "ı")
                return raw_name

        # If title exists, look right after title
        if title:
            match = re.search(re.escape(title) + r"\s+([A-ZÇĞİÖŞÜ][a-zçğıöşüI]+(?:\s+[A-ZÇĞİÖŞÜ][a-zçğıöşüI]+){1,3})", text)
            if match:
                name = match.group(1).strip().split("\n")[0].split("|")[0].strip()
                name = name.replace("YIlmaz", "Yılmaz").replace("I", "ı")
                return name

        return None

    def _detect_department(self, text: str) -> Optional[str]:
        patterns = self.aliases.get("field_patterns", {}).get("department", [])
        for pat in patterns:
            match = re.search(pat, text, re.IGNORECASE)
            if match:
                dept = match.group(1).strip()
                # Remove trailing noise
                dept = dept.split("\n")[0].split(";")[0].strip()
                if len(dept) >= 3:
                    return dept
        return None

    def _detect_project_title(self, text: str) -> Optional[str]:
        patterns = self.aliases.get("field_patterns", {}).get("project_title", [])
        for pat in patterns:
            match = re.search(pat, text, re.IGNORECASE)
            if match:
                title = match.group(1).strip()
                title = title.split("\n")[0].strip()
                if len(title) >= 5:
                    return title
        return None

    def _detect_budgets(
        self,
        text: str,
        line_items: List[BudgetLineItem]
    ) -> Tuple[Optional[float], Optional[float]]:
        patterns = self.aliases.get("field_patterns", {})
        req_patterns = patterns.get("requested_budget", [])
        exist_patterns = patterns.get("existing_approved_budget", [])

        requested_budget = None
        existing_budget = None

        for pat in req_patterns:
            for match in re.finditer(pat, text, re.IGNORECASE):
                parsed = CurrencyParser.parse_tl_amount(match.group(1))
                if parsed and parsed > 0:
                    requested_budget = parsed
                    break
            if requested_budget is not None:
                break

        for pat in exist_patterns:
            for match in re.finditer(pat, text, re.IGNORECASE):
                parsed = CurrencyParser.parse_tl_amount(match.group(1))
                if parsed and parsed > 0:
                    existing_budget = parsed
                    break
            if existing_budget is not None:
                break

        # If requested_budget wasn't found in text, sum line items if any
        if requested_budget is None and line_items:
            sum_total = sum(item.total_tl for item in line_items if item.total_tl)
            if sum_total > 0:
                requested_budget = round(sum_total, 2)

        return requested_budget, existing_budget

    def _detect_durations(
        self,
        text: str,
        sub_type: SubmissionType
    ) -> Tuple[Optional[int], Optional[int]]:
        patterns = self.aliases.get("field_patterns", {})
        req_patterns = patterns.get("requested_duration", [])
        exist_patterns = patterns.get("existing_duration", [])

        req_duration = None
        exist_duration = None

        # If ek süre (ADDITIONAL_TIME) text contains "24 ay" (mevcut) and "18 ay" (ek süre)
        if sub_type == SubmissionType.ADDITIONAL_TIME:
            # Check pattern: "mevcut süresi: 24 ay ... talep edilen ek süre: 18 ay"
            m_mevcut = re.search(r"mevcut(?:\s*onaylanan|\s*proje)?\s*s[uü]re(?:si)?[:：|/\s]+([0-9]{1,2})\s*(?:ay)?", text, re.IGNORECASE)
            m_ek = re.search(r"(?:talep\s*edilen\s*)?ek\s*s[uü]re(?:\s*talebi)?[:：|/\s]+([0-9]{1,2})\s*(?:ay)?", text, re.IGNORECASE)
            if m_mevcut:
                exist_duration = int(m_mevcut.group(1))
            if m_ek:
                req_duration = int(m_ek.group(1))

        if req_duration is None:
            for pat in req_patterns:
                for match in re.finditer(pat, text, re.IGNORECASE):
                    months = CurrencyParser.parse_duration_months(match.group(0))
                    if months:
                        req_duration = months
                        break
                if req_duration is not None:
                    break

        if exist_duration is None:
            for pat in exist_patterns:
                for match in re.finditer(pat, text, re.IGNORECASE):
                    months = CurrencyParser.parse_duration_months(match.group(0))
                    if months:
                        exist_duration = months
                        break
                if exist_duration is not None:
                    break

        return req_duration, exist_duration

    def _extract_budget_line_items(
        self,
        tables: List[Dict[str, Any]],
        combined_text: str
    ) -> List[BudgetLineItem]:
        items: List[BudgetLineItem] = []
        category_map = self.aliases.get("budget_categories", {})

        for tbl in tables:
            headers = [str(h).lower() for h in tbl.get("headers", [])]
            rows = tbl.get("rows", [])
            if not rows or len(headers) < 2:
                continue

            # Identify column indexes
            name_idx = self._find_col_index(headers, ["kalem", "malzeme", "açıklama", "aciklama", "adı", "adi", "tanım"])
            cat_idx = self._find_col_index(headers, ["kategori", "tür", "tur", "kalem türü"])
            qty_idx = self._find_col_index(headers, ["miktar", "adet"])
            unit_idx = self._find_col_index(headers, ["birim", "ölçü"])
            unit_price_idx = self._find_col_index(headers, ["birim fiyat", "fiyat", "birim tutar"])
            total_idx = self._find_col_index(headers, ["toplam", "tutar", "toplam tutar", "toplam tl"])
            days_idx = self._find_col_index(headers, ["gün", "gun", "gün sayısı", "gun sayisi"])
            daily_rate_idx = self._find_col_index(headers, ["gündelik", "gunluk", "günlük tutar", "yolluk"])

            # If headers don't have total_idx, maybe last column is total
            if total_idx is None and len(headers) >= 3:
                total_idx = len(headers) - 1

            if name_idx is None and len(headers) >= 2:
                # Fallback: if col 0 is index/sira, name is col 1
                name_idx = 1 if len(headers) > 1 else 0

            for row in rows:
                if not row or len(row) <= (total_idx or 0):
                    continue

                raw_name = row[name_idx] if name_idx is not None and name_idx < len(row) else ""
                clean_name = str(raw_name).strip()
                if not clean_name or clean_name.lower().startswith(("toplam", "genel toplam", "ara toplam")):
                    continue

                # Parse total amount
                raw_total = row[total_idx] if total_idx is not None and total_idx < len(row) else ""
                parsed_total = CurrencyParser.parse_tl_amount(raw_total)
                if parsed_total is None or parsed_total <= 0:
                    continue

                # Parse quantity and unit price
                quantity = None
                if qty_idx is not None and qty_idx < len(row):
                    q_val = CurrencyParser.parse_tl_amount(row[qty_idx])
                    if q_val:
                        quantity = float(q_val)

                unit_price = None
                if unit_price_idx is not None and unit_price_idx < len(row):
                    p_val = CurrencyParser.parse_tl_amount(row[unit_price_idx])
                    if p_val:
                        unit_price = float(p_val)

                unit_str = row[unit_idx].strip() if unit_idx is not None and unit_idx < len(row) else None

                # Days and daily rate for travel
                days = None
                if days_idx is not None and days_idx < len(row):
                    d_val = CurrencyParser.parse_tl_amount(row[days_idx])
                    if d_val:
                        days = int(d_val)

                daily_rate = None
                if daily_rate_idx is not None and daily_rate_idx < len(row):
                    dr_val = CurrencyParser.parse_tl_amount(row[daily_rate_idx])
                    if dr_val:
                        daily_rate = float(dr_val)

                # Determine category
                raw_cat = row[cat_idx] if cat_idx is not None and cat_idx < len(row) else ""
                category = self._match_budget_category(str(raw_cat) + " " + clean_name, category_map)

                # If travel and daily rate or days found
                if category == BudgetCategory.TRAVEL:
                    if days and daily_rate and not parsed_total:
                        parsed_total = round(days * daily_rate, 2)
                    elif days and parsed_total and not daily_rate:
                        daily_rate = round(parsed_total / days, 2)

                items.append(BudgetLineItem(
                    category=category,
                    name=clean_name,
                    quantity=quantity,
                    unit=unit_str,
                    unit_price_tl=unit_price,
                    total_tl=parsed_total,
                    vat_included=True,
                    days=days,
                    daily_rate_tl=daily_rate
                ))

        return items

    def _find_col_index(self, headers: List[str], keywords: List[str]) -> Optional[int]:
        for idx, h in enumerate(headers):
            for kw in keywords:
                if kw in h:
                    return idx
        return None

    def _match_budget_category(
        self,
        text: str,
        category_map: Dict[str, List[str]]
    ) -> BudgetCategory:
        norm = text.lower()
        for cat_name, kw_list in category_map.items():
            for kw in kw_list:
                if kw in norm:
                    return BudgetCategory(cat_name)
        return BudgetCategory.OTHER

    def _detect_journal_quartile(self, text: str) -> Optional[str]:
        norm = text.lower()
        quartile_map = self.aliases.get("journal_quartiles", {})

        # Priority 1: Q1 TOP 10
        for kw in quartile_map.get("Q1_TOP10", []):
            if kw in norm:
                return "Q1_TOP10"

        # Priority 2: Q1 Other
        for kw in quartile_map.get("Q1_OTHER", []):
            if re.search(r"\b" + re.escape(kw) + r"\b", norm):
                return "Q1_OTHER"

        # Priority 3: Q2
        for kw in quartile_map.get("Q2", []):
            if re.search(r"\b" + re.escape(kw) + r"\b", norm):
                return "Q2"

        # Priority 4: Q3
        for kw in quartile_map.get("Q3", []):
            if re.search(r"\b" + re.escape(kw) + r"\b", norm):
                return "Q3"

        # Priority 5: Q4
        for kw in quartile_map.get("Q4", []):
            if re.search(r"\b" + re.escape(kw) + r"\b", norm):
                return "Q4"

        return None

    def _detect_ethics_keywords(self, text: str) -> bool:
        norm = text.lower()
        keywords = self.aliases.get("ethics_keywords", [
            "insan denek", "klinik", "anket", "hasta", "deney hayvanı"
        ])
        for kw in keywords:
            if kw in norm:
                return True
        return False
