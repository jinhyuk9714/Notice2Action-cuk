package com.cuk.notice2action.extraction.service.model;

import java.util.List;

public record StructuredEligibility(
    boolean universal,
    List<String> statuses,
    List<String> excludedStatuses,
    List<Integer> years,
    String department
) {
  public StructuredEligibility {
    statuses = statuses == null ? List.of() : List.copyOf(statuses);
    excludedStatuses = excludedStatuses == null ? List.of() : List.copyOf(excludedStatuses);
    years = years == null ? List.of() : List.copyOf(years);
  }
}
