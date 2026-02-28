CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS notice_source (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  title VARCHAR(255),
  source_category VARCHAR(40) NOT NULL,
  raw_text TEXT NOT NULL,
  source_url TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS extracted_action (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  source_id UUID NULL REFERENCES notice_source(id) ON DELETE CASCADE,
  title VARCHAR(255) NOT NULL,
  action_summary TEXT NOT NULL,
  due_at_iso TIMESTAMPTZ NULL,
  due_at_label VARCHAR(255),
  eligibility TEXT,
  required_items_json TEXT NOT NULL DEFAULT '[]',
  system_hint VARCHAR(100),
  inferred BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
