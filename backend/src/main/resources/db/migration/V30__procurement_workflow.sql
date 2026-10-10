CREATE TABLE procurement_configuration (
    tenant_id UUID PRIMARY KEY REFERENCES identity_tenant(id),
    base_currency CHAR(3) NOT NULL DEFAULT 'MAD',
    allow_partial_receipts BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE procurement_approval_threshold (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    code VARCHAR(40) NOT NULL,
    label VARCHAR(180) NOT NULL,
    min_amount NUMERIC(14,2) NOT NULL,
    max_amount NUMERIC(14,2),
    required_approvals INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_procurement_threshold_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_procurement_threshold_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_procurement_threshold_amounts CHECK (
        min_amount >= 0 AND (max_amount IS NULL OR max_amount > min_amount)
    ),
    CONSTRAINT ck_procurement_threshold_approvals CHECK (required_approvals BETWEEN 1 AND 5)
);

CREATE TABLE procurement_purchase_request (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    approval_threshold_id UUID,
    cost_center_id UUID,
    requester_user_id UUID NOT NULL REFERENCES identity_user_account(id),
    request_number VARCHAR(70) NOT NULL,
    requested_on DATE NOT NULL,
    title VARCHAR(180) NOT NULL,
    business_need VARCHAR(1000) NOT NULL,
    urgency VARCHAR(20) NOT NULL,
    estimated_amount NUMERIC(14,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE,
    approved_at TIMESTAMP WITH TIME ZONE,
    rejected_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_procurement_request_number UNIQUE (tenant_id, request_number),
    CONSTRAINT uq_procurement_request_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_procurement_request_threshold FOREIGN KEY (approval_threshold_id, tenant_id)
        REFERENCES procurement_approval_threshold(id, tenant_id),
    CONSTRAINT fk_procurement_request_cost_center FOREIGN KEY (cost_center_id, tenant_id)
        REFERENCES accounting_cost_center(id, tenant_id),
    CONSTRAINT ck_procurement_request_urgency CHECK (urgency IN ('NORMAL','HIGH','CRITICAL')),
    CONSTRAINT ck_procurement_request_amount CHECK (estimated_amount > 0),
    CONSTRAINT ck_procurement_request_status CHECK (
        status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','ORDERED','RECEIVED','INVOICED','PAID')
    )
);

CREATE TABLE procurement_purchase_request_line (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    purchase_request_id UUID NOT NULL,
    line_position INTEGER NOT NULL,
    description VARCHAR(500) NOT NULL,
    quantity NUMERIC(12,3) NOT NULL,
    unit VARCHAR(40) NOT NULL,
    estimated_unit_price NUMERIC(14,2) NOT NULL,
    estimated_total NUMERIC(14,2) NOT NULL,
    CONSTRAINT uq_procurement_request_line_position UNIQUE (
        tenant_id, purchase_request_id, line_position
    ),
    CONSTRAINT uq_procurement_request_line_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_procurement_request_line_request FOREIGN KEY (purchase_request_id, tenant_id)
        REFERENCES procurement_purchase_request(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT ck_procurement_request_line_values CHECK (
        line_position > 0 AND quantity > 0 AND estimated_unit_price >= 0 AND estimated_total >= 0
    )
);

CREATE TABLE procurement_request_approval (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    purchase_request_id UUID NOT NULL,
    approver_user_id UUID NOT NULL REFERENCES identity_user_account(id),
    decision VARCHAR(20) NOT NULL,
    note VARCHAR(700),
    decided_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_procurement_request_approver UNIQUE (
        tenant_id, purchase_request_id, approver_user_id
    ),
    CONSTRAINT uq_procurement_approval_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_procurement_approval_request FOREIGN KEY (purchase_request_id, tenant_id)
        REFERENCES procurement_purchase_request(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT ck_procurement_approval_decision CHECK (decision IN ('APPROVED','REJECTED'))
);

CREATE TABLE procurement_purchase_order (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    purchase_request_id UUID NOT NULL,
    supplier_id UUID NOT NULL,
    order_number VARCHAR(70) NOT NULL,
    ordered_on DATE NOT NULL,
    expected_on DATE,
    delivery_address VARCHAR(700),
    notes VARCHAR(1000),
    total_amount NUMERIC(14,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    issued_by_user_id UUID NOT NULL REFERENCES identity_user_account(id),
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_procurement_order_number UNIQUE (tenant_id, order_number),
    CONSTRAINT uq_procurement_order_request UNIQUE (tenant_id, purchase_request_id),
    CONSTRAINT uq_procurement_order_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_procurement_order_request FOREIGN KEY (purchase_request_id, tenant_id)
        REFERENCES procurement_purchase_request(id, tenant_id),
    CONSTRAINT fk_procurement_order_supplier FOREIGN KEY (supplier_id, tenant_id)
        REFERENCES accounting_supplier(id, tenant_id),
    CONSTRAINT ck_procurement_order_amount CHECK (total_amount > 0),
    CONSTRAINT ck_procurement_order_status CHECK (
        status IN ('ISSUED','PARTIALLY_RECEIVED','RECEIVED','INVOICED','PAID','CANCELLED')
    )
);

CREATE TABLE procurement_purchase_order_line (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    purchase_order_id UUID NOT NULL,
    request_line_id UUID NOT NULL,
    line_position INTEGER NOT NULL,
    description VARCHAR(500) NOT NULL,
    quantity_ordered NUMERIC(12,3) NOT NULL,
    quantity_received NUMERIC(12,3) NOT NULL DEFAULT 0,
    unit VARCHAR(40) NOT NULL,
    unit_price NUMERIC(14,2) NOT NULL,
    line_total NUMERIC(14,2) NOT NULL,
    CONSTRAINT uq_procurement_order_line_position UNIQUE (
        tenant_id, purchase_order_id, line_position
    ),
    CONSTRAINT uq_procurement_order_line_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_procurement_order_line_order FOREIGN KEY (purchase_order_id, tenant_id)
        REFERENCES procurement_purchase_order(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_procurement_order_line_request_line FOREIGN KEY (request_line_id, tenant_id)
        REFERENCES procurement_purchase_request_line(id, tenant_id),
    CONSTRAINT ck_procurement_order_line_values CHECK (
        line_position > 0 AND quantity_ordered > 0 AND quantity_received >= 0
        AND quantity_received <= quantity_ordered AND unit_price >= 0 AND line_total >= 0
    )
);

CREATE TABLE procurement_goods_receipt (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    purchase_order_id UUID NOT NULL,
    receipt_number VARCHAR(70) NOT NULL,
    received_on DATE NOT NULL,
    delivery_note VARCHAR(120),
    note VARCHAR(700),
    received_by_user_id UUID NOT NULL REFERENCES identity_user_account(id),
    status VARCHAR(20) NOT NULL,
    confirmed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_procurement_receipt_number UNIQUE (tenant_id, receipt_number),
    CONSTRAINT uq_procurement_receipt_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_procurement_receipt_order FOREIGN KEY (purchase_order_id, tenant_id)
        REFERENCES procurement_purchase_order(id, tenant_id),
    CONSTRAINT ck_procurement_receipt_status CHECK (status = 'CONFIRMED')
);

CREATE TABLE procurement_goods_receipt_line (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    goods_receipt_id UUID NOT NULL,
    purchase_order_line_id UUID NOT NULL,
    quantity_received NUMERIC(12,3) NOT NULL,
    CONSTRAINT uq_procurement_receipt_order_line UNIQUE (
        tenant_id, goods_receipt_id, purchase_order_line_id
    ),
    CONSTRAINT fk_procurement_receipt_line_receipt FOREIGN KEY (goods_receipt_id, tenant_id)
        REFERENCES procurement_goods_receipt(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_procurement_receipt_line_order_line FOREIGN KEY (purchase_order_line_id, tenant_id)
        REFERENCES procurement_purchase_order_line(id, tenant_id),
    CONSTRAINT ck_procurement_receipt_line_quantity CHECK (quantity_received > 0)
);

CREATE TABLE procurement_invoice_match (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    purchase_order_id UUID NOT NULL,
    supplier_invoice_id UUID NOT NULL,
    matched_amount NUMERIC(14,2) NOT NULL,
    variance_amount NUMERIC(14,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    matched_by_user_id UUID NOT NULL REFERENCES identity_user_account(id),
    matched_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_procurement_invoice_order UNIQUE (tenant_id, purchase_order_id),
    CONSTRAINT uq_procurement_invoice_supplier_invoice UNIQUE (tenant_id, supplier_invoice_id),
    CONSTRAINT uq_procurement_invoice_match_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_procurement_invoice_match_order FOREIGN KEY (purchase_order_id, tenant_id)
        REFERENCES procurement_purchase_order(id, tenant_id),
    CONSTRAINT fk_procurement_invoice_match_invoice FOREIGN KEY (supplier_invoice_id, tenant_id)
        REFERENCES accounting_supplier_invoice(id, tenant_id),
    CONSTRAINT ck_procurement_invoice_match_amount CHECK (matched_amount > 0),
    CONSTRAINT ck_procurement_invoice_match_status CHECK (status IN ('MATCHED','EXCEPTION','PAID'))
);

CREATE TABLE procurement_supplier_payment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    invoice_match_id UUID NOT NULL,
    supplier_id UUID NOT NULL,
    journal_entry_id UUID NOT NULL,
    payment_reference VARCHAR(90) NOT NULL,
    paid_on DATE NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    recorded_by_user_id UUID NOT NULL REFERENCES identity_user_account(id),
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_procurement_payment_reference UNIQUE (tenant_id, payment_reference),
    CONSTRAINT uq_procurement_payment_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_procurement_payment_match FOREIGN KEY (invoice_match_id, tenant_id)
        REFERENCES procurement_invoice_match(id, tenant_id),
    CONSTRAINT fk_procurement_payment_supplier FOREIGN KEY (supplier_id, tenant_id)
        REFERENCES accounting_supplier(id, tenant_id),
    CONSTRAINT fk_procurement_payment_entry FOREIGN KEY (journal_entry_id, tenant_id)
        REFERENCES accounting_journal_entry(id, tenant_id),
    CONSTRAINT ck_procurement_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_procurement_payment_method CHECK (
        payment_method IN ('CASH','BANK_TRANSFER','CHECK','CARD','ONLINE_PAYMENT','OTHER')
    ),
    CONSTRAINT ck_procurement_payment_status CHECK (status = 'RECORDED')
);

CREATE INDEX idx_procurement_request_status
    ON procurement_purchase_request (tenant_id, status, requested_on);
CREATE INDEX idx_procurement_order_status
    ON procurement_purchase_order (tenant_id, status, ordered_on);
CREATE INDEX idx_procurement_receipt_order
    ON procurement_goods_receipt (tenant_id, purchase_order_id, received_on);
CREATE INDEX idx_procurement_payment_match
    ON procurement_supplier_payment (tenant_id, invoice_match_id, paid_on);
