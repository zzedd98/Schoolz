CREATE TABLE inventory_location (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    campus_id UUID,
    parent_location_id UUID,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(180) NOT NULL,
    location_type VARCHAR(30) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_inventory_location_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_inventory_location_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_inventory_location_campus FOREIGN KEY (campus_id, tenant_id)
        REFERENCES school_campus(id, tenant_id),
    CONSTRAINT fk_inventory_location_parent FOREIGN KEY (parent_location_id, tenant_id)
        REFERENCES inventory_location(id, tenant_id),
    CONSTRAINT ck_inventory_location_type CHECK (
        location_type IN ('CAMPUS','BUILDING','ROOM','WAREHOUSE','LIBRARY','OFFICE','OTHER')
    )
);

CREATE TABLE inventory_item (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    sku VARCHAR(70) NOT NULL,
    name VARCHAR(180) NOT NULL,
    category VARCHAR(40) NOT NULL,
    unit VARCHAR(30) NOT NULL,
    minimum_stock NUMERIC(14,3) NOT NULL DEFAULT 0,
    description VARCHAR(700),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_inventory_item_sku UNIQUE (tenant_id, sku),
    CONSTRAINT uq_inventory_item_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_inventory_item_minimum CHECK (minimum_stock >= 0),
    CONSTRAINT ck_inventory_item_category CHECK (
        category IN ('COMPUTER','TABLET','PROJECTOR','BOOK','UNIFORM','TEACHING_MATERIAL','OTHER')
    )
);

CREATE TABLE inventory_stock_balance (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    inventory_item_id UUID NOT NULL,
    location_id UUID NOT NULL,
    quantity NUMERIC(14,3) NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_inventory_balance UNIQUE (tenant_id, inventory_item_id, location_id),
    CONSTRAINT uq_inventory_balance_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_inventory_balance_item FOREIGN KEY (inventory_item_id, tenant_id)
        REFERENCES inventory_item(id, tenant_id),
    CONSTRAINT fk_inventory_balance_location FOREIGN KEY (location_id, tenant_id)
        REFERENCES inventory_location(id, tenant_id),
    CONSTRAINT ck_inventory_balance_quantity CHECK (quantity >= 0)
);

CREATE TABLE inventory_stock_movement (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    inventory_item_id UUID NOT NULL,
    from_location_id UUID,
    to_location_id UUID,
    movement_type VARCHAR(30) NOT NULL,
    quantity NUMERIC(14,3) NOT NULL,
    occurred_on DATE NOT NULL,
    reference VARCHAR(120),
    reason VARCHAR(700) NOT NULL,
    recorded_by_user_id UUID NOT NULL REFERENCES identity_user_account(id),
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_inventory_movement_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_inventory_movement_item FOREIGN KEY (inventory_item_id, tenant_id)
        REFERENCES inventory_item(id, tenant_id),
    CONSTRAINT fk_inventory_movement_from FOREIGN KEY (from_location_id, tenant_id)
        REFERENCES inventory_location(id, tenant_id),
    CONSTRAINT fk_inventory_movement_to FOREIGN KEY (to_location_id, tenant_id)
        REFERENCES inventory_location(id, tenant_id),
    CONSTRAINT ck_inventory_movement_quantity CHECK (quantity > 0),
    CONSTRAINT ck_inventory_movement_type CHECK (
        movement_type IN ('INBOUND','OUTBOUND','TRANSFER','ADJUSTMENT_IN','ADJUSTMENT_OUT')
    ),
    CONSTRAINT ck_inventory_movement_locations CHECK (
        (movement_type IN ('INBOUND','ADJUSTMENT_IN') AND from_location_id IS NULL AND to_location_id IS NOT NULL)
        OR (movement_type IN ('OUTBOUND','ADJUSTMENT_OUT') AND from_location_id IS NOT NULL AND to_location_id IS NULL)
        OR (movement_type = 'TRANSFER' AND from_location_id IS NOT NULL AND to_location_id IS NOT NULL
            AND from_location_id <> to_location_id)
    )
);

