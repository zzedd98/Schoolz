CREATE TABLE identity_tenant (
    id UUID PRIMARY KEY,
    slug VARCHAR(80) NOT NULL UNIQUE,
    display_name VARCHAR(180) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE identity_user_account (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    email VARCHAR(254) NOT NULL,
    normalized_email VARCHAR(254) NOT NULL,
    display_name VARCHAR(180) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    locale VARCHAR(10) NOT NULL DEFAULT 'fr-MA',
    status VARCHAR(40) NOT NULL,
    email_verified_at TIMESTAMP WITH TIME ZONE,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP WITH TIME ZONE,
    mfa_secret_ciphertext VARCHAR(700),
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_identity_user_tenant_email UNIQUE (tenant_id, normalized_email),
    CONSTRAINT ck_identity_user_attempts CHECK (failed_login_attempts >= 0)
);

CREATE TABLE identity_role (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    code VARCHAR(80) NOT NULL,
    name VARCHAR(140) NOT NULL,
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_identity_role_tenant_code UNIQUE (tenant_id, code)
);

CREATE TABLE identity_role_permission (
    role_id UUID NOT NULL REFERENCES identity_role(id) ON DELETE CASCADE,
    permission_code VARCHAR(120) NOT NULL,
    PRIMARY KEY (role_id, permission_code)
);

CREATE TABLE identity_user_role (
    user_id UUID NOT NULL REFERENCES identity_user_account(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES identity_role(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE identity_refresh_session (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    user_id UUID NOT NULL REFERENCES identity_user_account(id),
    family_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    rotated_to UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE,
    ip_address VARCHAR(64),
    user_agent VARCHAR(500)
);

CREATE TABLE identity_one_time_token (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    user_id UUID NOT NULL REFERENCES identity_user_account(id),
    purpose VARCHAR(40) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE identity_audit_log (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    actor_user_id UUID,
    action VARCHAR(120) NOT NULL,
    resource_type VARCHAR(120) NOT NULL,
    resource_id VARCHAR(120),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ip_address VARCHAR(64),
    metadata TEXT NOT NULL,
    before_state TEXT,
    after_state TEXT
);

CREATE INDEX idx_identity_user_tenant ON identity_user_account (tenant_id);
CREATE INDEX idx_identity_role_tenant ON identity_role (tenant_id);
CREATE INDEX idx_identity_session_user ON identity_refresh_session (tenant_id, user_id);
CREATE INDEX idx_identity_session_family ON identity_refresh_session (family_id);
CREATE INDEX idx_identity_token_user ON identity_one_time_token (tenant_id, user_id, purpose);
CREATE INDEX idx_identity_audit_tenant_time ON identity_audit_log (tenant_id, occurred_at DESC);
