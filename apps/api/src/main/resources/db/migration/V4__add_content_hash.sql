ALTER TABLE notice_source ADD COLUMN content_hash VARCHAR(64);
CREATE INDEX idx_notice_source_content_hash ON notice_source(content_hash);
CREATE INDEX idx_notice_source_source_url ON notice_source(source_url);
