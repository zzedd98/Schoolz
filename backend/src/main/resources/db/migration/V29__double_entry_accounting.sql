CREATE TABLE accounting_configuration (
    tenant_id UUID PRIMARY KEY REFERENCES identity_tenant(id),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    base_currency CHAR(3) NOT NULL DEFAULT 'MAD',
    family_ledger_auto_post BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE accounting_chart_of_account (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    parent_account_id UUID,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(180) NOT NULL,
    account_type VARCHAR(30) NOT NULL,
    description VARCHAR(700),
    allow_posting BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_accounting_account_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_accounting_account_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_accounting_account_parent FOREIGN KEY (parent_account_id, tenant_id)
        REFERENCES accounting_chart_of_account(id, tenant_id),
    CONSTRAINT ck_accounting_account_type CHECK (
        account_type IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE')
    )
);

CREATE TABLE accounting_journal (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    code VARCHAR(30) NOT NULL,
    name VARCHAR(180) NOT NULL,
    journal_type VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_accounting_journal_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_accounting_journal_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_accounting_journal_type CHECK (
        journal_type IN ('SALES','PURCHASE','BANK','CASH','PAYROLL','GENERAL')
    )
);

CREATE TABLE accounting_fiscal_period (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    code VARCHAR(40) NOT NULL,
    label VARCHAR(180) NOT NULL,
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    closed_by_user_id UUID,
    closed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_accounting_period_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_accounting_period_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_accounting_period_dates CHECK (ends_on >= starts_on),
    CONSTRAINT ck_accounting_period_status CHECK (status IN ('OPEN','CLOSED'))
);

CREATE TABLE accounting_cost_center (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    code VARCHAR(40) NOT NULL,
    name VARCHAR(180) NOT NULL,
    description VARCHAR(700),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_accounting_cost_center_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_accounting_cost_center_id_tenant UNIQUE (id, tenant_id)
);

CREATE TABLE accounting_journal_entry (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    journal_id UUID NOT NULL,
    fiscal_period_id UUID NOT NULL,
    reversal_of_entry_id UUID,
    entry_number VARCHAR(70) NOT NULL,
    entry_date DATE NOT NULL,
    reference VARCHAR(120) NOT NULL,
    description VARCHAR(700) NOT NULL,
    source_module VARCHAR(50) NOT NULL,
    source_type VARCHAR(60) NOT NULL,
    source_id VARCHAR(100) NOT NULL,
    currency CHAR(3) NOT NULL,
    total_debit NUMERIC(19,2) NOT NULL,
    total_credit NUMERIC(19,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_by_user_id UUID,
    posted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reversed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_accounting_entry_number UNIQUE (tenant_id, entry_number),
    CONSTRAINT uq_accounting_entry_source UNIQUE (
        tenant_id, source_module, source_type, source_id
    ),
    CONSTRAINT uq_accounting_entry_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_accounting_entry_journal FOREIGN KEY (journal_id, tenant_id)
        REFERENCES accounting_journal(id, tenant_id),
    CONSTRAINT fk_accounting_entry_period FOREIGN KEY (fiscal_period_id, tenant_id)
        REFERENCES accounting_fiscal_period(id, tenant_id),
    CONSTRAINT fk_accounting_entry_reversal FOREIGN KEY (reversal_of_entry_id, tenant_id)
        REFERENCES accounting_journal_entry(id, tenant_id),
    CONSTRAINT ck_accounting_entry_status CHECK (status IN ('POSTED','REVERSED')),
    CONSTRAINT ck_accounting_entry_balanced CHECK (
        total_debit > 0 AND total_debit = total_credit
    )
);

CREATE TABLE accounting_journal_line (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    journal_entry_id UUID NOT NULL,
    account_id UUID NOT NULL,
    cost_center_id UUID,
    line_position INTEGER NOT NULL,
    label VARCHAR(300) NOT NULL,
    debit_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    credit_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_accounting_line_position UNIQUE (
        tenant_id, journal_entry_id, line_position
    ),
    CONSTRAINT uq_accounting_line_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_accounting_line_entry FOREIGN KEY (journal_entry_id, tenant_id)
        REFERENCES accounting_journal_entry(id, tenant_id),
    CONSTRAINT fk_accounting_line_account FOREIGN KEY (account_id, tenant_id)
        REFERENCES accounting_chart_of_account(id, tenant_id),
    CONSTRAINT fk_accounting_line_cost_center FOREIGN KEY (cost_center_id, tenant_id)
        REFERENCES accounting_cost_center(id, tenant_id),
    CONSTRAINT ck_accounting_line_side CHECK (
        (debit_amount > 0 AND credit_amount = 0)
        OR (credit_amount > 0 AND debit_amount = 0)
    )
);

CREATE TABLE accounting_supplier (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    code VARCHAR(40) NOT NULL,
    name VARCHAR(180) NOT NULL,
    tax_identifier VARCHAR(80),
    email VARCHAR(254),
    phone VARCHAR(60),
    address VARCHAR(700),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_accounting_supplier_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_accounting_supplier_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_accounting_supplier_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE TABLE accounting_supplier_invoice (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    supplier_id UUID NOT NULL,
    journal_id UUID NOT NULL,
    expense_account_id UUID NOT NULL,
    payable_account_id UUID NOT NULL,
    cost_center_id UUID,
    journal_entry_id UUID,
    invoice_number VARCHAR(90) NOT NULL,
    invoice_date DATE NOT NULL,
    due_date DATE NOT NULL,
    description VARCHAR(700) NOT NULL,
    total_amount NUMERIC(19,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    posted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_accounting_supplier_invoice UNIQUE (
        tenant_id, supplier_id, invoice_number
    ),
    CONSTRAINT uq_accounting_supplier_invoice_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_accounting_supplier_invoice_supplier FOREIGN KEY (supplier_id, tenant_id)
        REFERENCES accounting_supplier(id, tenant_id),
    CONSTRAINT fk_accounting_supplier_invoice_journal FOREIGN KEY (journal_id, tenant_id)
        REFERENCES accounting_journal(id, tenant_id),
    CONSTRAINT fk_accounting_supplier_invoice_expense FOREIGN KEY (expense_account_id, tenant_id)
        REFERENCES accounting_chart_of_account(id, tenant_id),
    CONSTRAINT fk_accounting_supplier_invoice_payable FOREIGN KEY (payable_account_id, tenant_id)
        REFERENCES accounting_chart_of_account(id, tenant_id),
    CONSTRAINT fk_accounting_supplier_invoice_cost FOREIGN KEY (cost_center_id, tenant_id)
        REFERENCES accounting_cost_center(id, tenant_id),
    CONSTRAINT fk_accounting_supplier_invoice_entry FOREIGN KEY (journal_entry_id, tenant_id)
        REFERENCES accounting_journal_entry(id, tenant_id),
    CONSTRAINT ck_accounting_supplier_invoice_dates CHECK (due_date >= invoice_date),
    CONSTRAINT ck_accounting_supplier_invoice_total CHECK (total_amount > 0),
    CONSTRAINT ck_accounting_supplier_invoice_status CHECK (
        status IN ('DRAFT','POSTED','PAID','CANCELLED')
    )
);

CREATE TABLE accounting_expense (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    supplier_id UUID,
    journal_id UUID NOT NULL,
    expense_account_id UUID NOT NULL,
    payment_account_id UUID NOT NULL,
    cost_center_id UUID,
    journal_entry_id UUID,
    expense_reference VARCHAR(90) NOT NULL,
    expense_date DATE NOT NULL,
    description VARCHAR(700) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    payment_method VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    posted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_accounting_expense_reference UNIQUE (tenant_id, expense_reference),
    CONSTRAINT uq_accounting_expense_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_accounting_expense_supplier FOREIGN KEY (supplier_id, tenant_id)
        REFERENCES accounting_supplier(id, tenant_id),
    CONSTRAINT fk_accounting_expense_journal FOREIGN KEY (journal_id, tenant_id)
        REFERENCES accounting_journal(id, tenant_id),
    CONSTRAINT fk_accounting_expense_expense_account FOREIGN KEY (expense_account_id, tenant_id)
        REFERENCES accounting_chart_of_account(id, tenant_id),
    CONSTRAINT fk_accounting_expense_payment_account FOREIGN KEY (payment_account_id, tenant_id)
        REFERENCES accounting_chart_of_account(id, tenant_id),
    CONSTRAINT fk_accounting_expense_cost FOREIGN KEY (cost_center_id, tenant_id)
        REFERENCES accounting_cost_center(id, tenant_id),
    CONSTRAINT fk_accounting_expense_entry FOREIGN KEY (journal_entry_id, tenant_id)
        REFERENCES accounting_journal_entry(id, tenant_id),
    CONSTRAINT ck_accounting_expense_amount CHECK (amount > 0),
    CONSTRAINT ck_accounting_expense_status CHECK (
        status IN ('DRAFT','POSTED','CANCELLED')
    )
);

CREATE TABLE accounting_bank_account (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    ledger_account_id UUID NOT NULL,
    journal_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    bank_name VARCHAR(180) NOT NULL,
    account_label VARCHAR(180) NOT NULL,
    iban_masked VARCHAR(80),
    currency CHAR(3) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_accounting_bank_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_accounting_bank_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_accounting_bank_ledger FOREIGN KEY (ledger_account_id, tenant_id)
        REFERENCES accounting_chart_of_account(id, tenant_id),
    CONSTRAINT fk_accounting_bank_journal FOREIGN KEY (journal_id, tenant_id)
        REFERENCES accounting_journal(id, tenant_id)
);

CREATE TABLE accounting_reconciliation (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    bank_account_id UUID NOT NULL,
    statement_reference VARCHAR(100) NOT NULL,
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL,
    opening_balance NUMERIC(19,2) NOT NULL,
    closing_balance NUMERIC(19,2) NOT NULL,
    matched_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    completed_by_user_id UUID,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_accounting_reconciliation_ref UNIQUE (
        tenant_id, bank_account_id, statement_reference
    ),
    CONSTRAINT uq_accounting_reconciliation_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_accounting_reconciliation_bank FOREIGN KEY (bank_account_id, tenant_id)
        REFERENCES accounting_bank_account(id, tenant_id),
    CONSTRAINT ck_accounting_reconciliation_dates CHECK (ends_on >= starts_on),
    CONSTRAINT ck_accounting_reconciliation_status CHECK (
        status IN ('DRAFT','COMPLETED')
    )
);

CREATE TABLE accounting_reconciliation_item (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    reconciliation_id UUID NOT NULL,
    journal_entry_id UUID NOT NULL,
    matched_amount NUMERIC(19,2) NOT NULL,
    matched_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_accounting_reconciliation_entry UNIQUE (
        tenant_id, reconciliation_id, journal_entry_id
    ),
    CONSTRAINT uq_accounting_reconciliation_item_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_accounting_reconciliation_item_parent FOREIGN KEY (reconciliation_id, tenant_id)
        REFERENCES accounting_reconciliation(id, tenant_id),
    CONSTRAINT fk_accounting_reconciliation_item_entry FOREIGN KEY (journal_entry_id, tenant_id)
        REFERENCES accounting_journal_entry(id, tenant_id),
    CONSTRAINT ck_accounting_reconciliation_item_amount CHECK (matched_amount > 0)
);

CREATE TABLE accounting_event_mapping (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    event_type VARCHAR(60) NOT NULL,
    journal_id UUID NOT NULL,
    debit_account_id UUID NOT NULL,
    credit_account_id UUID NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_accounting_event_mapping UNIQUE (tenant_id, event_type),
    CONSTRAINT uq_accounting_event_mapping_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_accounting_mapping_journal FOREIGN KEY (journal_id, tenant_id)
        REFERENCES accounting_journal(id, tenant_id),
    CONSTRAINT fk_accounting_mapping_debit FOREIGN KEY (debit_account_id, tenant_id)
        REFERENCES accounting_chart_of_account(id, tenant_id),
    CONSTRAINT fk_accounting_mapping_credit FOREIGN KEY (credit_account_id, tenant_id)
        REFERENCES accounting_chart_of_account(id, tenant_id)
);

CREATE INDEX idx_accounting_account_type
    ON accounting_chart_of_account (tenant_id, account_type, active, code);
CREATE INDEX idx_accounting_period_dates
    ON accounting_fiscal_period (tenant_id, status, starts_on, ends_on);
CREATE INDEX idx_accounting_entry_date
    ON accounting_journal_entry (tenant_id, entry_date DESC, posted_at DESC);
CREATE INDEX idx_accounting_entry_source
    ON accounting_journal_entry (tenant_id, source_module, source_type, source_id);
CREATE INDEX idx_accounting_line_account
    ON accounting_journal_line (tenant_id, account_id, journal_entry_id);
CREATE INDEX idx_accounting_supplier_status
    ON accounting_supplier (tenant_id, status, name);
CREATE INDEX idx_accounting_supplier_invoice_status
    ON accounting_supplier_invoice (tenant_id, status, due_date);
CREATE INDEX idx_accounting_expense_status
    ON accounting_expense (tenant_id, status, expense_date DESC);
CREATE INDEX idx_accounting_reconciliation_status
    ON accounting_reconciliation (tenant_id, status, ends_on DESC);
