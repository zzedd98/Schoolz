CREATE TABLE global_search_document (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id) ON DELETE CASCADE,
    source_type VARCHAR(30) NOT NULL,
    source_id UUID NOT NULL,
    display_name VARCHAR(300) NOT NULL,
    secondary_text VARCHAR(500),
    route_path VARCHAR(700) NOT NULL,
    search_text VARCHAR(4000) NOT NULL,
    normalized_text VARCHAR(4000) NOT NULL,
    metadata_json VARCHAR(5000) NOT NULL,
    indexed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_global_search_source UNIQUE (tenant_id, source_type, source_id),
    CONSTRAINT ck_global_search_type CHECK (source_type IN
        ('STUDENT','PARENT','TEACHER','INVOICE','CLASSROOM','DOCUMENT'))
);

CREATE INDEX idx_global_search_tenant_type
    ON global_search_document(tenant_id, source_type, display_name);
CREATE INDEX idx_global_search_normalized
    ON global_search_document(tenant_id, normalized_text);
