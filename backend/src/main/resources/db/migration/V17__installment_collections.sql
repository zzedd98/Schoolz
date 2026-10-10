ALTER TABLE billing_payment_schedule
    ADD COLUMN schedule_position INTEGER DEFAULT 1 NOT NULL;

ALTER TABLE billing_payment_schedule
    ADD COLUMN paid_amount NUMERIC(14,2) DEFAULT 0 NOT NULL;

ALTER TABLE billing_payment_schedule
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;

UPDATE billing_payment_schedule
SET updated_at = created_at
WHERE updated_at IS NULL;

ALTER TABLE billing_payment_schedule
    ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE billing_payment_schedule
    ADD CONSTRAINT ck_billing_schedule_position CHECK (schedule_position > 0);

ALTER TABLE billing_payment_schedule
    ADD CONSTRAINT ck_billing_schedule_paid CHECK (paid_amount >= 0 AND paid_amount <= amount);

ALTER TABLE billing_payment_schedule
    ADD CONSTRAINT uq_billing_schedule_tenant UNIQUE (id, tenant_id);

CREATE TABLE billing_schedule_payment_allocation (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    payment_id UUID NOT NULL,
    payment_schedule_id UUID NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    allocated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_billing_schedule_allocation_payment FOREIGN KEY (payment_id, tenant_id)
        REFERENCES billing_payment(id, tenant_id),
    CONSTRAINT fk_billing_schedule_allocation_schedule FOREIGN KEY (payment_schedule_id, tenant_id)
        REFERENCES billing_payment_schedule(id, tenant_id),
    CONSTRAINT uq_billing_schedule_allocation UNIQUE (tenant_id, payment_id, payment_schedule_id),
    CONSTRAINT ck_billing_schedule_allocation_amount CHECK (amount <> 0)
);

CREATE TABLE billing_reminder_rule (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    name VARCHAR(120) NOT NULL,
    offset_days INTEGER NOT NULL,
    channel VARCHAR(30) NOT NULL,
    automatic BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL,
    template_key VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_billing_reminder_rule UNIQUE (tenant_id, offset_days, channel),
    CONSTRAINT uq_billing_reminder_rule_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_billing_reminder_offset CHECK (offset_days BETWEEN -365 AND 365),
    CONSTRAINT ck_billing_reminder_channel CHECK (channel IN ('PUSH','EMAIL','SMS','WHATSAPP'))
);

CREATE TABLE billing_collection_action (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    family_account_id UUID NOT NULL,
    payment_schedule_id UUID,
    reminder_rule_id UUID,
    action_type VARCHAR(30) NOT NULL,
    channel VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    adapter_key VARCHAR(80) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    planned_for DATE NOT NULL,
    executed_at TIMESTAMP WITH TIME ZONE,
    idempotency_key VARCHAR(180) NOT NULL,
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_billing_collection_family FOREIGN KEY (family_account_id, tenant_id)
        REFERENCES billing_family_account(id, tenant_id),
    CONSTRAINT fk_billing_collection_schedule FOREIGN KEY (payment_schedule_id, tenant_id)
        REFERENCES billing_payment_schedule(id, tenant_id),
    CONSTRAINT fk_billing_collection_rule FOREIGN KEY (reminder_rule_id, tenant_id)
        REFERENCES billing_reminder_rule(id, tenant_id),
    CONSTRAINT fk_billing_collection_actor FOREIGN KEY (created_by)
        REFERENCES identity_user_account(id),
    CONSTRAINT uq_billing_collection_action_key UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT ck_billing_collection_action_type CHECK (action_type IN ('MANUAL','AUTOMATIC')),
    CONSTRAINT ck_billing_collection_channel CHECK (channel IN ('PUSH','EMAIL','SMS','WHATSAPP')),
    CONSTRAINT ck_billing_collection_status CHECK (status IN ('PREVIEW','SENT','FAILED'))
);

CREATE INDEX idx_billing_schedule_plan_position
    ON billing_payment_schedule (tenant_id, installment_plan_id, schedule_position);

CREATE INDEX idx_billing_schedule_allocation_schedule
    ON billing_schedule_payment_allocation (tenant_id, payment_schedule_id, allocated_at);

CREATE INDEX idx_billing_reminder_active
    ON billing_reminder_rule (tenant_id, active, automatic, offset_days);

CREATE INDEX idx_billing_collection_account
    ON billing_collection_action (tenant_id, family_account_id, planned_for, created_at);
