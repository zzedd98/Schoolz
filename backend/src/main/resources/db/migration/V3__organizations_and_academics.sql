CREATE TABLE school_organization (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL UNIQUE REFERENCES identity_tenant(id),
    code VARCHAR(80) NOT NULL,
    name VARCHAR(180) NOT NULL,
    legal_name VARCHAR(220),
    logo_object_key VARCHAR(500),
    address_line1 VARCHAR(220),
    address_line2 VARCHAR(220),
    city VARCHAR(120),
    region VARCHAR(120),
    postal_code VARCHAR(20),
    country_code VARCHAR(2) NOT NULL DEFAULT 'MA',
    phone VARCHAR(40),
    email VARCHAR(254),
    business_identifiers TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_school_organization_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_school_organization_id_tenant UNIQUE (id, tenant_id)
);

CREATE TABLE school_settings (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL UNIQUE REFERENCES identity_tenant(id),
    organization_id UUID NOT NULL UNIQUE,
    default_locale VARCHAR(10) NOT NULL,
    supported_locales VARCHAR(80) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    timezone VARCHAR(80) NOT NULL,
    week_starts_on INTEGER NOT NULL,
    working_days VARCHAR(100) NOT NULL,
    day_starts_at TIME NOT NULL,
    day_ends_at TIME NOT NULL,
    calendar_notes VARCHAR(500),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_school_settings_organization_tenant FOREIGN KEY (organization_id, tenant_id)
        REFERENCES school_organization(id, tenant_id),
    CONSTRAINT ck_school_settings_week_start CHECK (week_starts_on BETWEEN 1 AND 7),
    CONSTRAINT ck_school_settings_hours CHECK (day_starts_at < day_ends_at)
);

CREATE TABLE school_campus (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    organization_id UUID NOT NULL,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(180) NOT NULL,
    address_line1 VARCHAR(220),
    address_line2 VARCHAR(220),
    city VARCHAR(120),
    region VARCHAR(120),
    postal_code VARCHAR(20),
    country_code VARCHAR(2) NOT NULL DEFAULT 'MA',
    phone VARCHAR(40),
    email VARCHAR(254),
    locale VARCHAR(10) NOT NULL,
    timezone VARCHAR(80) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_school_campus_organization_tenant FOREIGN KEY (organization_id, tenant_id)
        REFERENCES school_organization(id, tenant_id),
    CONSTRAINT uq_school_campus_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_school_campus_id_tenant UNIQUE (id, tenant_id)
);

CREATE TABLE academic_year (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    organization_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(140) NOT NULL,
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_academic_year_organization_tenant FOREIGN KEY (organization_id, tenant_id)
        REFERENCES school_organization(id, tenant_id),
    CONSTRAINT uq_academic_year_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_academic_year_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_academic_year_dates CHECK (starts_on < ends_on)
);

CREATE TABLE academic_term (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    academic_year_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(140) NOT NULL,
    term_type VARCHAR(30) NOT NULL,
    sequence_number INTEGER NOT NULL,
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_academic_term_year_tenant FOREIGN KEY (academic_year_id, tenant_id)
        REFERENCES academic_year(id, tenant_id),
    CONSTRAINT uq_academic_term_year_code UNIQUE (academic_year_id, code),
    CONSTRAINT uq_academic_term_year_sequence UNIQUE (academic_year_id, sequence_number),
    CONSTRAINT ck_academic_term_dates CHECK (starts_on < ends_on),
    CONSTRAINT ck_academic_term_sequence CHECK (sequence_number > 0)
);

CREATE TABLE school_level (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    organization_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(140) NOT NULL,
    sequence_number INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_school_level_organization_tenant FOREIGN KEY (organization_id, tenant_id)
        REFERENCES school_organization(id, tenant_id),
    CONSTRAINT uq_school_level_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_school_level_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_school_level_sequence CHECK (sequence_number > 0)
);

CREATE TABLE grade_level (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    school_level_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(140) NOT NULL,
    sequence_number INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_grade_level_school_level_tenant FOREIGN KEY (school_level_id, tenant_id)
        REFERENCES school_level(id, tenant_id),
    CONSTRAINT uq_grade_level_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_grade_level_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_grade_level_sequence CHECK (sequence_number > 0)
);

CREATE TABLE campus_grade_offering (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    campus_id UUID NOT NULL,
    grade_level_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_offering_campus_tenant FOREIGN KEY (campus_id, tenant_id)
        REFERENCES school_campus(id, tenant_id),
    CONSTRAINT fk_offering_grade_tenant FOREIGN KEY (grade_level_id, tenant_id)
        REFERENCES grade_level(id, tenant_id),
    CONSTRAINT uq_campus_grade_offering UNIQUE (tenant_id, campus_id, grade_level_id)
);

CREATE TABLE physical_room (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    campus_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(140) NOT NULL,
    room_type VARCHAR(40) NOT NULL,
    capacity INTEGER NOT NULL,
    floor_label VARCHAR(40),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_physical_room_campus_tenant FOREIGN KEY (campus_id, tenant_id)
        REFERENCES school_campus(id, tenant_id),
    CONSTRAINT uq_physical_room_campus_code UNIQUE (campus_id, code),
    CONSTRAINT uq_physical_room_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_physical_room_capacity CHECK (capacity > 0)
);

CREATE TABLE classroom (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    campus_id UUID NOT NULL,
    academic_year_id UUID NOT NULL,
    grade_level_id UUID NOT NULL,
    physical_room_id UUID,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(140) NOT NULL,
    capacity INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_classroom_campus_tenant FOREIGN KEY (campus_id, tenant_id)
        REFERENCES school_campus(id, tenant_id),
    CONSTRAINT fk_classroom_year_tenant FOREIGN KEY (academic_year_id, tenant_id)
        REFERENCES academic_year(id, tenant_id),
    CONSTRAINT fk_classroom_grade_tenant FOREIGN KEY (grade_level_id, tenant_id)
        REFERENCES grade_level(id, tenant_id),
    CONSTRAINT fk_classroom_room_tenant FOREIGN KEY (physical_room_id, tenant_id)
        REFERENCES physical_room(id, tenant_id),
    CONSTRAINT uq_classroom_year_campus_code UNIQUE (academic_year_id, campus_id, code),
    CONSTRAINT ck_classroom_capacity CHECK (capacity > 0)
);

CREATE INDEX idx_school_campus_tenant ON school_campus (tenant_id);
CREATE INDEX idx_academic_year_tenant ON academic_year (tenant_id, status);
CREATE INDEX idx_academic_term_year ON academic_term (tenant_id, academic_year_id, sequence_number);
CREATE INDEX idx_grade_level_school_level ON grade_level (tenant_id, school_level_id);
CREATE INDEX idx_campus_grade_offering ON campus_grade_offering (tenant_id, campus_id);
CREATE INDEX idx_physical_room_campus ON physical_room (tenant_id, campus_id);
CREATE INDEX idx_classroom_scope ON classroom (tenant_id, academic_year_id, campus_id);
