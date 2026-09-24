-- V10: Add source ('M' = mobile, 'O' = online/web) and last_updated timestamp tracking across tables

ALTER TABLE catalog_products
    ADD COLUMN IF NOT EXISTS source VARCHAR(1) NOT NULL DEFAULT 'O',
    ADD COLUMN IF NOT EXISTS last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD CONSTRAINT chk_catalog_products_source CHECK (source IN ('M', 'O'));

ALTER TABLE listings
    ADD COLUMN IF NOT EXISTS source VARCHAR(1) NOT NULL DEFAULT 'O',
    ADD COLUMN IF NOT EXISTS last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD CONSTRAINT chk_listings_source CHECK (source IN ('M', 'O'));

ALTER TABLE customer_orders
    ADD COLUMN IF NOT EXISTS source VARCHAR(1) NOT NULL DEFAULT 'O',
    ADD COLUMN IF NOT EXISTS last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD CONSTRAINT chk_customer_orders_source CHECK (source IN ('M', 'O'));

ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS source VARCHAR(1) NOT NULL DEFAULT 'O',
    ADD COLUMN IF NOT EXISTS last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD CONSTRAINT chk_order_items_source CHECK (source IN ('M', 'O'));

ALTER TABLE shops
    ADD COLUMN IF NOT EXISTS source VARCHAR(1) NOT NULL DEFAULT 'O',
    ADD COLUMN IF NOT EXISTS last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD CONSTRAINT chk_shops_source CHECK (source IN ('M', 'O'));

ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS source VARCHAR(1) NOT NULL DEFAULT 'O',
    ADD COLUMN IF NOT EXISTS last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD CONSTRAINT chk_app_users_source CHECK (source IN ('M', 'O'));

ALTER TABLE support_tickets
    ADD COLUMN IF NOT EXISTS source VARCHAR(1) NOT NULL DEFAULT 'O',
    ADD COLUMN IF NOT EXISTS last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD CONSTRAINT chk_support_tickets_source CHECK (source IN ('M', 'O'));

ALTER TABLE ticket_messages
    ADD COLUMN IF NOT EXISTS source VARCHAR(1) NOT NULL DEFAULT 'O',
    ADD COLUMN IF NOT EXISTS last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD CONSTRAINT chk_ticket_messages_source CHECK (source IN ('M', 'O'));

ALTER TABLE seller_applications
    ADD COLUMN IF NOT EXISTS source VARCHAR(1) NOT NULL DEFAULT 'O',
    ADD COLUMN IF NOT EXISTS last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD CONSTRAINT chk_seller_applications_source CHECK (source IN ('M', 'O'));

ALTER TABLE reviews
    ADD COLUMN IF NOT EXISTS source VARCHAR(1) NOT NULL DEFAULT 'O',
    ADD COLUMN IF NOT EXISTS last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD CONSTRAINT chk_reviews_source CHECK (source IN ('M', 'O'));

ALTER TABLE product_requests
    ADD COLUMN IF NOT EXISTS source VARCHAR(1) NOT NULL DEFAULT 'O',
    ADD COLUMN IF NOT EXISTS last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD CONSTRAINT chk_product_requests_source CHECK (source IN ('M', 'O'));

ALTER TABLE offers
    ADD COLUMN IF NOT EXISTS source VARCHAR(1) NOT NULL DEFAULT 'O',
    ADD COLUMN IF NOT EXISTS last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD CONSTRAINT chk_offers_source CHECK (source IN ('M', 'O'));

ALTER TABLE services
    ADD COLUMN IF NOT EXISTS source VARCHAR(1) NOT NULL DEFAULT 'O',
    ADD COLUMN IF NOT EXISTS last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD CONSTRAINT chk_services_source CHECK (source IN ('M', 'O'));

ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS source VARCHAR(1) NOT NULL DEFAULT 'O',
    ADD COLUMN IF NOT EXISTS last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD CONSTRAINT chk_bookings_source CHECK (source IN ('M', 'O'));

ALTER TABLE service_requests
    ADD COLUMN IF NOT EXISTS source VARCHAR(1) NOT NULL DEFAULT 'O',
    ADD COLUMN IF NOT EXISTS last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD CONSTRAINT chk_service_requests_source CHECK (source IN ('M', 'O'));

ALTER TABLE coupons
    ADD COLUMN IF NOT EXISTS source VARCHAR(1) NOT NULL DEFAULT 'O',
    ADD COLUMN IF NOT EXISTS last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD CONSTRAINT chk_coupons_source CHECK (source IN ('M', 'O'));

ALTER TABLE advertisements
    ADD COLUMN IF NOT EXISTS source VARCHAR(1) NOT NULL DEFAULT 'O',
    ADD COLUMN IF NOT EXISTS last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD CONSTRAINT chk_advertisements_source CHECK (source IN ('M', 'O'));

ALTER TABLE platform_settings
    ADD COLUMN IF NOT EXISTS source VARCHAR(1) NOT NULL DEFAULT 'O',
    ADD COLUMN IF NOT EXISTS last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD CONSTRAINT chk_platform_settings_source CHECK (source IN ('M', 'O'));
