CREATE TABLE admission_portal_account (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    email VARCHAR(254) NOT NULL,
    normalized_email VARCHAR(254) NOT NULL,
    display_name VARCHAR(180) NOT NULL,
    phone VARCHAR(40),
    password_hash VARCHAR(100) NOT NULL,
    locale VARCHAR(10) NOT NULL DEFAULT 'fr-MA',
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_admission_portal_email UNIQUE (tenant_id, normalized_email),
    CONSTRAINT uq_admission_portal_account_tenant UNIQUE (id, tenant_id)
);

CREATE TABLE admission_portal_session (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    account_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE,
    ip_address VARCHAR(64),
    user_agent VARCHAR(500),
    CONSTRAINT fk_admission_session_account FOREIGN KEY (account_id, tenant_id)
        REFERENCES admission_portal_account(id, tenant_id)
);

CREATE TABLE admission_campaign (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    academic_year_id UUID,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(180) NOT NULL,
    description VARCHAR(1000),
    opens_on DATE NOT NULL,
    closes_on DATE NOT NULL,
    application_fee NUMERIC(14,2) NOT NULL DEFAULT 0,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'MAD',
    required_document_types TEXT NOT NULL DEFAULT '[]',
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_admission_campaign_year FOREIGN KEY (academic_year_id, tenant_id)
        REFERENCES academic_year(id, tenant_id),
    CONSTRAINT uq_admission_campaign_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_admission_campaign_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_admission_campaign_dates CHECK (opens_on <= closes_on),
    CONSTRAINT ck_admission_campaign_fee CHECK (application_fee >= 0)
);

CREATE TABLE admission_step (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    campaign_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(180) NOT NULL,
    instructions VARCHAR(1000),
    sequence_number INTEGER NOT NULL,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_admission_step_campaign FOREIGN KEY (campaign_id, tenant_id)
        REFERENCES admission_campaign(id, tenant_id),
    CONSTRAINT uq_admission_step_code UNIQUE (tenant_id, campaign_id, code),
    CONSTRAINT uq_admission_step_sequence UNIQUE (tenant_id, campaign_id, sequence_number),
    CONSTRAINT ck_admission_step_sequence CHECK (sequence_number > 0)
);

CREATE TABLE admission_prospect (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    campaign_id UUID,
    first_name VARCHAR(120) NOT NULL,
    last_name VARCHAR(120) NOT NULL,
    email VARCHAR(254),
    phone VARCHAR(40),
    child_name VARCHAR(180),
    desired_grade_code VARCHAR(60),
    source VARCHAR(80) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'NEW_LEAD',
    notes VARCHAR(1000),
    converted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_admission_prospect_campaign FOREIGN KEY (campaign_id, tenant_id)
        REFERENCES admission_campaign(id, tenant_id),
    CONSTRAINT uq_admission_prospect_tenant UNIQUE (id, tenant_id)
);

CREATE TABLE admission_applicant (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    portal_account_id UUID NOT NULL,
    first_name VARCHAR(120) NOT NULL,
    last_name VARCHAR(120) NOT NULL,
    birth_date DATE NOT NULL,
    gender VARCHAR(40),
    nationality_code VARCHAR(2),
    current_school VARCHAR(180),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_admission_applicant_account FOREIGN KEY (portal_account_id, tenant_id)
        REFERENCES admission_portal_account(id, tenant_id),
    CONSTRAINT uq_admission_applicant_tenant UNIQUE (id, tenant_id)
);

CREATE TABLE admission_application (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    campaign_id UUID NOT NULL,
    applicant_id UUID NOT NULL,
    prospect_id UUID,
    desired_campus_id UUID,
    desired_grade_level_id UUID,
    reference_number VARCHAR(40) NOT NULL,
    current_status VARCHAR(40) NOT NULL DEFAULT 'APPLICATION_STARTED',
    form_data TEXT NOT NULL DEFAULT '{}',
    completeness_percent INTEGER NOT NULL DEFAULT 20,
    submitted_at TIMESTAMP WITH TIME ZONE,
    enrolled_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_admission_application_campaign FOREIGN KEY (campaign_id, tenant_id)
        REFERENCES admission_campaign(id, tenant_id),
    CONSTRAINT fk_admission_application_applicant FOREIGN KEY (applicant_id, tenant_id)
        REFERENCES admission_applicant(id, tenant_id),
    CONSTRAINT fk_admission_application_prospect FOREIGN KEY (prospect_id, tenant_id)
        REFERENCES admission_prospect(id, tenant_id),
    CONSTRAINT fk_admission_application_campus FOREIGN KEY (desired_campus_id, tenant_id)
        REFERENCES school_campus(id, tenant_id),
    CONSTRAINT fk_admission_application_grade FOREIGN KEY (desired_grade_level_id, tenant_id)
        REFERENCES grade_level(id, tenant_id),
    CONSTRAINT uq_admission_application_reference UNIQUE (tenant_id, reference_number),
    CONSTRAINT uq_admission_application_campaign_applicant UNIQUE
        (tenant_id, campaign_id, applicant_id),
    CONSTRAINT uq_admission_application_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_admission_completeness CHECK (completeness_percent BETWEEN 0 AND 100)
);

