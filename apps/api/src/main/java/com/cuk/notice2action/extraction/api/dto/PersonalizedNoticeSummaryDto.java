package com.cuk.notice2action.extraction.api.dto;

import java.util.List;
import java.util.UUID;

public record PersonalizedNoticeSummaryDto(
    UUID id,
    String title,
    String publishedAt,
    String sourceUrl,
    String boardLabel,
    List<String> importanceReasons,
    String actionability,
    NoticeDueHintDto dueHint,
    int relevanceScore
) {
  public PersonalizedNoticeSummaryDto {
    importanceReasons = importanceReasons == null ? List.of() : List.copyOf(importanceReasons);
  }
}
