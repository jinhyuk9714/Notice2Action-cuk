package com.cuk.notice2action.extraction.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "extracted_action")
public class ExtractedActionEntity {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_id")
  private NoticeSourceEntity source;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "action_summary", nullable = false, columnDefinition = "TEXT")
  private String actionSummary;

  @Column(name = "due_at_iso")
  private OffsetDateTime dueAtIso;

  @Column(name = "due_at_label")
  private String dueAtLabel;

  @Column(name = "eligibility", columnDefinition = "TEXT")
  private String eligibility;

  @Column(name = "required_items_json", nullable = false, columnDefinition = "TEXT")
  private String requiredItemsJson;

  @Column(name = "system_hint")
  private String systemHint;

  @Column(name = "inferred", nullable = false)
  private boolean inferred;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @OneToMany(mappedBy = "action", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<EvidenceSnippetEntity> evidenceSnippets = new ArrayList<>();

  protected ExtractedActionEntity() {}

  public ExtractedActionEntity(UUID id, NoticeSourceEntity source, String title,
      String actionSummary, OffsetDateTime dueAtIso, String dueAtLabel,
      String eligibility, String requiredItemsJson, String systemHint,
      boolean inferred, OffsetDateTime createdAt) {
    this.id = id;
    this.source = source;
    this.title = title;
    this.actionSummary = actionSummary;
    this.dueAtIso = dueAtIso;
    this.dueAtLabel = dueAtLabel;
    this.eligibility = eligibility;
    this.requiredItemsJson = requiredItemsJson;
    this.systemHint = systemHint;
    this.inferred = inferred;
    this.createdAt = createdAt;
  }

  public void addEvidence(EvidenceSnippetEntity evidence) {
    evidenceSnippets.add(evidence);
    evidence.setAction(this);
  }

  public UUID getId() {
    return id;
  }

  public NoticeSourceEntity getSource() {
    return source;
  }

  public String getTitle() {
    return title;
  }

  public String getActionSummary() {
    return actionSummary;
  }

  public OffsetDateTime getDueAtIso() {
    return dueAtIso;
  }

  public String getDueAtLabel() {
    return dueAtLabel;
  }

  public String getEligibility() {
    return eligibility;
  }

  public String getRequiredItemsJson() {
    return requiredItemsJson;
  }

  public String getSystemHint() {
    return systemHint;
  }

  public boolean isInferred() {
    return inferred;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public List<EvidenceSnippetEntity> getEvidenceSnippets() {
    return evidenceSnippets;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public void setActionSummary(String actionSummary) {
    this.actionSummary = actionSummary;
  }

  public void setDueAtIso(OffsetDateTime dueAtIso) {
    this.dueAtIso = dueAtIso;
  }

  public void setDueAtLabel(String dueAtLabel) {
    this.dueAtLabel = dueAtLabel;
  }

  public void setEligibility(String eligibility) {
    this.eligibility = eligibility;
  }

  public void setRequiredItemsJson(String requiredItemsJson) {
    this.requiredItemsJson = requiredItemsJson;
  }

  public void setSystemHint(String systemHint) {
    this.systemHint = systemHint;
  }
}
