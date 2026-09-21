ALTER TABLE app_users
    ADD COLUMN dob DATE,
    ADD COLUMN pin_code VARCHAR(12),
    ADD COLUMN shop_radius_km INTEGER;

CREATE TABLE otp_challenges (
    id VARCHAR(64) PRIMARY KEY,
    destination VARCHAR(180) NOT NULL,
    code_hash VARCHAR(100) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    purpose VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_otp_destination ON otp_challenges (destination);
CREATE INDEX idx_otp_active ON otp_challenges (destination, purpose, consumed_at);

ALTER TABLE catalog_products
    ADD COLUMN image_url TEXT;

CREATE TABLE catalog_product_gallery (
    catalog_product_id VARCHAR(64) NOT NULL REFERENCES catalog_products (id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    sort_order INTEGER NOT NULL
);

ALTER TABLE shops
    ADD COLUMN image_url TEXT;

ALTER TABLE advertisements
    ADD COLUMN image_url TEXT;

ALTER TABLE listings
    ADD COLUMN warranty VARCHAR(80);

ALTER TABLE customer_orders
    ADD COLUMN packing_by TIMESTAMPTZ,
    ADD COLUMN ready_by TIMESTAMPTZ,
    ADD COLUMN deliver_by TIMESTAMPTZ,
    ADD COLUMN payment_method VARCHAR(30),
    ADD COLUMN payment_status VARCHAR(20),
    ADD COLUMN payment_ref_id VARCHAR(80),
    ADD COLUMN discount NUMERIC(12, 2),
    ADD COLUMN coupon_code VARCHAR(40);

ALTER TABLE order_items
    ADD COLUMN warranty VARCHAR(80);

CREATE TABLE order_events (
    order_id VARCHAR(64) NOT NULL REFERENCES customer_orders (id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL,
    at TIMESTAMPTZ NOT NULL,
    sort_order INTEGER NOT NULL
);

ALTER TABLE reviews
    ADD COLUMN order_id VARCHAR(64) REFERENCES customer_orders (id),
    ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE review_images (
    review_id VARCHAR(64) NOT NULL REFERENCES reviews (id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    sort_order INTEGER NOT NULL
);

ALTER TABLE support_tickets
    ADD COLUMN assigned_to_user_id VARCHAR(64) REFERENCES app_users (id),
    ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE ticket_message_images (
    ticket_message_id VARCHAR(64) NOT NULL REFERENCES ticket_messages (id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    sort_order INTEGER NOT NULL
);
