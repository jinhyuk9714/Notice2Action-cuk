package com.cuk.notice2action.extraction.api.dto;

import java.util.List;
import java.util.UUID;

public record PersonalizedNoticeDetailDto(
    UUID id,
    String title,
    String publishedAt,
    String sourceUrl,
    List<String> importanceReasons,
    String actionability,
    NoticeDueHintDto dueHint,
    int relevanceScore,
    String body,
    List<NoticeAttachmentDto> attachments,
    List<NoticeActionBlockDto> actionBlocks
) {
  public PersonalizedNoticeDetailDto {
    importanceReasons = importanceReasons == null ? List.of() : List.copyOf(importanceReasons);
    attachments = attachments == null ? List.of() : List.copyOf(attachments);
    actionBlocks = actionBlocks == null ? List.of() : List.copyOf(actionBlocks);
  }
}
