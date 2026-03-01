package com.cuk.notice2action.extraction.api.dto;

import java.util.List;

public record ActionListResponse(
    List<SavedActionSummaryDto> actions,
    int currentPage,
    int pageSize,
    long totalElements,
    int totalPages,
    boolean hasNext
) {
  public ActionListResponse {
    actions = actions == null ? List.of() : List.copyOf(actions);
  }
}
