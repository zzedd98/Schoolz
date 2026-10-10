CREATE TABLE document_category (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    code VARCHAR(80) NOT NULL,
    label_fr VARCHAR(180) NOT NULL,
    label_ar VARCHAR(180),
    system_category BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_document_category_code UNIQUE (tenant_id, code)
);

CREATE TABLE document_file (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    category_id UUID NOT NULL REFERENCES document_category(id),
    document_type VARCHAR(40) NOT NULL,
    title VARCHAR(240) NOT NULL,
    description VARCHAR(2000),
    owner_kind VARCHAR(20) NOT NULL,
    owner_reference_id UUID,
    visibility VARCHAR(20) NOT NULL DEFAULT 'INTERNAL',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    expires_on DATE,
    current_version_number INTEGER NOT NULL DEFAULT 0,
    created_by_user_id UUID REFERENCES identity_user_account(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_document_type CHECK (document_type IN ('PARENT_ID','BIRTH_CERTIFICATE','PHOTO','CERTIFICATE','CONTRACT','AUTHORIZATION','REPORT_CARD','INVOICE','ATTESTATION','INTERNAL')),
    CONSTRAINT ck_document_owner CHECK (owner_kind IN ('SCHOOL','STUDENT','PARENT','STAFF','FAMILY_ACCOUNT')),
    CONSTRAINT ck_document_visibility CHECK (visibility IN ('INTERNAL','FAMILY','PRIVATE')),
    CONSTRAINT ck_document_status CHECK (status IN ('ACTIVE','ARCHIVED')),
    CONSTRAINT ck_document_version CHECK (current_version_number >= 0)
);

CREATE TABLE document_object_blob (
    object_key VARCHAR(500) PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    content BYTEA NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    stored_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_document_blob_size CHECK (size_bytes > 0)
);

CREATE TABLE document_version (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    document_id UUID NOT NULL REFERENCES document_file(id),
    version_number INTEGER NOT NULL,
    object_key VARCHAR(500) NOT NULL REFERENCES document_object_blob(object_key),
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    source_kind VARCHAR(20) NOT NULL,
    pdf_template VARCHAR(40),
    created_by_user_id UUID REFERENCES identity_user_account(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_document_version UNIQUE (tenant_id, document_id, version_number),
    CONSTRAINT uq_document_object_key UNIQUE (object_key),
    CONSTRAINT ck_document_source CHECK (source_kind IN ('UPLOAD','GENERATED')),
    CONSTRAINT ck_document_version_number CHECK (version_number > 0),
    CONSTRAINT ck_document_version_size CHECK (size_bytes > 0)
);

CREATE TABLE document_access_grant (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    document_id UUID NOT NULL REFERENCES document_file(id),
    grantee_kind VARCHAR(20) NOT NULL,
    grantee_reference_id UUID NOT NULL,
    can_view BOOLEAN NOT NULL DEFAULT TRUE,
    can_download BOOLEAN NOT NULL DEFAULT TRUE,
    valid_until TIMESTAMP WITH TIME ZONE,
    granted_by_user_id UUID REFERENCES identity_user_account(id),
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_document_grant UNIQUE (tenant_id, document_id, grantee_kind, grantee_reference_id),
    CONSTRAINT ck_document_grantee CHECK (grantee_kind IN ('PARENT','USER','ROLE'))
);

CREATE TABLE document_signed_download (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    document_version_id UUID NOT NULL REFERENCES document_version(id),
    token_hash VARCHAR(64) NOT NULL,
    requested_by_kind VARCHAR(20) NOT NULL,
    requested_by_reference_id UUID NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    max_downloads INTEGER NOT NULL DEFAULT 1,
    download_count INTEGER NOT NULL DEFAULT 0,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_downloaded_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_document_download_token UNIQUE (token_hash),
    CONSTRAINT ck_document_requester CHECK (requested_by_kind IN ('PARENT','USER')),
    CONSTRAINT ck_document_download_counts CHECK (max_downloads > 0 AND download_count >= 0)
);

CREATE INDEX idx_document_file_tenant_type ON document_file(tenant_id, document_type, updated_at DESC);
CREATE INDEX idx_document_file_owner ON document_file(tenant_id, owner_kind, owner_reference_id);
CREATE INDEX idx_document_version_document ON document_version(tenant_id, document_id, version_number DESC);
CREATE INDEX idx_document_grant_parent ON document_access_grant(tenant_id, grantee_kind, grantee_reference_id);
CREATE INDEX idx_document_download_expiry ON document_signed_download(token_hash, expires_at);
