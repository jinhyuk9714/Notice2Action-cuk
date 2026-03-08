CREATE TABLE notice_feed_sync_state (
  feed_key VARCHAR(80) PRIMARY KEY,
  state VARCHAR(20) NOT NULL,
  last_successful_sync_at TIMESTAMPTZ NULL,
  last_attempted_sync_at TIMESTAMPTZ NULL,
  last_error_message TEXT NULL,
  notice_count BIGINT NOT NULL DEFAULT 0
);
