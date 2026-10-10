CREATE TABLE school_calendar_event (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    title VARCHAR(220) NOT NULL,
    description TEXT,
    event_type VARCHAR(30) NOT NULL,
    audience_scope VARCHAR(20) NOT NULL,
    campus_id UUID,
    classroom_id UUID,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    all_day BOOLEAN NOT NULL DEFAULT FALSE,
    location VARCHAR(220),
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    sync_provider VARCHAR(80),
    external_reference VARCHAR(180),
    sync_status VARCHAR(20) NOT NULL DEFAULT 'LOCAL_ONLY',
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_school_calendar_campus_tenant FOREIGN KEY (campus_id, tenant_id)
        REFERENCES school_campus(id, tenant_id),
    CONSTRAINT fk_school_calendar_class_tenant FOREIGN KEY (classroom_id, tenant_id)
        REFERENCES classroom(id, tenant_id),
    CONSTRAINT fk_school_calendar_creator_tenant FOREIGN KEY (created_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT uq_school_calendar_event_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uq_school_calendar_external UNIQUE (tenant_id, sync_provider, external_reference),
    CONSTRAINT ck_school_calendar_type CHECK (
        event_type IN ('HOLIDAY','EXAM','MEETING','TRIP','ACTIVITY','PARENT_TEACHER')
    ),
    CONSTRAINT ck_school_calendar_scope CHECK (
        (audience_scope='SCHOOL' AND campus_id IS NULL AND classroom_id IS NULL)
        OR (audience_scope='CAMPUS' AND campus_id IS NOT NULL AND classroom_id IS NULL)
        OR (audience_scope='CLASS' AND campus_id IS NULL AND classroom_id IS NOT NULL)
    ),
    CONSTRAINT ck_school_calendar_dates CHECK (ends_at > starts_at),
    CONSTRAINT ck_school_calendar_status CHECK (status IN ('SCHEDULED','CANCELLED')),
    CONSTRAINT ck_school_calendar_sync CHECK (sync_status IN ('LOCAL_ONLY','SYNCED','PENDING','ERROR'))
);

CREATE INDEX idx_school_calendar_range
    ON school_calendar_event (tenant_id, starts_at, ends_at, status);
CREATE INDEX idx_school_calendar_filter
    ON school_calendar_event (tenant_id, event_type, campus_id, starts_at);
