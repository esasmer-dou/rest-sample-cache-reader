-- Demo-only initialization, executed by PostgreSQL on a NEW named volume.
-- Existing volumes are not migrated or reset automatically.
CREATE TABLE IF NOT EXISTS sample_customers (
    id bigserial PRIMARY KEY,
    customer_no varchar(32) NOT NULL UNIQUE,
    full_name varchar(128) NOT NULL,
    segment varchar(32) NOT NULL,
    email varchar(160) NOT NULL DEFAULT '',
    status varchar(32) NOT NULL DEFAULT 'active',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_sample_customers_segment ON sample_customers(segment, id);
CREATE INDEX IF NOT EXISTS idx_sample_customers_status ON sample_customers(status, id);
INSERT INTO sample_customers (customer_no, full_name, segment, email, status) VALUES
    ('CUST-1001', 'Compose Seed Customer', 'pilot', 'seed@example.invalid', 'active'),
    ('CUST-1002', 'Compose Enterprise Customer', 'enterprise', 'enterprise@example.invalid', 'active'),
    ('CUST-1003', 'Compose Passive Customer', 'standard', 'passive@example.invalid', 'passive')
ON CONFLICT (customer_no) DO NOTHING;
