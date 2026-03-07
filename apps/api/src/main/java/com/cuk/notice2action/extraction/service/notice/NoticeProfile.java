package com.cuk.notice2action.extraction.service.notice;

import java.util.List;

public record NoticeProfile(
    String department,
    Integer year,
    String status,
    List<String> keywords,
    List<String> preferredBoards
) {
  public NoticeProfile {
    keywords = keywords == null ? List.of() : List.copyOf(keywords);
    preferredBoards = preferredBoards == null ? List.of() : List.copyOf(preferredBoards);
  }

  public NoticeProfile(String department, Integer year, String status, List<String> keywords) {
    this(department, year, status, keywords, List.of());
  }

  public boolean isConfigured() {
    return hasText(department) || year != null || hasText(status) || !keywords.isEmpty() || !preferredBoards.isEmpty();
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
