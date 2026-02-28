package com.cuk.notice2action.extraction.api.dto;

import com.cuk.notice2action.extraction.domain.SourceCategory;
import java.util.List;

public record ActionExtractionRequest(
    String sourceText,
    String sourceUrl,
    String sourceTitle,
    SourceCategory sourceCategory,
    List<String> focusProfile
) {
  public ActionExtractionRequest {
    sourceCategory = sourceCategory == null ? SourceCategory.NOTICE : sourceCategory;
    focusProfile = focusProfile == null ? List.of() : List.copyOf(focusProfile);
  }
}
