DROP TABLE IF EXISTS action_logs CASCADE;
DROP TABLE IF EXISTS disposal_scans CASCADE;
DROP TABLE IF EXISTS disposal_sessions CASCADE;
DROP TABLE IF EXISTS inventory_scans CASCADE;
DROP TABLE IF EXISTS inventory_sessions CASCADE;
DROP TABLE IF EXISTS equipment_items CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS employees CASCADE;
DROP TABLE IF EXISTS equipment_types CASCADE;
DROP TABLE IF EXISTS locations CASCADE;
DROP TABLE IF EXISTS buildings CASCADE;
DROP TABLE IF EXISTS departments CASCADE;

CREATE TABLE departments (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE buildings (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE locations (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    building_id BIGINT NOT NULL REFERENCES buildings(id) ON DELETE CASCADE,
    type        VARCHAR(32) NOT NULL,
    CONSTRAINT uq_locations_building_name_type UNIQUE (building_id, name, type)
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

CREATE TABLE inventory_sessions (
    id            BIGSERIAL PRIMARY KEY,
    started_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at   TIMESTAMPTZ,
    status        VARCHAR(32) NOT NULL,
    location_id   BIGINT REFERENCES locations(id) ON DELETE SET NULL,
    scanned_count INTEGER NOT NULL DEFAULT 0,
    started_by    VARCHAR(120),
    finished_by   VARCHAR(120)
);

CREATE TABLE inventory_scans (
    id               BIGSERIAL PRIMARY KEY,
    session_id        BIGINT NOT NULL REFERENCES inventory_sessions(id) ON DELETE CASCADE,
    equipment_item_id BIGINT REFERENCES equipment_items(id) ON DELETE SET NULL,
    inventory_number  VARCHAR(64),
    equipment_name    VARCHAR(255),
    type_name         VARCHAR(255),
    location_name     VARCHAR(255),
    assigned_person   VARCHAR(255),
    scanned_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE disposal_sessions (
    id            BIGSERIAL PRIMARY KEY,
    started_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at   TIMESTAMPTZ,
    status        VARCHAR(32) NOT NULL,
    building_id   BIGINT REFERENCES buildings(id) ON DELETE SET NULL,
    seal_number   VARCHAR(128),
    scanned_count INTEGER NOT NULL DEFAULT 0,
    started_by    VARCHAR(120),
    finished_by   VARCHAR(120)
);

CREATE TABLE disposal_scans (
    id               BIGSERIAL PRIMARY KEY,
    session_id        BIGINT NOT NULL REFERENCES disposal_sessions(id) ON DELETE CASCADE,
    equipment_id      BIGINT NOT NULL REFERENCES equipment_items(id) ON DELETE CASCADE,
    inventory_number  VARCHAR(64) NOT NULL,
    equipment_name    VARCHAR(255) NOT NULL,
    type_name         VARCHAR(255) NOT NULL,
    location_name     VARCHAR(255) NOT NULL,
    scanned_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_disposal_session_equipment UNIQUE (session_id, equipment_id)
);

CREATE TABLE action_logs (
    id      BIGSERIAL PRIMARY KEY,
    ts      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor   VARCHAR(120) NOT NULL,
    message TEXT NOT NULL
);

INSERT INTO departments (name) VALUES
    ('Администрация'),
    ('Финансовая служба'),
    ('Материальный учет')
ON CONFLICT (name) DO NOTHING;

INSERT INTO buildings (name) VALUES
    ('Нахимовский пр. 21'),
    ('Нежинская ул. 7')
ON CONFLICT (name) DO NOTHING;

INSERT INTO locations (name, building_id, type) VALUES
    ('Склад', (SELECT id FROM buildings WHERE name = 'Нахимовский пр. 21'), 'WAREHOUSE'),
    ('Склад', (SELECT id FROM buildings WHERE name = 'Нежинская ул. 7'), 'WAREHOUSE')
ON CONFLICT (building_id, name, type) DO NOTHING;

INSERT INTO locations (name, building_id, type)
SELECT 'Кабинет ' || gs::text, (SELECT id FROM buildings WHERE name = 'Нахимовский пр. 21'), 'CABINET'
FROM generate_series(1, 300) AS gs
ON CONFLICT (building_id, name, type) DO NOTHING;

INSERT INTO locations (name, building_id, type)
SELECT 'Кабинет ' || gs::text, (SELECT id FROM buildings WHERE name = 'Нежинская ул. 7'), 'CABINET'
FROM generate_series(1, 300) AS gs
ON CONFLICT (building_id, name, type) DO NOTHING;

INSERT INTO equipment_types (name) VALUES
    ('Настольный ПК'),
    ('Ноутбук'),
    ('Принтер'),
    ('Монитор')
ON CONFLICT (name) DO NOTHING;

INSERT INTO employees (full_name, department_id)
SELECT 'Иванов Иван (материально ответственное лицо)', (SELECT id FROM departments WHERE name = 'Материальный учет')
WHERE NOT EXISTS (SELECT 1 FROM employees WHERE full_name = 'Иванов Иван (материально ответственное лицо)');

INSERT INTO employees (full_name, department_id)
SELECT 'Смирнова Анна (экономист)', (SELECT id FROM departments WHERE name = 'Финансовая служба')
WHERE NOT EXISTS (SELECT 1 FROM employees WHERE full_name = 'Смирнова Анна (экономист)');

INSERT INTO users (username, password_hash, email, role, department_id, enabled) VALUES
    ('admin', '$2a$10$iCHSknnT.NlWopUcHUZS4u0vB8/LBkasS5P8zYB2VL.saDtlPo81m', 'andrejkuzevonov@gmail.com', 'ADMIN',
     (SELECT id FROM departments WHERE name = 'Администрация'), TRUE),
    ('warehouse', '$2a$10$Q/qmyXyOaxVWquw1royiq.MFP9qRg6JRzVVMmpU60HLKV6GElRnJC', 'warehouse@example.com', 'WAREHOUSE',
     (SELECT id FROM departments WHERE name = 'Материальный учет'), TRUE),
    ('economist', '$2a$10$AziuFyTDXOKaW/bu/nto7OW35B5Q44m2KPAeyyo83zzxeDVKfNr3O', 'economist@example.com', 'ECONOMIST',
     (SELECT id FROM departments WHERE name = 'Финансовая служба'), TRUE)
ON CONFLICT (username) DO NOTHING;

INSERT INTO equipment_items (inventory_number, name, type_id, location_id, employee_id, purchase_price, purchase_date)
VALUES
    ('LO-00000001', 'Рабочая станция Lenovo',
        (SELECT id FROM equipment_types WHERE name = 'Настольный ПК'),
        (SELECT l.id FROM locations l JOIN buildings b ON b.id = l.building_id WHERE l.name = 'Кабинет 101' AND b.name = 'Нахимовский пр. 21' AND l.type = 'CABINET'),
        (SELECT id FROM employees WHERE full_name LIKE 'Иванов Иван%'),
        125000, CURRENT_DATE - INTERVAL '6 months'),
    ('LO-00000002', 'Ноутбук Dell Latitude',
        (SELECT id FROM equipment_types WHERE name = 'Ноутбук'),
        (SELECT l.id FROM locations l JOIN buildings b ON b.id = l.building_id WHERE l.name = 'Кабинет 205' AND b.name = 'Нахимовский пр. 21' AND l.type = 'CABINET'),
        NULL,
        98000, CURRENT_DATE - INTERVAL '3 months'),
    ('LO-00000003', 'Принтер HP LaserJet',
        (SELECT id FROM equipment_types WHERE name = 'Принтер'),
        (SELECT l.id FROM locations l JOIN buildings b ON b.id = l.building_id WHERE l.name = 'Кабинет 101' AND b.name = 'Нахимовский пр. 21' AND l.type = 'CABINET'),
        (SELECT id FROM employees WHERE full_name LIKE 'Смирнова Анна%'),
        45000, CURRENT_DATE - INTERVAL '12 months'),
    ('LO-00000004', 'Монитор LG 27',
        (SELECT id FROM equipment_types WHERE name = 'Монитор'),
        (SELECT l.id FROM locations l JOIN buildings b ON b.id = l.building_id WHERE l.name = 'Склад' AND b.name = 'Нахимовский пр. 21' AND l.type = 'WAREHOUSE'),
        NULL,
        32000, CURRENT_DATE - INTERVAL '8 months')
ON CONFLICT (inventory_number) DO NOTHING;

INSERT INTO action_logs (actor, message) VALUES
    ('system', 'Initial reference data created'),
    ('system', 'Default users admin/warehouse/economist created');
