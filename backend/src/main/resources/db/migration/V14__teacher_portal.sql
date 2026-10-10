CREATE TABLE teacher_portal_profile (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    user_account_id UUID NOT NULL,
    staff_profile_id UUID NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_teacher_portal_user_tenant
        FOREIGN KEY (user_account_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT fk_teacher_portal_staff_tenant
        FOREIGN KEY (staff_profile_id, tenant_id)
        REFERENCES staff_profile(id, tenant_id),
    CONSTRAINT uq_teacher_portal_user UNIQUE (tenant_id, user_account_id),
    CONSTRAINT uq_teacher_portal_staff UNIQUE (tenant_id, staff_profile_id),
    CONSTRAINT uq_teacher_portal_id_tenant UNIQUE (id, tenant_id)
);

CREATE INDEX idx_teacher_portal_active_user
    ON teacher_portal_profile (tenant_id, user_account_id, active);
