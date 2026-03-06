ALTER TABLE extracted_action
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'pending';

UPDATE extracted_action
SET status = 'pending'
WHERE status IS NULL;
