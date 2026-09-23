-- GreenOwl: extend shops into providers with capability flags;
-- add first-class services, bookings, and service requests.

ALTER TABLE shops
    ADD COLUMN IF NOT EXISTS provider_type VARCHAR(30) NOT NULL DEFAULT 'PRODUCT_BUSINESS',
    ADD COLUMN IF NOT EXISTS products_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS services_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS bookings_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS service_requests_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS orders_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS quick_delivery_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS verification_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',
    ADD COLUMN IF NOT EXISTS service_area TEXT,
    ADD COLUMN IF NOT EXISTS profession VARCHAR(120);

-- Existing Dukkan shops remain product providers; quick delivery stays off until admin enables it.
UPDATE shops
SET provider_type = COALESCE(provider_type, 'PRODUCT_BUSINESS'),
    products_allowed = TRUE,
    orders_allowed = TRUE,
    services_allowed = FALSE,
    bookings_allowed = FALSE,
    service_requests_allowed = FALSE,
    quick_delivery_allowed = FALSE,
    verification_status = COALESCE(verification_status, 'UNVERIFIED');

ALTER TABLE categories
    ADD COLUMN IF NOT EXISTS kind VARCHAR(20) NOT NULL DEFAULT 'PRODUCT';

UPDATE categories SET kind = 'PRODUCT' WHERE kind IS NULL OR kind = '';

INSERT INTO categories (id, name, emoji, kind) VALUES
    ('fashion', 'Fashion', '👗', 'PRODUCT'),
    ('salon', 'Salon', '💇', 'SERVICE'),
    ('home-repair', 'Home Repair', '🔧', 'SERVICE'),
    ('ac-repair', 'AC Repair', '❄️', 'SERVICE'),
    ('beauty', 'Beauty', '✨', 'SERVICE'),
    ('fitness', 'Fitness', '💪', 'SERVICE'),
    ('tutors', 'Tutors', '📚', 'SERVICE'),
    ('restaurants', 'Restaurants', '🍽️', 'BOTH'),
    ('auto-services', 'Auto Services', '🚗', 'SERVICE')
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    emoji = EXCLUDED.emoji,
    kind = EXCLUDED.kind;

CREATE TABLE IF NOT EXISTS services (
    id VARCHAR(64) PRIMARY KEY,
    provider_id VARCHAR(64) NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    name VARCHAR(220) NOT NULL,
    description TEXT,
    category_id VARCHAR(64) REFERENCES categories (id),
    price NUMERIC(12, 2),
    starting_price NUMERIC(12, 2),
    duration_minutes INTEGER,
    service_area TEXT,
    booking_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    request_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    image_url TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_services_provider ON services (provider_id);
CREATE INDEX IF NOT EXISTS idx_services_category ON services (category_id);
CREATE INDEX IF NOT EXISTS idx_services_status ON services (status);

CREATE TABLE IF NOT EXISTS service_images (
    service_id VARCHAR(64) NOT NULL REFERENCES services (id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    sort_order INTEGER NOT NULL,
    PRIMARY KEY (service_id, sort_order)
);

CREATE TABLE IF NOT EXISTS bookings (
    id VARCHAR(64) PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL REFERENCES app_users (id),
    provider_id VARCHAR(64) NOT NULL REFERENCES shops (id),
    service_id VARCHAR(64) NOT NULL REFERENCES services (id),
    scheduled_start TIMESTAMPTZ NOT NULL,
    scheduled_end TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_bookings_customer ON bookings (customer_id, created_at);
CREATE INDEX IF NOT EXISTS idx_bookings_provider_status ON bookings (provider_id, status);
CREATE INDEX IF NOT EXISTS idx_bookings_service ON bookings (service_id);

CREATE TABLE IF NOT EXISTS service_requests (
    id VARCHAR(64) PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL REFERENCES app_users (id),
    provider_id VARCHAR(64) NOT NULL REFERENCES shops (id),
    service_id VARCHAR(64) NOT NULL REFERENCES services (id),
    customer_address TEXT NOT NULL,
    customer_lat DOUBLE PRECISION,
    customer_lng DOUBLE PRECISION,
    description TEXT,
    preferred_time TIMESTAMPTZ,
    contact_phone VARCHAR(30),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_service_requests_customer ON service_requests (customer_id, created_at);
CREATE INDEX IF NOT EXISTS idx_service_requests_provider_status ON service_requests (provider_id, status);
CREATE INDEX IF NOT EXISTS idx_service_requests_service ON service_requests (service_id);
