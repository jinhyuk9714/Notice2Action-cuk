ALTER TABLE extracted_action
    ADD COLUMN additional_dates_json TEXT NOT NULL DEFAULT '[]';
