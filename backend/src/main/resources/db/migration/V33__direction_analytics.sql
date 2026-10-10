CREATE TABLE analytics_snapshot (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    campus_id UUID NOT NULL,
    snapshot_date DATE NOT NULL,
    total_students INTEGER NOT NULL DEFAULT 0,
    present_students INTEGER NOT NULL DEFAULT 0,
    absent_students INTEGER NOT NULL DEFAULT 0,
    teachers_absent INTEGER NOT NULL DEFAULT 0,
    expected_revenue NUMERIC(18,2) NOT NULL DEFAULT 0,
    collected_revenue NUMERIC(18,2) NOT NULL DEFAULT 0,
    outstanding_revenue NUMERIC(18,2) NOT NULL DEFAULT 0,
    new_applications INTEGER NOT NULL DEFAULT 0,
    enrolled_students INTEGER NOT NULL DEFAULT 0,
    capacity_total INTEGER NOT NULL DEFAULT 0,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'MAD',
    source VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_analytics_snapshot UNIQUE (tenant_id, campus_id, snapshot_date),
    CONSTRAINT fk_analytics_snapshot_tenant FOREIGN KEY (tenant_id)
        REFERENCES identity_tenant(id) ON DELETE CASCADE,
    CONSTRAINT fk_analytics_snapshot_campus FOREIGN KEY (tenant_id, campus_id)
        REFERENCES school_campus(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_analytics_snapshot_counts CHECK (
        total_students >= 0 AND present_students >= 0 AND absent_students >= 0
        AND teachers_absent >= 0 AND new_applications >= 0
        AND enrolled_students >= 0 AND capacity_total >= 0
        AND present_students + absent_students <= total_students
    ),
    CONSTRAINT ck_analytics_snapshot_money CHECK (
        expected_revenue >= 0 AND collected_revenue >= 0 AND outstanding_revenue >= 0
        AND collected_revenue + outstanding_revenue = expected_revenue
    ),
    CONSTRAINT ck_analytics_snapshot_source CHECK (source IN ('SCHEDULED','MANUAL','IMPORT'))
);

CREATE TABLE analytics_dimension_value (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    campus_id UUID,
    snapshot_date DATE NOT NULL,
    metric_code VARCHAR(50) NOT NULL,
    dimension_code VARCHAR(80) NOT NULL,
    label_fr VARCHAR(180) NOT NULL,
    label_ar VARCHAR(180),
    metric_value NUMERIC(18,4) NOT NULL,
    target_value NUMERIC(18,4),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_analytics_dimension_tenant FOREIGN KEY (tenant_id)
        REFERENCES identity_tenant(id) ON DELETE CASCADE,
    CONSTRAINT fk_analytics_dimension_campus FOREIGN KEY (tenant_id, campus_id)
        REFERENCES school_campus(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_analytics_dimension_metric CHECK (
        metric_code IN ('CLASS_DISTRIBUTION','ADMISSIONS_FUNNEL','REVENUE_BY_SERVICE','UNPAID_AGING')
    ),
    CONSTRAINT ck_analytics_dimension_values CHECK (
        metric_value >= 0 AND (target_value IS NULL OR target_value >= 0)
    )
);

CREATE INDEX idx_analytics_snapshot_scope
    ON analytics_snapshot(tenant_id, snapshot_date DESC, campus_id);
CREATE INDEX idx_analytics_dimension_scope
    ON analytics_dimension_value(tenant_id, campus_id, snapshot_date DESC, metric_code, sort_order);
