CREATE TABLE early_warning_rule (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id) ON DELETE CASCADE,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(180) NOT NULL,
    description VARCHAR(500) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    match_mode VARCHAR(10) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_user_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_early_warning_rule_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_early_warning_rule_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_early_warning_rule_severity CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_early_warning_rule_match CHECK (match_mode IN ('ALL','ANY')),
    CONSTRAINT fk_early_warning_rule_actor FOREIGN KEY (created_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id)
);

CREATE TABLE early_warning_rule_condition (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    rule_id UUID NOT NULL,
    signal_type VARCHAR(50) NOT NULL,
    comparison_operator VARCHAR(10) NOT NULL,
    threshold_value NUMERIC(18,4) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_early_warning_condition_rule FOREIGN KEY (rule_id, tenant_id)
        REFERENCES early_warning_rule(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT uq_early_warning_condition_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uq_early_warning_condition UNIQUE (tenant_id, rule_id, signal_type),
    CONSTRAINT ck_early_warning_condition_signal CHECK (
        signal_type IN ('ABSENCE_RATE','LATE_COUNT','AVERAGE_GRADE','GRADE_DROP',
                        'INCIDENT_COUNT','UNPAID_AMOUNT','MISSING_DOCUMENT_COUNT')
    ),
    CONSTRAINT ck_early_warning_condition_operator CHECK (
        comparison_operator IN ('GT','GTE','LT','LTE','EQ')
    ),
    CONSTRAINT ck_early_warning_condition_threshold CHECK (threshold_value >= 0)
);

CREATE TABLE early_warning_student_signal (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    student_id UUID NOT NULL,
    measured_on DATE NOT NULL,
    signal_type VARCHAR(50) NOT NULL,
    numeric_value NUMERIC(18,4) NOT NULL,
    unit_code VARCHAR(30) NOT NULL,
    evidence_count INTEGER NOT NULL DEFAULT 0,
    source_module VARCHAR(50) NOT NULL,
    factual_detail VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_early_warning_signal_student FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT uq_early_warning_signal UNIQUE (tenant_id, student_id, measured_on, signal_type),
    CONSTRAINT ck_early_warning_signal_type CHECK (
        signal_type IN ('ABSENCE_RATE','LATE_COUNT','AVERAGE_GRADE','GRADE_DROP',
                        'INCIDENT_COUNT','UNPAID_AMOUNT','MISSING_DOCUMENT_COUNT')
    ),
    CONSTRAINT ck_early_warning_signal_value CHECK (numeric_value >= 0 AND evidence_count >= 0)
);

CREATE TABLE early_warning_alert (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    rule_id UUID NOT NULL,
    student_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    score NUMERIC(5,2) NOT NULL,
    factual_summary VARCHAR(800) NOT NULL,
    first_detected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_detected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    acknowledged_by_user_id UUID,
    acknowledgement_note VARCHAR(500),
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolved_by_user_id UUID,
    resolution_note VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_early_warning_alert_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_early_warning_alert_rule FOREIGN KEY (rule_id, tenant_id)
        REFERENCES early_warning_rule(id, tenant_id),
    CONSTRAINT fk_early_warning_alert_student FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT fk_early_warning_alert_ack_actor FOREIGN KEY (acknowledged_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT fk_early_warning_alert_resolve_actor FOREIGN KEY (resolved_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT ck_early_warning_alert_status CHECK (status IN ('OPEN','ACKNOWLEDGED','RESOLVED','DISMISSED')),
    CONSTRAINT ck_early_warning_alert_severity CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_early_warning_alert_score CHECK (score >= 0 AND score <= 100)
);

CREATE TABLE early_warning_alert_evidence (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    alert_id UUID NOT NULL,
    condition_id UUID NOT NULL,
    signal_type VARCHAR(50) NOT NULL,
    actual_value NUMERIC(18,4) NOT NULL,
    threshold_value NUMERIC(18,4) NOT NULL,
    comparison_operator VARCHAR(10) NOT NULL,
    matched BOOLEAN NOT NULL,
    factual_detail VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_early_warning_evidence_alert FOREIGN KEY (alert_id, tenant_id)
        REFERENCES early_warning_alert(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_early_warning_evidence_condition FOREIGN KEY (condition_id, tenant_id)
        REFERENCES early_warning_rule_condition(id, tenant_id)
);

CREATE TABLE early_warning_evaluation (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id) ON DELETE CASCADE,
    evaluated_on DATE NOT NULL,
    rules_evaluated INTEGER NOT NULL,
    students_evaluated INTEGER NOT NULL,
    alerts_created INTEGER NOT NULL,
    alerts_refreshed INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    initiated_by_user_id UUID,
    CONSTRAINT fk_early_warning_evaluation_actor FOREIGN KEY (initiated_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id),
    CONSTRAINT ck_early_warning_evaluation_counts CHECK (
        rules_evaluated >= 0 AND students_evaluated >= 0
        AND alerts_created >= 0 AND alerts_refreshed >= 0
    ),
    CONSTRAINT ck_early_warning_evaluation_status CHECK (status IN ('COMPLETED','FAILED'))
);

CREATE INDEX idx_early_warning_signal_latest
    ON early_warning_student_signal(tenant_id, student_id, measured_on DESC, signal_type);
CREATE INDEX idx_early_warning_alert_queue
    ON early_warning_alert(tenant_id, status, severity, last_detected_at DESC);
CREATE INDEX idx_early_warning_evaluation_history
    ON early_warning_evaluation(tenant_id, evaluated_on DESC, completed_at DESC);
