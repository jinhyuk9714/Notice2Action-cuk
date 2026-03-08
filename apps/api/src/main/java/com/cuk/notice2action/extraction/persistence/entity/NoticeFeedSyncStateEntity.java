package com.cuk.notice2action.extraction.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "notice_feed_sync_state")
public class NoticeFeedSyncStateEntity {

  @Id
  @Column(name = "feed_key", nullable = false, length = 80)
  private String feedKey;

  @Column(name = "state", nullable = false, length = 20)
  private String state;

  @Column(name = "last_successful_sync_at")
  private OffsetDateTime lastSuccessfulSyncAt;

  @Column(name = "last_attempted_sync_at")
  private OffsetDateTime lastAttemptedSyncAt;

  @Column(name = "last_error_message")
  private String lastErrorMessage;

  @Column(name = "notice_count", nullable = false)
  private long noticeCount;

  protected NoticeFeedSyncStateEntity() {}

  public NoticeFeedSyncStateEntity(String feedKey, String state) {
    this.feedKey = feedKey;
    this.state = state;
  }

  public String getFeedKey() {
    return feedKey;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public OffsetDateTime getLastSuccessfulSyncAt() {
    return lastSuccessfulSyncAt;
  }

  public void setLastSuccessfulSyncAt(OffsetDateTime lastSuccessfulSyncAt) {
    this.lastSuccessfulSyncAt = lastSuccessfulSyncAt;
  }

  public OffsetDateTime getLastAttemptedSyncAt() {
    return lastAttemptedSyncAt;
  }

  public void setLastAttemptedSyncAt(OffsetDateTime lastAttemptedSyncAt) {
    this.lastAttemptedSyncAt = lastAttemptedSyncAt;
  }

  public String getLastErrorMessage() {
    return lastErrorMessage;
  }

  public void setLastErrorMessage(String lastErrorMessage) {
    this.lastErrorMessage = lastErrorMessage;
  }

  public long getNoticeCount() {
    return noticeCount;
  }

  public void setNoticeCount(long noticeCount) {
    this.noticeCount = noticeCount;
  }
}
