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

  public SourceCategory getSourceCategory() {
    return sourceCategory;
  }

  public String getRawText() {
    return rawText;
  }

  public String getSourceUrl() {
    return sourceUrl;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
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
