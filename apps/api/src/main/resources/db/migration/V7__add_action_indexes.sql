-- Index for listing actions ordered by creation time (newest first).
CREATE INDEX idx_action_created_at ON extracted_action(created_at DESC);
