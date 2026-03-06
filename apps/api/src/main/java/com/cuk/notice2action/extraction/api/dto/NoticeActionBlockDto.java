package com.cuk.notice2action.extraction.api.dto;

import java.util.List;

public record NoticeActionBlockDto(
    String title,
    String summary,
    String dueAtIso,
    String dueAtLabel,
    List<String> requiredItems,
    String systemHint,
    List<EvidenceSnippetDto> evidence,
    double confidenceScore
) {
  public NoticeActionBlockDto {
    requiredItems = requiredItems == null ? List.of() : List.copyOf(requiredItems);
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
  }
}
