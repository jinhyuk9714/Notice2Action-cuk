-- Make content_hash unique to prevent concurrent duplicate inserts.
-- Filtered index: allows multiple NULLs while enforcing uniqueness for non-null values.
DROP INDEX IF EXISTS idx_notice_source_content_hash;
CREATE UNIQUE INDEX idx_notice_source_content_hash_unique
  ON notice_source(content_hash)
  WHERE content_hash IS NOT NULL;
