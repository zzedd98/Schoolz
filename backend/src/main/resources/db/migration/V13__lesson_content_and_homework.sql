CREATE TABLE lesson_content (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    teacher_assignment_id UUID NOT NULL,
    classroom_id UUID NOT NULL,
    academic_term_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    teacher_staff_id UUID NOT NULL,
    lesson_date DATE NOT NULL,
    title VARCHAR(180) NOT NULL,
    summary VARCHAR(1000),
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_at TIMESTAMP WITH TIME ZONE,
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_lesson_content_assignment_tenant
        FOREIGN KEY (teacher_assignment_id, tenant_id)
        REFERENCES timetable_teacher_assignment(id, tenant_id),
    CONSTRAINT fk_lesson_content_class_tenant FOREIGN KEY (classroom_id, tenant_id)
        REFERENCES classroom(id, tenant_id),
    CONSTRAINT fk_lesson_content_term_tenant FOREIGN KEY (academic_term_id, tenant_id)
        REFERENCES academic_term(id, tenant_id),
    CONSTRAINT fk_lesson_content_subject_tenant FOREIGN KEY (subject_id, tenant_id)
        REFERENCES timetable_subject(id, tenant_id),
    CONSTRAINT fk_lesson_content_teacher_tenant FOREIGN KEY (teacher_staff_id, tenant_id)
        REFERENCES staff_profile(id, tenant_id),
    CONSTRAINT fk_lesson_content_creator_tenant FOREIGN KEY (created_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT uq_lesson_content_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_lesson_content_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_lesson_content_publication CHECK (
        (status = 'DRAFT' AND published_at IS NULL)
        OR (status = 'PUBLISHED' AND published_at IS NOT NULL)
    )
);

CREATE TABLE homework_assignment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    teacher_assignment_id UUID NOT NULL,
    lesson_content_id UUID,
    classroom_id UUID NOT NULL,
    academic_term_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    teacher_staff_id UUID NOT NULL,
    title VARCHAR(180) NOT NULL,
    instructions TEXT NOT NULL,
    assigned_on DATE NOT NULL,
    due_at TIMESTAMP WITH TIME ZONE NOT NULL,
    estimated_minutes INTEGER,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_at TIMESTAMP WITH TIME ZONE,
    closed_at TIMESTAMP WITH TIME ZONE,
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_homework_assignment_tenant
        FOREIGN KEY (teacher_assignment_id, tenant_id)
        REFERENCES timetable_teacher_assignment(id, tenant_id),
    CONSTRAINT fk_homework_lesson_content_tenant FOREIGN KEY (lesson_content_id, tenant_id)
        REFERENCES lesson_content(id, tenant_id),
    CONSTRAINT fk_homework_class_tenant FOREIGN KEY (classroom_id, tenant_id)
        REFERENCES classroom(id, tenant_id),
    CONSTRAINT fk_homework_term_tenant FOREIGN KEY (academic_term_id, tenant_id)
        REFERENCES academic_term(id, tenant_id),
    CONSTRAINT fk_homework_subject_tenant FOREIGN KEY (subject_id, tenant_id)
        REFERENCES timetable_subject(id, tenant_id),
    CONSTRAINT fk_homework_teacher_tenant FOREIGN KEY (teacher_staff_id, tenant_id)
        REFERENCES staff_profile(id, tenant_id),
    CONSTRAINT fk_homework_creator_tenant FOREIGN KEY (created_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT uq_homework_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_homework_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED')),
    CONSTRAINT ck_homework_estimated_minutes CHECK (
        estimated_minutes IS NULL OR estimated_minutes BETWEEN 1 AND 600
    ),
    CONSTRAINT ck_homework_publication CHECK (
        (status = 'DRAFT' AND published_at IS NULL AND closed_at IS NULL)
        OR (status = 'PUBLISHED' AND published_at IS NOT NULL AND closed_at IS NULL)
        OR (status = 'CLOSED' AND published_at IS NOT NULL AND closed_at IS NOT NULL)
    )
);

CREATE TABLE homework_attachment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    lesson_content_id UUID,
    homework_id UUID,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    binary_data BYTEA NOT NULL,
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_homework_attachment_lesson_tenant FOREIGN KEY (lesson_content_id, tenant_id)
        REFERENCES lesson_content(id, tenant_id),
    CONSTRAINT fk_homework_attachment_homework_tenant FOREIGN KEY (homework_id, tenant_id)
        REFERENCES homework_assignment(id, tenant_id),
    CONSTRAINT fk_homework_attachment_creator_tenant
        FOREIGN KEY (created_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT uq_homework_attachment_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_homework_attachment_owner CHECK (
        (lesson_content_id IS NOT NULL AND homework_id IS NULL)
        OR (lesson_content_id IS NULL AND homework_id IS NOT NULL)
    ),
    CONSTRAINT ck_homework_attachment_size CHECK (size_bytes BETWEEN 1 AND 10485760)
);

CREATE TABLE homework_student_status (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    homework_id UUID NOT NULL,
    student_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'TO_DO',
    completed_at TIMESTAMP WITH TIME ZONE,
    marked_by_portal_account_id UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_homework_status_homework_tenant FOREIGN KEY (homework_id, tenant_id)
        REFERENCES homework_assignment(id, tenant_id),
    CONSTRAINT fk_homework_status_student_tenant FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT fk_homework_status_portal_tenant
        FOREIGN KEY (marked_by_portal_account_id, tenant_id)
        REFERENCES admission_portal_account(id, tenant_id),
    CONSTRAINT uq_homework_student_status UNIQUE (tenant_id, homework_id, student_id),
    CONSTRAINT ck_homework_student_status CHECK (status IN ('TO_DO', 'DONE')),
    CONSTRAINT ck_homework_student_completion CHECK (
        (status = 'TO_DO' AND completed_at IS NULL)
        OR (status = 'DONE' AND completed_at IS NOT NULL)
    )
);

CREATE INDEX idx_lesson_content_calendar
    ON lesson_content (tenant_id, classroom_id, academic_term_id, lesson_date, status);
CREATE INDEX idx_homework_calendar
    ON homework_assignment (tenant_id, classroom_id, academic_term_id, due_at, status);
CREATE INDEX idx_homework_attachment_lesson
    ON homework_attachment (tenant_id, lesson_content_id);
CREATE INDEX idx_homework_attachment_assignment
    ON homework_attachment (tenant_id, homework_id);
CREATE INDEX idx_homework_student_status_student
    ON homework_student_status (tenant_id, student_id, status, homework_id);
