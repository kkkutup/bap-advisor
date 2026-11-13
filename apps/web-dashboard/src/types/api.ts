// Types strictly matching contracts/openapi-core-public.yaml

export type UserRole = 'ADMIN' | 'COORDINATOR' | 'OFFICER' | 'COMMISSION_VIEWER';

export type SubmissionType =
  | 'NEW_PROJECT'
  | 'OPEN_ACCESS'
  | 'ADDITIONAL_BUDGET'
  | 'ADDITIONAL_TIME'
  | 'PROCUREMENT_ADVANCE';

export type SubmissionStatus =
  | 'DRAFT'
  | 'OCR_PROCESSING'
  | 'OCR_FAILED'
  | 'EXTRACTION_REVIEW'
  | 'EVALUATING'
  | 'OFFICER_REVIEW'
  | 'REVISION_REQUESTED'
  | 'COMMISSION_REFERRED'
  | 'REJECTED_FORMAL';

export type DocumentRole =
  | 'APPLICATION_FORM'
  | 'BUDGET_TABLE'
  | 'PROFORMA_INVOICE'
  | 'TECHNICAL_SPEC'
  | 'ETHICS_APPROVAL'
  | 'THESIS_PROPOSAL'
  | 'SIGNED_PETITION'
  | 'PUBLICATION_PROOF'
  | 'OTHER';

export type BudgetCategory =
  | 'CONSUMABLE'
  | 'EQUIPMENT'
  | 'SERVICE_PURCHASE'
  | 'TRAVEL'
  | 'PUBLICATION'
  | 'OTHER';

export type FindingSeverity =
  | 'COMPLIANT'
  | 'VIOLATION'
  | 'MISSING_DOCUMENT'
  | 'AMBIGUOUS_COMMISSION';

export type FindingSource = 'DETERMINISTIC_RULE' | 'RAG_LLM';

export type RuleCategory =
  | 'BUDGET'
  | 'PROJECT_BUDGET'
  | 'DURATION'
  | 'ELIGIBILITY'
  | 'DOCUMENTS'
  | 'PROCUREMENT'
  | 'PER_DIEM'
  | 'OPEN_ACCESS'
  | 'FORMAL_SIGNATURE';

export interface UserProfileResponse {
  id: string;
  email: string;
  full_name: string;
  role: UserRole;
  is_active: boolean;
  created_at?: string;
}

export interface AuthTokenResponse {
  access_token: string;
  refresh_token?: string;
  token_type: string;
  expires_in_seconds: number;
  user: UserProfileResponse;
}

export interface BudgetLineItemDto {
  category: BudgetCategory;
  name: string;
  quantity?: number | null;
  unit?: string | null;
  unit_price_tl?: number | null;
  total_tl: number;
  vat_included?: boolean | null;
  days?: number | null;
  daily_rate_tl?: number | null;
  proforma_count?: number | null;
  has_technical_spec?: boolean | null;
}

export interface StructuredSubmissionDto {
  detected_submission_type: SubmissionType;
  detected_project_code?: string | null;
  applicant_name?: string | null;
  applicant_title?: string | null;
  department?: string | null;
  project_title?: string | null;
  requested_budget_total_tl?: number | null;
  existing_approved_budget_tl?: number | null;
  requested_duration_months?: number | null;
  existing_duration_months?: number | null;
  budget_line_items: BudgetLineItemDto[];
  journal_quartile?: 'Q1_TOP10' | 'Q1_OTHER' | 'Q2' | 'Q3' | 'Q4' | null;
  wet_signature_present: boolean;
  ethics_required_keywords_detected: boolean;
  detected_attachments: DocumentRole[];
  notes?: string | null;
}

export interface EvaluationModuleFlagsDto {
  run_deterministic_rules?: boolean;
  run_rag_checklist?: boolean;
  generate_action_drafts?: boolean;
  llm_provider_override?: string | null;
  llm_model_override?: string | null;
}

export interface FindingDto {
  id?: string | null;
  source_type: FindingSource;
  rule_code: string;
  category: RuleCategory;
  severity: FindingSeverity;
  title_tr: string;
  explanation_tr: string;
  regulation_doc_name: string;
  regulation_article_ref: string;
  verbatim_quote?: string | null;
  expected_value?: string | null;
  actual_value?: string | null;
  officer_override_status?: 'APPROVED' | 'REJECTED' | 'WAIVED' | null;
  officer_override_reason?: string | null;
}

export interface SubmissionDocumentDto {
  id: string;
  file_name: string;
  mime_type: string;
  file_size_bytes: number;
  doc_role: DocumentRole;
  ocr_confidence?: number | null;
  has_signature_detected?: boolean | null;
  storage_path: string;
  parsed_markdown?: string | null;
}

