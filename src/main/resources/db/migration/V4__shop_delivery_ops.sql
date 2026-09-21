ALTER TABLE shops
    ADD COLUMN is_open BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN open_time VARCHAR(8) NOT NULL DEFAULT '09:00',
    ADD COLUMN close_time VARCHAR(8) NOT NULL DEFAULT '21:00';

CREATE TABLE shop_employees (
    id VARCHAR(64) PRIMARY KEY,
    shop_id VARCHAR(64) NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    role VARCHAR(20) NOT NULL,
    phone VARCHAR(30),
    available BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_shop_employees_shop ON shop_employees (shop_id);

CREATE TABLE shop_transport (
    id VARCHAR(64) PRIMARY KEY,
    shop_id VARCHAR(64) NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    kind VARCHAR(20) NOT NULL,
    label VARCHAR(80) NOT NULL,
    registration VARCHAR(40),
    capacity_kg INTEGER,
    available BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_shop_transport_shop ON shop_transport (shop_id);

INSERT INTO shop_employees (id, shop_id, name, role, phone, available)
SELECT v.id, v.shop_id, v.name, v.role, v.phone, v.available
FROM (
    VALUES
        ('emp-gupta-1', 'shop-gupta', 'Amit Yadav', 'RIDER', '9811100101', TRUE),
        ('emp-gupta-2', 'shop-gupta', 'Suresh Pal', 'RIDER', '9811100102', TRUE),
        ('emp-gupta-3', 'shop-gupta', 'Pooja Devi', 'PACKER', '9811100103', TRUE)
) AS v(id, shop_id, name, role, phone, available)
WHERE EXISTS (SELECT 1 FROM shops s WHERE s.id = v.shop_id)
ON CONFLICT (id) DO NOTHING;

INSERT INTO shop_transport (id, shop_id, kind, label, registration, capacity_kg, available)
SELECT v.id, v.shop_id, v.kind, v.label, v.registration, v.capacity_kg, v.available
FROM (
    VALUES
        ('veh-gupta-1', 'shop-gupta', 'BIKE', 'Hero Splendor', 'DL 1S AB 4412', 15, TRUE),
        ('veh-gupta-2', 'shop-gupta', 'BIKE', 'Bajaj Platina', 'DL 1S AB 4413', 12, TRUE),
        ('veh-gupta-3', 'shop-gupta', 'CYCLE', 'Shop cycle', NULL, 8, TRUE)
) AS v(id, shop_id, kind, label, registration, capacity_kg, available)
WHERE EXISTS (SELECT 1 FROM shops s WHERE s.id = v.shop_id)
ON CONFLICT (id) DO NOTHING;

INSERT INTO partners (id, name, vehicle, phone, available)
VALUES ('ptn-arjun', 'Arjun Tempo', 'Tempo', '9000000003', TRUE)
ON CONFLICT (id) DO NOTHING;
