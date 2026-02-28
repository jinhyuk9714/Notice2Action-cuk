package com.cuk.notice2action.extraction.api.dto;

import java.util.List;

public record ActionExtractionResponse(List<ExtractedActionDto> actions) {
  public ActionExtractionResponse {
    actions = actions == null ? List.of() : List.copyOf(actions);
  }
}
