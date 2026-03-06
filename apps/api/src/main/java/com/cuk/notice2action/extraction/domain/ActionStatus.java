package com.cuk.notice2action.extraction.domain;

import java.util.Locale;
import java.util.Set;

public final class ActionStatus {

  public static final String PENDING = "pending";
  public static final String COMPLETED = "completed";

  private static final Set<String> VALID_STATUSES = Set.of(PENDING, COMPLETED);

  private ActionStatus() {}

  public static String normalizeNullable(String raw, String fieldName) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    if (!VALID_STATUSES.contains(normalized)) {
      throw new IllegalArgumentException("잘못된 상태값입니다: " + fieldName + " = " + raw);
    }
    return normalized;
  }

  public static String defaultStatus(String status) {
    return status == null || status.isBlank() ? PENDING : status;
  }
}
