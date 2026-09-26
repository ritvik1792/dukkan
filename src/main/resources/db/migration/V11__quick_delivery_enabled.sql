-- Platform switch for Dukkan quick delivery (partner riders). Off until an admin turns it on.

ALTER TABLE platform_settings
    ADD COLUMN IF NOT EXISTS quick_delivery_enabled BOOLEAN NOT NULL DEFAULT FALSE;