CREATE TABLE admission_application_status (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    application_id UUID NOT NULL,
    from_status VARCHAR(40),
    to_status VARCHAR(40) NOT NULL,
    comment VARCHAR(500),
    changed_by_user_id UUID,
    changed_by_portal_account_id UUID,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_admission_status_application FOREIGN KEY (application_id, tenant_id)
        REFERENCES admission_application(id, tenant_id),
    CONSTRAINT fk_admission_status_portal_account FOREIGN KEY
        (changed_by_portal_account_id, tenant_id)
        REFERENCES admission_portal_account(id, tenant_id)
);

CREATE TABLE admission_document (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    application_id UUID NOT NULL,
    document_type VARCHAR(80) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    binary_data BYTEA NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'UPLOADED',
    reviewed_by_user_id UUID,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    rejection_reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_admission_document_application FOREIGN KEY (application_id, tenant_id)
        REFERENCES admission_application(id, tenant_id),
    CONSTRAINT uq_admission_document_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_admission_document_size CHECK (size_bytes > 0)
);

CREATE TABLE admission_visit (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    prospect_id UUID,
    application_id UUID,
    campus_id UUID NOT NULL,
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(30) NOT NULL,
    notes VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_admission_visit_prospect FOREIGN KEY (prospect_id, tenant_id)
        REFERENCES admission_prospect(id, tenant_id),
    CONSTRAINT fk_admission_visit_application FOREIGN KEY (application_id, tenant_id)
        REFERENCES admission_application(id, tenant_id),
    CONSTRAINT fk_admission_visit_campus FOREIGN KEY (campus_id, tenant_id)
        REFERENCES school_campus(id, tenant_id),
    CONSTRAINT ck_admission_visit_owner CHECK (prospect_id IS NOT NULL OR application_id IS NOT NULL)
);

CREATE TABLE admission_interview (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    application_id UUID NOT NULL,
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    interviewer_user_id UUID,
    status VARCHAR(30) NOT NULL,
    score NUMERIC(5,2),
    notes VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_admission_interview_application FOREIGN KEY (application_id, tenant_id)
        REFERENCES admission_application(id, tenant_id)
);

CREATE TABLE admission_test (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    application_id UUID NOT NULL,
    test_type VARCHAR(80) NOT NULL,
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(30) NOT NULL,
    score NUMERIC(6,2),
    maximum_score NUMERIC(6,2),
    notes VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_admission_test_application FOREIGN KEY (application_id, tenant_id)
        REFERENCES admission_application(id, tenant_id),
    CONSTRAINT ck_admission_test_scores CHECK
        (score IS NULL OR maximum_score IS NULL OR (score >= 0 AND score <= maximum_score))
);

CREATE TABLE admission_decision (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    application_id UUID NOT NULL,
    decision VARCHAR(30) NOT NULL,
    reason VARCHAR(1000),
    decided_by_user_id UUID,
    decided_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_admission_decision_application FOREIGN KEY (application_id, tenant_id)
        REFERENCES admission_application(id, tenant_id),
    CONSTRAINT uq_admission_decision_application UNIQUE (tenant_id, application_id)
);

CREATE TABLE admission_enrollment_offer (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    application_id UUID NOT NULL,
    offer_reference VARCHAR(50) NOT NULL,
    offered_on DATE NOT NULL,
    expires_on DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    conditions VARCHAR(1000),
    responded_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_admission_offer_application FOREIGN KEY (application_id, tenant_id)
        REFERENCES admission_application(id, tenant_id),
    CONSTRAINT uq_admission_offer_application UNIQUE (tenant_id, application_id),
    CONSTRAINT uq_admission_offer_reference UNIQUE (tenant_id, offer_reference),
    CONSTRAINT ck_admission_offer_dates CHECK (offered_on <= expires_on)
);

CREATE TABLE admission_notification (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    portal_account_id UUID NOT NULL,
    application_id UUID,
    notification_type VARCHAR(60) NOT NULL,
    title VARCHAR(180) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_admission_notification_account FOREIGN KEY (portal_account_id, tenant_id)
        REFERENCES admission_portal_account(id, tenant_id),
    CONSTRAINT fk_admission_notification_application FOREIGN KEY (application_id, tenant_id)
        REFERENCES admission_application(id, tenant_id)
);

CREATE TABLE admission_fee_payment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    application_id UUID NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    payment_method VARCHAR(40) NOT NULL,
    payment_reference VARCHAR(80) NOT NULL,
    paid_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_admission_payment_application FOREIGN KEY (application_id, tenant_id)
        REFERENCES admission_application(id, tenant_id),
    CONSTRAINT uq_admission_payment_reference UNIQUE (tenant_id, payment_reference),
    CONSTRAINT ck_admission_payment_amount CHECK (amount > 0)
);

CREATE INDEX idx_admission_session_token ON admission_portal_session (token_hash, expires_at);
CREATE INDEX idx_admission_campaign_status ON admission_campaign (tenant_id, status, opens_on);
CREATE INDEX idx_admission_prospect_pipeline ON admission_prospect (tenant_id, status, created_at);
CREATE INDEX idx_admission_applicant_account ON admission_applicant (tenant_id, portal_account_id);
CREATE INDEX idx_admission_application_pipeline ON admission_application
    (tenant_id, campaign_id, current_status, updated_at);
CREATE INDEX idx_admission_status_history ON admission_application_status
    (tenant_id, application_id, occurred_at);
CREATE INDEX idx_admission_document_application ON admission_document
    (tenant_id, application_id, document_type);
CREATE INDEX idx_admission_notification_account ON admission_notification
    (tenant_id, portal_account_id, created_at);
CREATE INDEX idx_admission_payment_application ON admission_fee_payment
    (tenant_id, application_id, created_at);
