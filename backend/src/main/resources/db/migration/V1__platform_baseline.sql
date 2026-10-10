CREATE TABLE platform_metadata (
    metadata_key VARCHAR(100) PRIMARY KEY,
    metadata_value VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO platform_metadata (metadata_key, metadata_value)
VALUES ('schema_owner', 'schoolz-platform');

