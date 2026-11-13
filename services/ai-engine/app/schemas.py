from datetime import datetime, timezone
from enum import Enum
from typing import Any, Dict, List, Literal, Optional
from uuid import UUID, uuid4
from pydantic import BaseModel, Field, model_validator


# ------------------------------------------------------------------------------
# Enums
# ------------------------------------------------------------------------------
class SubmissionType(str, Enum):
    NEW_PROJECT = "NEW_PROJECT"
    OPEN_ACCESS = "OPEN_ACCESS"
    OPEN_ACCESS_SUPPORT = "OPEN_ACCESS_SUPPORT"
    ADDITIONAL_BUDGET = "ADDITIONAL_BUDGET"
    ADDITIONAL_TIME = "ADDITIONAL_TIME"
    PROCUREMENT_ADVANCE = "PROCUREMENT_ADVANCE"


class DocumentRole(str, Enum):
    APPLICATION_FORM = "APPLICATION_FORM"
    BUDGET_TABLE = "BUDGET_TABLE"
    PROFORMA_INVOICE = "PROFORMA_INVOICE"
    TECHNICAL_SPEC = "TECHNICAL_SPEC"
    ETHICS_APPROVAL = "ETHICS_APPROVAL"
    THESIS_PROPOSAL = "THESIS_PROPOSAL"
    THESIS_PROPOSAL_APPROVAL = "THESIS_PROPOSAL_APPROVAL"
    SIGNED_PETITION = "SIGNED_PETITION"
    PUBLICATION_PROOF = "PUBLICATION_PROOF"
    OTHER = "OTHER"


class BudgetCategory(str, Enum):
    CONSUMABLE = "CONSUMABLE"
    EQUIPMENT = "EQUIPMENT"
    SERVICE = "SERVICE"
    SERVICE_PURCHASE = "SERVICE_PURCHASE"
    SERVICE_PROCUREMENT = "SERVICE_PROCUREMENT"
    TRAVEL = "TRAVEL"
    PUBLICATION = "PUBLICATION"
    OPEN_ACCESS_FEE = "OPEN_ACCESS_FEE"
    OTHER = "OTHER"


class FindingSeverity(str, Enum):
    COMPLIANT = "COMPLIANT"
    VIOLATION = "VIOLATION"
    MISSING_DOCUMENT = "MISSING_DOCUMENT"
    AMBIGUOUS_COMMISSION = "AMBIGUOUS_COMMISSION"


class FindingSource(str, Enum):
    DETERMINISTIC_RULE = "DETERMINISTIC_RULE"
    RAG_LLM = "RAG_LLM"


class RuleCategory(str, Enum):
    BUDGET = "BUDGET"
    DURATION = "DURATION"
    ELIGIBILITY = "ELIGIBILITY"
    DOCUMENTS = "DOCUMENTS"
    PROCUREMENT = "PROCUREMENT"
    OPEN_ACCESS = "OPEN_ACCESS"
    FORMAL_SIGNATURE = "FORMAL_SIGNATURE"


class OverallComplianceStatus(str, Enum):
    COMPLIANT = "COMPLIANT"
    NEEDS_REVISION = "NEEDS_REVISION"
    COMMISSION_REVIEW = "COMMISSION_REVIEW"
    FORMAL_REJECTION = "FORMAL_REJECTION"


# ------------------------------------------------------------------------------
# Core Domain & Submission Models
# ------------------------------------------------------------------------------
class BudgetLineItem(BaseModel):
    category: BudgetCategory
    name: str
    quantity: Optional[float] = None
    unit: Optional[str] = None
    unit_price_tl: Optional[float] = None
    total_tl: float
    vat_included: Optional[bool] = True
    days: Optional[int] = None
    daily_rate_tl: Optional[float] = None
    proforma_count: Optional[int] = None
    has_technical_spec: Optional[bool] = None


