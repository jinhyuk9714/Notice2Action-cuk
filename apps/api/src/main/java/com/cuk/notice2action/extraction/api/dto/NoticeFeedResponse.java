package com.cuk.notice2action.extraction.api.dto;

import java.util.List;

public record NoticeFeedResponse(
    List<PersonalizedNoticeSummaryDto> notices,
    int currentPage,
    int pageSize,
    long totalElements,
    int totalPages,
    boolean hasNext,
    List<String> availableBoards
) {
  public NoticeFeedResponse {
    notices = notices == null ? List.of() : List.copyOf(notices);
    availableBoards = availableBoards == null ? List.of() : List.copyOf(availableBoards);
  }

  public NoticeFeedResponse(
      List<PersonalizedNoticeSummaryDto> notices,
      int currentPage,
      int pageSize,
      long totalElements,
      int totalPages,
      boolean hasNext
  ) {
    this(notices, currentPage, pageSize, totalElements, totalPages, hasNext, List.of());
  }
}
