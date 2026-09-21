-- Product availability request / temporary merchant offer foundation

ALTER TABLE platform_settings
    ADD COLUMN IF NOT EXISTS request_response_window_seconds INTEGER NOT NULL DEFAULT 120,
    ADD COLUMN IF NOT EXISTS request_wave_size INTEGER NOT NULL DEFAULT 5,
    ADD COLUMN IF NOT EXISTS request_max_shops INTEGER NOT NULL DEFAULT 20,
    ADD COLUMN IF NOT EXISTS offer_expiry_seconds INTEGER NOT NULL DEFAULT 900,
    ADD COLUMN IF NOT EXISTS request_max_waves INTEGER NOT NULL DEFAULT 3;

ALTER TABLE shops
    ADD COLUMN IF NOT EXISTS notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS notify_order_received BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS notify_order_status BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS notify_stock_confirmation BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE listings
    ADD COLUMN IF NOT EXISTS availability_confirmed_at TIMESTAMPTZ;

CREATE TABLE product_requests (
    id VARCHAR(64) PRIMARY KEY,
    buyer_id VARCHAR(64) NOT NULL REFERENCES app_users (id),
    catalog_product_id VARCHAR(64) NOT NULL REFERENCES catalog_products (id),
    listing_id VARCHAR(64) REFERENCES listings (id),
    query_text TEXT,
    buyer_lat DOUBLE PRECISION NOT NULL,
    buyer_lng DOUBLE PRECISION NOT NULL,
    status VARCHAR(30) NOT NULL,
    wave_index INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE request_shops (
    id VARCHAR(64) PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL REFERENCES product_requests (id) ON DELETE CASCADE,
    shop_id VARCHAR(64) NOT NULL REFERENCES shops (id),
    listing_id VARCHAR(64) REFERENCES listings (id),
    wave_index INTEGER NOT NULL,
    rank_score NUMERIC(12, 4) NOT NULL DEFAULT 0,
    distance_km NUMERIC(12, 4),
    status VARCHAR(30) NOT NULL,
    notified_at TIMESTAMPTZ,
    responded_at TIMESTAMPTZ,
    response_latency_ms BIGINT,
    UNIQUE (request_id, shop_id)
);

CREATE TABLE offers (
    id VARCHAR(64) PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL REFERENCES product_requests (id) ON DELETE CASCADE,
    request_shop_id VARCHAR(64) NOT NULL REFERENCES request_shops (id) ON DELETE CASCADE,
    shop_id VARCHAR(64) NOT NULL REFERENCES shops (id),
    listing_id VARCHAR(64) REFERENCES listings (id),
    unit_price NUMERIC(12, 2) NOT NULL,
    available_qty INTEGER NOT NULL,
    message TEXT,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE request_events (
    id VARCHAR(64) PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL REFERENCES product_requests (id) ON DELETE CASCADE,
    request_shop_id VARCHAR(64) REFERENCES request_shops (id) ON DELETE SET NULL,
    offer_id VARCHAR(64) REFERENCES offers (id) ON DELETE SET NULL,
    actor_user_id VARCHAR(64) REFERENCES app_users (id),
    event_type VARCHAR(60) NOT NULL,
    payload JSONB,
    created_at TIMESTAMPTZ NOT NULL
);

ALTER TABLE customer_orders
    ADD COLUMN IF NOT EXISTS request_id VARCHAR(64) REFERENCES product_requests (id),
    ADD COLUMN IF NOT EXISTS offer_id VARCHAR(64) REFERENCES offers (id);

CREATE INDEX idx_product_requests_buyer_status_created
    ON product_requests (buyer_id, status, created_at);

CREATE INDEX idx_request_shops_request_shop_status
    ON request_shops (request_id, shop_id, status);

CREATE INDEX idx_offers_request_shop_expires
    ON offers (request_id, shop_id, expires_at);

CREATE INDEX idx_request_events_request_created
    ON request_events (request_id, created_at);
