-- Ops comment on a join request: what to change, shown to the seller on the tracker.

ALTER TABLE seller_applications
    ADD COLUMN review_note TEXT;
