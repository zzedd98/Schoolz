CREATE TABLE billing_document_sequence (
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    document_type VARCHAR(30) NOT NULL,
    calendar_year INTEGER NOT NULL,
    next_value BIGINT NOT NULL DEFAULT 1,
    PRIMARY KEY (tenant_id, document_type, calendar_year),
    CONSTRAINT ck_billing_sequence_positive CHECK (next_value > 0)
);

CREATE TABLE billing_family_account (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    portal_account_id UUID NOT NULL,
    account_number VARCHAR(40) NOT NULL,
    display_name VARCHAR(180) NOT NULL,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'MAD',
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_billing_family_portal FOREIGN KEY (portal_account_id, tenant_id)
        REFERENCES admission_portal_account(id, tenant_id),
    CONSTRAINT uq_billing_family_portal UNIQUE (tenant_id, portal_account_id),
    CONSTRAINT uq_billing_family_number UNIQUE (tenant_id, account_number),
    CONSTRAINT uq_billing_family_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_billing_family_currency CHECK (CHAR_LENGTH(currency_code) = 3)
);

CREATE TABLE billing_discount (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    family_account_id UUID NOT NULL,
    student_id UUID,
    code VARCHAR(60) NOT NULL,
    label VARCHAR(180) NOT NULL,
    discount_type VARCHAR(20) NOT NULL,
    discount_value NUMERIC(14,2) NOT NULL,
    service_type VARCHAR(30),
    starts_on DATE,
    ends_on DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_billing_discount_family FOREIGN KEY (family_account_id, tenant_id)
        REFERENCES billing_family_account(id, tenant_id),
    CONSTRAINT fk_billing_discount_student FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT uq_billing_discount_code UNIQUE (tenant_id, family_account_id, code),
    CONSTRAINT uq_billing_discount_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_billing_discount_value CHECK (discount_value > 0),
    CONSTRAINT ck_billing_discount_dates CHECK (
        ends_on IS NULL OR starts_on IS NULL OR starts_on <= ends_on
    )
);

CREATE TABLE billing_charge (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    family_account_id UUID NOT NULL,
    student_id UUID,
    discount_id UUID,
    service_type VARCHAR(30) NOT NULL,
    label VARCHAR(180) NOT NULL,
    description VARCHAR(1000),
    quantity NUMERIC(12,2) NOT NULL,
    unit_amount NUMERIC(14,2) NOT NULL,
    gross_amount NUMERIC(14,2) NOT NULL,
    discount_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    net_amount NUMERIC(14,2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    charged_on DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    source_reference VARCHAR(120),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_billing_charge_family FOREIGN KEY (family_account_id, tenant_id)
        REFERENCES billing_family_account(id, tenant_id),
    CONSTRAINT fk_billing_charge_student FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT fk_billing_charge_discount FOREIGN KEY (discount_id, tenant_id)
        REFERENCES billing_discount(id, tenant_id),
    CONSTRAINT uq_billing_charge_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_billing_charge_quantity CHECK (quantity > 0),
    CONSTRAINT ck_billing_charge_unit CHECK (unit_amount >= 0),
    CONSTRAINT ck_billing_charge_amounts CHECK (
        gross_amount >= 0 AND discount_amount >= 0
        AND discount_amount <= gross_amount AND net_amount = gross_amount - discount_amount
    )
);

CREATE TABLE billing_invoice (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    family_account_id UUID NOT NULL,
    invoice_number VARCHAR(40) NOT NULL,
    issue_date DATE NOT NULL,
    due_date DATE NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    subtotal_amount NUMERIC(14,2) NOT NULL,
    discount_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(14,2) NOT NULL,
    paid_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    balance_amount NUMERIC(14,2) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_billing_invoice_family FOREIGN KEY (family_account_id, tenant_id)
        REFERENCES billing_family_account(id, tenant_id),
    CONSTRAINT uq_billing_invoice_number UNIQUE (tenant_id, invoice_number),
    CONSTRAINT uq_billing_invoice_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_billing_invoice_dates CHECK (issue_date <= due_date),
    CONSTRAINT ck_billing_invoice_amounts CHECK (
        subtotal_amount >= 0 AND discount_amount >= 0
        AND total_amount = subtotal_amount - discount_amount
        AND paid_amount >= 0 AND balance_amount >= 0
    )
);

CREATE TABLE billing_invoice_line (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    invoice_id UUID NOT NULL,
    charge_id UUID NOT NULL,
    student_id UUID,
    service_type VARCHAR(30) NOT NULL,
    label VARCHAR(180) NOT NULL,
    description VARCHAR(1000),
    quantity NUMERIC(12,2) NOT NULL,
    unit_amount NUMERIC(14,2) NOT NULL,
    gross_amount NUMERIC(14,2) NOT NULL,
    discount_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    line_total NUMERIC(14,2) NOT NULL,
    line_position INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_billing_line_invoice FOREIGN KEY (invoice_id, tenant_id)
        REFERENCES billing_invoice(id, tenant_id),
    CONSTRAINT fk_billing_line_charge FOREIGN KEY (charge_id, tenant_id)
        REFERENCES billing_charge(id, tenant_id),
    CONSTRAINT fk_billing_line_student FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT uq_billing_line_position UNIQUE (tenant_id, invoice_id, line_position),
    CONSTRAINT uq_billing_line_charge UNIQUE (tenant_id, charge_id),
    CONSTRAINT ck_billing_line_position CHECK (line_position > 0),
    CONSTRAINT ck_billing_line_amounts CHECK (
        quantity > 0 AND unit_amount >= 0 AND gross_amount >= 0
        AND discount_amount >= 0 AND discount_amount <= gross_amount
        AND line_total = gross_amount - discount_amount
    )
);

CREATE TABLE billing_payment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    family_account_id UUID NOT NULL,
    payment_number VARCHAR(40) NOT NULL,
    payment_type VARCHAR(20) NOT NULL,
    reversal_of_payment_id UUID,
    payment_method VARCHAR(30) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    external_reference VARCHAR(120),
    status VARCHAR(30) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    validated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reversal_reason VARCHAR(500),
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_billing_payment_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_billing_payment_family FOREIGN KEY (family_account_id, tenant_id)
        REFERENCES billing_family_account(id, tenant_id),
    CONSTRAINT fk_billing_payment_reversal FOREIGN KEY (reversal_of_payment_id, tenant_id)
        REFERENCES billing_payment(id, tenant_id),
    CONSTRAINT fk_billing_payment_actor FOREIGN KEY (created_by)
        REFERENCES identity_user_account(id),
    CONSTRAINT uq_billing_payment_number UNIQUE (tenant_id, payment_number),
    CONSTRAINT uq_billing_payment_reference UNIQUE (tenant_id, external_reference),
    CONSTRAINT uq_billing_payment_reversal UNIQUE (tenant_id, reversal_of_payment_id),
    CONSTRAINT ck_billing_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_billing_payment_reversal_shape CHECK (
        (payment_type = 'PAYMENT' AND reversal_of_payment_id IS NULL)
        OR (payment_type = 'REVERSAL' AND reversal_of_payment_id IS NOT NULL)
    )
);

