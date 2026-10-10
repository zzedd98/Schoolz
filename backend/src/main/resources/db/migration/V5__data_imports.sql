CREATE TABLE data_import_job (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    import_type VARCHAR(40) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_format VARCHAR(10) NOT NULL,
    status VARCHAR(30) NOT NULL,
    source_headers TEXT NOT NULL,
    column_mapping TEXT NOT NULL,
    total_rows INTEGER NOT NULL DEFAULT 0,
    valid_rows INTEGER NOT NULL DEFAULT 0,
    invalid_rows INTEGER NOT NULL DEFAULT 0,
    imported_rows INTEGER NOT NULL DEFAULT 0,
    error_summary VARCHAR(500),
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_data_import_job_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_data_import_job_counts CHECK (
        total_rows >= 0 AND valid_rows >= 0 AND invalid_rows >= 0 AND imported_rows >= 0
    )
);

CREATE TABLE data_import_row (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    job_id UUID NOT NULL,
    row_number INTEGER NOT NULL,
    raw_data TEXT NOT NULL,
    normalized_data TEXT NOT NULL,
    validation_errors TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    target_record_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_data_import_row_job_tenant FOREIGN KEY (job_id, tenant_id)
        REFERENCES data_import_job(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT uq_data_import_row_number UNIQUE (tenant_id, job_id, row_number),
    CONSTRAINT ck_data_import_row_number CHECK (row_number >= 2)
);

CREATE TABLE staff_profile (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    person_id UUID NOT NULL,
    employee_number VARCHAR(80) NOT NULL,
    job_title VARCHAR(160) NOT NULL,
    employment_status VARCHAR(30) NOT NULL,
    hired_on DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_staff_person_tenant FOREIGN KEY (person_id, tenant_id)
        REFERENCES person(id, tenant_id),
    CONSTRAINT uq_staff_person UNIQUE (tenant_id, person_id),
    CONSTRAINT uq_staff_employee_number UNIQUE (tenant_id, employee_number),
    CONSTRAINT uq_staff_id_tenant UNIQUE (id, tenant_id)
);

CREATE TABLE imported_legacy_payment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    import_job_id UUID NOT NULL,
    source_row_number INTEGER NOT NULL,
    student_id UUID NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    paid_on DATE NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    external_reference VARCHAR(120),
    currency_code VARCHAR(3) NOT NULL,
    reconciliation_status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_legacy_payment_job_tenant FOREIGN KEY (import_job_id, tenant_id)
        REFERENCES data_import_job(id, tenant_id),
    CONSTRAINT fk_legacy_payment_student_tenant FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT uq_legacy_payment_source UNIQUE
        (tenant_id, import_job_id, source_row_number),
    CONSTRAINT ck_legacy_payment_amount CHECK (amount > 0)
);

CREATE TABLE imported_opening_balance (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    import_job_id UUID NOT NULL,
    source_row_number INTEGER NOT NULL,
    student_id UUID NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    as_of_date DATE NOT NULL,
    description VARCHAR(300),
    currency_code VARCHAR(3) NOT NULL,
    reconciliation_status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_opening_balance_job_tenant FOREIGN KEY (import_job_id, tenant_id)
        REFERENCES data_import_job(id, tenant_id),
    CONSTRAINT fk_opening_balance_student_tenant FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT uq_opening_balance_source UNIQUE
        (tenant_id, import_job_id, source_row_number),
    CONSTRAINT ck_opening_balance_amount CHECK (amount <> 0)
);

CREATE INDEX idx_data_import_job_tenant ON data_import_job (tenant_id, created_at DESC);
CREATE INDEX idx_data_import_row_job ON data_import_row (tenant_id, job_id, row_number);
CREATE INDEX idx_staff_profile_tenant ON staff_profile (tenant_id, employment_status);
CREATE INDEX idx_legacy_payment_student ON imported_legacy_payment (tenant_id, student_id);
CREATE INDEX idx_opening_balance_student ON imported_opening_balance (tenant_id, student_id);
