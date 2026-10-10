CREATE TABLE billing_online_payment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    family_account_id UUID NOT NULL,
    invoice_id UUID,
    payment_schedule_id UUID,
    provider_key VARCHAR(50) NOT NULL,
    provider_payment_id VARCHAR(160) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    checkout_url VARCHAR(1000) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    resulting_payment_id UUID,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_online_payment_family FOREIGN KEY (family_account_id, tenant_id)
        REFERENCES billing_family_account(id, tenant_id),
    CONSTRAINT fk_online_payment_invoice FOREIGN KEY (invoice_id, tenant_id)
        REFERENCES billing_invoice(id, tenant_id),
    CONSTRAINT fk_online_payment_schedule FOREIGN KEY (payment_schedule_id, tenant_id)
        REFERENCES billing_payment_schedule(id, tenant_id),
    CONSTRAINT fk_online_payment_result FOREIGN KEY (resulting_payment_id, tenant_id)
        REFERENCES billing_payment(id, tenant_id),
    CONSTRAINT uq_online_payment_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uq_online_payment_provider UNIQUE (provider_key, provider_payment_id),
    CONSTRAINT uq_online_payment_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT ck_online_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_online_payment_status CHECK (
        status IN ('PENDING','AUTHORIZED','PAID','FAILED','CANCELLED','REFUNDED')
    )
);

CREATE TABLE billing_payment_webhook (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    online_payment_id UUID,
    provider_key VARCHAR(50) NOT NULL,
    event_id VARCHAR(180) NOT NULL,
    provider_payment_id VARCHAR(160),
    signature_valid BOOLEAN NOT NULL,
    processing_status VARCHAR(30) NOT NULL,
    payload_sha256 VARCHAR(64) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    delivery_count INTEGER NOT NULL DEFAULT 1,
    processed_at TIMESTAMP WITH TIME ZONE,
    error_message VARCHAR(500),
    CONSTRAINT fk_payment_webhook_tenant FOREIGN KEY (tenant_id)
        REFERENCES identity_tenant(id),
    CONSTRAINT fk_payment_webhook_intent FOREIGN KEY (online_payment_id, tenant_id)
        REFERENCES billing_online_payment(id, tenant_id),
    CONSTRAINT uq_payment_webhook_event UNIQUE (provider_key, event_id),
    CONSTRAINT ck_payment_webhook_status CHECK (
        processing_status IN ('RECEIVED','PROCESSED','DUPLICATE','IGNORED','FAILED','REJECTED')
    ),
    CONSTRAINT ck_payment_webhook_delivery_count CHECK (delivery_count > 0)
);

CREATE TABLE billing_online_refund (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    online_payment_id UUID NOT NULL,
    provider_refund_id VARCHAR(160) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    error_message VARCHAR(500),
    resulting_reversal_payment_id UUID,
    requested_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_online_refund_payment FOREIGN KEY (online_payment_id, tenant_id)
        REFERENCES billing_online_payment(id, tenant_id),
    CONSTRAINT fk_online_refund_reversal FOREIGN KEY (resulting_reversal_payment_id, tenant_id)
        REFERENCES billing_payment(id, tenant_id),
    CONSTRAINT fk_online_refund_actor FOREIGN KEY (requested_by)
        REFERENCES identity_user_account(id),
    CONSTRAINT uq_online_refund_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uq_online_refund_provider UNIQUE (provider_refund_id),
    CONSTRAINT uq_online_refund_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT uq_online_refund_full UNIQUE (tenant_id, online_payment_id),
    CONSTRAINT ck_online_refund_amount CHECK (amount > 0),
    CONSTRAINT ck_online_refund_status CHECK (status IN ('PENDING','SUCCEEDED','FAILED'))
);

CREATE INDEX idx_online_payment_account
    ON billing_online_payment (tenant_id, family_account_id, created_at);

CREATE INDEX idx_online_payment_status
    ON billing_online_payment (tenant_id, status, updated_at);

CREATE INDEX idx_payment_webhook_received
    ON billing_payment_webhook (provider_key, received_at);

CREATE INDEX idx_online_refund_payment
    ON billing_online_refund (tenant_id, online_payment_id, created_at);
