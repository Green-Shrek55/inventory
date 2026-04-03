DROP TABLE IF EXISTS action_logs CASCADE;
DROP TABLE IF EXISTS maintenance_tickets CASCADE;
DROP TABLE IF EXISTS equipment_items CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS employees CASCADE;
DROP TABLE IF EXISTS equipment_types CASCADE;
DROP TABLE IF EXISTS locations CASCADE;
DROP TABLE IF EXISTS departments CASCADE;

CREATE TABLE departments (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE locations (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE equipment_types (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE employees (
    id             BIGSERIAL PRIMARY KEY,
    full_name      VARCHAR(255) NOT NULL,
    department_id  BIGINT REFERENCES departments(id) ON DELETE SET NULL
);

CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    username       VARCHAR(100) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    email          VARCHAR(255) NOT NULL UNIQUE,
    role           VARCHAR(32) NOT NULL,
    department_id  BIGINT REFERENCES departments(id) ON DELETE SET NULL,
    enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    two_factor_code_hash VARCHAR(255),
    two_factor_code_expires_at TIMESTAMPTZ,
    password_reset_token VARCHAR(255),
    password_reset_token_expires_at TIMESTAMPTZ,
    password_reset_code_hash VARCHAR(255),
    password_reset_code_expires_at TIMESTAMPTZ,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    login_locked_until TIMESTAMPTZ
);

CREATE TABLE equipment_items (
    id               BIGSERIAL PRIMARY KEY,
    inventory_number VARCHAR(64) NOT NULL UNIQUE,
    name             VARCHAR(255) NOT NULL,
    type_id          BIGINT REFERENCES equipment_types(id) ON DELETE SET NULL,
    location_id      BIGINT REFERENCES locations(id) ON DELETE SET NULL,
    employee_id      BIGINT REFERENCES employees(id) ON DELETE SET NULL,
    purchase_price   NUMERIC(15,2) NOT NULL DEFAULT 0,
    purchase_date    DATE,
    archived         BOOLEAN NOT NULL DEFAULT FALSE,
    archived_at      TIMESTAMPTZ,
    last_inventory_scan_at TIMESTAMPTZ
);

CREATE TABLE maintenance_tickets (
    id            BIGSERIAL PRIMARY KEY,
    equipment_id  BIGINT NOT NULL REFERENCES equipment_items(id) ON DELETE CASCADE,
    title         VARCHAR(200) NOT NULL,
    description   TEXT,
    status        VARCHAR(32) NOT NULL DEFAULT 'NEW',
    assignee_id   BIGINT REFERENCES employees(id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at     TIMESTAMPTZ,
    created_by    VARCHAR(120),
    last_updated_by VARCHAR(120),
    resolution_note TEXT
);

CREATE TABLE action_logs (
    id      BIGSERIAL PRIMARY KEY,
    ts      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor   VARCHAR(120) NOT NULL,
    message TEXT NOT NULL
);

INSERT INTO departments (name) VALUES
    ('IT'),
    ('Finance'),
    ('Procurement')
ON CONFLICT (name) DO NOTHING;

INSERT INTO locations (name) VALUES
    ('Office 101'),
    ('Warehouse'),
    ('Meeting room')
ON CONFLICT (name) DO NOTHING;

INSERT INTO equipment_types (name) VALUES
    ('Desktop PC'),
    ('Laptop'),
    ('Printer'),
    ('Monitor')
ON CONFLICT (name) DO NOTHING;

INSERT INTO employees (full_name, department_id)
SELECT 'Ivan Ivanov (sysadmin)', (SELECT id FROM departments WHERE name = 'IT')
WHERE NOT EXISTS (SELECT 1 FROM employees WHERE full_name = 'Ivan Ivanov (sysadmin)');

INSERT INTO employees (full_name, department_id)
SELECT 'Petr Petrov (support engineer)', (SELECT id FROM departments WHERE name = 'IT')
WHERE NOT EXISTS (SELECT 1 FROM employees WHERE full_name = 'Petr Petrov (support engineer)');

INSERT INTO employees (full_name, department_id)
SELECT 'Anna Smirnova (economist)', (SELECT id FROM departments WHERE name = 'Finance')
WHERE NOT EXISTS (SELECT 1 FROM employees WHERE full_name = 'Anna Smirnova (economist)');

INSERT INTO employees (full_name, department_id)
SELECT 'Alexey Sidorov (procurement specialist)', (SELECT id FROM departments WHERE name = 'Procurement')
WHERE NOT EXISTS (SELECT 1 FROM employees WHERE full_name = 'Alexey Sidorov (procurement specialist)');

INSERT INTO users (username, password_hash, email, role, department_id, enabled) VALUES
    ('admin', '$2a$10$iCHSknnT.NlWopUcHUZS4u0vB8/LBkasS5P8zYB2VL.saDtlPo81m', 'andrejkuzevonov@gmail.com', 'ADMIN',
     (SELECT id FROM departments WHERE name = 'IT'), TRUE),
    ('it', '$2a$10$Q/qmyXyOaxVWquw1royiq.MFP9qRg6JRzVVMmpU60HLKV6GElRnJC', 'leninklitorochek@gmail.com', 'IT',
     (SELECT id FROM departments WHERE name = 'IT'), TRUE),
    ('economist', '$2a$10$AziuFyTDXOKaW/bu/nto7OW35B5Q44m2KPAeyyo83zzxeDVKfNr3O', 'economist@example.com', 'ECONOMIST',
     (SELECT id FROM departments WHERE name = 'Finance'), TRUE)
ON CONFLICT (username) DO NOTHING;

INSERT INTO equipment_items (inventory_number, name, type_id, location_id, employee_id, purchase_price, purchase_date)
VALUES
    ('PC-0001', 'Lenovo workstation',
        (SELECT id FROM equipment_types WHERE name = 'Desktop PC'),
        (SELECT id FROM locations WHERE name = 'Office 101'),
        (SELECT id FROM employees WHERE full_name LIKE 'Ivan Ivanov%'),
        125000, CURRENT_DATE - INTERVAL '6 months'),
    ('NB-0002', 'Dell Latitude laptop',
        (SELECT id FROM equipment_types WHERE name = 'Laptop'),
        (SELECT id FROM locations WHERE name = 'Meeting room'),
        (SELECT id FROM employees WHERE full_name LIKE 'Petr Petrov%'),
        98000, CURRENT_DATE - INTERVAL '3 months'),
    ('PR-0003', 'HP LaserJet printer',
        (SELECT id FROM equipment_types WHERE name = 'Printer'),
        (SELECT id FROM locations WHERE name = 'Office 101'),
        (SELECT id FROM employees WHERE full_name LIKE 'Anna Smirnova%'),
        45000, CURRENT_DATE - INTERVAL '12 months'),
    ('MN-0004', 'LG 27 monitor',
        (SELECT id FROM equipment_types WHERE name = 'Monitor'),
        (SELECT id FROM locations WHERE name = 'Warehouse'),
        NULL,
        32000, CURRENT_DATE - INTERVAL '8 months'),
    ('NB-0005', 'Procurement laptop',
        (SELECT id FROM equipment_types WHERE name = 'Laptop'),
        (SELECT id FROM locations WHERE name = 'Office 101'),
        (SELECT id FROM employees WHERE full_name LIKE 'Alexey Sidorov%'),
        76000, CURRENT_DATE - INTERVAL '2 months')
ON CONFLICT (inventory_number) DO NOTHING;

INSERT INTO action_logs (actor, message) VALUES
    ('system', 'Initial reference data created'),
    ('system', 'Default users admin/it/economist created');

INSERT INTO maintenance_tickets (equipment_id, title, description, status, created_by, last_updated_by)
SELECT id, 'Initialization', 'Example maintenance ticket', 'IN_PROGRESS', 'system', 'system'
FROM equipment_items
WHERE inventory_number = 'PC-0001'
  AND NOT EXISTS (SELECT 1 FROM maintenance_tickets WHERE title = 'Initialization');
