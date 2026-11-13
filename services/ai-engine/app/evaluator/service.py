import json
import logging
from typing import Any, Dict, List, Optional
from uuid import uuid4
from app.evaluator.guardrail import sanitize_findings_list
from app.evaluator.prompts import (
    EVALUATE_USER_PROMPT_TEMPLATE,
    QA_SYSTEM_PROMPT,
    STRICT_DOMAIN_GUARDRAIL_SYSTEM_PROMPT,
)
from app.evaluator.synthesizer import GroundedSynthesizer
from app.llm.gateway import get_llm_gateway
from app.rag.retriever import get_retriever
from app.schemas import (
    CitationItem,
    EvaluateRequest,
    EvaluateResponse,
    FindingItem,
    FindingSeverity,
    OverallComplianceStatus,
    QaRequest,
    QaResponse,
)

logger = logging.getLogger(__name__)


class EvaluationService:
    def __init__(self):
        self.retriever = get_retriever()
        self.llm_gateway = get_llm_gateway()
        self.synthesizer = GroundedSynthesizer()

    async def evaluate_submission(self, request: EvaluateRequest) -> EvaluateResponse:
        flags = request.module_flags
        s_data = request.structured_data
        p_code = request.project_code or s_data.detected_project_code
        s_type = request.submission_type

        # 1. Retrieve relevant regulation chunks
        query_terms = [
            str(s_type.value),
            p_code or "",
            f"bütçe üst limiti {s_data.requested_budget_total_tl or ''}",
            f"proje süresi {s_data.requested_duration_months or ''} ay",
            "ıslak imza şartı",
            "proforma fatura ve teknik şartname",
        ]
        if s_data.ethics_required_keywords_detected:
            query_terms.append("etik kurul onay belgesi")
        if s_data.journal_quartile:
            query_terms.append(f"açık erişim {s_data.journal_quartile}")

        retrieval_query = " ".join(query_terms)
        relevant_chunks = self.retriever.retrieve(
            retrieval_query,
            top_k=6,
            project_code=p_code,
            submission_type=s_type.value,
        )

        rag_findings: List[FindingItem] = []
        revision_draft: Optional[str] = None
        referral_summary: Optional[str] = None
        llm_model_name = self.llm_gateway.get_active_model()
        provider_used = self.llm_gateway.provider

        # Check if remote LLM call should be attempted
        llm_used_successfully = False
        if flags.run_rag_checklist or flags.generate_action_drafts:
            llm_text = await self._attempt_llm_evaluation(
                request=request,
                relevant_chunks=relevant_chunks,
                provider_override=flags.llm_provider_override,
                model_override=flags.llm_model_override,
            )
            if llm_text:
                try:
                    # Attempt parse structured JSON
                    parsed = json.loads(llm_text)
                    if "rag_findings" in parsed and isinstance(parsed["rag_findings"], list):
                        for f_dict in parsed["rag_findings"]:
                            f_dict.setdefault("id", uuid4())
                            f_dict.setdefault("source_type", "RAG_LLM")
                            rag_findings.append(FindingItem(**f_dict))
                    revision_draft = parsed.get("revision_draft_tr")
                    referral_summary = parsed.get("commission_referral_summary_tr")
                    llm_used_successfully = True
                except Exception as e:
                    logger.warning(f"Failed to parse LLM evaluation JSON output: {e}; using fallback.")

        # If LLM did not run or failed to return valid JSON, synthesize rule-grounded drafts
        if not llm_used_successfully:
            llm_model_name = f"{llm_model_name}-grounded-synthesizer"
            if flags.run_rag_checklist:
                rag_findings = self.synthesizer.synthesize_rag_findings(
                    submission_type=s_type,
                    project_code=p_code,
                    structured_data=s_data,
                    relevant_chunks=relevant_chunks,
                    deterministic_findings=request.deterministic_findings,
                )

        # Enforce strict domain guardrail on findings
        rag_findings = sanitize_findings_list(rag_findings)

        # Combine deterministic findings + RAG findings
        combined_findings = list(request.deterministic_findings) + list(rag_findings)

        # Compute overall status
        overall_status = self.synthesizer.calculate_overall_status(combined_findings)

        # Generate action drafts if requested and not already provided
        if flags.generate_action_drafts:
            issues = [
                f for f in combined_findings
                if f.severity in (FindingSeverity.VIOLATION, FindingSeverity.MISSING_DOCUMENT)
            ]
            if not revision_draft and issues:
                revision_draft = self.synthesizer.generate_revision_draft(
                    structured_data=s_data,
                    project_code=p_code,
                    issues=issues,
                )
            if not referral_summary:
                referral_summary = self.synthesizer.generate_commission_referral_summary(
                    structured_data=s_data,
                    project_code=p_code,
                    findings=combined_findings,
                )

        return EvaluateResponse(
            evaluation_id=uuid4(),
            overall_compliance_status=overall_status,
            rag_findings=rag_findings,
            combined_findings=combined_findings,
            revision_draft_tr=revision_draft,
            commission_referral_summary_tr=referral_summary,
            llm_provider_used=provider_used,
            llm_model_name=llm_model_name,
        )

    async def _attempt_llm_evaluation(
        self,
        request: EvaluateRequest,
        relevant_chunks: List[Dict[str, Any]],
        provider_override: Optional[str] = None,
        model_override: Optional[str] = None,
    ) -> Optional[str]:
        s_data = request.structured_data
        chunks_context = "\n\n".join([
            f"--- [Mevzuat: {c['regulation_doc_name']} | Madde: {c['regulation_article_ref']}] ---\n{c['content_text']}"
            for c in relevant_chunks
        ])
        det_findings_str = json.dumps(
            [f.model_dump(mode="json") for f in request.deterministic_findings],
            ensure_ascii=False,
            indent=2,
        )

        user_content = EVALUATE_USER_PROMPT_TEMPLATE.format(
            submission_type=request.submission_type.value,
            project_code=request.project_code or s_data.detected_project_code or "BAP",
            applicant_title=s_data.applicant_title or "Öğretim Üyesi",
            applicant_name=s_data.applicant_name or "Yürütücü",
            department=s_data.department or "Bölüm",
            requested_budget=f"{s_data.requested_budget_total_tl:,.2f}" if s_data.requested_budget_total_tl else "0.00 (Belirtilmemiş/Eksik)",
            requested_duration=(
                str(s_data.requested_duration_months)
                if s_data.requested_duration_months and s_data.requested_duration_months > 0
                else "0 (BELİRTİLMEMİŞ / EKSİK)"
            ),
            wet_signature="VAR" if s_data.wet_signature_present else "YOK (Eksik)",
            ethics_required="EVET" if s_data.ethics_required_keywords_detected else "HAYIR",
            attachments=", ".join([a.value for a in s_data.detected_attachments]),
            deterministic_findings_json=det_findings_str,
            rag_chunks_context=chunks_context,
        )

        messages = [
            {"role": "system", "content": STRICT_DOMAIN_GUARDRAIL_SYSTEM_PROMPT},
            {"role": "user", "content": user_content},
        ]

        return await self.llm_gateway.chat_completion(
            messages=messages,
            temperature=0.1,
            response_format={"type": "json_object"},
            provider_override=provider_override,
            model_override=model_override,
            timeout=25.0,
        )

    async def ask_question(self, request: QaRequest) -> QaResponse:
        sub_ctx = request.submission_context
        p_code = sub_ctx.detected_project_code if sub_ctx else None
        s_type = sub_ctx.detected_submission_type.value if sub_ctx and sub_ctx.detected_submission_type else None

        relevant_chunks = self.retriever.retrieve(
            request.question,
            top_k=5,
            project_code=p_code,
            submission_type=s_type,
        )

        provider_used = self.llm_gateway.provider
        model_name = self.llm_gateway.get_active_model()

        # Build submission context block
        submission_summary_lines = []
        if sub_ctx:
            submission_summary_lines.append(f"- Başvuru Türü: {s_type or 'NEW_PROJECT'}")
            submission_summary_lines.append(f"- Proje Kodu: {p_code or 'Belirtilmemiş'}")
            submission_summary_lines.append(f"- Proje Başlığı: {sub_ctx.project_title or 'Belirtilmemiş'}")
            submission_summary_lines.append(
                f"- Yürütücü: {sub_ctx.applicant_title or ''} {sub_ctx.applicant_name or 'Belirtilmemiş'} ({sub_ctx.department or 'Bölüm Belirtilmemiş'})"
            )
            budget_str = f"{sub_ctx.requested_budget_total_tl:,.2f} TL" if sub_ctx.requested_budget_total_tl else "Belirtilmemiş (0 TL)"
            dur_str = (
                f"{sub_ctx.requested_duration_months} Ay"
                if sub_ctx.requested_duration_months and sub_ctx.requested_duration_months > 0
                else "0 Ay (Belirtilmemiş / Eksik)"
            )
            submission_summary_lines.append(f"- Talep Edilen Bütçe: {budget_str}")
            submission_summary_lines.append(f"- Talep Edilen Süre: {dur_str}")
            submission_summary_lines.append(f"- Islak İmza: {'Mevcut' if sub_ctx.wet_signature_present else 'Eksik'}")
            submission_summary_lines.append(
                f"- Etik Kurul Gereksinimi: {'Evet' if sub_ctx.ethics_required_keywords_detected else 'Hayır'}"
            )
            if sub_ctx.budget_line_items:
                items_str = "; ".join(
                    [f"{it.name} ({it.category.value}: {it.total_tl:,.2f} TL)" for it in sub_ctx.budget_line_items[:15]]
                )
                submission_summary_lines.append(f"- Bütçe Kalemleri ({len(sub_ctx.budget_line_items)} adet): {items_str}")
            if sub_ctx.notes:
                submission_summary_lines.append(f"- Başvuru Dosyası İçeriği / Notlar:\n{sub_ctx.notes[:4000]}")
        submission_context_text = "\n".join(submission_summary_lines) if submission_summary_lines else "Başvuru bağlamı belirtilmedi."

        # Try LLM first
        llm_answer = None
        context_chunks_text = "\n\n".join([
            f"[{c['regulation_doc_name']} - {c['regulation_article_ref']}]:\n{c.get('raw_text') or c.get('content_text', '')}"
            for c in relevant_chunks
        ])

        messages: List[Dict[str, str]] = [
            {"role": "system", "content": QA_SYSTEM_PROMPT},
        ]

        # Include recent chat history (up to last 6 messages)
        if request.chat_history:
            for hist_msg in request.chat_history[-6:]:
                if hist_msg.content and hist_msg.content.strip():
                    messages.append({"role": hist_msg.role, "content": hist_msg.content.strip()})

        messages.append({
            "role": "user",
            "content": (
                f"BAŞVURU DOSYASI ÖZETİ:\n{submission_context_text}\n\n"
                f"İLGİLİ MEÜ BAP MEVZUAT METİNLERİ:\n{context_chunks_text}\n\n"
                f"KULLANICI SORUSU: {request.question}\n\n"
                "Cevabını yukarıdaki MEÜ BAP mevzuat maddelerine ve başvuru dosyası verilerine dayandırarak Türkçe resmi üslupla ver."
            ),
        })

        llm_resp = await self.llm_gateway.chat_completion(messages=messages, temperature=0.1, timeout=25.0)
        if llm_resp and len(llm_resp.strip()) > 20:
            llm_answer = llm_resp.strip()

        if llm_answer:
            citations = [
                CitationItem(
                    regulation_doc_name=c["regulation_doc_name"],
                    regulation_article_ref=c["regulation_article_ref"],
                    verbatim_quote=c["verbatim_quote"],
                    score=float(c.get("score", 0.9)),
                )
                for c in relevant_chunks[:3]
            ]
            return QaResponse(
                answer_tr=llm_answer,
                citations=citations,
                llm_provider_used=provider_used,
                llm_model_name=model_name,
            )

        # Fallback to grounded synthesis
        answer_tr, citations = self.synthesizer.answer_qa(
            question=request.question,
            relevant_chunks=relevant_chunks,
            submission_context=sub_ctx,
        )
        return QaResponse(
            answer_tr=answer_tr,
            citations=citations,
            llm_provider_used=provider_used,
            llm_model_name=f"{model_name}-grounded-synthesizer",
        )


_global_eval_service: Optional[EvaluationService] = None


def get_evaluation_service() -> EvaluationService:
    global _global_eval_service
    if _global_eval_service is None:
        _global_eval_service = EvaluationService()
    return _global_eval_service
