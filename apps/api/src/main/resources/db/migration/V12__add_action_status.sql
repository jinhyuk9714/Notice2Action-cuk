ALTER TABLE extracted_action
  ADD COLUMN status TEXT NOT NULL DEFAULT 'pending';