CREATE TABLE billing_payment_allocation (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    payment_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    allocated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_billing_allocation_payment FOREIGN KEY (payment_id, tenant_id)
        REFERENCES billing_payment(id, tenant_id),
    CONSTRAINT fk_billing_allocation_invoice FOREIGN KEY (invoice_id, tenant_id)
        REFERENCES billing_invoice(id, tenant_id),
    CONSTRAINT uq_billing_allocation UNIQUE (tenant_id, payment_id, invoice_id),
    CONSTRAINT ck_billing_allocation_nonzero CHECK (amount <> 0)
);

CREATE TABLE billing_credit (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    family_account_id UUID NOT NULL,
    credit_number VARCHAR(40) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    available_amount NUMERIC(14,2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(30) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID,
    CONSTRAINT fk_billing_credit_family FOREIGN KEY (family_account_id, tenant_id)
        REFERENCES billing_family_account(id, tenant_id),
    CONSTRAINT fk_billing_credit_actor FOREIGN KEY (created_by)
        REFERENCES identity_user_account(id),
    CONSTRAINT uq_billing_credit_number UNIQUE (tenant_id, credit_number),
    CONSTRAINT uq_billing_credit_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_billing_credit_amounts CHECK (
        amount > 0 AND available_amount >= 0 AND available_amount <= amount
    )
);

CREATE TABLE billing_refund (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    family_account_id UUID NOT NULL,
    credit_id UUID NOT NULL,
    refund_number VARCHAR(40) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    refund_method VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(30) NOT NULL,
    refunded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID,
    CONSTRAINT fk_billing_refund_family FOREIGN KEY (family_account_id, tenant_id)
        REFERENCES billing_family_account(id, tenant_id),
    CONSTRAINT fk_billing_refund_credit FOREIGN KEY (credit_id, tenant_id)
        REFERENCES billing_credit(id, tenant_id),
    CONSTRAINT fk_billing_refund_actor FOREIGN KEY (created_by)
        REFERENCES identity_user_account(id),
    CONSTRAINT uq_billing_refund_number UNIQUE (tenant_id, refund_number),
    CONSTRAINT uq_billing_refund_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_billing_refund_amount CHECK (amount > 0)
);

CREATE TABLE billing_installment_plan (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    family_account_id UUID NOT NULL,
    plan_number VARCHAR(40) NOT NULL,
    name VARCHAR(180) NOT NULL,
    frequency VARCHAR(30) NOT NULL,
    total_amount NUMERIC(14,2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    starts_on DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_billing_plan_family FOREIGN KEY (family_account_id, tenant_id)
        REFERENCES billing_family_account(id, tenant_id),
    CONSTRAINT uq_billing_plan_number UNIQUE (tenant_id, plan_number),
    CONSTRAINT uq_billing_plan_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_billing_plan_amount CHECK (total_amount > 0)
);

CREATE TABLE billing_payment_schedule (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    installment_plan_id UUID NOT NULL,
    invoice_id UUID,
    due_date DATE NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    paid_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_billing_schedule_plan FOREIGN KEY (installment_plan_id, tenant_id)
        REFERENCES billing_installment_plan(id, tenant_id),
    CONSTRAINT fk_billing_schedule_invoice FOREIGN KEY (invoice_id, tenant_id)
        REFERENCES billing_invoice(id, tenant_id),
    CONSTRAINT uq_billing_schedule_due UNIQUE (tenant_id, installment_plan_id, due_date),
    CONSTRAINT ck_billing_schedule_amount CHECK (amount > 0)
);

CREATE TABLE billing_receipt (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    family_account_id UUID NOT NULL,
    payment_id UUID NOT NULL,
    receipt_number VARCHAR(40) NOT NULL,
    receipt_type VARCHAR(30) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(30) NOT NULL,
    CONSTRAINT fk_billing_receipt_family FOREIGN KEY (family_account_id, tenant_id)
        REFERENCES billing_family_account(id, tenant_id),
    CONSTRAINT fk_billing_receipt_payment FOREIGN KEY (payment_id, tenant_id)
        REFERENCES billing_payment(id, tenant_id),
    CONSTRAINT uq_billing_receipt_number UNIQUE (tenant_id, receipt_number),
    CONSTRAINT uq_billing_receipt_payment UNIQUE (tenant_id, payment_id),
    CONSTRAINT uq_billing_receipt_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_billing_receipt_amount CHECK (amount > 0)
);

CREATE TABLE billing_financial_adjustment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    family_account_id UUID NOT NULL,
    invoice_id UUID,
    adjustment_number VARCHAR(40) NOT NULL,
    direction VARCHAR(20) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(30) NOT NULL,
    reversal_of_adjustment_id UUID,
    posted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID,
    CONSTRAINT uq_billing_adjustment_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_billing_adjustment_family FOREIGN KEY (family_account_id, tenant_id)
        REFERENCES billing_family_account(id, tenant_id),
    CONSTRAINT fk_billing_adjustment_invoice FOREIGN KEY (invoice_id, tenant_id)
        REFERENCES billing_invoice(id, tenant_id),
    CONSTRAINT fk_billing_adjustment_reversal FOREIGN KEY (reversal_of_adjustment_id, tenant_id)
        REFERENCES billing_financial_adjustment(id, tenant_id),
    CONSTRAINT fk_billing_adjustment_actor FOREIGN KEY (created_by)
        REFERENCES identity_user_account(id),
    CONSTRAINT uq_billing_adjustment_number UNIQUE (tenant_id, adjustment_number),
    CONSTRAINT ck_billing_adjustment_amount CHECK (amount > 0)
);

CREATE INDEX idx_billing_family_status ON billing_family_account (tenant_id, status, display_name);
CREATE INDEX idx_billing_charge_family ON billing_charge (tenant_id, family_account_id, charged_on);
CREATE INDEX idx_billing_invoice_family ON billing_invoice (tenant_id, family_account_id, due_date, status);
CREATE INDEX idx_billing_payment_family ON billing_payment (tenant_id, family_account_id, received_at);
CREATE INDEX idx_billing_allocation_invoice ON billing_payment_allocation (tenant_id, invoice_id);
CREATE INDEX idx_billing_credit_family ON billing_credit (tenant_id, family_account_id, status);
CREATE INDEX idx_billing_refund_family ON billing_refund (tenant_id, family_account_id, refunded_at);
CREATE INDEX idx_billing_schedule_due ON billing_payment_schedule (tenant_id, due_date, status);
CREATE INDEX idx_billing_receipt_family ON billing_receipt (tenant_id, family_account_id, issued_at);
CREATE INDEX idx_billing_adjustment_family ON billing_financial_adjustment (tenant_id, family_account_id, posted_at);
