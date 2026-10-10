CREATE INDEX idx_student_directory_cover
    ON student_profile (tenant_id, person_id, status, id);
CREATE INDEX idx_student_identifier_primary
    ON student_identifier (tenant_id, student_id, primary_identifier, identifier_value);
CREATE INDEX idx_student_enrollment_active
    ON student_enrollment (tenant_id, student_id, status, academic_year_id, classroom_id, campus_id);
CREATE INDEX idx_person_directory_order
    ON person (tenant_id, last_name, first_name, id);
CREATE INDEX idx_privacy_request_due
    ON privacy_data_subject_request (tenant_id, due_at, status);
CREATE INDEX idx_webhook_delivery_queue
    ON webhook_delivery (status, next_attempt_at, created_at);
