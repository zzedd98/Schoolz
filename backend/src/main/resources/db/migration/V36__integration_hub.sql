CREATE TABLE integration_adapter (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id) ON DELETE CASCADE,
    adapter_key VARCHAR(60) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    category VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL,
    transport_mode VARCHAR(30) NOT NULL,
    capabilities_json VARCHAR(2000) NOT NULL,
    disclaimer VARCHAR(800) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_integration_adapter_key UNIQUE (tenant_id, adapter_key),
    CONSTRAINT uq_integration_adapter_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_integration_adapter_status CHECK (status IN ('READY','CONFIGURATION_REQUIRED','DISABLED')),
    CONSTRAINT ck_integration_transport CHECK (transport_mode IN ('FILE_ONLY','LOCAL_ADAPTER','PROVIDER_API'))
);

CREATE TABLE integration_mapping_profile (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    adapter_id UUID NOT NULL,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(180) NOT NULL,
    dataset_type VARCHAR(50) NOT NULL,
    direction VARCHAR(20) NOT NULL,
    file_format VARCHAR(10) NOT NULL,
    field_mapping_json VARCHAR(5000) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_integration_profile_adapter FOREIGN KEY (adapter_id, tenant_id)
        REFERENCES integration_adapter(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT uq_integration_profile_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_integration_profile_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_integration_profile_direction CHECK (direction IN ('IMPORT','EXPORT','BOTH')),
    CONSTRAINT ck_integration_profile_format CHECK (file_format IN ('CSV','XLSX'))
);

CREATE TABLE integration_transfer_job (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    adapter_id UUID NOT NULL,
    mapping_profile_id UUID,
    direction VARCHAR(20) NOT NULL,
    dataset_type VARCHAR(50) NOT NULL,
    file_format VARCHAR(10) NOT NULL,
    status VARCHAR(30) NOT NULL,
    original_file_name VARCHAR(255),
    row_count INTEGER NOT NULL DEFAULT 0,
    success_count INTEGER NOT NULL DEFAULT 0,
    error_count INTEGER NOT NULL DEFAULT 0,
    sha256 VARCHAR(64),
    created_by_user_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_integration_job_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_integration_job_adapter FOREIGN KEY (adapter_id, tenant_id)
        REFERENCES integration_adapter(id, tenant_id),
    CONSTRAINT fk_integration_job_profile FOREIGN KEY (mapping_profile_id, tenant_id)
        REFERENCES integration_mapping_profile(id, tenant_id),
    CONSTRAINT fk_integration_job_actor FOREIGN KEY (created_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT ck_integration_job_direction CHECK (direction IN ('IMPORT','EXPORT')),
    CONSTRAINT ck_integration_job_format CHECK (file_format IN ('CSV','XLSX')),
    CONSTRAINT ck_integration_job_status CHECK (status IN ('VALIDATED','COMPLETED','COMPLETED_WITH_ERRORS','FAILED')),
    CONSTRAINT ck_integration_job_counts CHECK (row_count >= 0 AND success_count >= 0 AND error_count >= 0)
);

CREATE TABLE integration_transfer_row (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    transfer_job_id UUID NOT NULL,
    row_number INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    data_json VARCHAR(8000) NOT NULL,
    error_message VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_integration_row_job FOREIGN KEY (transfer_job_id, tenant_id)
        REFERENCES integration_transfer_job(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT uq_integration_row_number UNIQUE (tenant_id, transfer_job_id, row_number),
    CONSTRAINT ck_integration_row_status CHECK (status IN ('VALID','INVALID'))
);

CREATE TABLE integration_export_file (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    transfer_job_id UUID NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    file_content BYTEA NOT NULL,
    file_size BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_integration_file_job FOREIGN KEY (transfer_job_id, tenant_id)
        REFERENCES integration_transfer_job(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT uq_integration_file_job UNIQUE (tenant_id, transfer_job_id),
    CONSTRAINT ck_integration_file_size CHECK (file_size > 0)
);

CREATE INDEX idx_integration_profile_lookup ON integration_mapping_profile(tenant_id, adapter_id, dataset_type, active);
CREATE INDEX idx_integration_job_history ON integration_transfer_job(tenant_id, created_at DESC, status);
CREATE INDEX idx_integration_row_errors ON integration_transfer_row(tenant_id, transfer_job_id, status);
