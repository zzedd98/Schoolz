CREATE TABLE health_policy (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    vaccination_tracking_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by_user_id UUID REFERENCES identity_user_account(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_health_policy_tenant UNIQUE (tenant_id)
);

CREATE TABLE health_profile (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    student_id UUID NOT NULL REFERENCES student_profile(id),
    blood_group VARCHAR(12),
    insurance_provider VARCHAR(180),
    insurance_number VARCHAR(120),
    physician_name VARCHAR(180),
    physician_phone VARCHAR(40),
    emergency_notes VARCHAR(1500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_health_profile_student UNIQUE (tenant_id, student_id)
);

CREATE TABLE health_allergy (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    health_profile_id UUID NOT NULL REFERENCES health_profile(id),
    allergen VARCHAR(180) NOT NULL,
    reaction_description VARCHAR(1000),
    severity VARCHAR(20) NOT NULL,
    emergency_instructions VARCHAR(1500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    recorded_by_user_id UUID REFERENCES identity_user_account(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_health_allergy_severity CHECK (severity IN ('LOW','MODERATE','HIGH','CRITICAL'))
);

CREATE TABLE health_medication_instruction (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    health_profile_id UUID NOT NULL REFERENCES health_profile(id),
    medication_name VARCHAR(180) NOT NULL,
    dosage_text VARCHAR(180) NOT NULL,
    administration_route VARCHAR(80),
    schedule_text VARCHAR(300),
    starts_on DATE,
    ends_on DATE,
    prescriber_name VARCHAR(180),
    authorization_reference VARCHAR(180),
    instructions VARCHAR(1500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    recorded_by_user_id UUID REFERENCES identity_user_account(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_health_medication_dates CHECK (ends_on IS NULL OR starts_on IS NULL OR ends_on >= starts_on)
);

CREATE TABLE health_incident (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    student_id UUID NOT NULL REFERENCES student_profile(id),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    incident_type VARCHAR(40) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    location VARCHAR(180),
    description VARCHAR(2000) NOT NULL,
    action_taken VARCHAR(2000),
    guardian_notified_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    recorded_by_user_id UUID REFERENCES identity_user_account(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_health_incident_severity CHECK (severity IN ('LOW','MODERATE','HIGH','CRITICAL')),
    CONSTRAINT ck_health_incident_status CHECK (status IN ('OPEN','MONITORING','CLOSED','TRANSFERRED'))
);

CREATE TABLE health_nurse_visit (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    student_id UUID NOT NULL REFERENCES student_profile(id),
    incident_id UUID REFERENCES health_incident(id),
    visited_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    observations VARCHAR(2000),
    temperature_celsius NUMERIC(4,1),
    action_taken VARCHAR(2000),
    return_to_class BOOLEAN,
    referred_to VARCHAR(180),
    recorded_by_user_id UUID REFERENCES identity_user_account(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_health_visit_temperature CHECK (temperature_celsius IS NULL OR temperature_celsius BETWEEN 30 AND 45)
);

CREATE TABLE health_emergency_action (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    incident_id UUID NOT NULL REFERENCES health_incident(id),
    action_type VARCHAR(40) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    performed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    performed_by_user_id UUID REFERENCES identity_user_account(id),
    guardian_contacted BOOLEAN NOT NULL DEFAULT FALSE,
    external_reference VARCHAR(180),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_health_emergency_type CHECK (action_type IN ('FIRST_AID','GUARDIAN_CALL','EMERGENCY_SERVICE','TRANSFER','OTHER'))
);

CREATE TABLE health_vaccination_record (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    health_profile_id UUID NOT NULL REFERENCES health_profile(id),
    vaccine_name VARCHAR(180) NOT NULL,
    dose_label VARCHAR(80),
    administered_on DATE NOT NULL,
    provider_name VARCHAR(180),
    document_reference VARCHAR(180),
    next_due_on DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'RECORDED',
    recorded_by_user_id UUID REFERENCES identity_user_account(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_health_vaccination_status CHECK (status IN ('RECORDED','VERIFIED','EXEMPTED')),
    CONSTRAINT ck_health_vaccination_dates CHECK (next_due_on IS NULL OR next_due_on >= administered_on)
);

CREATE INDEX idx_health_profile_student ON health_profile (tenant_id, student_id);
CREATE INDEX idx_health_allergy_active ON health_allergy (tenant_id, health_profile_id, active);
CREATE INDEX idx_health_medication_active ON health_medication_instruction (tenant_id, health_profile_id, active);
CREATE INDEX idx_health_incident_student ON health_incident (tenant_id, student_id, occurred_at);
CREATE INDEX idx_health_incident_status ON health_incident (tenant_id, status, severity);
CREATE INDEX idx_health_visit_student ON health_nurse_visit (tenant_id, student_id, visited_at);
CREATE INDEX idx_health_vaccination_profile ON health_vaccination_record (tenant_id, health_profile_id, administered_on);
