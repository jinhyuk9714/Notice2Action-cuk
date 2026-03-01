package com.cuk.notice2action.extraction.api.dto;

import com.cuk.notice2action.extraction.domain.SourceCategory;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SourceDetailDto(
    UUID id,
    String title,
    SourceCategory sourceCategory,
    String sourceUrl,
    OffsetDateTime createdAt,
    List<SavedActionSummaryDto> actions
) {
  public SourceDetailDto {
    actions = actions == null ? List.of() : List.copyOf(actions);
  }
}
