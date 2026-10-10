CREATE TABLE cafeteria_meal_plan (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL REFERENCES identity_tenant(id), code VARCHAR(60) NOT NULL,
    name VARCHAR(180) NOT NULL, plan_type VARCHAR(20) NOT NULL, monthly_price NUMERIC(14,2),
    single_meal_price NUMERIC(14,2), active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_meal_plan UNIQUE(tenant_id,code),
    CONSTRAINT ck_meal_plan_type CHECK(plan_type IN('MONTHLY','SINGLE','HYBRID'))
);
CREATE TABLE cafeteria_menu (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL REFERENCES identity_tenant(id), service_date DATE NOT NULL,
    meal_type VARCHAR(20) NOT NULL, title VARCHAR(180) NOT NULL, description VARCHAR(1000),
    published BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_cafeteria_menu UNIQUE(tenant_id,service_date,meal_type),
    CONSTRAINT ck_menu_meal_type CHECK(meal_type IN('BREAKFAST','LUNCH','SNACK'))
);
CREATE TABLE cafeteria_allergen (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL REFERENCES identity_tenant(id), code VARCHAR(60) NOT NULL,
    label_fr VARCHAR(180) NOT NULL, label_ar VARCHAR(180), active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_cafeteria_allergen UNIQUE(tenant_id,code)
);
CREATE TABLE cafeteria_food_restriction (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL REFERENCES identity_tenant(id), student_id UUID NOT NULL REFERENCES student_profile(id),
    restriction_type VARCHAR(30) NOT NULL, allergen_id UUID REFERENCES cafeteria_allergen(id), details VARCHAR(1000),
    severity VARCHAR(20) NOT NULL, emergency_action VARCHAR(1000), verified_by VARCHAR(180), verified_at TIMESTAMP WITH TIME ZONE,
    active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_food_restriction_type CHECK(restriction_type IN('ALLERGY','INTOLERANCE','RELIGIOUS','MEDICAL','PREFERENCE')),
    CONSTRAINT ck_food_restriction_severity CHECK(severity IN('LOW','MEDIUM','HIGH','CRITICAL'))
);
CREATE TABLE cafeteria_meal_subscription (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL REFERENCES identity_tenant(id), student_id UUID NOT NULL REFERENCES student_profile(id),
    meal_plan_id UUID NOT NULL REFERENCES cafeteria_meal_plan(id), starts_on DATE NOT NULL, ends_on DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', billing_mode VARCHAR(20) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_meal_subscription UNIQUE(tenant_id,student_id,meal_plan_id,starts_on),
    CONSTRAINT ck_meal_subscription_status CHECK(status IN('ACTIVE','PAUSED','ENDED')),
    CONSTRAINT ck_meal_billing_mode CHECK(billing_mode IN('MONTHLY','PER_MEAL'))
);
CREATE TABLE cafeteria_meal_attendance (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL REFERENCES identity_tenant(id), student_id UUID NOT NULL REFERENCES student_profile(id),
    menu_id UUID NOT NULL REFERENCES cafeteria_menu(id), subscription_id UUID REFERENCES cafeteria_meal_subscription(id),
    attendance_type VARCHAR(20) NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_by_user_id UUID REFERENCES identity_user_account(id),
    CONSTRAINT uq_meal_attendance UNIQUE(tenant_id,student_id,menu_id),
    CONSTRAINT ck_meal_attendance_type CHECK(attendance_type IN('SUBSCRIPTION','ONE_OFF'))
);
CREATE TABLE cafeteria_meal_charge (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL REFERENCES identity_tenant(id), student_id UUID NOT NULL REFERENCES student_profile(id),
    subscription_id UUID REFERENCES cafeteria_meal_subscription(id), attendance_id UUID REFERENCES cafeteria_meal_attendance(id),
    billing_charge_id UUID NOT NULL REFERENCES billing_charge(id), charge_type VARCHAR(20) NOT NULL,
    amount NUMERIC(14,2) NOT NULL, charged_on DATE NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_meal_charge_type CHECK(charge_type IN('MONTHLY','ONE_OFF')),
    CONSTRAINT ck_meal_charge_amount CHECK(amount>=0)
);
CREATE INDEX idx_meal_subscription_student ON cafeteria_meal_subscription(tenant_id,student_id,status);
CREATE INDEX idx_meal_attendance_date ON cafeteria_meal_attendance(tenant_id,menu_id,occurred_at);
CREATE INDEX idx_food_restriction_student ON cafeteria_food_restriction(tenant_id,student_id,active);
