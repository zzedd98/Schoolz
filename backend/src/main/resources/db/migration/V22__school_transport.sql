CREATE TABLE transport_vehicle (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    campus_id UUID REFERENCES school_campus(id),
    code VARCHAR(60) NOT NULL,
    registration_number VARCHAR(40) NOT NULL,
    make_model VARCHAR(160),
    capacity INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_transport_vehicle_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_transport_vehicle_registration UNIQUE (tenant_id, registration_number),
    CONSTRAINT ck_transport_vehicle_capacity CHECK (capacity > 0),
    CONSTRAINT ck_transport_vehicle_status CHECK (status IN ('ACTIVE','MAINTENANCE','INACTIVE'))
);

CREATE TABLE transport_driver (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    staff_id UUID REFERENCES staff_profile(id),
    display_name VARCHAR(180) NOT NULL,
    phone VARCHAR(40),
    license_number VARCHAR(80) NOT NULL,
    license_expires_on DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_transport_driver_license UNIQUE (tenant_id, license_number)
);

CREATE TABLE transport_assistant (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    staff_id UUID REFERENCES staff_profile(id),
    display_name VARCHAR(180) NOT NULL,
    phone VARCHAR(40),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE transport_route (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    campus_id UUID REFERENCES school_campus(id),
    code VARCHAR(60) NOT NULL,
    name VARCHAR(180) NOT NULL,
    description VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_transport_route_code UNIQUE (tenant_id, code)
);

CREATE TABLE transport_stop (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    route_id UUID NOT NULL REFERENCES transport_route(id),
    sequence_number INTEGER NOT NULL,
    name VARCHAR(180) NOT NULL,
    address VARCHAR(300),
    latitude NUMERIC(9,6),
    longitude NUMERIC(9,6),
    planned_offset_minutes INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_transport_stop_sequence UNIQUE (tenant_id, route_id, sequence_number),
    CONSTRAINT ck_transport_stop_sequence CHECK (sequence_number > 0),
    CONSTRAINT ck_transport_stop_offset CHECK (planned_offset_minutes >= 0)
);

CREATE TABLE transport_route_assignment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    route_id UUID NOT NULL REFERENCES transport_route(id),
    vehicle_id UUID NOT NULL REFERENCES transport_vehicle(id),
    driver_id UUID NOT NULL REFERENCES transport_driver(id),
    assistant_id UUID REFERENCES transport_assistant(id),
    service_date DATE NOT NULL,
    direction VARCHAR(20) NOT NULL,
    planned_departure_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_transport_assignment UNIQUE (tenant_id, route_id, service_date, direction),
    CONSTRAINT ck_transport_assignment_direction CHECK (direction IN ('MORNING','EVENING')),
    CONSTRAINT ck_transport_assignment_status CHECK (status IN ('PLANNED','READY','COMPLETED','CANCELLED'))
);

CREATE TABLE transport_student_subscription (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    student_id UUID NOT NULL REFERENCES student_profile(id),
    route_id UUID NOT NULL REFERENCES transport_route(id),
    pickup_stop_id UUID NOT NULL REFERENCES transport_stop(id),
    dropoff_stop_id UUID NOT NULL REFERENCES transport_stop(id),
    morning_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    evening_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    starts_on DATE NOT NULL,
    ends_on DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_transport_subscription UNIQUE (tenant_id, student_id, route_id, starts_on),
    CONSTRAINT ck_transport_subscription_dates CHECK (ends_on IS NULL OR ends_on >= starts_on),
    CONSTRAINT ck_transport_subscription_status CHECK (status IN ('ACTIVE','PAUSED','ENDED'))
);

CREATE TABLE transport_trip (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    assignment_id UUID NOT NULL REFERENCES transport_route_assignment(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    gps_provider_key VARCHAR(80),
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_transport_trip_assignment UNIQUE (tenant_id, assignment_id),
    CONSTRAINT ck_transport_trip_status CHECK (status IN ('PLANNED','BOARDING','IN_PROGRESS','COMPLETED','CANCELLED'))
);

CREATE TABLE transport_pickup_event (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    trip_id UUID NOT NULL REFERENCES transport_trip(id),
    subscription_id UUID NOT NULL REFERENCES transport_student_subscription(id),
    student_id UUID NOT NULL REFERENCES student_profile(id),
    stop_id UUID NOT NULL REFERENCES transport_stop(id),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_by_user_id UUID REFERENCES identity_user_account(id),
    source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    CONSTRAINT uq_transport_pickup UNIQUE (tenant_id, trip_id, student_id),
    CONSTRAINT ck_transport_pickup_source CHECK (source IN ('MANUAL','QR','NFC'))
);

CREATE TABLE transport_dropoff_event (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    trip_id UUID NOT NULL REFERENCES transport_trip(id),
    subscription_id UUID NOT NULL REFERENCES transport_student_subscription(id),
    student_id UUID NOT NULL REFERENCES student_profile(id),
    stop_id UUID NOT NULL REFERENCES transport_stop(id),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_by_user_id UUID REFERENCES identity_user_account(id),
    source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    CONSTRAINT uq_transport_dropoff UNIQUE (tenant_id, trip_id, student_id),
    CONSTRAINT ck_transport_dropoff_source CHECK (source IN ('MANUAL','QR','NFC'))
);

CREATE TABLE transport_vehicle_position (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES identity_tenant(id),
    trip_id UUID NOT NULL REFERENCES transport_trip(id),
    vehicle_id UUID NOT NULL REFERENCES transport_vehicle(id),
    provider_key VARCHAR(80) NOT NULL,
    external_position_id VARCHAR(180),
    latitude NUMERIC(9,6) NOT NULL,
    longitude NUMERIC(9,6) NOT NULL,
    speed_kph NUMERIC(6,2),
    accuracy_meters NUMERIC(8,2),
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_transport_position_lat CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_transport_position_lon CHECK (longitude BETWEEN -180 AND 180)
);

CREATE INDEX idx_transport_assignment_date ON transport_route_assignment(tenant_id, service_date, direction);
CREATE INDEX idx_transport_subscription_student ON transport_student_subscription(tenant_id, student_id, status);
CREATE INDEX idx_transport_trip_status ON transport_trip(tenant_id, status, created_at DESC);
CREATE INDEX idx_transport_pickup_trip ON transport_pickup_event(tenant_id, trip_id, occurred_at);
CREATE INDEX idx_transport_dropoff_trip ON transport_dropoff_event(tenant_id, trip_id, occurred_at);
CREATE INDEX idx_transport_position_trip ON transport_vehicle_position(tenant_id, trip_id, recorded_at DESC);
