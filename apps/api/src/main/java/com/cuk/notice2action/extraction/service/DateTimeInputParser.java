package com.cuk.notice2action.extraction.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class DateTimeInputParser {

  private static final ZoneOffset APP_OFFSET = ZoneOffset.ofHours(9);

  private DateTimeInputParser() {
  }

  public static OffsetDateTime parse(String raw, String fieldName, boolean endOfDay) {
    if (raw == null || raw.isBlank()) {
      return null;
    }

    String input = raw.trim();

    try {
      return OffsetDateTime.parse(input);
    } catch (Exception ignored) {
      // fallback below
    }

    try {
      LocalDate localDate = LocalDate.parse(input);
      LocalTime localTime = endOfDay ? LocalTime.MAX : LocalTime.MIN;
      return localDate.atTime(localTime).atOffset(APP_OFFSET);
    } catch (Exception ignored) {
      // fallback below
    }

    try {
      return LocalDateTime.parse(input).atOffset(APP_OFFSET);
    } catch (Exception ignored) {
      throw new IllegalArgumentException(
          "잘못된 날짜 형식입니다: " + fieldName + " = " + raw
      );
    }
  }
}
