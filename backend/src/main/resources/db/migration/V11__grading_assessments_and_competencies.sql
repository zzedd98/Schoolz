ALTER TABLE academic_term
    ADD CONSTRAINT uq_academic_term_id_tenant UNIQUE (id, tenant_id);

CREATE TABLE grading_grade_scale (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    code VARCHAR(60) NOT NULL,
    name VARCHAR(160) NOT NULL,
    scale_type VARCHAR(30) NOT NULL,
    minimum_score DECIMAL(8,3),
    maximum_score DECIMAL(8,3),
    passing_score DECIMAL(8,3),
    rounding_scale INTEGER NOT NULL DEFAULT 2,
    competency_levels TEXT NOT NULL DEFAULT '[]',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_grading_scale_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_grading_scale_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_grading_scale_type CHECK (scale_type IN ('NUMERIC', 'COMPETENCY')),
    CONSTRAINT ck_grading_scale_rounding CHECK (rounding_scale BETWEEN 0 AND 4),
    CONSTRAINT ck_grading_scale_numeric_bounds CHECK (
        (scale_type = 'COMPETENCY' AND minimum_score IS NULL AND maximum_score IS NULL)
        OR
        (scale_type = 'NUMERIC' AND minimum_score IS NOT NULL AND maximum_score IS NOT NULL
            AND minimum_score < maximum_score
            AND (passing_score IS NULL OR passing_score BETWEEN minimum_score AND maximum_score))
    )
);

CREATE TABLE grading_policy (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    scope_type VARCHAR(30) NOT NULL,
    scope_key VARCHAR(80) NOT NULL,
    school_level_id UUID,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(180) NOT NULL,
    evaluation_mode VARCHAR(30) NOT NULL,
    grade_scale_id UUID,
    competency_scale_id UUID,
    calculation_strategy VARCHAR(40) NOT NULL,
    calculation_config TEXT NOT NULL DEFAULT '{}',
    rankings_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    report_cards_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_grading_policy_level_tenant FOREIGN KEY (school_level_id, tenant_id)
        REFERENCES school_level(id, tenant_id),
    CONSTRAINT fk_grading_policy_scale_tenant FOREIGN KEY (grade_scale_id, tenant_id)
        REFERENCES grading_grade_scale(id, tenant_id),
    CONSTRAINT fk_grading_policy_competency_scale_tenant
        FOREIGN KEY (competency_scale_id, tenant_id)
        REFERENCES grading_grade_scale(id, tenant_id),
    CONSTRAINT uq_grading_policy_scope UNIQUE (tenant_id, scope_type, scope_key),
    CONSTRAINT uq_grading_policy_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_grading_policy_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_grading_policy_scope CHECK (scope_type IN ('SCHOOL_LEVEL', 'CHILDCARE')),
    CONSTRAINT ck_grading_policy_mode CHECK (
        evaluation_mode IN ('NONE', 'COMPETENCY_ONLY', 'HYBRID', 'NUMERIC')
    ),
    CONSTRAINT ck_grading_policy_strategy CHECK (
        calculation_strategy IN ('WEIGHTED_MEAN', 'CATEGORY_WEIGHTED')
    ),
    CONSTRAINT ck_grading_policy_scope_level CHECK (
        (scope_type = 'SCHOOL_LEVEL' AND school_level_id IS NOT NULL)
        OR (scope_type = 'CHILDCARE' AND school_level_id IS NULL)
    ),
    CONSTRAINT ck_grading_policy_scale_mode CHECK (
        (evaluation_mode IN ('NUMERIC', 'HYBRID') AND grade_scale_id IS NOT NULL)
        OR evaluation_mode IN ('NONE', 'COMPETENCY_ONLY')
    ),
    CONSTRAINT ck_grading_policy_competency_scale_mode CHECK (
        (evaluation_mode IN ('COMPETENCY_ONLY', 'HYBRID') AND competency_scale_id IS NOT NULL)
        OR evaluation_mode IN ('NONE', 'NUMERIC')
    )
);

CREATE TABLE grading_assessment_type (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    code VARCHAR(60) NOT NULL,
    name_fr VARCHAR(160) NOT NULL,
    name_ar VARCHAR(160),
    calculation_category VARCHAR(60) NOT NULL,
    numeric_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    competency_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_grading_assessment_type_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_grading_assessment_type_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_grading_assessment_type_capability CHECK (numeric_enabled OR competency_enabled)
);

