ALTER TABLE grading_report_card ADD COLUMN supersedes_report_card_id UUID;
ALTER TABLE grading_report_card ADD COLUMN language_mode VARCHAR(20);
ALTER TABLE grading_report_card ADD COLUMN file_name VARCHAR(240);
ALTER TABLE grading_report_card ADD COLUMN pdf_content BYTEA;
ALTER TABLE grading_report_card ADD COLUMN pdf_sha256 CHAR(64);
ALTER TABLE grading_report_card ADD COLUMN pdf_size BIGINT;
ALTER TABLE grading_report_card ADD COLUMN publication_snapshot TEXT;
ALTER TABLE grading_report_card ADD COLUMN publication_note VARCHAR(500);
ALTER TABLE grading_report_card ADD COLUMN signed_by_name VARCHAR(180);
ALTER TABLE grading_report_card ADD COLUMN signed_by_title VARCHAR(180);
ALTER TABLE grading_report_card ADD COLUMN published_by_user_id UUID;
ALTER TABLE grading_report_card ADD COLUMN published_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE grading_report_card
    ADD CONSTRAINT fk_grading_report_supersedes_tenant
        FOREIGN KEY (supersedes_report_card_id, tenant_id)
        REFERENCES grading_report_card(id, tenant_id);

ALTER TABLE grading_report_card
    ADD CONSTRAINT fk_grading_report_publisher_tenant
        FOREIGN KEY (published_by_user_id, tenant_id)
        REFERENCES identity_user_account(id, tenant_id);

ALTER TABLE grading_report_card
    ADD CONSTRAINT ck_grading_report_language
        CHECK (language_mode IS NULL OR language_mode IN ('FR', 'AR', 'BILINGUAL'));

ALTER TABLE grading_report_card
    ADD CONSTRAINT ck_grading_report_pdf_size
        CHECK (pdf_size IS NULL OR pdf_size > 0);

ALTER TABLE grading_report_card
    ADD CONSTRAINT ck_grading_report_publication
        CHECK (
            (status <> 'PUBLISHED')
            OR (
                language_mode IS NOT NULL
                AND file_name IS NOT NULL
                AND pdf_content IS NOT NULL
                AND pdf_sha256 IS NOT NULL
                AND pdf_size IS NOT NULL
                AND publication_snapshot IS NOT NULL
                AND signed_by_name IS NOT NULL
                AND published_by_user_id IS NOT NULL
                AND published_at IS NOT NULL
            )
        );

CREATE INDEX idx_grading_report_version_chain
    ON grading_report_card (tenant_id, student_id, academic_term_id, version_number DESC);

CREATE INDEX idx_grading_report_publication
    ON grading_report_card (tenant_id, status, published_at DESC);
