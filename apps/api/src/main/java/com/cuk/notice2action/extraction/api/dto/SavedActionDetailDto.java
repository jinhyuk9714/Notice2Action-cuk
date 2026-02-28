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
    OffsetDateTime createdAt,
    SourceInfoDto source,
    List<EvidenceSnippetDto> evidence
) {
  public SavedActionDetailDto {
    requiredItems = requiredItems == null ? List.of() : List.copyOf(requiredItems);
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
  }
}
