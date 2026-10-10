ALTER TABLE identity_user_account
    ADD CONSTRAINT uq_identity_user_id_tenant UNIQUE (id, tenant_id);

CREATE TABLE childcare_policy (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    campus_id UUID NOT NULL,
    medication_recording_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    medication_authorization_required BOOLEAN NOT NULL DEFAULT TRUE,
    instructions VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_childcare_policy_campus FOREIGN KEY (campus_id, tenant_id)
        REFERENCES school_campus(id, tenant_id),
    CONSTRAINT uq_childcare_policy_campus UNIQUE (tenant_id, campus_id)
);

CREATE TABLE childcare_group (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    campus_id UUID NOT NULL,
    reference_educator_id UUID,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(140) NOT NULL,
    min_age_months INTEGER NOT NULL,
    max_age_months INTEGER NOT NULL,
    capacity INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_childcare_group_campus FOREIGN KEY (campus_id, tenant_id)
        REFERENCES school_campus(id, tenant_id),
    CONSTRAINT fk_childcare_group_educator FOREIGN KEY (reference_educator_id, tenant_id)
        REFERENCES staff_profile(id, tenant_id),
    CONSTRAINT uq_childcare_group_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uq_childcare_group_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_childcare_group_ages CHECK
        (min_age_months >= 0 AND max_age_months >= min_age_months),
    CONSTRAINT ck_childcare_group_capacity CHECK (capacity > 0)
);

CREATE TABLE childcare_profile (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    student_id UUID NOT NULL,
    group_id UUID NOT NULL,
    expected_arrival_time TIME NOT NULL,
    expected_departure_time TIME NOT NULL,
    meal_instructions VARCHAR(1000),
    bottle_instructions VARCHAR(1000),
    sleep_instructions VARCHAR(1000),
    toilet_stage VARCHAR(60),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_childcare_profile_student FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT fk_childcare_profile_group FOREIGN KEY (group_id, tenant_id)
        REFERENCES childcare_group(id, tenant_id),
    CONSTRAINT uq_childcare_profile_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uq_childcare_profile_student UNIQUE (tenant_id, student_id),
    CONSTRAINT ck_childcare_profile_schedule CHECK
        (expected_arrival_time < expected_departure_time)
);

CREATE TABLE childcare_pickup_person (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    childcare_profile_id UUID NOT NULL,
    display_name VARCHAR(180) NOT NULL,
    relationship_type VARCHAR(60) NOT NULL,
    phone VARCHAR(40),
    authorization_status VARCHAR(30) NOT NULL,
    identity_reference VARCHAR(120),
    notes VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_childcare_pickup_profile FOREIGN KEY (childcare_profile_id, tenant_id)
        REFERENCES childcare_profile(id, tenant_id),
    CONSTRAINT uq_childcare_pickup_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_childcare_pickup_status CHECK
        (authorization_status IN ('AUTHORIZED', 'PROHIBITED'))
);

CREATE TABLE childcare_daily_log (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    childcare_profile_id UUID NOT NULL,
    log_date DATE NOT NULL,
    arrival_at TIMESTAMP WITH TIME ZONE,
    departure_at TIMESTAMP WITH TIME ZONE,
    departure_pickup_person_id UUID,
    mood VARCHAR(40),
    summary VARCHAR(1000),
    status VARCHAR(30) NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    published_by_user_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_childcare_log_profile FOREIGN KEY (childcare_profile_id, tenant_id)
        REFERENCES childcare_profile(id, tenant_id),
    CONSTRAINT fk_childcare_log_pickup FOREIGN KEY (departure_pickup_person_id, tenant_id)
        REFERENCES childcare_pickup_person(id, tenant_id),
    CONSTRAINT fk_childcare_log_publisher FOREIGN KEY (published_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT uq_childcare_log_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uq_childcare_log_day UNIQUE (tenant_id, childcare_profile_id, log_date),
    CONSTRAINT ck_childcare_log_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_childcare_log_times CHECK
        (departure_at IS NULL OR arrival_at IS NULL OR departure_at >= arrival_at)
);

CREATE TABLE childcare_daily_event (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    daily_log_id UUID NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE,
    label VARCHAR(180),
    quantity NUMERIC(10,2),
    unit VARCHAR(40),
    outcome VARCHAR(80),
    notes VARCHAR(1000),
    temperature_celsius NUMERIC(4,1),
    incident_severity VARCHAR(30),
    medication_name VARCHAR(180),
    medication_dose VARCHAR(120),
    authorization_reference VARCHAR(180),
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_childcare_event_log FOREIGN KEY (daily_log_id, tenant_id)
        REFERENCES childcare_daily_log(id, tenant_id),
    CONSTRAINT fk_childcare_event_user FOREIGN KEY (created_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT ck_childcare_event_type CHECK
        (event_type IN ('MEAL', 'BOTTLE', 'SLEEP', 'DIAPER', 'TOILET', 'ACTIVITY',
                        'MOOD', 'OBSERVATION', 'INCIDENT', 'TEMPERATURE', 'MEDICATION')),
    CONSTRAINT ck_childcare_event_duration CHECK
        (ended_at IS NULL OR ended_at >= occurred_at),
    CONSTRAINT ck_childcare_temperature CHECK
        (temperature_celsius IS NULL OR
         (temperature_celsius >= 30.0 AND temperature_celsius <= 45.0))
);

CREATE INDEX idx_childcare_group_campus ON childcare_group (tenant_id, campus_id, active);
CREATE INDEX idx_childcare_profile_group ON childcare_profile (tenant_id, group_id, active);
CREATE INDEX idx_childcare_pickup_profile ON childcare_pickup_person
    (tenant_id, childcare_profile_id, authorization_status);
CREATE INDEX idx_childcare_log_day ON childcare_daily_log (tenant_id, log_date, status);
CREATE INDEX idx_childcare_event_log ON childcare_daily_event
    (tenant_id, daily_log_id, occurred_at);
