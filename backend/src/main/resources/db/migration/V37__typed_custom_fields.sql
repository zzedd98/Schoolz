CREATE TABLE custom_field_definition (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id) ON DELETE CASCADE,
    entity_type VARCHAR(40) NOT NULL,
    field_key VARCHAR(80) NOT NULL,
    label VARCHAR(160) NOT NULL,
    description VARCHAR(500),
    field_type VARCHAR(20) NOT NULL,
    options_json VARCHAR(4000) NOT NULL DEFAULT '[]',
    required BOOLEAN NOT NULL DEFAULT FALSE,
    read_permission VARCHAR(100),
    write_permission VARCHAR(100),
    display_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_user_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_custom_field_definition UNIQUE (tenant_id, entity_type, field_key),
    CONSTRAINT uq_custom_field_definition_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_custom_field_definition_actor FOREIGN KEY (created_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT ck_custom_field_entity CHECK (entity_type IN ('STUDENT','PERSON','HOUSEHOLD','EMPLOYEE','ADMISSION','INVOICE')),
    CONSTRAINT ck_custom_field_type CHECK (field_type IN ('TEXT','NUMBER','DATE','BOOLEAN','SELECT','MULTI_SELECT')),
    CONSTRAINT ck_custom_field_order CHECK (display_order >= 0)
);

CREATE TABLE custom_field_value (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    definition_id UUID NOT NULL,
    entity_id UUID NOT NULL,
    text_value VARCHAR(2000),
    number_value DECIMAL(19,4),
    date_value DATE,
    boolean_value BOOLEAN,
    json_value VARCHAR(4000),
    updated_by_user_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_custom_field_value_definition FOREIGN KEY (definition_id, tenant_id)
        REFERENCES custom_field_definition(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_custom_field_value_actor FOREIGN KEY (updated_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT uq_custom_field_value UNIQUE (tenant_id, definition_id, entity_id),
    CONSTRAINT ck_custom_field_single_storage CHECK (
        (CASE WHEN text_value IS NULL THEN 0 ELSE 1 END) +
        (CASE WHEN number_value IS NULL THEN 0 ELSE 1 END) +
        (CASE WHEN date_value IS NULL THEN 0 ELSE 1 END) +
        (CASE WHEN boolean_value IS NULL THEN 0 ELSE 1 END) +
        (CASE WHEN json_value IS NULL THEN 0 ELSE 1 END) = 1
    )
);

CREATE INDEX idx_custom_field_definition_lookup ON custom_field_definition(tenant_id, entity_type, active, display_order);
CREATE INDEX idx_custom_field_value_entity ON custom_field_value(tenant_id, entity_id, definition_id);
