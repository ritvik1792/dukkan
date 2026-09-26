-- No restaurant catalogue. Drop the unused category so it cannot reappear.
DELETE FROM shop_categories WHERE category_id = 'restaurants';
DELETE FROM application_categories WHERE category_id = 'restaurants';
UPDATE services SET category_id = NULL WHERE category_id = 'restaurants';
DELETE FROM categories WHERE id = 'restaurants';
