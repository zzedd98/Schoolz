CREATE TABLE library_configuration (
    tenant_id UUID PRIMARY KEY REFERENCES identity_tenant(id),
    fine_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    fine_per_overdue_day NUMERIC(10,2) NOT NULL DEFAULT 0,
    maximum_fine NUMERIC(10,2) NOT NULL DEFAULT 0,
    default_loan_days INTEGER NOT NULL DEFAULT 14,
    reservation_hold_days INTEGER NOT NULL DEFAULT 3,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_library_configuration_amounts CHECK (
        fine_per_overdue_day >= 0 AND maximum_fine >= 0
        AND default_loan_days BETWEEN 1 AND 120
        AND reservation_hold_days BETWEEN 1 AND 30
    )
);

CREATE TABLE library_author (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    display_name VARCHAR(180) NOT NULL,
    sort_name VARCHAR(180) NOT NULL,
    biography VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_library_author_name UNIQUE (tenant_id, sort_name),
    CONSTRAINT uq_library_author_id_tenant UNIQUE (id, tenant_id)
);

CREATE TABLE library_category (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(140) NOT NULL,
    color VARCHAR(20),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_library_category_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_library_category_id_tenant UNIQUE (id, tenant_id)
);

CREATE TABLE library_book (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    category_id UUID NOT NULL,
    isbn VARCHAR(20),
    title VARCHAR(260) NOT NULL,
    subtitle VARCHAR(260),
    publisher VARCHAR(180),
    publication_year INTEGER,
    language_code VARCHAR(10) NOT NULL,
    description VARCHAR(1200),
    cover_key VARCHAR(300),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_library_book_isbn UNIQUE (tenant_id, isbn),
    CONSTRAINT uq_library_book_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_library_book_category FOREIGN KEY (category_id, tenant_id)
        REFERENCES library_category(id, tenant_id),
    CONSTRAINT ck_library_book_year CHECK (
        publication_year IS NULL OR publication_year BETWEEN 1000 AND 2200
    )
);

