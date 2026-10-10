CREATE TABLE business_configuration_item (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id) ON DELETE CASCADE,
    category VARCHAR(40) NOT NULL,
    config_key VARCHAR(120) NOT NULL,
    label VARCHAR(180) NOT NULL,
    description VARCHAR(800) NOT NULL,
    value_type VARCHAR(20) NOT NULL,
    current_value_json VARCHAR(12000) NOT NULL,
    default_value_json VARCHAR(12000) NOT NULL,
    options_json VARCHAR(6000) NOT NULL DEFAULT '[]',
    constraints_json VARCHAR(4000) NOT NULL DEFAULT '{}',
    source_module VARCHAR(60) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    editable BOOLEAN NOT NULL DEFAULT TRUE,
    version INTEGER NOT NULL DEFAULT 1,
    updated_by_user_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_business_configuration_key UNIQUE (tenant_id, config_key),
    CONSTRAINT uq_business_configuration_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_business_configuration_actor FOREIGN KEY (updated_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT ck_business_configuration_category CHECK (category IN
        ('ACADEMIC','CALENDAR','SCHEDULE','FINANCE','ATTENDANCE','DISCIPLINE',
         'DOCUMENTS','NOTIFICATIONS','GRADING','SECURITY')),
    CONSTRAINT ck_business_configuration_type CHECK (value_type IN
        ('TEXT','NUMBER','BOOLEAN','TIME','SELECT','MULTI_SELECT','JSON','MONEY','PERCENT')),
    CONSTRAINT ck_business_configuration_order CHECK (display_order >= 0),
    CONSTRAINT ck_business_configuration_version CHECK (version >= 1)
);

CREATE TABLE business_configuration_revision (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    item_id UUID NOT NULL,
    version INTEGER NOT NULL,
    previous_value_json VARCHAR(12000) NOT NULL,
    next_value_json VARCHAR(12000) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    changed_by_user_id UUID NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_business_configuration_revision_item FOREIGN KEY (item_id, tenant_id)
        REFERENCES business_configuration_item(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_business_configuration_revision_actor FOREIGN KEY (changed_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT uq_business_configuration_revision UNIQUE (tenant_id, item_id, version)
);

CREATE INDEX idx_business_configuration_category
    ON business_configuration_item(tenant_id, category, display_order);
CREATE INDEX idx_business_configuration_revision_history
    ON business_configuration_revision(tenant_id, item_id, version DESC);
