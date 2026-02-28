package com.cuk.notice2action.extraction.api.dto;

import com.cuk.notice2action.extraction.domain.SourceCategory;
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
    String eligibility,
    List<String> requiredItems,
    String systemHint,
    SourceCategory sourceCategory,
    List<EvidenceSnippetDto> evidence,
    boolean inferred,
    OffsetDateTime createdAt
) {
  public ExtractedActionDto {
    requiredItems = requiredItems == null ? List.of() : List.copyOf(requiredItems);
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
  }
}
