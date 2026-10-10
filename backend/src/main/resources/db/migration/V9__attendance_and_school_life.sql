ALTER TABLE student_enrollment
    ADD CONSTRAINT uq_student_enrollment_id_tenant UNIQUE (id, tenant_id);

CREATE TABLE attendance_absence_reason (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    code VARCHAR(50) NOT NULL,
    name_fr VARCHAR(140) NOT NULL,
    name_ar VARCHAR(140) NOT NULL,
    category VARCHAR(30) NOT NULL,
    requires_document BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_attendance_reason_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uq_attendance_reason_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_attendance_reason_category CHECK
        (category IN ('MEDICAL', 'FAMILY', 'TRANSPORT', 'ADMINISTRATIVE', 'OTHER'))
);

CREATE TABLE attendance_session (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    campus_id UUID NOT NULL,
    academic_year_id UUID NOT NULL,
    classroom_id UUID NOT NULL,
    teacher_staff_id UUID NOT NULL,
    session_date DATE NOT NULL,
    subject_code VARCHAR(60) NOT NULL,
    subject_name VARCHAR(140) NOT NULL,
    starts_at TIME NOT NULL,
    ends_at TIME NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    submitted_at TIMESTAMP WITH TIME ZONE,
    submitted_by_user_id UUID,
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_attendance_session_campus FOREIGN KEY (campus_id, tenant_id)
        REFERENCES school_campus(id, tenant_id),
    CONSTRAINT fk_attendance_session_year FOREIGN KEY (academic_year_id, tenant_id)
        REFERENCES academic_year(id, tenant_id),
    CONSTRAINT fk_attendance_session_classroom FOREIGN KEY (classroom_id, tenant_id)
        REFERENCES classroom(id, tenant_id),
    CONSTRAINT fk_attendance_session_teacher FOREIGN KEY (teacher_staff_id, tenant_id)
        REFERENCES staff_profile(id, tenant_id),
    CONSTRAINT fk_attendance_session_submitter FOREIGN KEY (submitted_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT fk_attendance_session_creator FOREIGN KEY (created_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT uq_attendance_session_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uq_attendance_session_slot UNIQUE
        (tenant_id, classroom_id, session_date, starts_at),
    CONSTRAINT ck_attendance_session_time CHECK (starts_at < ends_at),
    CONSTRAINT ck_attendance_session_status CHECK (status IN ('OPEN', 'SUBMITTED', 'CANCELLED'))
);

CREATE TABLE attendance_record (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    session_id UUID NOT NULL,
    student_enrollment_id UUID NOT NULL,
    student_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PRESENT',
    notes VARCHAR(500),
    marked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    marked_by_user_id UUID NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_attendance_record_session FOREIGN KEY (session_id, tenant_id)
        REFERENCES attendance_session(id, tenant_id),
    CONSTRAINT fk_attendance_record_enrollment FOREIGN KEY (student_enrollment_id, tenant_id)
        REFERENCES student_enrollment(id, tenant_id),
    CONSTRAINT fk_attendance_record_student FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT fk_attendance_record_marker FOREIGN KEY (marked_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT uq_attendance_record_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uq_attendance_record_student UNIQUE (tenant_id, session_id, student_id),
    CONSTRAINT ck_attendance_record_status CHECK
        (status IN ('PRESENT', 'ABSENT', 'LATE', 'EARLY_DEPARTURE'))
);

CREATE TABLE attendance_absence (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    attendance_record_id UUID NOT NULL,
    student_id UUID NOT NULL,
    absence_reason_id UUID,
    occurred_on DATE NOT NULL,
    resolution_status VARCHAR(20) NOT NULL DEFAULT 'UNEXCUSED',
    notified_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_attendance_absence_record FOREIGN KEY (attendance_record_id, tenant_id)
        REFERENCES attendance_record(id, tenant_id),
    CONSTRAINT fk_attendance_absence_student FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT fk_attendance_absence_reason FOREIGN KEY (absence_reason_id, tenant_id)
        REFERENCES attendance_absence_reason(id, tenant_id),
    CONSTRAINT uq_attendance_absence_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uq_attendance_absence_record UNIQUE (tenant_id, attendance_record_id),
    CONSTRAINT ck_attendance_absence_resolution CHECK
        (resolution_status IN ('UNEXCUSED', 'PENDING', 'EXCUSED', 'REJECTED'))
);

CREATE TABLE attendance_late_arrival (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    attendance_record_id UUID NOT NULL,
    arrived_at TIME NOT NULL,
    minutes_late INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_attendance_late_record FOREIGN KEY (attendance_record_id, tenant_id)
        REFERENCES attendance_record(id, tenant_id),
    CONSTRAINT uq_attendance_late_record UNIQUE (tenant_id, attendance_record_id),
    CONSTRAINT ck_attendance_late_minutes CHECK (minutes_late > 0)
);

CREATE TABLE attendance_early_departure (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    attendance_record_id UUID NOT NULL,
    left_at TIME NOT NULL,
    reason VARCHAR(300),
    pickup_person_name VARCHAR(180),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_attendance_early_record FOREIGN KEY (attendance_record_id, tenant_id)
        REFERENCES attendance_record(id, tenant_id),
    CONSTRAINT uq_attendance_early_record UNIQUE (tenant_id, attendance_record_id)
);

CREATE TABLE attendance_excused_absence (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    absence_id UUID NOT NULL,
    portal_account_id UUID NOT NULL,
    absence_reason_id UUID NOT NULL,
    parent_statement VARCHAR(1000) NOT NULL,
    evidence_object_key VARCHAR(500),
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_at TIMESTAMP WITH TIME ZONE,
    reviewed_by_user_id UUID,
    review_notes VARCHAR(500),
    CONSTRAINT fk_attendance_excused_absence FOREIGN KEY (absence_id, tenant_id)
        REFERENCES attendance_absence(id, tenant_id),
    CONSTRAINT fk_attendance_excused_portal FOREIGN KEY (portal_account_id, tenant_id)
        REFERENCES admission_portal_account(id, tenant_id),
    CONSTRAINT fk_attendance_excused_reason FOREIGN KEY (absence_reason_id, tenant_id)
        REFERENCES attendance_absence_reason(id, tenant_id),
    CONSTRAINT fk_attendance_excused_reviewer FOREIGN KEY (reviewed_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT uq_attendance_excused_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uq_attendance_excused_absence UNIQUE (tenant_id, absence_id),
    CONSTRAINT ck_attendance_excused_review CHECK
        (review_status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE TABLE attendance_parent_notification (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    portal_account_id UUID NOT NULL,
    student_id UUID NOT NULL,
    absence_id UUID,
    notification_type VARCHAR(40) NOT NULL,
    title VARCHAR(180) NOT NULL,
    message VARCHAR(700) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UNREAD',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_attendance_notification_portal FOREIGN KEY (portal_account_id, tenant_id)
        REFERENCES admission_portal_account(id, tenant_id),
    CONSTRAINT fk_attendance_notification_student FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT fk_attendance_notification_absence FOREIGN KEY (absence_id, tenant_id)
        REFERENCES attendance_absence(id, tenant_id),
    CONSTRAINT uq_attendance_notification_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_attendance_notification_type CHECK
        (notification_type IN ('ABSENCE_RECORDED', 'JUSTIFICATION_APPROVED', 'JUSTIFICATION_REJECTED')),
    CONSTRAINT ck_attendance_notification_status CHECK (status IN ('UNREAD', 'READ'))
);

CREATE TABLE disciplinary_incident (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    student_id UUID NOT NULL,
    classroom_id UUID,
    reported_by_user_id UUID NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    category VARCHAR(60) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    description VARCHAR(1200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_disciplinary_student FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT fk_disciplinary_classroom FOREIGN KEY (classroom_id, tenant_id)
        REFERENCES classroom(id, tenant_id),
    CONSTRAINT fk_disciplinary_reporter FOREIGN KEY (reported_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT uq_disciplinary_incident_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_disciplinary_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_disciplinary_status CHECK (status IN ('OPEN', 'UNDER_REVIEW', 'CLOSED'))
);

CREATE TABLE attendance_sanction (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    incident_id UUID NOT NULL,
    sanction_type VARCHAR(60) NOT NULL,
    description VARCHAR(800) NOT NULL,
    starts_on DATE NOT NULL,
    ends_on DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    decided_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_attendance_sanction_incident FOREIGN KEY (incident_id, tenant_id)
        REFERENCES disciplinary_incident(id, tenant_id),
    CONSTRAINT fk_attendance_sanction_decider FOREIGN KEY (decided_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT ck_attendance_sanction_dates CHECK (ends_on IS NULL OR starts_on <= ends_on),
    CONSTRAINT ck_attendance_sanction_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED'))
);

CREATE TABLE teacher_observation (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    student_id UUID NOT NULL,
    classroom_id UUID,
    teacher_staff_id UUID NOT NULL,
    observation_date DATE NOT NULL,
    category VARCHAR(60) NOT NULL,
    observation VARCHAR(1200) NOT NULL,
    visible_to_parent BOOLEAN NOT NULL DEFAULT FALSE,
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_teacher_observation_student FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT fk_teacher_observation_classroom FOREIGN KEY (classroom_id, tenant_id)
        REFERENCES classroom(id, tenant_id),
    CONSTRAINT fk_teacher_observation_staff FOREIGN KEY (teacher_staff_id, tenant_id)
        REFERENCES staff_profile(id, tenant_id),
    CONSTRAINT fk_teacher_observation_creator FOREIGN KEY (created_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id)
);

CREATE INDEX idx_attendance_session_teacher_day ON attendance_session
    (tenant_id, teacher_staff_id, session_date, starts_at);
CREATE INDEX idx_attendance_record_student ON attendance_record
    (tenant_id, student_id, marked_at);
CREATE INDEX idx_attendance_absence_student ON attendance_absence
    (tenant_id, student_id, occurred_on, resolution_status);
CREATE INDEX idx_attendance_justification_review ON attendance_excused_absence
    (tenant_id, review_status, submitted_at);
CREATE INDEX idx_attendance_parent_notifications ON attendance_parent_notification
    (tenant_id, portal_account_id, status, created_at);
CREATE INDEX idx_disciplinary_student ON disciplinary_incident
    (tenant_id, student_id, occurred_at);
CREATE INDEX idx_teacher_observation_student ON teacher_observation
    (tenant_id, student_id, observation_date);
