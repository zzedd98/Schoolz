CREATE TABLE payroll_period (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    code VARCHAR(60) NOT NULL,
    label VARCHAR(160) NOT NULL,
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL,
    payment_date DATE NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_payroll_period_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_payroll_period_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_payroll_period_dates CHECK (ends_on >= starts_on),
    CONSTRAINT ck_payroll_period_status CHECK (
        status IN ('DRAFT','OPEN','CALCULATED','FINALIZED','EXPORTED')
    )
);

CREATE TABLE payroll_salary_component (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    code VARCHAR(60) NOT NULL,
    name VARCHAR(180) NOT NULL,
    component_type VARCHAR(30) NOT NULL,
    calculation_basis VARCHAR(30) NOT NULL,
    description VARCHAR(700),
    expense_account_code VARCHAR(40),
    liability_account_code VARCHAR(40),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_payroll_component_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_payroll_component_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_payroll_component_type CHECK (
        component_type IN ('BASE','BONUS','DEDUCTION','OVERTIME','ABSENCE','ALLOWANCE')
    ),
    CONSTRAINT ck_payroll_component_basis CHECK (
        calculation_basis IN ('PROFILE_AMOUNT','FIXED','PERCENT_OF_BASE','PER_UNIT')
    )
);

CREATE TABLE payroll_salary_rule (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    component_id UUID NOT NULL,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(180) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 100,
    fixed_amount NUMERIC(19,2),
    rate_percent NUMERIC(12,6),
    unit_rate NUMERIC(19,4),
    condition_expression VARCHAR(700),
    source_reference VARCHAR(700),
    legally_validated BOOLEAN NOT NULL DEFAULT FALSE,
    effective_from DATE NOT NULL,
    effective_to DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_payroll_rule_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_payroll_rule_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_payroll_rule_component FOREIGN KEY (component_id, tenant_id)
        REFERENCES payroll_salary_component(id, tenant_id),
    CONSTRAINT ck_payroll_rule_dates CHECK (
        effective_to IS NULL OR effective_to >= effective_from
    ),
    CONSTRAINT ck_payroll_rule_rate CHECK (
        rate_percent IS NULL OR (rate_percent >= 0 AND rate_percent <= 1000)
    )
);

CREATE TABLE payroll_employee_salary_profile (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    employee_id UUID NOT NULL,
    code VARCHAR(80) NOT NULL,
    currency CHAR(3) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    payment_method VARCHAR(40),
    notes VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_payroll_profile_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_payroll_profile_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_payroll_profile_employee FOREIGN KEY (employee_id, tenant_id)
        REFERENCES hr_employee(id, tenant_id),
    CONSTRAINT ck_payroll_profile_dates CHECK (
        effective_to IS NULL OR effective_to >= effective_from
    )
);

CREATE TABLE payroll_profile_component (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    profile_id UUID NOT NULL,
    component_id UUID NOT NULL,
    rule_id UUID,
    configured_amount NUMERIC(19,2),
    configured_rate NUMERIC(12,6),
    configured_unit_rate NUMERIC(19,4),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_payroll_profile_component UNIQUE (tenant_id, profile_id, component_id),
    CONSTRAINT uq_payroll_profile_component_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_payroll_profile_component_profile FOREIGN KEY (profile_id, tenant_id)
        REFERENCES payroll_employee_salary_profile(id, tenant_id),
    CONSTRAINT fk_payroll_profile_component_component FOREIGN KEY (component_id, tenant_id)
        REFERENCES payroll_salary_component(id, tenant_id),
    CONSTRAINT fk_payroll_profile_component_rule FOREIGN KEY (rule_id, tenant_id)
        REFERENCES payroll_salary_rule(id, tenant_id)
);

CREATE TABLE payroll_run (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    period_id UUID NOT NULL,
    run_number VARCHAR(80) NOT NULL,
    status VARCHAR(30) NOT NULL,
    employee_count INTEGER NOT NULL DEFAULT 0,
    total_gross NUMERIC(19,2) NOT NULL DEFAULT 0,
    total_deductions NUMERIC(19,2) NOT NULL DEFAULT 0,
    total_net NUMERIC(19,2) NOT NULL DEFAULT 0,
    started_by_user_id UUID,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    calculated_at TIMESTAMP WITH TIME ZONE,
    finalized_by_user_id UUID,
    finalized_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_payroll_run_number UNIQUE (tenant_id, run_number),
    CONSTRAINT uq_payroll_run_period UNIQUE (tenant_id, period_id),
    CONSTRAINT uq_payroll_run_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_payroll_run_period FOREIGN KEY (period_id, tenant_id)
        REFERENCES payroll_period(id, tenant_id),
    CONSTRAINT ck_payroll_run_status CHECK (
        status IN ('DRAFT','CALCULATING','CALCULATED','FINALIZED','EXPORTED')
    )
);