class StructuredSubmissionData(BaseModel):
    detected_submission_type: SubmissionType = SubmissionType.NEW_PROJECT
    detected_project_code: Optional[str] = None
    applicant_name: Optional[str] = None
    applicant_title: Optional[str] = None
    department: Optional[str] = None
    project_title: Optional[str] = None
    requested_budget_total_tl: Optional[float] = None
    existing_approved_budget_tl: Optional[float] = None
    requested_duration_months: Optional[int] = None
    existing_duration_months: Optional[int] = None
    budget_line_items: List[BudgetLineItem] = Field(default_factory=list)
    journal_quartile: Optional[Literal["Q1_TOP10", "Q1_OTHER", "Q2", "Q3", "Q4"]] = None
    wet_signature_present: bool = False
    ethics_required_keywords_detected: bool = False
    detected_attachments: List[DocumentRole] = Field(default_factory=list)
    notes: Optional[str] = None

    @model_validator(mode="before")
    @classmethod
    def normalize_submission_data(cls, data: Any) -> Any:
        if isinstance(data, dict):
            if not data.get("detected_submission_type"):
                data["detected_submission_type"] = "NEW_PROJECT"
            if data.get("wet_signature_present") is None:
                data["wet_signature_present"] = False
            if data.get("ethics_required_keywords_detected") is None:
                data["ethics_required_keywords_detected"] = False
            if data.get("budget_line_items") is None:
                data["budget_line_items"] = []
            if data.get("detected_attachments") is None:
                data["detected_attachments"] = []
        return data


# ------------------------------------------------------------------------------
# OCR & Extraction Models
# ------------------------------------------------------------------------------
class FileExtractItem(BaseModel):
    doc_id: UUID
    storage_path: str
    mime_type: str
    file_name: str


class OcrExtractRequest(BaseModel):
    version_id: UUID
    files: List[FileExtractItem]
    enable_vision_fallback: bool = False


class ExtractedDocumentResult(BaseModel):
    doc_id: UUID
    file_name: str
    detected_doc_role: DocumentRole
    ocr_confidence: float
    has_signature_detected: bool
    parsed_markdown: Optional[str] = None
    parsed_tables_json: Optional[List[Dict[str, Any]]] = None


class OcrExtractResponse(BaseModel):
    version_id: UUID
    documents: List[ExtractedDocumentResult]
    structured_data: StructuredSubmissionData


# ------------------------------------------------------------------------------
# Regulation RAG Ingestion Models
# ------------------------------------------------------------------------------
class RegulationIngestRequest(BaseModel):
    regulation_doc_id: UUID
    storage_path: str
    title_tr: str
    doc_type: Literal["YONETMELIK", "YONERGE", "KILAVUZ", "ESASLAR", "REHBER", "CETVEL"]
    replace_previous_slug: Optional[str] = None
    version_label: Optional[str] = None


class RegulationIngestResponse(BaseModel):
    regulation_doc_id: UUID
    slug: str
    total_chunks_created: int
    articles_indexed: List[str]
    status: str = "INDEXED"


# ------------------------------------------------------------------------------
# Evaluation & Findings Models
# ------------------------------------------------------------------------------
class FindingItem(BaseModel):
    id: Optional[UUID] = None
    source_type: FindingSource
    rule_code: str
    category: RuleCategory
    severity: FindingSeverity
    title_tr: str
    explanation_tr: str
    regulation_doc_name: str
    regulation_article_ref: str
    verbatim_quote: Optional[str] = None
    expected_value: Optional[str] = None
    actual_value: Optional[str] = None
    officer_override_status: Optional[Literal["APPROVED", "REJECTED", "WAIVED"]] = None
    officer_override_reason: Optional[str] = None


class EvaluationModuleFlags(BaseModel):
    run_deterministic_rules: bool = True
    run_rag_checklist: bool = True
    generate_action_drafts: bool = True
    llm_provider_override: Optional[Literal["nanogpt", "ollama", "vllm"]] = None
    llm_model_override: Optional[str] = None


class DocumentMarkdownItem(BaseModel):
    doc_id: UUID
    doc_role: DocumentRole
    markdown: str