CREATE TABLE grading_coefficient (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    academic_year_id UUID NOT NULL,
    grade_level_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    coefficient DECIMAL(8,3) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_grading_coefficient_year_tenant FOREIGN KEY (academic_year_id, tenant_id)
        REFERENCES academic_year(id, tenant_id),
    CONSTRAINT fk_grading_coefficient_level_tenant FOREIGN KEY (grade_level_id, tenant_id)
        REFERENCES grade_level(id, tenant_id),
    CONSTRAINT fk_grading_coefficient_subject_tenant FOREIGN KEY (subject_id, tenant_id)
        REFERENCES timetable_subject(id, tenant_id),
    CONSTRAINT uq_grading_coefficient UNIQUE
        (tenant_id, academic_year_id, grade_level_id, subject_id),
    CONSTRAINT uq_grading_coefficient_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_grading_coefficient_positive CHECK (coefficient > 0)
);

CREATE TABLE grading_assessment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    academic_year_id UUID NOT NULL,
    academic_term_id UUID NOT NULL,
    classroom_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    assessment_type_id UUID NOT NULL,
    teacher_staff_id UUID NOT NULL,
    title VARCHAR(180) NOT NULL,
    description VARCHAR(1000),
    assessment_date DATE NOT NULL,
    maximum_score DECIMAL(8,3),
    coefficient DECIMAL(8,3) NOT NULL DEFAULT 1,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_grading_assessment_year_tenant FOREIGN KEY (academic_year_id, tenant_id)
        REFERENCES academic_year(id, tenant_id),
    CONSTRAINT fk_grading_assessment_term_tenant FOREIGN KEY (academic_term_id, tenant_id)
        REFERENCES academic_term(id, tenant_id),
    CONSTRAINT fk_grading_assessment_class_tenant FOREIGN KEY (classroom_id, tenant_id)
        REFERENCES classroom(id, tenant_id),
    CONSTRAINT fk_grading_assessment_subject_tenant FOREIGN KEY (subject_id, tenant_id)
        REFERENCES timetable_subject(id, tenant_id),
    CONSTRAINT fk_grading_assessment_type_tenant FOREIGN KEY (assessment_type_id, tenant_id)
        REFERENCES grading_assessment_type(id, tenant_id),
    CONSTRAINT fk_grading_assessment_teacher_tenant FOREIGN KEY (teacher_staff_id, tenant_id)
        REFERENCES staff_profile(id, tenant_id),
    CONSTRAINT fk_grading_assessment_creator_tenant FOREIGN KEY (created_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT uq_grading_assessment_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_grading_assessment_score CHECK (maximum_score IS NULL OR maximum_score > 0),
    CONSTRAINT ck_grading_assessment_coefficient CHECK (coefficient > 0),
    CONSTRAINT ck_grading_assessment_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED'))
);

