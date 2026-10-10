CREATE TABLE hr_department (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    campus_id UUID,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_hr_department_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_hr_department_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_hr_department_campus FOREIGN KEY (campus_id, tenant_id)
        REFERENCES school_campus(id, tenant_id)
);

CREATE TABLE hr_position (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    department_id UUID,
    code VARCHAR(60) NOT NULL,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(500),
    teacher_position BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_hr_position_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_hr_position_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_hr_position_department FOREIGN KEY (department_id, tenant_id)
        REFERENCES hr_department(id, tenant_id)
);

CREATE TABLE hr_employee (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    person_id UUID NOT NULL,
    staff_profile_id UUID NOT NULL,
    department_id UUID,
    position_id UUID,
    manager_employee_id UUID,
    work_email VARCHAR(254),
    work_phone VARCHAR(40),
    status VARCHAR(30) NOT NULL,
    joined_on DATE NOT NULL,
    ended_on DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_hr_employee_person UNIQUE (tenant_id, person_id),
    CONSTRAINT uq_hr_employee_staff UNIQUE (tenant_id, staff_profile_id),
    CONSTRAINT uq_hr_employee_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_hr_employee_person FOREIGN KEY (person_id, tenant_id)
        REFERENCES person(id, tenant_id),
    CONSTRAINT fk_hr_employee_staff FOREIGN KEY (staff_profile_id, tenant_id)
        REFERENCES staff_profile(id, tenant_id),
    CONSTRAINT fk_hr_employee_department FOREIGN KEY (department_id, tenant_id)
        REFERENCES hr_department(id, tenant_id),
    CONSTRAINT fk_hr_employee_position FOREIGN KEY (position_id, tenant_id)
        REFERENCES hr_position(id, tenant_id),
    CONSTRAINT fk_hr_employee_manager FOREIGN KEY (manager_employee_id, tenant_id)
        REFERENCES hr_employee(id, tenant_id),
    CONSTRAINT ck_hr_employee_status CHECK (status IN ('ACTIVE','ON_LEAVE','SUSPENDED','ENDED')),
    CONSTRAINT ck_hr_employee_dates CHECK (ended_on IS NULL OR ended_on >= joined_on)
);

CREATE TABLE hr_employment_contract (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    employee_id UUID NOT NULL,
    contract_number VARCHAR(80) NOT NULL,
    contract_type VARCHAR(40) NOT NULL,
    starts_on DATE NOT NULL,
    ends_on DATE,
    weekly_hours NUMERIC(5,2),
    status VARCHAR(30) NOT NULL,
    notes VARCHAR(1000),
    created_by_user_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_hr_contract_number UNIQUE (tenant_id, contract_number),
    CONSTRAINT uq_hr_contract_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_hr_contract_employee FOREIGN KEY (employee_id, tenant_id)
        REFERENCES hr_employee(id, tenant_id),
    CONSTRAINT ck_hr_contract_dates CHECK (ends_on IS NULL OR ends_on >= starts_on),
    CONSTRAINT ck_hr_contract_hours CHECK (weekly_hours IS NULL OR (weekly_hours > 0 AND weekly_hours <= 80)),
    CONSTRAINT ck_hr_contract_status CHECK (status IN ('DRAFT','ACTIVE','EXPIRED','TERMINATED'))
);

