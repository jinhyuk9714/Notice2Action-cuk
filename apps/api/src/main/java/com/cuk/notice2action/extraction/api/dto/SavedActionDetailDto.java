package com.cuk.notice2action.extraction.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SavedActionDetailDto(
    UUID id,
    String title,
    String actionSummary,
    String dueAtIso,
    String dueAtLabel,
    String eligibility,
    List<String> requiredItems,
    String systemHint,
    boolean inferred,
    double confidenceScore,
    OffsetDateTime createdAt,
    SourceInfoDto source,
    List<EvidenceSnippetDto> evidence,
    List<FieldOverrideInfoDto> overrides,
    String status
) {
  public SavedActionDetailDto {
    requiredItems = requiredItems == null ? List.of() : List.copyOf(requiredItems);
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
    overrides = overrides == null ? List.of() : List.copyOf(overrides);
  }
}
