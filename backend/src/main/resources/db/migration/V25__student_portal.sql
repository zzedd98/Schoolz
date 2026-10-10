CREATE TABLE student_portal_profile (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    user_account_id UUID NOT NULL,
    student_id UUID NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_student_portal_user_tenant
        FOREIGN KEY (user_account_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_student_portal_student_tenant
        FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT uq_student_portal_user UNIQUE (tenant_id, user_account_id),
    CONSTRAINT uq_student_portal_student UNIQUE (tenant_id, student_id),
    CONSTRAINT uq_student_portal_profile_id_tenant UNIQUE (id, tenant_id)
);

CREATE INDEX idx_student_portal_student
    ON student_portal_profile (tenant_id, student_id, active);