CREATE TABLE payroll_payslip (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    payroll_run_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    salary_profile_id UUID NOT NULL,
    payslip_number VARCHAR(90) NOT NULL,
    currency CHAR(3) NOT NULL,
    gross_amount NUMERIC(19,2) NOT NULL,
    deduction_amount NUMERIC(19,2) NOT NULL,
    net_amount NUMERIC(19,2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    calculated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finalized_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_payroll_payslip_number UNIQUE (tenant_id, payslip_number),
    CONSTRAINT uq_payroll_payslip_employee UNIQUE (tenant_id, payroll_run_id, employee_id),
    CONSTRAINT uq_payroll_payslip_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_payroll_payslip_run FOREIGN KEY (payroll_run_id, tenant_id)
        REFERENCES payroll_run(id, tenant_id),
    CONSTRAINT fk_payroll_payslip_employee FOREIGN KEY (employee_id, tenant_id)
        REFERENCES hr_employee(id, tenant_id),
    CONSTRAINT fk_payroll_payslip_profile FOREIGN KEY (salary_profile_id, tenant_id)
        REFERENCES payroll_employee_salary_profile(id, tenant_id),
    CONSTRAINT ck_payroll_payslip_status CHECK (status IN ('DRAFT','CALCULATED','FINALIZED'))
);

CREATE TABLE payroll_payslip_line (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    payslip_id UUID NOT NULL,
    component_id UUID NOT NULL,
    rule_id UUID,
    component_code VARCHAR(60) NOT NULL,
    component_name VARCHAR(180) NOT NULL,
    component_type VARCHAR(30) NOT NULL,
    quantity NUMERIC(12,4),
    rate NUMERIC(19,6),
    amount NUMERIC(19,2) NOT NULL,
    source VARCHAR(40) NOT NULL,
    description VARCHAR(700),
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_payroll_payslip_line_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_payroll_line_payslip FOREIGN KEY (payslip_id, tenant_id)
        REFERENCES payroll_payslip(id, tenant_id),
    CONSTRAINT fk_payroll_line_component FOREIGN KEY (component_id, tenant_id)
        REFERENCES payroll_salary_component(id, tenant_id),
    CONSTRAINT fk_payroll_line_rule FOREIGN KEY (rule_id, tenant_id)
        REFERENCES payroll_salary_rule(id, tenant_id)
);

CREATE TABLE payroll_salary_adjustment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    employee_id UUID NOT NULL,
    period_id UUID NOT NULL,
    component_id UUID NOT NULL,
    quantity NUMERIC(12,4),
    unit_rate NUMERIC(19,4),
    amount NUMERIC(19,2),
    reason VARCHAR(700) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_by_user_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    applied_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_payroll_adjustment_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_payroll_adjustment_employee FOREIGN KEY (employee_id, tenant_id)
        REFERENCES hr_employee(id, tenant_id),
    CONSTRAINT fk_payroll_adjustment_period FOREIGN KEY (period_id, tenant_id)
        REFERENCES payroll_period(id, tenant_id),
    CONSTRAINT fk_payroll_adjustment_component FOREIGN KEY (component_id, tenant_id)
        REFERENCES payroll_salary_component(id, tenant_id),
    CONSTRAINT ck_payroll_adjustment_status CHECK (status IN ('PENDING','APPLIED','CANCELLED')),
    CONSTRAINT ck_payroll_adjustment_value CHECK (amount IS NOT NULL OR quantity IS NOT NULL)
);

CREATE TABLE payroll_accounting_export (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    payroll_run_id UUID NOT NULL,
    export_reference VARCHAR(90) NOT NULL,
    format VARCHAR(20) NOT NULL,
    content_text TEXT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    created_by_user_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_payroll_export_reference UNIQUE (tenant_id, export_reference),
    CONSTRAINT uq_payroll_export_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_payroll_export_run FOREIGN KEY (payroll_run_id, tenant_id)
        REFERENCES payroll_run(id, tenant_id),
    CONSTRAINT ck_payroll_export_format CHECK (format IN ('CSV','JSON'))
);

CREATE INDEX idx_payroll_period_status ON payroll_period (tenant_id, status, starts_on DESC);
CREATE INDEX idx_payroll_rule_component ON payroll_salary_rule (tenant_id, component_id, active);
CREATE INDEX idx_payroll_profile_employee ON payroll_employee_salary_profile (tenant_id, employee_id, active);
CREATE INDEX idx_payroll_run_status ON payroll_run (tenant_id, status, started_at DESC);
CREATE INDEX idx_payroll_payslip_run ON payroll_payslip (tenant_id, payroll_run_id, employee_id);
CREATE INDEX idx_payroll_line_payslip ON payroll_payslip_line (tenant_id, payslip_id, display_order);
CREATE INDEX idx_payroll_adjustment_period ON payroll_salary_adjustment (tenant_id, period_id, status);
