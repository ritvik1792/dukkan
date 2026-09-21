-- Lookup data only (no login accounts, no passwords).
-- ON CONFLICT keeps this safe if a local DB already has these rows from the old seeder.
INSERT INTO categories (id, name, emoji) VALUES
    ('grocery', 'Grocery', '🛒'),
    ('fresh', 'Fruits & veg', '🥬'),
    ('dairy', 'Dairy', '🥛'),
    ('snacks', 'Snacks', '🍪'),
    ('home', 'Home', '🏠'),
    ('electronics', 'Electronics', '🔌'),
    ('industrial', 'Industrial', '⚙️'),
    ('apparel', 'Apparel', '👕')
ON CONFLICT (id) DO NOTHING;

INSERT INTO neighborhoods (id, name, area, lat, lng) VALUES
    ('cp', 'Connaught Place', 'New Delhi', 28.6328, 77.2197),
    ('karol', 'Karol Bagh', 'Central Delhi', 28.6518, 77.1905),
    ('saket', 'Saket', 'South Delhi', 28.5244, 77.2066),
    ('noida', 'Sector 18', 'Noida', 28.5708, 77.326)
ON CONFLICT (id) DO NOTHING;

INSERT INTO platform_settings (id, delivery_radius_km, partner_eta_minutes, show_demo_role_switcher)
VALUES ('default', 5, 12, FALSE)
ON CONFLICT (id) DO NOTHING;
