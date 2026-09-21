CREATE TABLE app_users (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(180) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    shop_id VARCHAR(64),
    phone VARCHAR(30)
);

CREATE TABLE categories (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    emoji VARCHAR(16) NOT NULL
);

CREATE TABLE neighborhoods (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    area VARCHAR(120) NOT NULL,
    lat DOUBLE PRECISION NOT NULL,
    lng DOUBLE PRECISION NOT NULL
);

CREATE TABLE shops (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(180) NOT NULL,
    owner_user_id VARCHAR(64) NOT NULL REFERENCES app_users (id),
    description TEXT,
    address TEXT NOT NULL,
    lat DOUBLE PRECISION NOT NULL,
    lng DOUBLE PRECISION NOT NULL,
    rating NUMERIC(3, 2) NOT NULL DEFAULT 0,
    review_count INTEGER NOT NULL DEFAULT 0,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    gstin VARCHAR(32),
    year_started INTEGER,
    status VARCHAR(20) NOT NULL,
    partner_delivery_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    shop_delivery_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    partner_delivery_fee NUMERIC(12, 2) NOT NULL DEFAULT 0,
    shop_delivery_fee NUMERIC(12, 2) NOT NULL DEFAULT 0,
    min_order_amount NUMERIC(12, 2) NOT NULL DEFAULT 0
);

CREATE TABLE shop_categories (
    shop_id VARCHAR(64) NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    category_id VARCHAR(64) NOT NULL REFERENCES categories (id),
    PRIMARY KEY (shop_id, category_id)
);

CREATE TABLE catalog_products (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(220) NOT NULL,
    brand VARCHAR(120) NOT NULL,
    category_id VARCHAR(64) NOT NULL REFERENCES categories (id),
    description TEXT,
    unit VARCHAR(40) NOT NULL,
    image_label VARCHAR(40) NOT NULL,
    image_hue INTEGER NOT NULL
);

CREATE TABLE listings (
    id VARCHAR(64) PRIMARY KEY,
    catalog_product_id VARCHAR(64) NOT NULL REFERENCES catalog_products (id),
    shop_id VARCHAR(64) NOT NULL REFERENCES shops (id),
    base_price NUMERIC(12, 2) NOT NULL,
    seller_price NUMERIC(12, 2) NOT NULL,
    stock INTEGER NOT NULL,
    moq INTEGER NOT NULL DEFAULT 1,
    color VARCHAR(60),
    quality VARCHAR(60),
    status VARCHAR(20) NOT NULL
);

CREATE TABLE listing_tags (
    id VARCHAR(64) PRIMARY KEY,
    listing_id VARCHAR(64) NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    label VARCHAR(80) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    code VARCHAR(40),
    discount_percent INTEGER
);

CREATE TABLE partners (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    vehicle VARCHAR(60) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    available BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE customer_orders (
    id VARCHAR(64) PRIMARY KEY,
    buyer_id VARCHAR(64) NOT NULL REFERENCES app_users (id),
    shop_id VARCHAR(64) NOT NULL REFERENCES shops (id),
    delivery_mode VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    subtotal NUMERIC(12, 2) NOT NULL,
    delivery_fee NUMERIC(12, 2) NOT NULL,
    total NUMERIC(12, 2) NOT NULL,
    address TEXT NOT NULL,
    partner_id VARCHAR(64) REFERENCES partners (id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE order_items (
    id VARCHAR(64) PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL REFERENCES customer_orders (id) ON DELETE CASCADE,
    listing_id VARCHAR(64) NOT NULL REFERENCES listings (id),
    catalog_product_id VARCHAR(64) NOT NULL REFERENCES catalog_products (id),
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    delivery_mode VARCHAR(20) NOT NULL,
    delivery_fee NUMERIC(12, 2) NOT NULL
);

CREATE TABLE reviews (
    id VARCHAR(64) PRIMARY KEY,
    catalog_product_id VARCHAR(64) NOT NULL REFERENCES catalog_products (id),
    listing_id VARCHAR(64) REFERENCES listings (id),
    shop_id VARCHAR(64) NOT NULL REFERENCES shops (id),
    buyer_id VARCHAR(64) NOT NULL REFERENCES app_users (id),
    rating INTEGER NOT NULL,
    title VARCHAR(180) NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    seller_reply TEXT,
    seller_replied_at TIMESTAMPTZ
);

CREATE TABLE support_tickets (
    id VARCHAR(64) PRIMARY KEY,
    kind VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    subject VARCHAR(220) NOT NULL,
    buyer_id VARCHAR(64) REFERENCES app_users (id),
    shop_id VARCHAR(64) REFERENCES shops (id),
    order_id VARCHAR(64) REFERENCES customer_orders (id),
    listing_id VARCHAR(64) REFERENCES listings (id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE ticket_messages (
    id VARCHAR(64) PRIMARY KEY,
    ticket_id VARCHAR(64) NOT NULL REFERENCES support_tickets (id) ON DELETE CASCADE,
    author_id VARCHAR(64) NOT NULL REFERENCES app_users (id),
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE seller_applications (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL REFERENCES app_users (id),
    shop_id VARCHAR(64) NOT NULL REFERENCES shops (id),
    status VARCHAR(20) NOT NULL,
    business_name VARCHAR(180) NOT NULL,
    owner_name VARCHAR(120) NOT NULL,
    email VARCHAR(180) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    address TEXT NOT NULL,
    gstin VARCHAR(32),
    notes TEXT,
    submitted_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE application_categories (
    application_id VARCHAR(64) NOT NULL REFERENCES seller_applications (id) ON DELETE CASCADE,
    category_id VARCHAR(64) NOT NULL REFERENCES categories (id),
    PRIMARY KEY (application_id, category_id)
);

CREATE TABLE coupons (
    id VARCHAR(64) PRIMARY KEY,
    shop_id VARCHAR(64) NOT NULL REFERENCES shops (id),
    code VARCHAR(40) NOT NULL,
    label VARCHAR(120) NOT NULL,
    discount_percent INTEGER NOT NULL,
    min_order_amount NUMERIC(12, 2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (shop_id, code)
);

CREATE TABLE advertisements (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(180) NOT NULL,
    subtitle TEXT NOT NULL,
    cta VARCHAR(80) NOT NULL,
    href VARCHAR(220) NOT NULL,
    badge VARCHAR(40) NOT NULL,
    hue INTEGER NOT NULL,
    catalog_product_id VARCHAR(64) REFERENCES catalog_products (id),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE platform_settings (
    id VARCHAR(16) PRIMARY KEY,
    delivery_radius_km INTEGER NOT NULL,
    partner_eta_minutes INTEGER NOT NULL,
    show_demo_role_switcher BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_listings_shop ON listings (shop_id);
CREATE INDEX idx_listings_catalog ON listings (catalog_product_id);
CREATE INDEX idx_orders_buyer ON customer_orders (buyer_id);
CREATE INDEX idx_orders_shop ON customer_orders (shop_id);
