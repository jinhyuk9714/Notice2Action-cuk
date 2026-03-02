-- Index for looking up actions by source (used by source detail and duplicate detection).
CREATE INDEX idx_extracted_action_source_id ON extracted_action(source_id);
