CREATE TABLE workflow_definition (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id) ON DELETE CASCADE,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(180) NOT NULL,
    description VARCHAR(600) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    max_depth INTEGER NOT NULL DEFAULT 6,
    created_by_user_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_workflow_definition_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_workflow_definition_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_workflow_definition_depth CHECK (max_depth BETWEEN 1 AND 12),
    CONSTRAINT fk_workflow_definition_actor FOREIGN KEY (created_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id)
);

CREATE TABLE workflow_trigger (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    workflow_id UUID NOT NULL,
    event_type VARCHAR(140) NOT NULL,
    source_module VARCHAR(80) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_workflow_trigger_definition FOREIGN KEY (workflow_id, tenant_id)
        REFERENCES workflow_definition(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT uq_workflow_trigger UNIQUE (tenant_id, workflow_id, event_type)
);

CREATE TABLE workflow_condition (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    workflow_id UUID NOT NULL,
    field_path VARCHAR(180) NOT NULL,
    comparison_operator VARCHAR(20) NOT NULL,
    expected_value VARCHAR(500),
    value_type VARCHAR(20) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_workflow_condition_definition FOREIGN KEY (workflow_id, tenant_id)
        REFERENCES workflow_definition(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT ck_workflow_condition_operator CHECK (
        comparison_operator IN ('EQ','NE','GT','GTE','LT','LTE','EXISTS','IN')
    ),
    CONSTRAINT ck_workflow_condition_type CHECK (
        value_type IN ('STRING','NUMBER','BOOLEAN')
    )
);

CREATE TABLE workflow_action (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    workflow_id UUID NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    action_label VARCHAR(180) NOT NULL,
    configuration_json VARCHAR(4000) NOT NULL,
    emitted_event_type VARCHAR(140),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_workflow_action_definition FOREIGN KEY (workflow_id, tenant_id)
        REFERENCES workflow_definition(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT ck_workflow_action_type CHECK (
        action_type IN ('SEND_NOTIFICATION','SEND_EMAIL','CREATE_TASK','NOTIFY_PARENT','EMIT_EVENT')
    ),
    CONSTRAINT ck_workflow_action_emission CHECK (
        (action_type='EMIT_EVENT' AND emitted_event_type IS NOT NULL)
        OR (action_type<>'EMIT_EVENT' AND emitted_event_type IS NULL)
    )
);

CREATE TABLE workflow_execution (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    workflow_id UUID NOT NULL,
    event_id UUID NOT NULL,
    root_event_id UUID NOT NULL,
    parent_execution_id UUID,
    trigger_event_type VARCHAR(140) NOT NULL,
    status VARCHAR(30) NOT NULL,
    depth INTEGER NOT NULL,
    payload_json VARCHAR(8000) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    failure_reason VARCHAR(1000),
    CONSTRAINT uq_workflow_execution_event UNIQUE (tenant_id, workflow_id, event_id),
    CONSTRAINT uq_workflow_execution_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_workflow_execution_definition FOREIGN KEY (workflow_id, tenant_id)
        REFERENCES workflow_definition(id, tenant_id),
    CONSTRAINT fk_workflow_execution_parent FOREIGN KEY (parent_execution_id, tenant_id)
        REFERENCES workflow_execution(id, tenant_id),
    CONSTRAINT ck_workflow_execution_status CHECK (
        status IN ('SKIPPED','COMPLETED','LOOP_BLOCKED','FAILED')
    ),
    CONSTRAINT ck_workflow_execution_depth CHECK (depth BETWEEN 0 AND 12)
);

CREATE TABLE workflow_execution_log (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    execution_id UUID NOT NULL,
    sequence_number INTEGER NOT NULL,
    log_level VARCHAR(10) NOT NULL,
    step_type VARCHAR(30) NOT NULL,
    message VARCHAR(600) NOT NULL,
    detail_json VARCHAR(4000),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_workflow_log_execution FOREIGN KEY (execution_id, tenant_id)
        REFERENCES workflow_execution(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT uq_workflow_execution_log UNIQUE (tenant_id, execution_id, sequence_number),
    CONSTRAINT ck_workflow_log_level CHECK (log_level IN ('INFO','WARN','ERROR')),
    CONSTRAINT ck_workflow_log_step CHECK (
        step_type IN ('TRIGGER','CONDITION','ACTION','LOOP_GUARD','EXECUTION')
    )
);

CREATE INDEX idx_workflow_trigger_lookup
    ON workflow_trigger(tenant_id, event_type, workflow_id);
CREATE INDEX idx_workflow_execution_history
    ON workflow_execution(tenant_id, started_at DESC, status);
CREATE INDEX idx_workflow_execution_root
    ON workflow_execution(tenant_id, root_event_id, depth);
CREATE INDEX idx_workflow_execution_log
    ON workflow_execution_log(tenant_id, execution_id, sequence_number);
