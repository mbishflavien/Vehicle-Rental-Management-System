-- VRMS relational schema (PostgreSQL).
--
-- Written to be idempotent: on an empty database it creates everything; on a database created by
-- earlier versions of VRMS (Hibernate ddl-auto) it adds what is missing and replaces outdated
-- constraints, without touching existing rows. Hibernate only validates the schema afterwards.

-- ---------------------------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS users (
    user_id         uuid            PRIMARY KEY,
    full_name       varchar(255)    NOT NULL,
    email           varchar(255)    NOT NULL,
    password_hash   varchar(255)    NOT NULL,
    role            varchar(20)     NOT NULL,
    job_title       varchar(255),
    created_at      timestamptz
);
ALTER TABLE users ADD COLUMN IF NOT EXISTS enabled        boolean     NOT NULL DEFAULT true;
ALTER TABLE users ADD COLUMN IF NOT EXISTS auth_provider  varchar(20) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at  timestamptz;
ALTER TABLE users ADD COLUMN IF NOT EXISTS job_title      varchar(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS created_at     timestamptz;

CREATE TABLE IF NOT EXISTS branches (
    branch_id       uuid            PRIMARY KEY,
    name            varchar(80)     NOT NULL,
    city            varchar(60)     NOT NULL,
    address         varchar(150),
    phone_number    varchar(20)
);

CREATE TABLE IF NOT EXISTS customers (
    customer_id             uuid            PRIMARY KEY,
    full_name               varchar(100)    NOT NULL,
    email                   varchar(255)    NOT NULL,
    phone_number            varchar(255),
    driver_license_number   varchar(255)    NOT NULL,
    created_at              timestamptz
);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS user_id    uuid;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS created_at timestamptz;

CREATE TABLE IF NOT EXISTS vehicles (
    vehicle_id      uuid            PRIMARY KEY,
    plate_number    varchar(255)    NOT NULL,
    model           varchar(80)     NOT NULL,
    daily_rate      double precision NOT NULL,
    vehicle_status  varchar(20)
);
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS category      varchar(20);
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS transmission  varchar(20);
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS fuel_type     varchar(20);
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS seats         integer;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS image_url     varchar(1000);
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS branch_id     uuid;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS created_at    timestamptz;

CREATE TABLE IF NOT EXISTS rental_contracts (
    contract_id     uuid            PRIMARY KEY,
    customer_id     uuid            NOT NULL,
    vehicle_id      uuid            NOT NULL,
    start_date      date            NOT NULL,
    end_date        date            NOT NULL,
    total_cost      double precision,
    contract_status varchar(20)
);
ALTER TABLE rental_contracts ADD COLUMN IF NOT EXISTS pickup_branch_id uuid;
ALTER TABLE rental_contracts ADD COLUMN IF NOT EXISTS issued_by        uuid;
ALTER TABLE rental_contracts ADD COLUMN IF NOT EXISTS created_at       timestamptz;

-- ---------------------------------------------------------------------------------------------
-- Domain rules as CHECK constraints (recreated so older, narrower versions are replaced)
-- ---------------------------------------------------------------------------------------------

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD  CONSTRAINT users_role_check CHECK (role IN ('ADMIN', 'AGENT', 'CUSTOMER'));
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_auth_provider_check;
ALTER TABLE users ADD  CONSTRAINT users_auth_provider_check CHECK (auth_provider IN ('LOCAL', 'GOOGLE', 'GITHUB'));

-- BR-02: driver licenses start with DL-
ALTER TABLE customers DROP CONSTRAINT IF EXISTS ck_customers_license_format;
ALTER TABLE customers ADD  CONSTRAINT ck_customers_license_format CHECK (driver_license_number ~ '^DL-[A-Z0-9-]+$');

-- BR-04: Rwandan plates, stored without spaces (RAB123A); BR-03: positive daily rate
ALTER TABLE vehicles DROP CONSTRAINT IF EXISTS ck_vehicles_plate_format;
ALTER TABLE vehicles ADD  CONSTRAINT ck_vehicles_plate_format CHECK (plate_number ~ '^RA[A-Z][0-9]{3}[A-Z]$');
ALTER TABLE vehicles DROP CONSTRAINT IF EXISTS ck_vehicles_daily_rate_positive;
ALTER TABLE vehicles ADD  CONSTRAINT ck_vehicles_daily_rate_positive CHECK (daily_rate > 0);
ALTER TABLE vehicles DROP CONSTRAINT IF EXISTS vehicles_seats_check;
ALTER TABLE vehicles ADD  CONSTRAINT vehicles_seats_check CHECK (seats IS NULL OR seats BETWEEN 1 AND 60);
ALTER TABLE vehicles DROP CONSTRAINT IF EXISTS vehicles_vehicle_status_check;
ALTER TABLE vehicles ADD  CONSTRAINT vehicles_vehicle_status_check CHECK (vehicle_status IN ('AVAILABLE', 'RENTED', 'MAINTENANCE', 'RESERVED'));
ALTER TABLE vehicles DROP CONSTRAINT IF EXISTS vehicles_category_check;
ALTER TABLE vehicles ADD  CONSTRAINT vehicles_category_check CHECK (category IN ('SUV', 'SEDAN', 'HATCHBACK', 'VAN', 'COMMERCIAL'));
ALTER TABLE vehicles DROP CONSTRAINT IF EXISTS vehicles_transmission_check;
ALTER TABLE vehicles ADD  CONSTRAINT vehicles_transmission_check CHECK (transmission IN ('AUTOMATIC', 'MANUAL'));
ALTER TABLE vehicles DROP CONSTRAINT IF EXISTS vehicles_fuel_type_check;
ALTER TABLE vehicles ADD  CONSTRAINT vehicles_fuel_type_check CHECK (fuel_type IN ('PETROL', 'DIESEL', 'HYBRID', 'ELECTRIC'));

-- A rental ends after it starts; money is never negative
ALTER TABLE rental_contracts DROP CONSTRAINT IF EXISTS ck_contracts_dates;
ALTER TABLE rental_contracts ADD  CONSTRAINT ck_contracts_dates CHECK (end_date > start_date);
ALTER TABLE rental_contracts DROP CONSTRAINT IF EXISTS ck_contracts_total_cost;
ALTER TABLE rental_contracts ADD  CONSTRAINT ck_contracts_total_cost CHECK (total_cost IS NULL OR total_cost >= 0);
ALTER TABLE rental_contracts DROP CONSTRAINT IF EXISTS rental_contracts_contract_status_check;
ALTER TABLE rental_contracts ADD  CONSTRAINT rental_contracts_contract_status_check CHECK (contract_status IN ('PENDING', 'ACTIVE', 'COMPLETED', 'CANCELLED'));

-- ---------------------------------------------------------------------------------------------
-- Uniqueness (BR-01: no duplicate customers). Emails are unique regardless of letter case.
-- ---------------------------------------------------------------------------------------------

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_email_ci          ON users (lower(email));
CREATE UNIQUE INDEX IF NOT EXISTS uk_customers_email_ci      ON customers (lower(email));
CREATE UNIQUE INDEX IF NOT EXISTS uk_customers_license       ON customers (driver_license_number);
CREATE UNIQUE INDEX IF NOT EXISTS uk_customers_user          ON customers (user_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_vehicles_plate          ON vehicles (plate_number);
CREATE UNIQUE INDEX IF NOT EXISTS uk_branches_name_ci        ON branches (lower(name));

-- ---------------------------------------------------------------------------------------------
-- Foreign keys (added only if the column has none yet, so older databases don't get duplicates)
-- ---------------------------------------------------------------------------------------------

DO $$
DECLARE
    fk record;
BEGIN
    FOR fk IN SELECT * FROM (VALUES
        ('customers',        'user_id',          'users',     'user_id',     'fk_customers_user',          'SET NULL'),
        ('vehicles',         'branch_id',        'branches',  'branch_id',   'fk_vehicles_branch',         'SET NULL'),
        ('rental_contracts', 'customer_id',      'customers', 'customer_id', 'fk_contracts_customer',      'RESTRICT'),
        ('rental_contracts', 'vehicle_id',       'vehicles',  'vehicle_id',  'fk_contracts_vehicle',       'RESTRICT'),
        ('rental_contracts', 'pickup_branch_id', 'branches',  'branch_id',   'fk_contracts_pickup_branch', 'SET NULL'),
        ('rental_contracts', 'issued_by',        'users',     'user_id',     'fk_contracts_issued_by',     'SET NULL')
    ) AS t(tbl, col, ref_tbl, ref_col, name, on_delete)
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint c
            JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
            WHERE c.contype = 'f' AND c.conrelid = fk.tbl::regclass AND a.attname = fk.col
        ) THEN
            EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (%I) REFERENCES %I (%I) ON DELETE %s',
                           fk.tbl, fk.name, fk.col, fk.ref_tbl, fk.ref_col, fk.on_delete);
        END IF;
    END LOOP;
END $$;

-- ---------------------------------------------------------------------------------------------
-- Indexes for the application's queries
-- ---------------------------------------------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_vehicles_status        ON vehicles (vehicle_status);
CREATE INDEX IF NOT EXISTS idx_vehicles_branch        ON vehicles (branch_id);
CREATE INDEX IF NOT EXISTS idx_contracts_status       ON rental_contracts (contract_status);
CREATE INDEX IF NOT EXISTS idx_contracts_created_at   ON rental_contracts (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_contracts_customer     ON rental_contracts (customer_id);
CREATE INDEX IF NOT EXISTS idx_contracts_vehicle      ON rental_contracts (vehicle_id);
-- "Does this vehicle/customer have an open booking?" only ever looks at PENDING/ACTIVE rows
CREATE INDEX IF NOT EXISTS idx_contracts_open_vehicle  ON rental_contracts (vehicle_id)  WHERE contract_status IN ('PENDING', 'ACTIVE');
CREATE INDEX IF NOT EXISTS idx_contracts_open_customer ON rental_contracts (customer_id) WHERE contract_status IN ('PENDING', 'ACTIVE');
CREATE INDEX IF NOT EXISTS idx_users_role             ON users (role);
