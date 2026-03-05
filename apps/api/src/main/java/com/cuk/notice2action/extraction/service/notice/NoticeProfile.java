package com.cuk.notice2action.extraction.service.notice;

import java.util.List;

public record NoticeProfile(
    String department,
    Integer year,
    String status,
    List<String> keywords
) {
  public NoticeProfile {
    keywords = keywords == null ? List.of() : List.copyOf(keywords);
  }

  public boolean isConfigured() {
    return hasText(department) || year != null || hasText(status) || !keywords.isEmpty();
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
