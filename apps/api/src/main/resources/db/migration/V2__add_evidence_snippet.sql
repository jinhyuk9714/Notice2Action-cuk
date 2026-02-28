CREATE TABLE evidence_snippet (
  id            UUID              PRIMARY KEY DEFAULT gen_random_uuid(),
  action_id     UUID              NOT NULL REFERENCES extracted_action(id) ON DELETE CASCADE,
  field_name    VARCHAR(100)      NOT NULL,
  snippet       TEXT              NOT NULL,
  confidence    DOUBLE PRECISION  NOT NULL DEFAULT 0.0,
  created_at    TIMESTAMPTZ       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_evidence_snippet_action_id ON evidence_snippet(action_id);
