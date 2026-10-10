CREATE TABLE platform_school_provisioning (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    request_fingerprint VARCHAR(64) NOT NULL,
    tenant_id UUID NOT NULL UNIQUE REFERENCES identity_tenant(id) ON DELETE CASCADE,
    owner_user_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    campus_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_school_provisioning_owner_tenant
        FOREIGN KEY (owner_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_school_provisioning_organization
        FOREIGN KEY (organization_id) REFERENCES school_organization(id) ON DELETE CASCADE,
    CONSTRAINT fk_school_provisioning_campus
        FOREIGN KEY (campus_id) REFERENCES school_campus(id) ON DELETE CASCADE
);

CREATE INDEX idx_school_provisioning_tenant
    ON platform_school_provisioning (tenant_id, created_at DESC);