export interface SubmissionVersionResponse {
  id: string;
  submission_id: string;
  version_no: number;
  extracted_metadata?: StructuredSubmissionDto;
  selected_modules?: EvaluationModuleFlagsDto;
  officer_notes?: string | null;
  submitted_at: string;
  documents: SubmissionDocumentDto[];
}

export interface SubmissionDetailResponse {
  id: string;
  reference_no: string;
  external_bap2_id?: string | null;
  submission_type: SubmissionType;
  project_code?: string | null;
  title: string;
  applicant_name: string;
  applicant_title?: string | null;
  department?: string | null;
  current_version_no: number;
  status: SubmissionStatus;
  assigned_officer_id?: string | null;
  assigned_officer_name?: string | null;
  created_at: string;
  updated_at?: string;
  latest_version?: SubmissionVersionResponse;
}

export interface PageSubmissionResponse {
  content: SubmissionDetailResponse[];
  total_elements: number;
  total_pages: number;
  number: number;
  size: number;
}

export interface EvaluationResultResponse {
  id: string;
  version_id: string;
  llm_provider_used: string;
  llm_model_name: string;
  overall_compliance_status: 'COMPLIANT' | 'NEEDS_REVISION' | 'COMMISSION_REVIEW' | 'FORMAL_REJECTION';
  revision_draft_tr?: string | null;
  commission_referral_summary_tr?: string | null;
  findings: FindingDto[];
  started_at: string;
  completed_at: string;
}

export interface VersionDiffResponse {
  from_version: number;
  to_version: number;
  budget_diff_tl: number;
  duration_diff_months: number;
  budget_items_added: BudgetLineItemDto[];
  budget_items_removed: BudgetLineItemDto[];
  budget_items_modified: Array<{
    item_name: string;
    old_total_tl: number;
    new_total_tl: number;
    reason_tr?: string;
  }>;
  resolved_findings: FindingDto[];
  new_findings: FindingDto[];
  unresolved_findings: FindingDto[];
  documents_added: string[];
  documents_removed: string[];
  summary_tr: string;
}

export interface FindingOverrideItem {
  finding_id: string;
  override_status: 'APPROVED' | 'REJECTED' | 'WAIVED';
  override_reason: string;
}

export interface StatusTransitionRequest {
  target_status: SubmissionStatus;
  officer_notes?: string | null;
  finding_overrides?: FindingOverrideItem[];
}

export interface QaCitation {
  regulation_doc_name: string;
  regulation_article_ref: string;
  verbatim_quote: string;
  score?: number | null;
}

export interface QaAnswerResponse {
  answer_tr: string;
  citations: QaCitation[];
  llm_provider_used: string;
}

export interface RegulationDocumentDto {
  id: string;
  slug: string;
  title_tr: string;
  doc_type: 'YONETMELIK' | 'YONERGE' | 'KILAVUZ' | 'ESASLAR' | 'REHBER' | 'CETVEL';
  version_label?: string | null;
  source_url?: string | null;
  is_active: boolean;
  ingested_at?: string | null;
}

export interface LimitConfigDto {
  id: string;
  fiscal_year: number;
  rule_category: RuleCategory;
  code: string;
  title_tr: string;
  max_amount_tl: number;
  max_duration_months?: number | null;
  currency: string;
  vat_included: boolean;
  conditions_json?: Record<string, any> | null;
  effective_from: string;
  is_active: boolean;
}

export interface AnalyticsOverviewResponse {
  total_submissions_count: number;
  status_breakdown: Record<string, number>;
  submission_type_breakdown: Record<string, number>;
  top_violated_articles: Array<{
    article_ref: string;
    doc_name: string;
    violation_count: number;
  }>;
  average_review_hours: number;
}

export interface ProviderStatusResponse {
  active_provider: 'nanogpt' | 'ollama' | 'vllm';
  active_model: string;
  airgapped_mode: boolean;
  available_providers: string[];
}

export interface ProviderConfigDto {
  llm_provider: 'nanogpt' | 'ollama' | 'vllm';
  nanogpt_api_key?: string | null;
  nanogpt_model?: string | null;
  ollama_base_url?: string | null;
  ollama_model?: string | null;
  vllm_base_url?: string | null;
  vllm_model?: string | null;
  airgapped_mode?: boolean | null;
}

export interface Bap2ImportRequest {
  external_bap2_id: string;
  submission_type: SubmissionType;
  project_code?: string | null;
  title: string;
  applicant_name: string;
  applicant_title?: string | null;
  department?: string | null;
  structured_data: StructuredSubmissionDto;
  file_urls?: Array<{
    url: string;
    file_name: string;
    doc_role: DocumentRole;
  }>;
}

export interface Bap2ExportResponse {
  submission_id: string;
  reference_no: string;
  external_bap2_id?: string | null;
  status: SubmissionStatus;
  overall_compliance_status: string;
  revision_draft_tr?: string | null;
  commission_referral_summary_tr?: string | null;
  findings: FindingDto[];
}
