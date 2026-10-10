CREATE TABLE enrollment_workflow (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    portal_account_id UUID NOT NULL,
    application_id UUID,
    student_id UUID,
    academic_year_id UUID NOT NULL,
    campus_id UUID NOT NULL,
    classroom_id UUID,
    workflow_type VARCHAR(30) NOT NULL,
    current_step VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL,
    registration_fee NUMERIC(14,2) NOT NULL DEFAULT 0,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'MAD',
    required_document_types TEXT NOT NULL DEFAULT '[]',
    activated_student_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_enrollment_workflow_portal FOREIGN KEY (portal_account_id, tenant_id)
        REFERENCES admission_portal_account(id, tenant_id),
    CONSTRAINT fk_enrollment_workflow_application FOREIGN KEY (application_id, tenant_id)
        REFERENCES admission_application(id, tenant_id),
    CONSTRAINT fk_enrollment_workflow_student FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT fk_enrollment_workflow_year FOREIGN KEY (academic_year_id, tenant_id)
        REFERENCES academic_year(id, tenant_id),
    CONSTRAINT fk_enrollment_workflow_campus FOREIGN KEY (campus_id, tenant_id)
        REFERENCES school_campus(id, tenant_id),
    CONSTRAINT fk_enrollment_workflow_classroom FOREIGN KEY (classroom_id, tenant_id)
        REFERENCES classroom(id, tenant_id),
    CONSTRAINT fk_enrollment_workflow_activated_student FOREIGN KEY (activated_student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT uq_enrollment_workflow_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uq_enrollment_workflow_application UNIQUE (tenant_id, application_id),
    CONSTRAINT uq_enrollment_workflow_renewal UNIQUE (tenant_id, student_id, academic_year_id),
    CONSTRAINT ck_enrollment_workflow_origin CHECK
        ((workflow_type='INITIAL' AND application_id IS NOT NULL AND student_id IS NULL)
         OR (workflow_type='RE_ENROLLMENT' AND application_id IS NULL AND student_id IS NOT NULL)),
    CONSTRAINT ck_enrollment_workflow_fee CHECK (registration_fee >= 0)
);

CREATE TABLE enrollment_contract (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    workflow_id UUID NOT NULL,
    contract_version VARCHAR(30) NOT NULL,
    title VARCHAR(180) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_enrollment_contract_workflow FOREIGN KEY (workflow_id, tenant_id)
        REFERENCES enrollment_workflow(id, tenant_id),
    CONSTRAINT uq_enrollment_contract_workflow UNIQUE (tenant_id, workflow_id)
);

CREATE TABLE enrollment_family_confirmation (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    workflow_id UUID NOT NULL,
    family_data TEXT NOT NULL,
    confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    confirmed_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_enrollment_family_workflow FOREIGN KEY (workflow_id, tenant_id)
        REFERENCES enrollment_workflow(id, tenant_id),
    CONSTRAINT uq_enrollment_family_workflow UNIQUE (tenant_id, workflow_id)
);

CREATE TABLE enrollment_service_selection (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    workflow_id UUID NOT NULL,
    transport_selected BOOLEAN NOT NULL DEFAULT FALSE,
    transport_route VARCHAR(120),
    cafeteria_selected BOOLEAN NOT NULL DEFAULT FALSE,
    activities TEXT NOT NULL DEFAULT '[]',
    payment_plan VARCHAR(40) NOT NULL,
    confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    confirmed_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_enrollment_services_workflow FOREIGN KEY (workflow_id, tenant_id)
        REFERENCES enrollment_workflow(id, tenant_id),
    CONSTRAINT uq_enrollment_services_workflow UNIQUE (tenant_id, workflow_id)
);

CREATE TABLE enrollment_document (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    workflow_id UUID NOT NULL,
    document_type VARCHAR(80) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    binary_data BYTEA NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_enrollment_document_workflow FOREIGN KEY (workflow_id, tenant_id)
        REFERENCES enrollment_workflow(id, tenant_id),
    CONSTRAINT ck_enrollment_document_size CHECK (size_bytes > 0)
);

CREATE TABLE enrollment_signature (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    workflow_id UUID NOT NULL,
    signer_name VARCHAR(180) NOT NULL,
    consent_text VARCHAR(500) NOT NULL,
    signature_hash VARCHAR(64) NOT NULL,
    ip_address VARCHAR(64),
    signed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_enrollment_signature_workflow FOREIGN KEY (workflow_id, tenant_id)
        REFERENCES enrollment_workflow(id, tenant_id),
    CONSTRAINT uq_enrollment_signature_workflow UNIQUE (tenant_id, workflow_id)
);

CREATE TABLE enrollment_fee_payment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    workflow_id UUID NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    payment_method VARCHAR(40) NOT NULL,
    payment_reference VARCHAR(80) NOT NULL,
    status VARCHAR(30) NOT NULL,
    paid_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_enrollment_fee_workflow FOREIGN KEY (workflow_id, tenant_id)
        REFERENCES enrollment_workflow(id, tenant_id),
    CONSTRAINT uq_enrollment_fee_reference UNIQUE (tenant_id, payment_reference),
    CONSTRAINT ck_enrollment_fee_amount CHECK (amount > 0)
);

CREATE TABLE enrollment_portal_student_link (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    portal_account_id UUID NOT NULL,
    student_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_enrollment_link_portal FOREIGN KEY (portal_account_id, tenant_id)
        REFERENCES admission_portal_account(id, tenant_id),
    CONSTRAINT fk_enrollment_link_student FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT uq_enrollment_link UNIQUE (tenant_id, portal_account_id, student_id)
);

CREATE INDEX idx_enrollment_workflow_portal ON enrollment_workflow
    (tenant_id, portal_account_id, status, updated_at);
CREATE INDEX idx_enrollment_workflow_admin ON enrollment_workflow
    (tenant_id, academic_year_id, status, current_step);
CREATE INDEX idx_enrollment_document_workflow ON enrollment_document
    (tenant_id, workflow_id, document_type);
