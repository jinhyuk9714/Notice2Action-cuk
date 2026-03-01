package com.cuk.notice2action.extraction.api.dto;

import java.util.List;

public record SourceListResponse(
    List<SourceSummaryDto> sources,
    int currentPage,
    int pageSize,
    long totalElements,
    int totalPages,
    boolean hasNext
) {
  public SourceListResponse {
    sources = sources == null ? List.of() : List.copyOf(sources);
  }
}
