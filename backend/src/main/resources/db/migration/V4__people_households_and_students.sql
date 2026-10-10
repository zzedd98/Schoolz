ALTER TABLE classroom
    ADD CONSTRAINT uq_classroom_id_tenant UNIQUE (id, tenant_id);

CREATE TABLE person (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    first_name VARCHAR(120) NOT NULL,
    last_name VARCHAR(120) NOT NULL,
    preferred_name VARCHAR(120),
    birth_date DATE,
    gender VARCHAR(40),
    nationality_code VARCHAR(2),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_person_id_tenant UNIQUE (id, tenant_id)
);

CREATE TABLE person_address (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    person_id UUID NOT NULL,
    address_type VARCHAR(30) NOT NULL,
    line1 VARCHAR(220) NOT NULL,
    line2 VARCHAR(220),
    city VARCHAR(120) NOT NULL,
    region VARCHAR(120),
    postal_code VARCHAR(20),
    country_code VARCHAR(2) NOT NULL,
    primary_address BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_person_address_person_tenant FOREIGN KEY (person_id, tenant_id)
        REFERENCES person(id, tenant_id)
);

CREATE TABLE person_contact_method (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    person_id UUID NOT NULL,
    contact_type VARCHAR(30) NOT NULL,
    contact_value VARCHAR(254) NOT NULL,
    normalized_value VARCHAR(254) NOT NULL,
    primary_contact BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_contact_person_tenant FOREIGN KEY (person_id, tenant_id)
        REFERENCES person(id, tenant_id),
    CONSTRAINT uq_contact_tenant_type_value UNIQUE (tenant_id, contact_type, normalized_value)
);

CREATE TABLE household (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    name VARCHAR(180) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_household_id_tenant UNIQUE (id, tenant_id)
);

CREATE TABLE student_profile (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    person_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    photo_object_key VARCHAR(500),
    archived_at TIMESTAMP WITH TIME ZONE,
    archive_reason VARCHAR(300),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_student_person_tenant FOREIGN KEY (person_id, tenant_id)
        REFERENCES person(id, tenant_id),
    CONSTRAINT uq_student_person UNIQUE (tenant_id, person_id),
    CONSTRAINT uq_student_id_tenant UNIQUE (id, tenant_id)
);

CREATE TABLE guardian_profile (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    person_id UUID NOT NULL,
    occupation VARCHAR(160),
    employer VARCHAR(180),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_guardian_person_tenant FOREIGN KEY (person_id, tenant_id)
        REFERENCES person(id, tenant_id),
    CONSTRAINT uq_guardian_person UNIQUE (tenant_id, person_id),
    CONSTRAINT uq_guardian_id_tenant UNIQUE (id, tenant_id)
);

CREATE TABLE household_relationship (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    household_id UUID NOT NULL,
    person_id UUID NOT NULL,
    relationship_type VARCHAR(50) NOT NULL,
    legal_guardian BOOLEAN NOT NULL DEFAULT FALSE,
    financial_responsible BOOLEAN NOT NULL DEFAULT FALSE,
    lives_with_student BOOLEAN NOT NULL DEFAULT TRUE,
    starts_on DATE,
    ends_on DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_relationship_household_tenant FOREIGN KEY (household_id, tenant_id)
        REFERENCES household(id, tenant_id),
    CONSTRAINT fk_relationship_person_tenant FOREIGN KEY (person_id, tenant_id)
        REFERENCES person(id, tenant_id),
    CONSTRAINT uq_household_person_relationship UNIQUE
        (tenant_id, household_id, person_id, relationship_type)
);

CREATE TABLE emergency_contact (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    student_id UUID NOT NULL,
    person_id UUID,
    display_name VARCHAR(180) NOT NULL,
    relationship_type VARCHAR(50) NOT NULL,
    phone VARCHAR(40) NOT NULL,
    priority INTEGER NOT NULL,
    authorized_pickup BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_emergency_student_tenant FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT fk_emergency_person_tenant FOREIGN KEY (person_id, tenant_id)
        REFERENCES person(id, tenant_id),
    CONSTRAINT uq_emergency_priority UNIQUE (tenant_id, student_id, priority),
    CONSTRAINT ck_emergency_priority CHECK (priority > 0)
);

CREATE TABLE student_enrollment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    student_id UUID NOT NULL,
    campus_id UUID NOT NULL,
    academic_year_id UUID NOT NULL,
    classroom_id UUID,
    status VARCHAR(30) NOT NULL,
    enrolled_on DATE NOT NULL,
    ended_on DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_enrollment_student_tenant FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT fk_enrollment_campus_tenant FOREIGN KEY (campus_id, tenant_id)
        REFERENCES school_campus(id, tenant_id),
    CONSTRAINT fk_enrollment_year_tenant FOREIGN KEY (academic_year_id, tenant_id)
        REFERENCES academic_year(id, tenant_id),
    CONSTRAINT fk_enrollment_classroom_tenant FOREIGN KEY (classroom_id, tenant_id)
        REFERENCES classroom(id, tenant_id),
    CONSTRAINT uq_student_enrollment_year UNIQUE (tenant_id, student_id, academic_year_id),
    CONSTRAINT ck_enrollment_dates CHECK (ended_on IS NULL OR enrolled_on <= ended_on)
);

CREATE TABLE student_identifier (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    student_id UUID NOT NULL,
    identifier_type VARCHAR(50) NOT NULL,
    identifier_value VARCHAR(120) NOT NULL,
    issuer VARCHAR(120),
    primary_identifier BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_identifier_student_tenant FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT uq_student_identifier_value UNIQUE
        (tenant_id, identifier_type, identifier_value)
);

CREATE TABLE person_custom_field (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    owner_type VARCHAR(40) NOT NULL,
    owner_id UUID NOT NULL,
    field_key VARCHAR(80) NOT NULL,
    field_value TEXT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_person_custom_field UNIQUE (tenant_id, owner_type, owner_id, field_key)
);

CREATE TABLE person_document (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    person_id UUID NOT NULL,
    document_type VARCHAR(60) NOT NULL,
    title VARCHAR(180) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    status VARCHAR(30) NOT NULL,
    expires_on DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_document_person_tenant FOREIGN KEY (person_id, tenant_id)
        REFERENCES person(id, tenant_id)
);

CREATE TABLE student_history (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    student_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    summary VARCHAR(300) NOT NULL,
    details TEXT,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_user_id UUID,
    CONSTRAINT fk_history_student_tenant FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id)
);

CREATE INDEX idx_person_name ON person (tenant_id, last_name, first_name);
CREATE INDEX idx_household_tenant ON household (tenant_id, name);
CREATE INDEX idx_student_status ON student_profile (tenant_id, status);
CREATE INDEX idx_guardian_tenant ON guardian_profile (tenant_id);
CREATE INDEX idx_relationship_household ON household_relationship (tenant_id, household_id);
CREATE INDEX idx_relationship_person ON household_relationship (tenant_id, person_id);
CREATE INDEX idx_enrollment_student ON student_enrollment (tenant_id, student_id, status);
CREATE INDEX idx_document_person ON person_document (tenant_id, person_id);
CREATE INDEX idx_student_history ON student_history (tenant_id, student_id, occurred_at DESC);
