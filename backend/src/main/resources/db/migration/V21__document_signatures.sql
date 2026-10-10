CREATE TABLE document_signature_workflow (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    document_id UUID NOT NULL REFERENCES document_file(id),
    document_version_id UUID NOT NULL REFERENCES document_version(id),
    status VARCHAR(20) NOT NULL,
    recipient_kind VARCHAR(20) NOT NULL,
    recipient_reference_id UUID NOT NULL,
    recipient_name VARCHAR(180) NOT NULL,
    recipient_email VARCHAR(254),
    legal_notice_version VARCHAR(40) NOT NULL,
    provider_key VARCHAR(80),
    provider_reference VARCHAR(180),
    assurance_level VARCHAR(60),
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE,
    viewed_at TIMESTAMP WITH TIME ZONE,
    accepted_at TIMESTAMP WITH TIME ZONE,
    signed_at TIMESTAMP WITH TIME ZONE,
    signatory_name VARCHAR(180),
    signatory_ip VARCHAR(64),
    evidence_sha256 VARCHAR(64),
    created_by_user_id UUID REFERENCES identity_user_account(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_signature_status CHECK (status IN ('GENERATED','SENT','VIEWED','ACCEPTED','SIGNED')),
    CONSTRAINT ck_signature_recipient CHECK (recipient_kind IN ('PARENT','USER')),
    CONSTRAINT uq_signature_document_recipient UNIQUE (tenant_id, document_version_id, recipient_kind, recipient_reference_id)
);

CREATE TABLE document_signature_event (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    workflow_id UUID NOT NULL REFERENCES document_signature_workflow(id),
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    actor_kind VARCHAR(20) NOT NULL,
    actor_reference_id UUID NOT NULL,
    actor_name VARCHAR(180) NOT NULL,
    ip_address VARCHAR(64),
    provider_key VARCHAR(80),
    provider_reference VARCHAR(180),
    evidence_sha256 VARCHAR(64),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_signature_event_actor CHECK (actor_kind IN ('PARENT','USER','SYSTEM')),
    CONSTRAINT ck_signature_event_status CHECK (to_status IN ('GENERATED','SENT','VIEWED','ACCEPTED','SIGNED'))
);

CREATE INDEX idx_signature_workflow_status ON document_signature_workflow(tenant_id, status, updated_at DESC);
CREATE INDEX idx_signature_workflow_recipient ON document_signature_workflow(tenant_id, recipient_kind, recipient_reference_id, updated_at DESC);
CREATE INDEX idx_signature_event_workflow ON document_signature_event(tenant_id, workflow_id, occurred_at);
