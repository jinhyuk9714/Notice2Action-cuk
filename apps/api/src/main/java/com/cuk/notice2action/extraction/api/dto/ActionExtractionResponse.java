package com.cuk.notice2action.extraction.api.dto;

import java.util.List;

public record ActionExtractionResponse(List<ExtractedActionDto> actions, boolean duplicate) {
  public ActionExtractionResponse(List<ExtractedActionDto> actions) {
    this(actions, false);
  }

  public ActionExtractionResponse {
    actions = actions == null ? List.of() : List.copyOf(actions);
  }
}