class EvaluateRequest(BaseModel):
    version_id: UUID = Field(default_factory=uuid4)
    submission_type: SubmissionType = SubmissionType.NEW_PROJECT
    project_code: Optional[str] = None
    structured_data: StructuredSubmissionData
    document_markdowns: List[DocumentMarkdownItem] = Field(default_factory=list)
    deterministic_findings: List[FindingItem] = Field(default_factory=list)
    module_flags: EvaluationModuleFlags = Field(default_factory=EvaluationModuleFlags)

    @model_validator(mode="before")
    @classmethod
    def normalize_fields(cls, data: Any) -> Any:
        if isinstance(data, dict):
            # Accept structured_submission as alias for structured_data
            if "structured_data" not in data and "structured_submission" in data:
                data["structured_data"] = data["structured_submission"]

            # Flatten or nest module_flags
            if "module_flags" not in data:
                flags = {}
                for k in [
                    "run_deterministic_rules",
                    "run_rag_checklist",
                    "generate_action_drafts",
                    "llm_provider_override",
                    "llm_model_override",
                ]:
                    if k in data:
                        flags[k] = data[k]
                data["module_flags"] = flags

            if "document_markdowns" not in data or data["document_markdowns"] is None:
                data["document_markdowns"] = []
            if "deterministic_findings" not in data or data["deterministic_findings"] is None:
                data["deterministic_findings"] = []

            # Infer submission_type if not provided at top-level
            if "submission_type" not in data:
                s_data = data.get("structured_data")
                if isinstance(s_data, dict) and "detected_submission_type" in s_data:
                    data["submission_type"] = s_data["detected_submission_type"]
                else:
                    data["submission_type"] = "NEW_PROJECT"
        return data


class EvaluateResponse(BaseModel):
    evaluation_id: UUID
    overall_compliance_status: OverallComplianceStatus
    rag_findings: List[FindingItem]
    combined_findings: List[FindingItem]
    revision_draft_tr: Optional[str] = None
    commission_referral_summary_tr: Optional[str] = None
    llm_provider_used: str
    llm_model_name: str


# ------------------------------------------------------------------------------
# Interactive Q&A Models
# ------------------------------------------------------------------------------
class ChatMessage(BaseModel):
    role: Literal["user", "assistant", "system"]
    content: str

    @model_validator(mode="before")
    @classmethod
    def normalize_role(cls, data: Any) -> Any:
        if isinstance(data, dict) and "role" in data and isinstance(data["role"], str):
            r = data["role"].strip().lower()
            if r not in ("user", "assistant", "system"):
                r = "user"
            data = dict(data)
            data["role"] = r
        return data


class QaRequest(BaseModel):
    version_id: UUID = Field(default_factory=uuid4)
    question: str
    submission_context: Optional[StructuredSubmissionData] = None
    chat_history: List[ChatMessage] = Field(default_factory=list)


class CitationItem(BaseModel):
    regulation_doc_name: str
    regulation_article_ref: str
    verbatim_quote: str
    score: Optional[float] = None


class QaResponse(BaseModel):
    answer_tr: str
    citations: List[CitationItem]
    llm_provider_used: str
    llm_model_name: str


# ------------------------------------------------------------------------------
# Provider Admin & Health Models
# ------------------------------------------------------------------------------
class ProviderStatusResponse(BaseModel):
    active_provider: Literal["nanogpt", "ollama", "vllm"]
    active_model: str
    available_providers: List[str]
    airgapped_mode: bool
    embedding_model: str
    embedding_dimension: int
    reranker_model: Optional[str] = None


class ProviderConfigRequest(BaseModel):
    llm_provider: Literal["nanogpt", "ollama", "vllm"]
    nanogpt_api_key: Optional[str] = None
    nanogpt_model: Optional[str] = None
    ollama_base_url: Optional[str] = None
    ollama_model: Optional[str] = None
    vllm_base_url: Optional[str] = None
    vllm_model: Optional[str] = None
    airgapped_mode: Optional[bool] = None


class HealthStatus(BaseModel):
    status: str = "UP"
    version: str = "0.1.0"
    airgapped_mode: bool
    llm_provider: str
    embedding_model: str
    timestamp: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))


class ErrorResponse(BaseModel):
    detail: str
    error_code: Optional[str] = None
