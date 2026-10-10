CREATE TABLE internal_task (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    title VARCHAR(220) NOT NULL,
    description TEXT,
    assignee_user_id UUID NOT NULL,
    created_by_user_id UUID NOT NULL,
    due_date DATE NOT NULL,
    priority VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    linked_entity_type VARCHAR(50),
    linked_entity_id UUID,
    source VARCHAR(20) NOT NULL,
    source_workflow_execution_id UUID,
    source_workflow_action_id UUID,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_internal_task_assignee_tenant FOREIGN KEY (assignee_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT fk_internal_task_creator_tenant FOREIGN KEY (created_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT uq_internal_task_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uq_internal_task_workflow_action UNIQUE
        (tenant_id, source_workflow_execution_id, source_workflow_action_id),
    CONSTRAINT ck_internal_task_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT ck_internal_task_status CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE', 'CANCELLED')),
    CONSTRAINT ck_internal_task_source CHECK (source IN ('MANUAL', 'WORKFLOW')),
    CONSTRAINT ck_internal_task_link CHECK (
        (linked_entity_type IS NULL AND linked_entity_id IS NULL)
        OR (linked_entity_type IS NOT NULL AND linked_entity_id IS NOT NULL)
    ),
    CONSTRAINT ck_internal_task_workflow_source CHECK (
        (source='WORKFLOW' AND source_workflow_execution_id IS NOT NULL AND source_workflow_action_id IS NOT NULL)
        OR (source='MANUAL' AND source_workflow_execution_id IS NULL AND source_workflow_action_id IS NULL)
    )
);

CREATE INDEX idx_internal_task_assignee
    ON internal_task (tenant_id, assignee_user_id, status, due_date, priority);
CREATE INDEX idx_internal_task_linked_entity
    ON internal_task (tenant_id, linked_entity_type, linked_entity_id);
CREATE INDEX idx_internal_task_due
    ON internal_task (tenant_id, status, due_date);
