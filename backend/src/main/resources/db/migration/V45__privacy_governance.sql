CREATE TABLE privacy_processing_purpose (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    purpose_code VARCHAR(60) NOT NULL,
    name VARCHAR(180) NOT NULL,
    description TEXT NOT NULL,
    lawful_basis VARCHAR(40) NOT NULL,
    data_categories TEXT NOT NULL,
    recipients TEXT,
    retention_days INTEGER NOT NULL,
    sensitive BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_privacy_purpose_creator FOREIGN KEY (created_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT uq_privacy_purpose_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uq_privacy_purpose UNIQUE (tenant_id, purpose_code),
    CONSTRAINT ck_privacy_purpose_retention CHECK (retention_days BETWEEN 1 AND 36500)
);

CREATE TABLE privacy_consent_event (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    purpose_id UUID NOT NULL,
    subject_type VARCHAR(20) NOT NULL,
    subject_reference VARCHAR(120) NOT NULL,
    decision VARCHAR(20) NOT NULL,
    source VARCHAR(40) NOT NULL,
    evidence_reference VARCHAR(220),
    decided_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_privacy_consent_purpose FOREIGN KEY (purpose_id, tenant_id)
        REFERENCES privacy_processing_purpose(id, tenant_id),
    CONSTRAINT fk_privacy_consent_recorder FOREIGN KEY (recorded_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT ck_privacy_consent_subject CHECK (subject_type IN ('STUDENT','GUARDIAN','EMPLOYEE','PROSPECT')),
    CONSTRAINT ck_privacy_consent_decision CHECK (decision IN ('GRANTED','WITHDRAWN'))
);

CREATE TABLE privacy_data_subject_request (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    request_number VARCHAR(40) NOT NULL,
    request_type VARCHAR(20) NOT NULL,
    subject_type VARCHAR(20) NOT NULL,
    subject_reference VARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    due_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolution_action VARCHAR(30),
    notes TEXT,
    created_by_user_id UUID NOT NULL,
    updated_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_privacy_request_creator FOREIGN KEY (created_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT fk_privacy_request_updater FOREIGN KEY (updated_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT uq_privacy_request_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uq_privacy_request_number UNIQUE (tenant_id, request_number),
    CONSTRAINT ck_privacy_request_type CHECK (request_type IN ('ACCESS','EXPORT','RECTIFICATION','ERASURE','RESTRICTION','OBJECTION')),
    CONSTRAINT ck_privacy_request_subject CHECK (subject_type IN ('STUDENT','GUARDIAN','EMPLOYEE','PROSPECT')),
    CONSTRAINT ck_privacy_request_status CHECK (status IN ('RECEIVED','IDENTITY_VERIFIED','IN_REVIEW','FULFILLED','REJECTED','CANCELLED')),
    CONSTRAINT ck_privacy_resolution CHECK (resolution_action IS NULL OR resolution_action IN ('EXPORTED','RECTIFIED','ANONYMIZED','DELETED','RESTRICTED','NO_ACTION'))
);

CREATE TABLE privacy_retention_policy (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    data_category VARCHAR(80) NOT NULL,
    retention_days INTEGER NOT NULL,
    expiry_action VARCHAR(20) NOT NULL,
    legal_hold BOOLEAN NOT NULL DEFAULT FALSE,
    last_reviewed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by_user_id UUID NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_privacy_retention_updater FOREIGN KEY (updated_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT uq_privacy_retention UNIQUE (tenant_id, data_category),
    CONSTRAINT ck_privacy_retention_days CHECK (retention_days BETWEEN 1 AND 36500),
    CONSTRAINT ck_privacy_retention_action CHECK (expiry_action IN ('ANONYMIZE','DELETE','REVIEW'))
);

CREATE TABLE privacy_export_log (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    request_id UUID,
    subject_type VARCHAR(20) NOT NULL,
    subject_reference VARCHAR(120) NOT NULL,
    scope_summary VARCHAR(500) NOT NULL,
    artifact_sha256 VARCHAR(64),
    status VARCHAR(20) NOT NULL,
    exported_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_privacy_export_request FOREIGN KEY (request_id, tenant_id)
        REFERENCES privacy_data_subject_request(id, tenant_id),
    CONSTRAINT fk_privacy_export_user FOREIGN KEY (exported_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT ck_privacy_export_status CHECK (status IN ('PREPARED','GENERATED','DELIVERED','EXPIRED','FAILED'))
);

CREATE TABLE privacy_hosting_configuration (
    tenant_id UUID PRIMARY KEY REFERENCES identity_tenant(id),
    country_code VARCHAR(2) NOT NULL,
    region_label VARCHAR(120) NOT NULL,
    provider_name VARCHAR(160) NOT NULL,
    encryption_at_rest BOOLEAN NOT NULL,
    encryption_in_transit BOOLEAN NOT NULL,
    cross_border_transfer BOOLEAN NOT NULL,
    legal_validation_status VARCHAR(20) NOT NULL,
    updated_by_user_id UUID NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_privacy_hosting_updater FOREIGN KEY (updated_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT ck_privacy_legal_status CHECK (legal_validation_status IN ('PENDING','APPROVED','REJECTED'))
);

CREATE INDEX idx_privacy_consent_subject ON privacy_consent_event (tenant_id, subject_type, subject_reference, decided_at);
CREATE INDEX idx_privacy_request_status ON privacy_data_subject_request (tenant_id, status, due_at);
CREATE INDEX idx_privacy_export_subject ON privacy_export_log (tenant_id, subject_type, subject_reference, created_at);