CREATE TABLE inventory_asset (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    current_location_id UUID,
    asset_tag VARCHAR(70) NOT NULL,
    name VARCHAR(180) NOT NULL,
    category VARCHAR(40) NOT NULL,
    brand VARCHAR(100),
    model VARCHAR(120),
    serial_number VARCHAR(140),
    acquired_on DATE,
    acquisition_value NUMERIC(14,2),
    currency CHAR(3) NOT NULL DEFAULT 'MAD',
    condition_status VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    notes VARCHAR(700),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_inventory_asset_tag UNIQUE (tenant_id, asset_tag),
    CONSTRAINT uq_inventory_asset_serial UNIQUE (tenant_id, serial_number),
    CONSTRAINT uq_inventory_asset_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_inventory_asset_location FOREIGN KEY (current_location_id, tenant_id)
        REFERENCES inventory_location(id, tenant_id),
    CONSTRAINT ck_inventory_asset_value CHECK (acquisition_value IS NULL OR acquisition_value >= 0),
    CONSTRAINT ck_inventory_asset_category CHECK (
        category IN ('COMPUTER','TABLET','PROJECTOR','BOOK','UNIFORM','TEACHING_MATERIAL','OTHER')
    ),
    CONSTRAINT ck_inventory_asset_condition CHECK (
        condition_status IN ('NEW','GOOD','FAIR','DAMAGED')
    ),
    CONSTRAINT ck_inventory_asset_status CHECK (
        status IN ('AVAILABLE','ASSIGNED','MAINTENANCE','LOST','RETIRED')
    )
);

CREATE TABLE inventory_asset_assignment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    asset_id UUID NOT NULL,
    destination_location_id UUID,
    assignee_type VARCHAR(30) NOT NULL,
    assignee_reference VARCHAR(100),
    assignee_label VARCHAR(180) NOT NULL,
    assigned_on DATE NOT NULL,
    due_on DATE,
    returned_on DATE,
    condition_out VARCHAR(30) NOT NULL,
    condition_in VARCHAR(30),
    note VARCHAR(700),
    assigned_by_user_id UUID NOT NULL REFERENCES identity_user_account(id),
    returned_by_user_id UUID REFERENCES identity_user_account(id),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_inventory_assignment_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_inventory_assignment_asset FOREIGN KEY (asset_id, tenant_id)
        REFERENCES inventory_asset(id, tenant_id),
    CONSTRAINT fk_inventory_assignment_location FOREIGN KEY (destination_location_id, tenant_id)
        REFERENCES inventory_location(id, tenant_id),
    CONSTRAINT ck_inventory_assignment_type CHECK (
        assignee_type IN ('PERSON','CLASSROOM','DEPARTMENT','SERVICE','OTHER')
    ),
    CONSTRAINT ck_inventory_assignment_status CHECK (status IN ('ACTIVE','RETURNED')),
    CONSTRAINT ck_inventory_assignment_condition_out CHECK (
        condition_out IN ('NEW','GOOD','FAIR','DAMAGED')
    ),
    CONSTRAINT ck_inventory_assignment_condition_in CHECK (
        condition_in IS NULL OR condition_in IN ('NEW','GOOD','FAIR','DAMAGED')
    ),
    CONSTRAINT ck_inventory_assignment_dates CHECK (
        (due_on IS NULL OR due_on >= assigned_on)
        AND (returned_on IS NULL OR returned_on >= assigned_on)
    )
);

CREATE TABLE inventory_asset_event (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    asset_id UUID NOT NULL,
    assignment_id UUID,
    event_type VARCHAR(30) NOT NULL,
    event_date DATE NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    location_id UUID,
    detail VARCHAR(700) NOT NULL,
    recorded_by_user_id UUID NOT NULL REFERENCES identity_user_account(id),
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_inventory_asset_event_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_inventory_event_asset FOREIGN KEY (asset_id, tenant_id)
        REFERENCES inventory_asset(id, tenant_id),
    CONSTRAINT fk_inventory_event_assignment FOREIGN KEY (assignment_id, tenant_id)
        REFERENCES inventory_asset_assignment(id, tenant_id),
    CONSTRAINT fk_inventory_event_location FOREIGN KEY (location_id, tenant_id)
        REFERENCES inventory_location(id, tenant_id),
    CONSTRAINT ck_inventory_event_type CHECK (
        event_type IN ('REGISTERED','ASSIGNED','RETURNED','STATUS_CHANGED')
    )
);

CREATE INDEX idx_inventory_movement_item_date
    ON inventory_stock_movement (tenant_id, inventory_item_id, occurred_on);
CREATE INDEX idx_inventory_asset_status
    ON inventory_asset (tenant_id, status, category);
CREATE INDEX idx_inventory_assignment_asset
    ON inventory_asset_assignment (tenant_id, asset_id, status);
CREATE INDEX idx_inventory_event_asset
    ON inventory_asset_event (tenant_id, asset_id, event_date);
