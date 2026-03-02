ALTER TABLE extracted_action
  ADD COLUMN machine_values_json TEXT NOT NULL DEFAULT '{}';