CREATE TABLE library_book_author (
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    book_id UUID NOT NULL,
    author_id UUID NOT NULL,
    author_position INTEGER NOT NULL,
    PRIMARY KEY (tenant_id, book_id, author_id),
    CONSTRAINT uq_library_book_author_position UNIQUE (tenant_id, book_id, author_position),
    CONSTRAINT fk_library_book_author_book FOREIGN KEY (book_id, tenant_id)
        REFERENCES library_book(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_library_book_author_author FOREIGN KEY (author_id, tenant_id)
        REFERENCES library_author(id, tenant_id),
    CONSTRAINT ck_library_book_author_position CHECK (author_position > 0)
);

CREATE TABLE library_book_copy (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    book_id UUID NOT NULL,
    location_id UUID,
    accession_number VARCHAR(70) NOT NULL,
    barcode VARCHAR(120),
    qr_code VARCHAR(180),
    acquired_on DATE,
    condition_status VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    notes VARCHAR(700),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_library_copy_accession UNIQUE (tenant_id, accession_number),
    CONSTRAINT uq_library_copy_barcode UNIQUE (tenant_id, barcode),
    CONSTRAINT uq_library_copy_qr UNIQUE (tenant_id, qr_code),
    CONSTRAINT uq_library_copy_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_library_copy_book FOREIGN KEY (book_id, tenant_id)
        REFERENCES library_book(id, tenant_id),
    CONSTRAINT fk_library_copy_location FOREIGN KEY (location_id, tenant_id)
        REFERENCES inventory_location(id, tenant_id),
    CONSTRAINT ck_library_copy_identifier CHECK (barcode IS NOT NULL OR qr_code IS NOT NULL),
    CONSTRAINT ck_library_copy_condition CHECK (
        condition_status IN ('NEW','GOOD','FAIR','DAMAGED')
    ),
    CONSTRAINT ck_library_copy_status CHECK (
        status IN ('AVAILABLE','ON_LOAN','RESERVED','LOST','REPAIR','RETIRED')
    )
);

CREATE TABLE library_reservation (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    book_id UUID NOT NULL,
    student_id UUID NOT NULL,
    ready_copy_id UUID,
    queue_position INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    reserved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ready_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    fulfilled_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    created_by_user_id UUID NOT NULL REFERENCES identity_user_account(id),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_library_reservation_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_library_reservation_book FOREIGN KEY (book_id, tenant_id)
        REFERENCES library_book(id, tenant_id),
    CONSTRAINT fk_library_reservation_student FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT fk_library_reservation_copy FOREIGN KEY (ready_copy_id, tenant_id)
        REFERENCES library_book_copy(id, tenant_id),
    CONSTRAINT ck_library_reservation_queue CHECK (queue_position > 0),
    CONSTRAINT ck_library_reservation_status CHECK (
        status IN ('WAITING','READY','FULFILLED','CANCELLED','EXPIRED')
    )
);

CREATE TABLE library_loan (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    book_copy_id UUID NOT NULL,
    student_id UUID NOT NULL,
    reservation_id UUID,
    loaned_on DATE NOT NULL,
    due_on DATE NOT NULL,
    returned_on DATE,
    condition_out VARCHAR(30) NOT NULL,
    condition_in VARCHAR(30),
    status VARCHAR(20) NOT NULL,
    loaned_by_user_id UUID NOT NULL REFERENCES identity_user_account(id),
    returned_by_user_id UUID REFERENCES identity_user_account(id),
    note VARCHAR(700),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_library_loan_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_library_loan_copy FOREIGN KEY (book_copy_id, tenant_id)
        REFERENCES library_book_copy(id, tenant_id),
    CONSTRAINT fk_library_loan_student FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT fk_library_loan_reservation FOREIGN KEY (reservation_id, tenant_id)
        REFERENCES library_reservation(id, tenant_id),
    CONSTRAINT ck_library_loan_dates CHECK (
        due_on >= loaned_on AND (returned_on IS NULL OR returned_on >= loaned_on)
    ),
    CONSTRAINT ck_library_loan_condition_out CHECK (
        condition_out IN ('NEW','GOOD','FAIR','DAMAGED')
    ),
    CONSTRAINT ck_library_loan_condition_in CHECK (
        condition_in IS NULL OR condition_in IN ('NEW','GOOD','FAIR','DAMAGED')
    ),
    CONSTRAINT ck_library_loan_status CHECK (status IN ('ACTIVE','RETURNED','LOST'))
);

CREATE TABLE library_fine (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    loan_id UUID NOT NULL,
    student_id UUID NOT NULL,
    overdue_days INTEGER NOT NULL,
    amount NUMERIC(10,2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'MAD',
    status VARCHAR(20) NOT NULL,
    resolution_note VARCHAR(700),
    assessed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolved_by_user_id UUID REFERENCES identity_user_account(id),
    CONSTRAINT uq_library_fine_loan UNIQUE (tenant_id, loan_id),
    CONSTRAINT uq_library_fine_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_library_fine_loan FOREIGN KEY (loan_id, tenant_id)
        REFERENCES library_loan(id, tenant_id),
    CONSTRAINT fk_library_fine_student FOREIGN KEY (student_id, tenant_id)
        REFERENCES student_profile(id, tenant_id),
    CONSTRAINT ck_library_fine_values CHECK (overdue_days > 0 AND amount >= 0),
    CONSTRAINT ck_library_fine_status CHECK (status IN ('OPEN','PAID','WAIVED'))
);

CREATE INDEX idx_library_book_title ON library_book (tenant_id, title);
CREATE INDEX idx_library_copy_status ON library_book_copy (tenant_id, book_id, status);
CREATE INDEX idx_library_loan_student ON library_loan (tenant_id, student_id, status, due_on);
CREATE INDEX idx_library_reservation_queue
    ON library_reservation (tenant_id, book_id, status, queue_position);
CREATE INDEX idx_library_fine_student ON library_fine (tenant_id, student_id, status);
