ALTER TABLE shops
    ADD COLUMN service_radius_km INTEGER NOT NULL DEFAULT 25;

ALTER TABLE customer_orders
    ADD COLUMN buyer_lat DOUBLE PRECISION,
    ADD COLUMN buyer_lng DOUBLE PRECISION,
    ADD COLUMN buyer_pin VARCHAR(12);

ALTER TABLE reviews
    ALTER COLUMN catalog_product_id DROP NOT NULL,
    ALTER COLUMN body DROP NOT NULL,
    ADD COLUMN service_id VARCHAR(64),
    ADD COLUMN target_kind VARCHAR(16) NOT NULL DEFAULT 'PRODUCT',
    ADD COLUMN verified_purchase BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN product_quality INTEGER,
    ADD COLUMN shop_experience INTEGER,
    ADD COLUMN staff_score INTEGER;
