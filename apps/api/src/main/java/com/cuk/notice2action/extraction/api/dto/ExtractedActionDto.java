package com.cuk.notice2action.extraction.api.dto;

import com.cuk.notice2action.extraction.domain.SourceCategory;
import com.cuk.notice2action.extraction.service.model.StructuredEligibility;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ExtractedActionDto(
    UUID id,
    UUID sourceId,
    String title,
    String actionSummary,
    String dueAtIso,
    String dueAtLabel,
    List<AdditionalDateDto> additionalDates,
    String eligibility,
    StructuredEligibility structuredEligibility,
    List<String> requiredItems,
    String systemHint,
    SourceCategory sourceCategory,
    List<EvidenceSnippetDto> evidence,
    boolean inferred,
    double confidenceScore,
    OffsetDateTime createdAt
) {
  public ExtractedActionDto {
    additionalDates = additionalDates == null ? List.of() : List.copyOf(additionalDates);
    requiredItems = requiredItems == null ? List.of() : List.copyOf(requiredItems);
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
  }

  public ExtractedActionDto(
      UUID id,
      UUID sourceId,
      String title,
      String actionSummary,
      String dueAtIso,
      String dueAtLabel,
      String eligibility,
      List<String> requiredItems,
      String systemHint,
      SourceCategory sourceCategory,
      List<EvidenceSnippetDto> evidence,
      boolean inferred,
      double confidenceScore,
      OffsetDateTime createdAt
  ) {
    this(
        id,
        sourceId,
        title,
        actionSummary,
        dueAtIso,
        dueAtLabel,
        List.of(),
        eligibility,
        null,
        requiredItems,
        systemHint,
        sourceCategory,
        evidence,
        inferred,
        confidenceScore,
        createdAt
    );
  }
}
