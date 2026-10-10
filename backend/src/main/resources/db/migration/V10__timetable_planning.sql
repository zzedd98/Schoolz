CREATE TABLE timetable_subject (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    code VARCHAR(60) NOT NULL,
    name_fr VARCHAR(160) NOT NULL,
    name_ar VARCHAR(160),
    color VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_timetable_subject_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_timetable_subject_id_tenant UNIQUE (id, tenant_id)
);

CREATE TABLE timetable_schedule (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    campus_id UUID NOT NULL,
    academic_year_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(180) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    valid_from DATE NOT NULL,
    valid_to DATE NOT NULL,
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_timetable_schedule_campus_tenant FOREIGN KEY (campus_id, tenant_id)
        REFERENCES school_campus(id, tenant_id),
    CONSTRAINT fk_timetable_schedule_year_tenant FOREIGN KEY (academic_year_id, tenant_id)
        REFERENCES academic_year(id, tenant_id),
    CONSTRAINT fk_timetable_schedule_creator_tenant FOREIGN KEY (created_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT uq_timetable_schedule_code UNIQUE (tenant_id, academic_year_id, campus_id, code),
    CONSTRAINT uq_timetable_schedule_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_timetable_schedule_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_timetable_schedule_dates CHECK (valid_from <= valid_to)
);

CREATE TABLE timetable_period (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    schedule_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(100) NOT NULL,
    sequence_number INTEGER NOT NULL,
    starts_at TIME NOT NULL,
    ends_at TIME NOT NULL,
    teaching BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_timetable_period_schedule_tenant FOREIGN KEY (schedule_id, tenant_id)
        REFERENCES timetable_schedule(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT uq_timetable_period_code UNIQUE (schedule_id, code),
    CONSTRAINT uq_timetable_period_sequence UNIQUE (schedule_id, sequence_number),
    CONSTRAINT uq_timetable_period_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_timetable_period_sequence CHECK (sequence_number > 0),
    CONSTRAINT ck_timetable_period_hours CHECK (starts_at < ends_at)
);

CREATE TABLE timetable_time_slot (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    schedule_id UUID NOT NULL,
    period_id UUID NOT NULL,
    day_of_week INTEGER NOT NULL,
    CONSTRAINT fk_timetable_slot_schedule_tenant FOREIGN KEY (schedule_id, tenant_id)
        REFERENCES timetable_schedule(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_timetable_slot_period_tenant FOREIGN KEY (period_id, tenant_id)
        REFERENCES timetable_period(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT uq_timetable_slot UNIQUE (schedule_id, day_of_week, period_id),
    CONSTRAINT uq_timetable_slot_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_timetable_slot_day CHECK (day_of_week BETWEEN 1 AND 7)
);

CREATE TABLE timetable_room_equipment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    room_id UUID NOT NULL,
    equipment_code VARCHAR(80) NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT fk_timetable_equipment_room_tenant FOREIGN KEY (room_id, tenant_id)
        REFERENCES physical_room(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT uq_timetable_room_equipment UNIQUE (tenant_id, room_id, equipment_code),
    CONSTRAINT ck_timetable_equipment_quantity CHECK (quantity > 0)
);

CREATE TABLE timetable_teacher_assignment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    schedule_id UUID NOT NULL,
    classroom_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    teacher_staff_id UUID NOT NULL,
    weekly_periods INTEGER NOT NULL,
    required_room_type VARCHAR(40),
    required_equipment VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_timetable_assignment_schedule_tenant FOREIGN KEY (schedule_id, tenant_id)
        REFERENCES timetable_schedule(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_timetable_assignment_class_tenant FOREIGN KEY (classroom_id, tenant_id)
        REFERENCES classroom(id, tenant_id),
    CONSTRAINT fk_timetable_assignment_subject_tenant FOREIGN KEY (subject_id, tenant_id)
        REFERENCES timetable_subject(id, tenant_id),
    CONSTRAINT fk_timetable_assignment_teacher_tenant FOREIGN KEY (teacher_staff_id, tenant_id)
        REFERENCES staff_profile(id, tenant_id),
    CONSTRAINT uq_timetable_assignment UNIQUE
        (schedule_id, classroom_id, subject_id, teacher_staff_id),
    CONSTRAINT uq_timetable_assignment_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_timetable_assignment_weekly CHECK (weekly_periods BETWEEN 1 AND 20)
);

CREATE TABLE timetable_lesson (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    schedule_id UUID NOT NULL,
    assignment_id UUID NOT NULL,
    classroom_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    teacher_staff_id UUID NOT NULL,
    room_id UUID NOT NULL,
    time_slot_id UUID NOT NULL,
    title VARCHAR(180),
    notes VARCHAR(500),
    source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_timetable_lesson_schedule_tenant FOREIGN KEY (schedule_id, tenant_id)
        REFERENCES timetable_schedule(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_timetable_lesson_assignment_tenant FOREIGN KEY (assignment_id, tenant_id)
        REFERENCES timetable_teacher_assignment(id, tenant_id),
    CONSTRAINT fk_timetable_lesson_class_tenant FOREIGN KEY (classroom_id, tenant_id)
        REFERENCES classroom(id, tenant_id),
    CONSTRAINT fk_timetable_lesson_subject_tenant FOREIGN KEY (subject_id, tenant_id)
        REFERENCES timetable_subject(id, tenant_id),
    CONSTRAINT fk_timetable_lesson_teacher_tenant FOREIGN KEY (teacher_staff_id, tenant_id)
        REFERENCES staff_profile(id, tenant_id),
    CONSTRAINT fk_timetable_lesson_room_tenant FOREIGN KEY (room_id, tenant_id)
        REFERENCES physical_room(id, tenant_id),
    CONSTRAINT fk_timetable_lesson_slot_tenant FOREIGN KEY (time_slot_id, tenant_id)
        REFERENCES timetable_time_slot(id, tenant_id),
    CONSTRAINT fk_timetable_lesson_creator_tenant FOREIGN KEY (created_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT uq_timetable_lesson_teacher_slot UNIQUE (schedule_id, teacher_staff_id, time_slot_id),
    CONSTRAINT uq_timetable_lesson_room_slot UNIQUE (schedule_id, room_id, time_slot_id),
    CONSTRAINT uq_timetable_lesson_class_slot UNIQUE (schedule_id, classroom_id, time_slot_id),
    CONSTRAINT uq_timetable_lesson_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_timetable_lesson_source CHECK (source IN ('MANUAL', 'OPTIMIZER'))
);

CREATE TABLE timetable_teacher_availability (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    schedule_id UUID NOT NULL,
    teacher_staff_id UUID NOT NULL,
    time_slot_id UUID NOT NULL,
    availability VARCHAR(20) NOT NULL,
    note VARCHAR(300),
    CONSTRAINT fk_timetable_teacher_availability_schedule_tenant
        FOREIGN KEY (schedule_id, tenant_id) REFERENCES timetable_schedule(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_timetable_teacher_availability_teacher_tenant
        FOREIGN KEY (teacher_staff_id, tenant_id) REFERENCES staff_profile(id, tenant_id),
    CONSTRAINT fk_timetable_teacher_availability_slot_tenant
        FOREIGN KEY (time_slot_id, tenant_id) REFERENCES timetable_time_slot(id, tenant_id),
    CONSTRAINT uq_timetable_teacher_availability UNIQUE
        (schedule_id, teacher_staff_id, time_slot_id),
    CONSTRAINT ck_timetable_teacher_availability_value
        CHECK (availability IN ('AVAILABLE', 'UNAVAILABLE', 'PREFERRED', 'AVOID'))
);

CREATE TABLE timetable_room_availability (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    schedule_id UUID NOT NULL,
    room_id UUID NOT NULL,
    time_slot_id UUID NOT NULL,
    availability VARCHAR(20) NOT NULL,
    note VARCHAR(300),
    CONSTRAINT fk_timetable_room_availability_schedule_tenant
        FOREIGN KEY (schedule_id, tenant_id) REFERENCES timetable_schedule(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_timetable_room_availability_room_tenant
        FOREIGN KEY (room_id, tenant_id) REFERENCES physical_room(id, tenant_id),
    CONSTRAINT fk_timetable_room_availability_slot_tenant
        FOREIGN KEY (time_slot_id, tenant_id) REFERENCES timetable_time_slot(id, tenant_id),
    CONSTRAINT uq_timetable_room_availability UNIQUE (schedule_id, room_id, time_slot_id),
    CONSTRAINT ck_timetable_room_availability_value
        CHECK (availability IN ('AVAILABLE', 'UNAVAILABLE', 'PREFERRED', 'AVOID'))
);

CREATE TABLE timetable_constraint (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    schedule_id UUID NOT NULL,
    constraint_type VARCHAR(60) NOT NULL,
    scope_type VARCHAR(30) NOT NULL,
    target_id UUID,
    configuration TEXT NOT NULL DEFAULT '{}',
    hard_constraint BOOLEAN NOT NULL DEFAULT TRUE,
    weight INTEGER NOT NULL DEFAULT 100,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_timetable_constraint_schedule_tenant FOREIGN KEY (schedule_id, tenant_id)
        REFERENCES timetable_schedule(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT uq_timetable_constraint UNIQUE
        (schedule_id, constraint_type, scope_type, target_id),
    CONSTRAINT ck_timetable_constraint_scope
        CHECK (scope_type IN ('SCHEDULE', 'TEACHER', 'CLASSROOM', 'SUBJECT', 'ROOM', 'ASSIGNMENT')),
    CONSTRAINT ck_timetable_constraint_weight CHECK (weight BETWEEN 1 AND 1000)
);

CREATE INDEX idx_timetable_schedule_scope
    ON timetable_schedule (tenant_id, academic_year_id, campus_id, status);
CREATE INDEX idx_timetable_period_schedule
    ON timetable_period (tenant_id, schedule_id, sequence_number);
CREATE INDEX idx_timetable_slot_schedule
    ON timetable_time_slot (tenant_id, schedule_id, day_of_week);
CREATE INDEX idx_timetable_assignment_schedule
    ON timetable_teacher_assignment (tenant_id, schedule_id, classroom_id);
CREATE INDEX idx_timetable_lesson_schedule
    ON timetable_lesson (tenant_id, schedule_id, time_slot_id);
CREATE INDEX idx_timetable_teacher_availability
    ON timetable_teacher_availability (tenant_id, schedule_id, teacher_staff_id);
CREATE INDEX idx_timetable_room_availability
    ON timetable_room_availability (tenant_id, schedule_id, room_id);
CREATE INDEX idx_timetable_constraint_schedule
    ON timetable_constraint (tenant_id, schedule_id, enabled);
