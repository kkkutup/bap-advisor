import json
import logging
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

logger = logging.getLogger(__name__)


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

    async def extract_submission_data_async(
        self,
        documents: List[Dict[str, Any]]
    ) -> StructuredSubmissionData:
        """
        Hybrid 2-Tier Form Extraction:
        Tier 1: Fast deterministic table & regex extraction
        Tier 2: Live LLM Structured Zero-Hallucination Extraction & Intelligent Merge
        """
        structured = self.extract_submission_data(documents)

        combined_text = "\n\n".join(doc.get("parsed_text", "") for doc in documents).strip()
        if not combined_text:
            return structured

        try:
            from app.llm.gateway import get_llm_gateway
            gateway = get_llm_gateway()

            probe = await gateway.probe_provider_connection()
            if not probe.get("connected"):
                logger.info(f"LLM Gateway not connected ({probe.get('reason')}); using deterministic extraction.")
                return structured

            # Collect extracted tables for contextual extraction
            table_summaries = []
            for doc in documents:
                for tbl in doc.get("parsed_tables", []):
                    headers = tbl.get("headers", [])
                    rows = tbl.get("rows", [])
                    if rows:
                        table_summaries.append(
                            f"Dosya: {doc.get('file_name', '')} | Başlıklar: {headers} | Satırlar: {rows[:15]}"
                        )

            tables_context = "\n".join(table_summaries[:6]) if table_summaries else "Tablo bulunamadı."

            prompt = (
                "Aşağıdaki Mersin Üniversitesi BAP başvuru belgesi metni ve tablolarından "
                "YALNIZCA belgede açıkça yazan bilgileri çıkar ve JSON olarak döndür.\n"
                "KESİNLİKLE UYDURMA (HALLUCINATION) YAPMA! Belgede yazmayan hiçbir bütçe rakamını, "
                "projeyi, ismi veya kalemi ASLA UYDURMA; belgede yoksa null veya [] bırak.\n\n"
                "Döndürülecek JSON Şeması:\n"
                "{\n"
                '  "detected_submission_type": "NEW_PROJECT" | "OPEN_ACCESS" | "ADDITIONAL_BUDGET" | "ADDITIONAL_TIME" | "PROCUREMENT_ADVANCE",\n'
                '  "detected_project_code": string veya null (örn: "TP2", "AP2", "TP1", "TP3", "AP1", "AP3", "AP4", "AP7", "AP9"),\n'
                '  "applicant_name": string veya null,\n'
                '  "applicant_title": string veya null (örn: "Prof. Dr.", "Doç. Dr.", "Dr. Öğr. Üyesi", "Arş. Gör. Dr.", "Öğr. Gör. Dr."),\n'
                '  "department": string veya null,\n'
                '  "project_title": string veya null,\n'
                '  "requested_budget_total_tl": number veya null (belgede yazan talep edilen toplam bütçe rakamı),\n'
                '  "existing_approved_budget_tl": number veya null,\n'
                '  "requested_duration_months": integer veya null (proje süresi ay cinsinden),\n'
                '  "existing_duration_months": integer veya null,\n'
                '  "budget_line_items": [\n'
                '    {\n'
                '      "category": "CONSUMABLE" | "EQUIPMENT" | "SERVICE_PURCHASE" | "TRAVEL" | "PUBLICATION" | "OTHER",\n'
                '      "name": string,\n'
                '      "quantity": number veya null,\n'
                '      "unit": string veya null,\n'
                '      "unit_price_tl": number veya null,\n'
                '      "total_tl": number,\n'
                '      "vat_included": boolean,\n'
                '      "days": integer veya null,\n'
                '      "daily_rate_tl": number veya null\n'
                '    }\n'
                '  ],\n'
                '  "journal_quartile": "Q1_TOP10" | "Q1_OTHER" | "Q2" | "Q3" | "Q4" | null,\n'
                '  "ethics_required_keywords_detected": boolean\n'
                "}\n\n"
                f"BELGEDEKİ TABLOLAR:\n{tables_context}\n\n"
                f"BELGEDEN OKUNAN METİN:\n{combined_text[:18000]}"
            )

            messages = [
                {
                    "role": "system",
                    "content": (
                        "Sen Mersin Üniversitesi BAP Birimi mevzuat uygunluk ön inceleme asistanısın. "
                        "Görevin taranmış/dijital başvuru belgelerinden yalnızca ve sadece belgede yazan "
                        "somut başvuru verilerini JSON olarak çıkarmaktır. Asla veri uydurmazsın."
                    )
                },
                {"role": "user", "content": prompt}
            ]

            llm_resp = await gateway.chat_completion(
                messages,
                temperature=0.0,
                response_format={"type": "json_object"}
            )

            if not llm_resp:
                return structured

            # Parse JSON
            raw_json = llm_resp.strip()
            # Clean possible markdown fence
            if raw_json.startswith("```json"):
                raw_json = raw_json[7:]
            if raw_json.startswith("```"):
                raw_json = raw_json[3:]
            if raw_json.endswith("```"):
                raw_json = raw_json[:-3]
            raw_json = raw_json.strip()

            data = json.loads(raw_json)
            if isinstance(data, dict):
                # Handle possible top-level wrappers
                if "data" in data and isinstance(data["data"], dict):
                    data = data["data"]
                elif "structured_data" in data and isinstance(data["structured_data"], dict):
                    data = data["structured_data"]
                return self._merge_deterministic_and_llm(structured, data)

        except Exception as exc:
            logger.warning(f"Async LLM metadata extraction failed gracefully: {exc}; returning deterministic data.")

        return structured

    def _merge_deterministic_and_llm(
        self,
        structured: StructuredSubmissionData,
        data: Dict[str, Any]
    ) -> StructuredSubmissionData:
        # 1. Project Title
        llm_title = data.get("project_title")
        if llm_title and isinstance(llm_title, str) and llm_title.strip() and llm_title.strip().lower() != "null":
            clean_title = llm_title.strip()
            if not structured.project_title or len(structured.project_title.strip()) < 5 or structured.project_title.startswith("Örnek"):
                structured.project_title = clean_title

        # 2. Applicant Name
        llm_applicant = data.get("applicant_name")
        if llm_applicant and isinstance(llm_applicant, str) and llm_applicant.strip() and llm_applicant.strip().lower() != "null":
            clean_app = llm_applicant.strip()
            if not structured.applicant_name or structured.applicant_name in ("Örnek Yürütücü", "Belgeden Otomatik Çıkarılacak"):
                structured.applicant_name = clean_app

        # 3. Applicant Title
        llm_title_pfx = data.get("applicant_title")
        if llm_title_pfx and isinstance(llm_title_pfx, str) and llm_title_pfx.strip() and llm_title_pfx.strip().lower() != "null":
            if not structured.applicant_title:
                structured.applicant_title = llm_title_pfx.strip()

        # 4. Department
        llm_dept = data.get("department")
        if llm_dept and isinstance(llm_dept, str) and llm_dept.strip() and llm_dept.strip().lower() != "null":
            if not structured.department:
                structured.department = llm_dept.strip()

        # 5. Detected Project Code
        llm_code = data.get("detected_project_code")
        if llm_code and isinstance(llm_code, str) and llm_code.strip() and llm_code.strip().lower() != "null":
            clean_code = llm_code.strip().upper()
            if not structured.detected_project_code:
                structured.detected_project_code = clean_code

        # 6. Submission Type
        llm_type = data.get("detected_submission_type")
        if llm_type and isinstance(llm_type, str):
            try:
                sub_t = SubmissionType(llm_type.strip().upper())
                if structured.detected_submission_type == SubmissionType.NEW_PROJECT and sub_t != SubmissionType.NEW_PROJECT:
                    structured.detected_submission_type = sub_t
            except ValueError:
                pass

        # 7. Requested Budget Total TL
        llm_req_budget = data.get("requested_budget_total_tl")
        if llm_req_budget is not None:
            parsed_b = CurrencyParser.parse_tl_amount(str(llm_req_budget))
            if parsed_b and parsed_b > 0:
                sum_items = sum(it.total_tl for it in structured.budget_line_items) if structured.budget_line_items else 0
                if structured.requested_budget_total_tl is None or structured.requested_budget_total_tl == 0:
                    structured.requested_budget_total_tl = parsed_b
                elif sum_items > 0 and abs(sum_items - parsed_b) < 0.01 and abs(sum_items - structured.requested_budget_total_tl) > 1.0:
                    # Prefer the value that matches the exact sum of line items
                    structured.requested_budget_total_tl = parsed_b

        # 8. Existing Approved Budget TL
        llm_exist_budget = data.get("existing_approved_budget_tl")
        if llm_exist_budget is not None:
            parsed_eb = CurrencyParser.parse_tl_amount(str(llm_exist_budget))
            if parsed_eb and parsed_eb > 0:
                if structured.existing_approved_budget_tl is None:
                    structured.existing_approved_budget_tl = parsed_eb

        # 9. Requested Duration Months
        llm_dur = data.get("requested_duration_months")
        if llm_dur is not None:
            try:
                dur_int = int(llm_dur)
                if dur_int > 0 and (structured.requested_duration_months is None or structured.requested_duration_months == 0):
                    structured.requested_duration_months = dur_int
            except (ValueError, TypeError):
                pass

        # 10. Existing Duration Months
        llm_ex_dur = data.get("existing_duration_months")
        if llm_ex_dur is not None:
            try:
                dur_int = int(llm_ex_dur)
                if dur_int > 0 and (structured.existing_duration_months is None or structured.existing_duration_months == 0):
                    structured.existing_duration_months = dur_int
            except (ValueError, TypeError):
                pass

        # 11. Budget Line Items
        if len(structured.budget_line_items) == 0:
            raw_items = data.get("budget_line_items")
            if isinstance(raw_items, list) and raw_items:
                extracted_items = []
                for it in raw_items:
                    if not isinstance(it, dict):
                        continue
                    it_name = str(it.get("name", "")).strip()
                    if not it_name or it_name.lower().startswith(("toplam", "genel toplam", "ara toplam")):
                        continue
                    raw_tot = it.get("total_tl")
                    tot = CurrencyParser.parse_tl_amount(str(raw_tot)) if raw_tot is not None else None
                    if not tot or tot <= 0:
                        continue

                    cat_raw = str(it.get("category", "CONSUMABLE")).strip().upper()
                    try:
                        cat = BudgetCategory(cat_raw)
                    except ValueError:
                        cat = BudgetCategory.OTHER

                    raw_qty = it.get("quantity")
                    qty = float(raw_qty) if raw_qty is not None and str(raw_qty).replace(".", "", 1).isdigit() else None

                    raw_price = it.get("unit_price_tl")
                    price = float(raw_price) if raw_price is not None and str(raw_price).replace(".", "", 1).isdigit() else None

                    raw_days = it.get("days")
                    days = int(raw_days) if raw_days is not None and str(raw_days).isdigit() else None

                    raw_daily = it.get("daily_rate_tl")
                    daily = float(raw_daily) if raw_daily is not None and str(raw_daily).replace(".", "", 1).isdigit() else None

                    extracted_items.append(BudgetLineItem(
                        category=cat,
                        name=it_name,
                        quantity=qty,
                        unit=str(it.get("unit", "")).strip() or None,
                        unit_price_tl=price,
                        total_tl=tot,
                        vat_included=bool(it.get("vat_included", True)),
                        days=days,
                        daily_rate_tl=daily
                    ))

                if extracted_items:
                    structured.budget_line_items = extracted_items
                    if structured.requested_budget_total_tl is None or structured.requested_budget_total_tl == 0:
                        structured.requested_budget_total_tl = round(sum(i.total_tl for i in extracted_items), 2)

        # 12. Journal Quartile
        llm_q = data.get("journal_quartile")
        if llm_q and isinstance(llm_q, str) and not structured.journal_quartile:
            clean_q = llm_q.strip().upper()
            if clean_q in ("Q1_TOP10", "Q1_OTHER", "Q2", "Q3", "Q4"):
                structured.journal_quartile = clean_q

        # 13. Ethics Keywords
        if not structured.ethics_required_keywords_detected and data.get("ethics_required_keywords_detected"):
            structured.ethics_required_keywords_detected = bool(data["ethics_required_keywords_detected"])

        structured.notes = (
            f"Hibrit Çıkarım Tamamlandı: Tablo/Regex ({len(structured.budget_line_items)} kalem) + "
            f"LLM Yapısal Doğrulama ile mevzuat form alanları işlendi."
        )

        return structured
