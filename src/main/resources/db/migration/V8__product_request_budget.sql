-- Optional buyer budget on product availability requests

ALTER TABLE product_requests
    ADD COLUMN IF NOT EXISTS max_budget NUMERIC(12, 2);
