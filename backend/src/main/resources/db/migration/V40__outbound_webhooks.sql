CREATE TABLE webhook_subscription (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    endpoint_url VARCHAR(1000) NOT NULL,
    event_types_json VARCHAR(3000) NOT NULL,
    secret_prefix VARCHAR(24) NOT NULL,
    secret_hash VARCHAR(64) NOT NULL,
    secret_ciphertext VARCHAR(2000) NOT NULL,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    timeout_seconds INTEGER NOT NULL DEFAULT 10,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_by_user_id UUID,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_webhook_subscription_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT uq_webhook_subscription_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uq_webhook_secret_hash UNIQUE (secret_hash),
    CONSTRAINT fk_webhook_subscription_creator FOREIGN KEY (created_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT fk_webhook_subscription_revoker FOREIGN KEY (revoked_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT ck_webhook_max_attempts CHECK (max_attempts BETWEEN 1 AND 10),
    CONSTRAINT ck_webhook_timeout CHECK (timeout_seconds BETWEEN 1 AND 30),
    CONSTRAINT ck_webhook_revoke CHECK (
        (revoked_at IS NULL AND revoked_by_user_id IS NULL)
        OR (revoked_at IS NOT NULL AND revoked_by_user_id IS NOT NULL)
    )
);

CREATE TABLE webhook_event (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id) ON DELETE CASCADE,
    event_type VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    payload_json VARCHAR(20000) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_webhook_event_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT uq_webhook_event_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_webhook_event_creator FOREIGN KEY (created_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT ck_webhook_event_type CHECK (event_type IN
        ('student.created','student.enrolled','invoice.created','invoice.paid',
         'absence.created','grade.published'))
);

CREATE TABLE webhook_delivery (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    subscription_id UUID NOT NULL,
    event_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    last_http_status INTEGER,
    last_error VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_webhook_delivery_subscription FOREIGN KEY (subscription_id, tenant_id)
        REFERENCES webhook_subscription(id, tenant_id),
    CONSTRAINT fk_webhook_delivery_event FOREIGN KEY (event_id, tenant_id)
        REFERENCES webhook_event(id, tenant_id),
    CONSTRAINT uq_webhook_delivery UNIQUE (tenant_id, subscription_id, event_id),
    CONSTRAINT uq_webhook_delivery_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_webhook_delivery_status CHECK (status IN
        ('PENDING','RETRY_SCHEDULED','DELIVERED','DEAD_LETTER')),
    CONSTRAINT ck_webhook_delivery_attempts CHECK (attempt_count BETWEEN 0 AND 99),
    CONSTRAINT ck_webhook_delivery_http CHECK
        (last_http_status IS NULL OR last_http_status BETWEEN 100 AND 599)
);

CREATE TABLE webhook_delivery_attempt (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    delivery_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    request_id UUID NOT NULL,
    signature_timestamp BIGINT NOT NULL,
    signature_value VARCHAR(128) NOT NULL,
    http_status INTEGER,
    outcome VARCHAR(30) NOT NULL,
    error_message VARCHAR(1000),
    duration_ms BIGINT NOT NULL,
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_webhook_attempt_delivery FOREIGN KEY (delivery_id, tenant_id)
        REFERENCES webhook_delivery(id, tenant_id),
    CONSTRAINT uq_webhook_attempt_number UNIQUE (tenant_id, delivery_id, attempt_number),
    CONSTRAINT uq_webhook_attempt_request UNIQUE (tenant_id, request_id),
    CONSTRAINT ck_webhook_attempt_outcome CHECK (outcome IN ('DELIVERED','FAILED')),
    CONSTRAINT ck_webhook_attempt_duration CHECK (duration_ms >= 0),
    CONSTRAINT ck_webhook_attempt_http CHECK
        (http_status IS NULL OR http_status BETWEEN 100 AND 599)
);

CREATE TABLE webhook_dead_letter (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    delivery_id UUID NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    failed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolved_by_user_id UUID,
    resolution_note VARCHAR(1000),
    CONSTRAINT fk_webhook_dead_delivery FOREIGN KEY (delivery_id, tenant_id)
        REFERENCES webhook_delivery(id, tenant_id),
    CONSTRAINT fk_webhook_dead_resolver FOREIGN KEY (resolved_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT uq_webhook_dead_delivery UNIQUE (tenant_id, delivery_id),
    CONSTRAINT ck_webhook_dead_resolution CHECK (
        (resolved_at IS NULL AND resolved_by_user_id IS NULL)
        OR (resolved_at IS NOT NULL AND resolved_by_user_id IS NOT NULL)
    )
);

CREATE INDEX idx_webhook_subscription_tenant
    ON webhook_subscription(tenant_id, active, created_at);
CREATE INDEX idx_webhook_event_tenant ON webhook_event(tenant_id, occurred_at DESC);
CREATE INDEX idx_webhook_delivery_due
    ON webhook_delivery(tenant_id, status, next_attempt_at);
CREATE INDEX idx_webhook_attempt_tenant
    ON webhook_delivery_attempt(tenant_id, attempted_at DESC);
CREATE INDEX idx_webhook_dead_tenant ON webhook_dead_letter(tenant_id, failed_at DESC);
