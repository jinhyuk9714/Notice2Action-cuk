package com.cuk.notice2action.extraction.api.dto;

import java.util.List;

public record NoticeFeedResponse(
    List<PersonalizedNoticeSummaryDto> notices,
    int currentPage,
    int pageSize,
    long totalElements,
    int totalPages,
    boolean hasNext
) {
  public NoticeFeedResponse {
    notices = notices == null ? List.of() : List.copyOf(notices);
  }
}
