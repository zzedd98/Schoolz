CREATE TABLE public_api_client (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(500),
    key_prefix VARCHAR(24) NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    scopes_json VARCHAR(3000) NOT NULL,
    rate_limit_per_minute INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_by_user_id UUID,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_public_api_client_hash UNIQUE (key_hash),
    CONSTRAINT uq_public_api_client_prefix UNIQUE (tenant_id, key_prefix),
    CONSTRAINT uq_public_api_client_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_public_api_client_creator FOREIGN KEY (created_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT fk_public_api_client_revoker FOREIGN KEY (revoked_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT ck_public_api_rate_limit CHECK (rate_limit_per_minute BETWEEN 1 AND 1000),
    CONSTRAINT ck_public_api_revoke CHECK (
        (revoked_at IS NULL AND revoked_by_user_id IS NULL)
        OR (revoked_at IS NOT NULL AND revoked_by_user_id IS NOT NULL)
    )
);

CREATE TABLE public_api_request_log (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    client_id UUID NOT NULL,
    request_id UUID NOT NULL,
    method VARCHAR(10) NOT NULL,
    request_path VARCHAR(500) NOT NULL,
    required_scope VARCHAR(100) NOT NULL,
    status_code INTEGER NOT NULL,
    outcome VARCHAR(30) NOT NULL,
    ip_address VARCHAR(80),
    duration_ms BIGINT NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_public_api_log_client FOREIGN KEY (client_id, tenant_id)
        REFERENCES public_api_client(id, tenant_id),
    CONSTRAINT uq_public_api_request UNIQUE (tenant_id, request_id),
    CONSTRAINT ck_public_api_status CHECK (status_code BETWEEN 100 AND 599),
    CONSTRAINT ck_public_api_duration CHECK (duration_ms >= 0),
    CONSTRAINT ck_public_api_outcome CHECK (outcome IN
        ('SUCCESS','UNAUTHORIZED_SCOPE','RATE_LIMITED','FAILED'))
);

CREATE INDEX idx_public_api_client_tenant ON public_api_client(tenant_id, active, created_at);
CREATE INDEX idx_public_api_log_rate ON public_api_request_log(client_id, requested_at DESC);
CREATE INDEX idx_public_api_log_tenant ON public_api_request_log(tenant_id, requested_at DESC);
