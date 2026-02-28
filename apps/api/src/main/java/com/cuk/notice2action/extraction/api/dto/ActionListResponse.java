package com.cuk.notice2action.extraction.api.dto;

import java.util.List;

public record ActionListResponse(List<SavedActionSummaryDto> actions) {
  public ActionListResponse {
    actions = actions == null ? List.of() : List.copyOf(actions);
  }
}