CREATE TABLE hr_work_schedule (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    employee_id UUID NOT NULL,
    day_of_week SMALLINT NOT NULL,
    starts_at TIME NOT NULL,
    ends_at TIME NOT NULL,
    break_minutes INTEGER NOT NULL DEFAULT 0,
    effective_from DATE NOT NULL,
    effective_to DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_hr_schedule_day UNIQUE (tenant_id, employee_id, day_of_week, effective_from),
    CONSTRAINT fk_hr_schedule_employee FOREIGN KEY (employee_id, tenant_id)
        REFERENCES hr_employee(id, tenant_id),
    CONSTRAINT ck_hr_schedule_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_hr_schedule_time CHECK (ends_at > starts_at),
    CONSTRAINT ck_hr_schedule_break CHECK (break_minutes BETWEEN 0 AND 240),
    CONSTRAINT ck_hr_schedule_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE TABLE hr_leave_request (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    employee_id UUID NOT NULL,
    leave_type VARCHAR(50) NOT NULL,
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL,
    working_days NUMERIC(6,2) NOT NULL,
    reason VARCHAR(1000),
    status VARCHAR(30) NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    manager_reviewed_by_user_id UUID,
    manager_reviewed_at TIMESTAMP WITH TIME ZONE,
    decided_by_user_id UUID,
    decided_at TIMESTAMP WITH TIME ZONE,
    decision_comment VARCHAR(1000),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_hr_leave_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_hr_leave_employee FOREIGN KEY (employee_id, tenant_id)
        REFERENCES hr_employee(id, tenant_id),
    CONSTRAINT ck_hr_leave_dates CHECK (ends_on >= starts_on),
    CONSTRAINT ck_hr_leave_days CHECK (working_days > 0),
    CONSTRAINT ck_hr_leave_status CHECK (status IN ('REQUESTED','MANAGER_REVIEW','APPROVED','REJECTED'))
);

CREATE TABLE hr_staff_attendance (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    employee_id UUID NOT NULL,
    work_date DATE NOT NULL,
    checked_in_at TIMESTAMP WITH TIME ZONE,
    checked_out_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(30) NOT NULL,
    source VARCHAR(40) NOT NULL,
    note VARCHAR(500),
    recorded_by_user_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_hr_attendance_day UNIQUE (tenant_id, employee_id, work_date),
    CONSTRAINT fk_hr_attendance_employee FOREIGN KEY (employee_id, tenant_id)
        REFERENCES hr_employee(id, tenant_id),
    CONSTRAINT ck_hr_attendance_status CHECK (status IN ('PRESENT','REMOTE','LEAVE','ABSENT','REST_DAY')),
    CONSTRAINT ck_hr_attendance_times CHECK (checked_out_at IS NULL OR checked_in_at IS NULL OR checked_out_at >= checked_in_at)
);

CREATE TABLE hr_staff_document (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    employee_id UUID NOT NULL,
    document_type VARCHAR(60) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    object_key VARCHAR(700) NOT NULL,
    issued_on DATE,
    expires_on DATE,
    status VARCHAR(30) NOT NULL,
    uploaded_by_user_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_hr_document_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_hr_document_employee FOREIGN KEY (employee_id, tenant_id)
        REFERENCES hr_employee(id, tenant_id),
    CONSTRAINT ck_hr_document_status CHECK (status IN ('VALID','EXPIRING','EXPIRED','ARCHIVED'))
);

CREATE TABLE hr_teacher_qualification (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    employee_id UUID NOT NULL,
    qualification_type VARCHAR(60) NOT NULL,
    title VARCHAR(220) NOT NULL,
    institution VARCHAR(220),
    subject_area VARCHAR(160),
    obtained_on DATE,
    expires_on DATE,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    verified_by_user_id UUID,
    verified_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_hr_qualification_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_hr_qualification_employee FOREIGN KEY (employee_id, tenant_id)
        REFERENCES hr_employee(id, tenant_id),
    CONSTRAINT ck_hr_qualification_dates CHECK (expires_on IS NULL OR obtained_on IS NULL OR expires_on >= obtained_on)
);

CREATE INDEX idx_hr_employee_directory ON hr_employee (tenant_id, status, department_id);
CREATE INDEX idx_hr_contract_employee ON hr_employment_contract (tenant_id, employee_id, starts_on DESC);
CREATE INDEX idx_hr_leave_workflow ON hr_leave_request (tenant_id, status, starts_on);
CREATE INDEX idx_hr_attendance_date ON hr_staff_attendance (tenant_id, work_date, status);
CREATE INDEX idx_hr_document_expiry ON hr_staff_document (tenant_id, expires_on, status);
CREATE INDEX idx_hr_qualification_employee ON hr_teacher_qualification (tenant_id, employee_id);
