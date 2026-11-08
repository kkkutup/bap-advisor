CREATE SCHEMA IF NOT EXISTS core;

CREATE TABLE IF NOT EXISTS core.users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(64) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS core.project_limit_configs (
    id UUID PRIMARY KEY,
    fiscal_year INT NOT NULL,
    rule_category VARCHAR(64) NOT NULL,
    code VARCHAR(64) NOT NULL,
    title_tr VARCHAR(255) NOT NULL,
    max_amount_tl NUMERIC(15,2) NOT NULL,
    max_duration_months INT,
    currency VARCHAR(16) NOT NULL DEFAULT 'TRY',
    vat_included BOOLEAN NOT NULL DEFAULT TRUE,
    conditions_json TEXT,
    effective_from DATE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS core.regulation_records (
    id UUID PRIMARY KEY,
    slug VARCHAR(128) NOT NULL UNIQUE,
    title_tr VARCHAR(255) NOT NULL,
    doc_type VARCHAR(64) NOT NULL,
    version_label VARCHAR(64),
    source_url VARCHAR(500),
    storage_path VARCHAR(500),
    sha256_hash VARCHAR(64),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    ingested_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS core.submissions (
    id UUID PRIMARY KEY,
    reference_no VARCHAR(64) NOT NULL UNIQUE,
    external_bap2_id VARCHAR(64),
    submission_type VARCHAR(64) NOT NULL,
    project_code VARCHAR(64),
    title VARCHAR(500) NOT NULL,
    applicant_name VARCHAR(255) NOT NULL,
    applicant_title VARCHAR(128),
    department VARCHAR(255),
    current_version_no INT NOT NULL DEFAULT 1,
    status VARCHAR(64) NOT NULL DEFAULT 'DRAFT',
    assigned_officer_id UUID,
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS core.submission_versions (
    id UUID PRIMARY KEY,
    submission_id UUID NOT NULL REFERENCES core.submissions(id) ON DELETE CASCADE,
    version_no INT NOT NULL,
    extracted_metadata_json TEXT,
    selected_modules_json TEXT,
    officer_notes TEXT,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS core.submission_documents (
    id UUID PRIMARY KEY,
    version_id UUID NOT NULL REFERENCES core.submission_versions(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(128) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    sha256_hash VARCHAR(64),
    doc_role VARCHAR(64) NOT NULL DEFAULT 'OTHER',
    ocr_status VARCHAR(64) DEFAULT 'PENDING',
    ocr_confidence NUMERIC(6,4),
    has_signature_detected BOOLEAN,
    parsed_markdown TEXT,
    parsed_tables_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS core.evaluations (
    id UUID PRIMARY KEY,
    version_id UUID NOT NULL REFERENCES core.submission_versions(id) ON DELETE CASCADE,
    llm_provider_used VARCHAR(64),
    llm_model_name VARCHAR(128),
    overall_compliance_status VARCHAR(64) NOT NULL,
    deterministic_score_summary TEXT,
    revision_draft_tr TEXT,
    commission_referral_summary_tr TEXT,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS core.evaluation_findings (
    id UUID PRIMARY KEY,
    evaluation_id UUID NOT NULL REFERENCES core.evaluations(id) ON DELETE CASCADE,
    source_type VARCHAR(64) NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    category VARCHAR(64) NOT NULL,
    severity VARCHAR(64) NOT NULL,
    title_tr VARCHAR(255) NOT NULL,
    explanation_tr TEXT NOT NULL,
    regulation_doc_name VARCHAR(255) NOT NULL,
    regulation_article_ref VARCHAR(255) NOT NULL,
    verbatim_quote TEXT,
    expected_value TEXT,
    actual_value TEXT,
    officer_override_status VARCHAR(64),
    officer_override_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS core.qa_messages (
    id UUID PRIMARY KEY,
    version_id UUID NOT NULL REFERENCES core.submission_versions(id) ON DELETE CASCADE,
    user_id UUID,
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    citations_json TEXT,
    llm_provider_used VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS core.audit_logs (
    id UUID PRIMARY KEY,
    actor_user_id UUID,
    action_type VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id VARCHAR(64),
    before_json TEXT,
    after_json TEXT,
    ip_address VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_submissions_ref_no ON core.submissions(reference_no);
CREATE INDEX IF NOT EXISTS idx_submissions_status ON core.submissions(status);
CREATE INDEX IF NOT EXISTS idx_limits_year_code ON core.project_limit_configs(fiscal_year, code);
CREATE INDEX IF NOT EXISTS idx_versions_sub_ver ON core.submission_versions(submission_id, version_no);
CREATE INDEX IF NOT EXISTS idx_docs_version ON core.submission_documents(version_id);
CREATE INDEX IF NOT EXISTS idx_findings_eval ON core.evaluation_findings(evaluation_id);
CREATE INDEX IF NOT EXISTS idx_audit_created ON core.audit_logs(created_at);