CREATE TABLE grading_grade (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    assessment_id UUID NOT NULL,
    student_id UUID NOT NULL,
    raw_score DECIMAL(8,3),
    grade_status VARCHAR(30) NOT NULL,
    teacher_comment VARCHAR(600),
    version BIGINT NOT NULL DEFAULT 0,
    created_by_user_id UUID NOT NULL,
    updated_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_grading_grade_assessment_tenant FOREIGN KEY (assessment_id, tenant_id)
        REFERENCES grading_assessment(id, tenant_id),
    CONSTRAINT fk_grading_grade_student_tenant FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT fk_grading_grade_creator_tenant FOREIGN KEY (created_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT fk_grading_grade_updater_tenant FOREIGN KEY (updated_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT uq_grading_grade_assessment_student UNIQUE (assessment_id, student_id),
    CONSTRAINT uq_grading_grade_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_grading_grade_status CHECK (
        grade_status IN ('GRADED', 'ABSENT', 'EXEMPT', 'NOT_GRADED')
    ),
    CONSTRAINT ck_grading_grade_value CHECK (
        (grade_status = 'GRADED' AND raw_score IS NOT NULL)
        OR (grade_status <> 'GRADED' AND raw_score IS NULL)
    )
);

CREATE TABLE grading_grade_history (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    grade_id UUID NOT NULL,
    assessment_id UUID NOT NULL,
    student_id UUID NOT NULL,
    change_type VARCHAR(30) NOT NULL,
    previous_score DECIMAL(8,3),
    new_score DECIMAL(8,3),
    previous_status VARCHAR(30),
    new_status VARCHAR(30) NOT NULL,
    reason VARCHAR(500),
    actor_user_id UUID NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    before_snapshot TEXT,
    after_snapshot TEXT NOT NULL,
    CONSTRAINT fk_grading_history_grade_tenant FOREIGN KEY (grade_id, tenant_id)
        REFERENCES grading_grade(id, tenant_id),
    CONSTRAINT fk_grading_history_assessment_tenant FOREIGN KEY (assessment_id, tenant_id)
        REFERENCES grading_assessment(id, tenant_id),
    CONSTRAINT fk_grading_history_student_tenant FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT fk_grading_history_actor_tenant FOREIGN KEY (actor_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT ck_grading_history_type CHECK (
        change_type IN ('CREATED', 'UPDATED', 'STATUS_CHANGED')
    ),
    CONSTRAINT ck_grading_history_reason CHECK (
        change_type = 'CREATED' OR (reason IS NOT NULL AND LENGTH(TRIM(reason)) >= 3)
    )
);

CREATE TABLE grading_competency (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    school_level_id UUID NOT NULL,
    subject_id UUID,
    parent_competency_id UUID,
    code VARCHAR(80) NOT NULL,
    name_fr VARCHAR(200) NOT NULL,
    name_ar VARCHAR(200),
    description VARCHAR(1000),
    sequence_number INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_grading_competency_level_tenant FOREIGN KEY (school_level_id, tenant_id)
        REFERENCES school_level(id, tenant_id),
    CONSTRAINT fk_grading_competency_subject_tenant FOREIGN KEY (subject_id, tenant_id)
        REFERENCES timetable_subject(id, tenant_id),
    CONSTRAINT uq_grading_competency_code UNIQUE (tenant_id, school_level_id, code),
    CONSTRAINT uq_grading_competency_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_grading_competency_sequence CHECK (sequence_number > 0)
);

ALTER TABLE grading_competency
    ADD CONSTRAINT fk_grading_competency_parent_tenant
    FOREIGN KEY (parent_competency_id, tenant_id)
    REFERENCES grading_competency(id, tenant_id);

CREATE TABLE grading_competency_evaluation (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    competency_id UUID NOT NULL,
    student_id UUID NOT NULL,
    academic_term_id UUID NOT NULL,
    assessment_id UUID,
    teacher_staff_id UUID NOT NULL,
    level_code VARCHAR(60) NOT NULL,
    observation VARCHAR(1000),
    evaluated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_grading_comp_eval_competency_tenant FOREIGN KEY (competency_id, tenant_id)
        REFERENCES grading_competency(id, tenant_id),
    CONSTRAINT fk_grading_comp_eval_student_tenant FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT fk_grading_comp_eval_term_tenant FOREIGN KEY (academic_term_id, tenant_id)
        REFERENCES academic_term(id, tenant_id),
    CONSTRAINT fk_grading_comp_eval_assessment_tenant FOREIGN KEY (assessment_id, tenant_id)
        REFERENCES grading_assessment(id, tenant_id),
    CONSTRAINT fk_grading_comp_eval_teacher_tenant FOREIGN KEY (teacher_staff_id, tenant_id)
        REFERENCES staff_profile(id, tenant_id),
    CONSTRAINT fk_grading_comp_eval_creator_tenant FOREIGN KEY (created_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT uq_grading_comp_eval_id_tenant UNIQUE (id, tenant_id)
);

CREATE TABLE grading_teacher_comment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    student_id UUID NOT NULL,
    academic_term_id UUID NOT NULL,
    subject_id UUID,
    teacher_staff_id UUID NOT NULL,
    comment_type VARCHAR(30) NOT NULL,
    body VARCHAR(1600) NOT NULL,
    visible_to_family BOOLEAN NOT NULL DEFAULT FALSE,
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_grading_comment_student_tenant FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT fk_grading_comment_term_tenant FOREIGN KEY (academic_term_id, tenant_id)
        REFERENCES academic_term(id, tenant_id),
    CONSTRAINT fk_grading_comment_subject_tenant FOREIGN KEY (subject_id, tenant_id)
        REFERENCES timetable_subject(id, tenant_id),
    CONSTRAINT fk_grading_comment_teacher_tenant FOREIGN KEY (teacher_staff_id, tenant_id)
        REFERENCES staff_profile(id, tenant_id),
    CONSTRAINT fk_grading_comment_creator_tenant FOREIGN KEY (created_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT uq_grading_comment_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_grading_comment_type CHECK (
        comment_type IN ('GENERAL', 'SUBJECT', 'COMPETENCY', 'BEHAVIOR')
    )
);

CREATE TABLE grading_report_card_template (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    school_level_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(180) NOT NULL,
    language_mode VARCHAR(20) NOT NULL,
    layout_config TEXT NOT NULL DEFAULT '{}',
    version_number INTEGER NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_grading_template_level_tenant FOREIGN KEY (school_level_id, tenant_id)
        REFERENCES school_level(id, tenant_id),
    CONSTRAINT uq_grading_template_code_version UNIQUE (tenant_id, code, version_number),
    CONSTRAINT uq_grading_template_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_grading_template_language CHECK (
        language_mode IN ('FR', 'AR', 'BILINGUAL')
    ),
    CONSTRAINT ck_grading_template_version CHECK (version_number > 0)
);

CREATE TABLE grading_report_card (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    student_id UUID NOT NULL,
    academic_year_id UUID NOT NULL,
    academic_term_id UUID NOT NULL,
    classroom_id UUID NOT NULL,
    policy_id UUID NOT NULL,
    template_id UUID,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    overall_average DECIMAL(8,3),
    class_rank INTEGER,
    class_size INTEGER,
    calculation_snapshot TEXT NOT NULL,
    version_number INTEGER NOT NULL DEFAULT 1,
    calculated_by_user_id UUID NOT NULL,
    calculated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_grading_report_student_tenant FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT fk_grading_report_year_tenant FOREIGN KEY (academic_year_id, tenant_id)
        REFERENCES academic_year(id, tenant_id),
    CONSTRAINT fk_grading_report_term_tenant FOREIGN KEY (academic_term_id, tenant_id)
        REFERENCES academic_term(id, tenant_id),
    CONSTRAINT fk_grading_report_class_tenant FOREIGN KEY (classroom_id, tenant_id)
        REFERENCES classroom(id, tenant_id),
    CONSTRAINT fk_grading_report_policy_tenant FOREIGN KEY (policy_id, tenant_id)
        REFERENCES grading_policy(id, tenant_id),
    CONSTRAINT fk_grading_report_template_tenant FOREIGN KEY (template_id, tenant_id)
        REFERENCES grading_report_card_template(id, tenant_id),
    CONSTRAINT fk_grading_report_calculator_tenant FOREIGN KEY (calculated_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT uq_grading_report_student_term_version UNIQUE
        (tenant_id, student_id, academic_term_id, version_number),
    CONSTRAINT uq_grading_report_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_grading_report_status CHECK (status IN ('DRAFT', 'CALCULATED', 'PUBLISHED')),
    CONSTRAINT ck_grading_report_rank CHECK (
        (class_rank IS NULL AND class_size IS NULL)
        OR (class_rank > 0 AND class_size > 0 AND class_rank <= class_size)
    ),
    CONSTRAINT ck_grading_report_version CHECK (version_number > 0)
);

CREATE TABLE grading_report_card_subject_result (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    report_card_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    average DECIMAL(8,3),
    coefficient DECIMAL(8,3) NOT NULL,
    category_breakdown TEXT NOT NULL DEFAULT '{}',
    teacher_comment VARCHAR(1600),
    CONSTRAINT fk_grading_report_result_card_tenant FOREIGN KEY (report_card_id, tenant_id)
        REFERENCES grading_report_card(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_grading_report_result_subject_tenant FOREIGN KEY (subject_id, tenant_id)
        REFERENCES timetable_subject(id, tenant_id),
    CONSTRAINT uq_grading_report_result UNIQUE (report_card_id, subject_id),
    CONSTRAINT ck_grading_report_result_coefficient CHECK (coefficient > 0)
);

CREATE INDEX idx_grading_policy_scope
    ON grading_policy (tenant_id, scope_type, scope_key, active);
CREATE INDEX idx_grading_coefficient_scope
    ON grading_coefficient (tenant_id, academic_year_id, grade_level_id);
CREATE INDEX idx_grading_assessment_board
    ON grading_assessment (tenant_id, classroom_id, academic_term_id, assessment_date);
CREATE INDEX idx_grading_grade_assessment
    ON grading_grade (tenant_id, assessment_id, student_id);
CREATE INDEX idx_grading_grade_student
    ON grading_grade (tenant_id, student_id, updated_at);
CREATE INDEX idx_grading_history_grade
    ON grading_grade_history (tenant_id, grade_id, changed_at);
CREATE INDEX idx_grading_competency_scope
    ON grading_competency (tenant_id, school_level_id, subject_id, sequence_number);
CREATE INDEX idx_grading_comp_eval_student
    ON grading_competency_evaluation (tenant_id, student_id, academic_term_id, evaluated_at);
CREATE INDEX idx_grading_comment_student
    ON grading_teacher_comment (tenant_id, student_id, academic_term_id);
CREATE INDEX idx_grading_report_class
    ON grading_report_card (tenant_id, classroom_id, academic_term_id, status);
