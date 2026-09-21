CREATE TABLE shop_partners (
    shop_id VARCHAR(64) NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    partner_id VARCHAR(64) NOT NULL REFERENCES partners (id) ON DELETE CASCADE,
    PRIMARY KEY (shop_id, partner_id)
);

CREATE INDEX idx_shop_partners_partner ON shop_partners (partner_id);

INSERT INTO partners (id, name, vehicle, phone, available)
VALUES
    ('ptn-ravi', 'Ravi Singh', 'Bike', '9000000001', TRUE),
    ('ptn-neha', 'Neha Das', 'Scooter', '9000000002', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO shop_partners (shop_id, partner_id)
SELECT 'shop-gupta', p.id
FROM partners p
WHERE p.id IN ('ptn-ravi', 'ptn-neha')
  AND EXISTS (SELECT 1 FROM shops s WHERE s.id = 'shop-gupta')
ON CONFLICT DO NOTHING;
