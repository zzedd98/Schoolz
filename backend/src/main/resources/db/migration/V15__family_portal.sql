CREATE TABLE family_portal_preference (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    portal_account_id UUID NOT NULL,
    preferred_student_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_family_portal_account_tenant
        FOREIGN KEY (portal_account_id, tenant_id)
        REFERENCES admission_portal_account(id, tenant_id),
    CONSTRAINT fk_family_portal_student_tenant
        FOREIGN KEY (preferred_student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT uq_family_portal_account UNIQUE (tenant_id, portal_account_id),
    CONSTRAINT uq_family_portal_preference_id_tenant UNIQUE (id, tenant_id)
);

CREATE INDEX idx_family_portal_preferred_student
    ON family_portal_preference (tenant_id, preferred_student_id);
