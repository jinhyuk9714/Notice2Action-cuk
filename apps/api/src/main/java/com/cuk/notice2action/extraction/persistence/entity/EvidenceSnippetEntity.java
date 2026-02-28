package com.cuk.notice2action.extraction.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "evidence_snippet")
public class EvidenceSnippetEntity {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "action_id", nullable = false)
  private ExtractedActionEntity action;

  @Column(name = "field_name", nullable = false, length = 100)
  private String fieldName;

  @Column(name = "snippet", nullable = false, columnDefinition = "TEXT")
  private String snippet;

  @Column(name = "confidence", nullable = false)
  private double confidence;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected EvidenceSnippetEntity() {}

  public EvidenceSnippetEntity(UUID id, ExtractedActionEntity action,
      String fieldName, String snippet, double confidence, OffsetDateTime createdAt) {
    this.id = id;
    this.action = action;
    this.fieldName = fieldName;
    this.snippet = snippet;
    this.confidence = confidence;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public ExtractedActionEntity getAction() {
    return action;
  }

  void setAction(ExtractedActionEntity action) {
    this.action = action;
  }

  public String getFieldName() {
    return fieldName;
  }

  public String getSnippet() {
    return snippet;
  }

  public double getConfidence() {
    return confidence;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
