package com.cuk.notice2action.extraction.persistence.entity;

import com.cuk.notice2action.extraction.domain.SourceCategory;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "notice_source")
public class NoticeSourceEntity {

  @Id
  private UUID id;

  @Column(name = "title")
  private String title;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_category", nullable = false, length = 40)
  private SourceCategory sourceCategory;

  @Column(name = "raw_text", nullable = false, columnDefinition = "TEXT")
  private String rawText;

  @Column(name = "source_url", columnDefinition = "TEXT")
  private String sourceUrl;

  @Column(name = "content_hash", length = 64)
  private String contentHash;

  @Column(name = "published_at")
  private LocalDate publishedAt;

  @Column(name = "external_notice_id", length = 64)
  private String externalNoticeId;

  @Column(name = "auto_collected", nullable = false)
  private boolean autoCollected;

  @Column(name = "actionability", nullable = false, length = 40)
  private String actionability = "informational";

  @Column(name = "primary_due_at")
  private OffsetDateTime primaryDueAt;

  @Column(name = "primary_due_label")
  private String primaryDueLabel;

  @Column(name = "attachments_json", nullable = false, columnDefinition = "TEXT")
  private String attachmentsJson = "[]";

  @Column(name = "notice_board_label", length = 80)
  private String noticeBoardLabel;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @OneToMany(mappedBy = "source", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ExtractedActionEntity> actions = new ArrayList<>();

  protected NoticeSourceEntity() {}

  public NoticeSourceEntity(UUID id, String title, SourceCategory sourceCategory,
      String rawText, String sourceUrl, OffsetDateTime createdAt) {
    this.id = id;
    this.title = title;
    this.sourceCategory = sourceCategory;
    this.rawText = rawText;
    this.sourceUrl = sourceUrl;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public SourceCategory getSourceCategory() {
    return sourceCategory;
  }

  public String getRawText() {
    return rawText;
  }

  public void setRawText(String rawText) {
    this.rawText = rawText;
  }

  public String getSourceUrl() {
    return sourceUrl;
  }

  public void setSourceUrl(String sourceUrl) {
    this.sourceUrl = sourceUrl;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDate getPublishedAt() {
    return publishedAt;
  }

  public void setPublishedAt(LocalDate publishedAt) {
    this.publishedAt = publishedAt;
  }

  public String getExternalNoticeId() {
    return externalNoticeId;
  }

  public void setExternalNoticeId(String externalNoticeId) {
    this.externalNoticeId = externalNoticeId;
  }

  public boolean isAutoCollected() {
    return autoCollected;
  }

  public void setAutoCollected(boolean autoCollected) {
    this.autoCollected = autoCollected;
  }

  public String getActionability() {
    return actionability;
  }

  public void setActionability(String actionability) {
    this.actionability = actionability;
  }

  public OffsetDateTime getPrimaryDueAt() {
    return primaryDueAt;
  }

  public void setPrimaryDueAt(OffsetDateTime primaryDueAt) {
    this.primaryDueAt = primaryDueAt;
  }

  public String getPrimaryDueLabel() {
    return primaryDueLabel;
  }

  public void setPrimaryDueLabel(String primaryDueLabel) {
    this.primaryDueLabel = primaryDueLabel;
  }

  public String getAttachmentsJson() {
    return attachmentsJson;
  }

  public void setAttachmentsJson(String attachmentsJson) {
    this.attachmentsJson = attachmentsJson;
  }

  public String getNoticeBoardLabel() {
    return noticeBoardLabel;
  }

  public void setNoticeBoardLabel(String noticeBoardLabel) {
    this.noticeBoardLabel = noticeBoardLabel;
  }

  public String getContentHash() {
    return contentHash;
  }

  public void setContentHash(String contentHash) {
    this.contentHash = contentHash;
  }

  public List<ExtractedActionEntity> getActions() {
    return actions;
  }
}
