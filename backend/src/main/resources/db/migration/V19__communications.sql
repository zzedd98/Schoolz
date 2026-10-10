CREATE TABLE communication_conversation (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    category VARCHAR(30) NOT NULL DEFAULT 'DIRECT_MESSAGE',
    subject VARCHAR(220) NOT NULL,
    internal_user_id UUID NOT NULL,
    portal_account_id UUID NOT NULL,
    student_id UUID,
    classroom_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    last_message_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_comm_conversation_user_tenant FOREIGN KEY (internal_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT fk_comm_conversation_parent_tenant FOREIGN KEY (portal_account_id, tenant_id)
        REFERENCES admission_portal_account(id, tenant_id),
    CONSTRAINT fk_comm_conversation_student_tenant FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT fk_comm_conversation_class_tenant FOREIGN KEY (classroom_id, tenant_id)
        REFERENCES classroom(id, tenant_id),
    CONSTRAINT uq_comm_conversation_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_comm_conversation_category CHECK (category = 'DIRECT_MESSAGE'),
    CONSTRAINT ck_comm_conversation_status CHECK (status IN ('OPEN', 'CLOSED'))
);

CREATE TABLE communication_message (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    conversation_id UUID NOT NULL,
    sender_kind VARCHAR(20) NOT NULL,
    sender_user_id UUID,
    sender_portal_account_id UUID,
    body TEXT NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_comm_message_conversation_tenant FOREIGN KEY (conversation_id, tenant_id)
        REFERENCES communication_conversation(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_comm_message_user_tenant FOREIGN KEY (sender_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT fk_comm_message_parent_tenant FOREIGN KEY (sender_portal_account_id, tenant_id)
        REFERENCES admission_portal_account(id, tenant_id),
    CONSTRAINT uq_comm_message_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_comm_message_sender CHECK (
        (sender_kind = 'INTERNAL' AND sender_user_id IS NOT NULL AND sender_portal_account_id IS NULL)
        OR (sender_kind = 'PARENT' AND sender_user_id IS NULL AND sender_portal_account_id IS NOT NULL)
        OR (sender_kind = 'SYSTEM' AND sender_user_id IS NULL AND sender_portal_account_id IS NULL)
    ),
    CONSTRAINT ck_comm_message_body CHECK (char_length(body) BETWEEN 1 AND 4000)
);

CREATE TABLE communication_announcement (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    title VARCHAR(220) NOT NULL,
    body TEXT NOT NULL,
    audience_scope VARCHAR(20) NOT NULL,
    campus_id UUID,
    classroom_id UUID,
    created_by_user_id UUID NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_comm_announcement_campus_tenant FOREIGN KEY (campus_id, tenant_id)
        REFERENCES school_campus(id, tenant_id),
    CONSTRAINT fk_comm_announcement_class_tenant FOREIGN KEY (classroom_id, tenant_id)
        REFERENCES classroom(id, tenant_id),
    CONSTRAINT fk_comm_announcement_creator_tenant FOREIGN KEY (created_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT uq_comm_announcement_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_comm_announcement_scope CHECK (
        (audience_scope = 'SCHOOL' AND campus_id IS NULL AND classroom_id IS NULL)
        OR (audience_scope = 'CAMPUS' AND campus_id IS NOT NULL AND classroom_id IS NULL)
        OR (audience_scope = 'CLASS' AND campus_id IS NULL AND classroom_id IS NOT NULL)
    ),
    CONSTRAINT ck_comm_announcement_body CHECK (char_length(body) BETWEEN 1 AND 10000)
);

CREATE TABLE communication_notification_template (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    template_key VARCHAR(100) NOT NULL,
    locale VARCHAR(10) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    category VARCHAR(30) NOT NULL,
    subject_template VARCHAR(220) NOT NULL,
    body_template TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_comm_template UNIQUE (tenant_id, template_key, locale, channel),
    CONSTRAINT ck_comm_template_channel CHECK (channel IN ('IN_APP', 'PUSH', 'EMAIL', 'SMS', 'WHATSAPP')),
    CONSTRAINT ck_comm_template_category CHECK (category IN ('ANNOUNCEMENT', 'DIRECT_MESSAGE', 'AUTOMATED_NOTIFICATION'))
);

CREATE TABLE communication_notification (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    category VARCHAR(30) NOT NULL,
    recipient_kind VARCHAR(20) NOT NULL,
    recipient_user_id UUID,
    recipient_portal_account_id UUID,
    student_id UUID,
    title VARCHAR(220) NOT NULL,
    body TEXT NOT NULL,
    resource_type VARCHAR(40),
    resource_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_comm_notification_user_tenant FOREIGN KEY (recipient_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT fk_comm_notification_parent_tenant FOREIGN KEY (recipient_portal_account_id, tenant_id)
        REFERENCES admission_portal_account(id, tenant_id),
    CONSTRAINT fk_comm_notification_student_tenant FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT uq_comm_notification_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_comm_notification_category CHECK (category IN ('ANNOUNCEMENT', 'DIRECT_MESSAGE', 'AUTOMATED_NOTIFICATION')),
    CONSTRAINT ck_comm_notification_recipient CHECK (
        (recipient_kind = 'INTERNAL' AND recipient_user_id IS NOT NULL AND recipient_portal_account_id IS NULL)
        OR (recipient_kind = 'PARENT' AND recipient_user_id IS NULL AND recipient_portal_account_id IS NOT NULL)
    )
);

CREATE TABLE communication_notification_preference (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    recipient_kind VARCHAR(20) NOT NULL,
    recipient_user_id UUID,
    recipient_portal_account_id UUID,
    recipient_key VARCHAR(36) NOT NULL,
    category VARCHAR(30) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_comm_preference_user_tenant FOREIGN KEY (recipient_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT fk_comm_preference_parent_tenant FOREIGN KEY (recipient_portal_account_id, tenant_id)
        REFERENCES admission_portal_account(id, tenant_id),
    CONSTRAINT ck_comm_preference_recipient CHECK (
        (recipient_kind = 'INTERNAL' AND recipient_user_id IS NOT NULL AND recipient_portal_account_id IS NULL)
        OR (recipient_kind = 'PARENT' AND recipient_user_id IS NULL AND recipient_portal_account_id IS NOT NULL)
    ),
    CONSTRAINT uq_comm_preference UNIQUE (tenant_id, recipient_kind, recipient_key, category, channel),
    CONSTRAINT ck_comm_preference_category CHECK (category IN ('ANNOUNCEMENT', 'DIRECT_MESSAGE', 'AUTOMATED_NOTIFICATION')),
    CONSTRAINT ck_comm_preference_channel CHECK (channel IN ('IN_APP', 'PUSH', 'EMAIL', 'SMS', 'WHATSAPP'))
);

CREATE TABLE communication_notification_delivery (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    notification_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    adapter_key VARCHAR(80) NOT NULL,
    recipient_masked VARCHAR(180),
    idempotency_key VARCHAR(160) NOT NULL,
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    delivered_at TIMESTAMP WITH TIME ZONE,
    error_message VARCHAR(500),
    CONSTRAINT fk_comm_delivery_notification_tenant FOREIGN KEY (notification_id, tenant_id)
        REFERENCES communication_notification(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT uq_comm_delivery_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT ck_comm_delivery_channel CHECK (channel IN ('IN_APP', 'PUSH', 'EMAIL', 'SMS', 'WHATSAPP')),
    CONSTRAINT ck_comm_delivery_status CHECK (status IN ('DELIVERED', 'PREVIEW', 'SKIPPED', 'FAILED'))
);

CREATE INDEX idx_comm_conversation_parent
    ON communication_conversation (tenant_id, portal_account_id, last_message_at DESC);
CREATE INDEX idx_comm_conversation_internal
    ON communication_conversation (tenant_id, internal_user_id, last_message_at DESC);
CREATE INDEX idx_comm_message_conversation
    ON communication_message (tenant_id, conversation_id, sent_at);
CREATE INDEX idx_comm_announcement_scope
    ON communication_announcement (tenant_id, audience_scope, published_at DESC);
CREATE INDEX idx_comm_notification_parent
    ON communication_notification (tenant_id, recipient_portal_account_id, read_at, created_at DESC);
CREATE INDEX idx_comm_notification_internal
    ON communication_notification (tenant_id, recipient_user_id, read_at, created_at DESC);
CREATE INDEX idx_comm_delivery_notification
    ON communication_notification_delivery (tenant_id, notification_id, channel);
