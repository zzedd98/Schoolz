ALTER TABLE communication_notification ADD COLUMN archived_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE communication_notification ADD COLUMN critical BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE communication_notification ADD COLUMN alert_key VARCHAR(80);

CREATE TABLE communication_notification_policy (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    alert_key VARCHAR(80) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    mandatory BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_comm_notification_policy UNIQUE (tenant_id, alert_key, channel),
    CONSTRAINT ck_comm_notification_policy_channel CHECK (channel IN ('PUSH', 'EMAIL', 'SMS'))
);

CREATE TABLE communication_center_preference (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    user_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_comm_center_preference_user_tenant FOREIGN KEY (user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT uq_comm_center_preference UNIQUE (tenant_id, user_id, channel),
    CONSTRAINT ck_comm_center_preference_channel CHECK (channel IN ('PUSH', 'EMAIL', 'SMS'))
);

CREATE INDEX idx_comm_notification_center
    ON communication_notification (tenant_id, recipient_user_id, archived_at, read_at, created_at DESC);
CREATE INDEX idx_comm_notification_policy
    ON communication_notification_policy (tenant_id, channel, mandatory, active);
