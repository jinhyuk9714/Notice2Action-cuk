ALTER TABLE notice_source
  ADD COLUMN IF NOT EXISTS published_at DATE,
  ADD COLUMN IF NOT EXISTS external_notice_id VARCHAR(64),
  ADD COLUMN IF NOT EXISTS auto_collected BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS actionability VARCHAR(40) NOT NULL DEFAULT 'informational',
  ADD COLUMN IF NOT EXISTS primary_due_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS primary_due_label VARCHAR(255),
  ADD COLUMN IF NOT EXISTS attachments_json TEXT NOT NULL DEFAULT '[]';

CREATE UNIQUE INDEX IF NOT EXISTS ux_notice_source_external_notice_id
  ON notice_source (external_notice_id)
  WHERE external_notice_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_notice_source_auto_collected_created_at
  ON notice_source (auto_collected, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notice_source_primary_due_at
  ON notice_source (primary_due_at);
