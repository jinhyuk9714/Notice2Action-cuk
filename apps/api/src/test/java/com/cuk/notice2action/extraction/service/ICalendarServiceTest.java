package com.cuk.notice2action.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cuk.notice2action.extraction.persistence.entity.ExtractedActionEntity;
import com.cuk.notice2action.extraction.persistence.entity.NoticeSourceEntity;
import com.cuk.notice2action.extraction.domain.SourceCategory;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ICalendarServiceTest {

  private final ICalendarService service = new ICalendarService();

  private ExtractedActionEntity createAction(String title, String summary,
      OffsetDateTime dueAt, String systemHint) {
    NoticeSourceEntity source = new NoticeSourceEntity(
        UUID.randomUUID(), "테스트 소스", SourceCategory.NOTICE,
        "원문 텍스트", null, OffsetDateTime.now()
    );
    return new ExtractedActionEntity(
        UUID.randomUUID(), source, title, summary,
        dueAt, dueAt != null ? dueAt.toLocalDate().toString() : null,
        "재학생", "[]", systemHint, false, 0.0, OffsetDateTime.now()
    );
  }

  @Test
  void generateCalendar_produces_valid_vcalendar_wrapper() {
    String result = service.generateCalendar(List.of());

    assertThat(result).startsWith("BEGIN:VCALENDAR\r\n");
    assertThat(result).endsWith("END:VCALENDAR\r\n");
    assertThat(result).contains("VERSION:2.0");
    assertThat(result).contains("PRODID:-//Notice2Action CUK//V2//KO");
    assertThat(result).contains("CALSCALE:GREGORIAN");
  }

  @Test
  void generateCalendar_empty_list_has_no_vevent() {
    String result = service.generateCalendar(List.of());

    assertThat(result).doesNotContain("BEGIN:VEVENT");
  }

  @Test
  void generateCalendar_includes_vevent_for_action_with_due_date() {
    OffsetDateTime dueAt = OffsetDateTime.of(2026, 3, 15, 18, 0, 0, 0, ZoneOffset.ofHours(9));
    ExtractedActionEntity action = createAction("장학금 신청", "교내 장학금 신청", dueAt, "TRINITY");

    String result = service.generateCalendar(List.of(action));

    assertThat(result).contains("BEGIN:VEVENT");
    assertThat(result).contains("SUMMARY:장학금 신청");
    assertThat(result).contains("DESCRIPTION:교내 장학금 신청");
    assertThat(result).contains("LOCATION:TRINITY");
    assertThat(result).contains("UID:" + action.getId() + "@notice2action.cuk");
    assertThat(result).contains("DTSTART:");
    assertThat(result).contains("DTEND:");
    assertThat(result).contains("END:VEVENT");
  }

  @Test
  void generateCalendar_skips_actions_without_due_date() {
    ExtractedActionEntity withDue = createAction("마감 있는 액션", "요약",
        OffsetDateTime.of(2026, 3, 15, 18, 0, 0, 0, ZoneOffset.ofHours(9)), null);
    ExtractedActionEntity withoutDue = createAction("마감 없는 액션", "요약", null, null);

    String result = service.generateCalendar(List.of(withDue, withoutDue));

    assertThat(result).contains("SUMMARY:마감 있는 액션");
    assertThat(result).doesNotContain("SUMMARY:마감 없는 액션");
  }

  @Test
  void generateCalendar_includes_three_valarm_reminders() {
    OffsetDateTime dueAt = OffsetDateTime.of(2026, 3, 15, 18, 0, 0, 0, ZoneOffset.ofHours(9));
    ExtractedActionEntity action = createAction("테스트", "요약", dueAt, null);

    String result = service.generateCalendar(List.of(action));

    assertThat(result).contains("BEGIN:VALARM");
    assertThat(result).contains("TRIGGER:-P7D");
    assertThat(result).contains("TRIGGER:-P3D");
    assertThat(result).contains("TRIGGER:-P1D");

    int alarmCount = result.split("BEGIN:VALARM").length - 1;
    assertThat(alarmCount).isEqualTo(3);
  }

  @Test
  void generateSingleEvent_produces_complete_calendar_for_one_action() {
    OffsetDateTime dueAt = OffsetDateTime.of(2026, 4, 1, 9, 0, 0, 0, ZoneOffset.ofHours(9));
    ExtractedActionEntity action = createAction("공결 신청", "공결 신청 안내", dueAt, "사이버캠퍼스");

    String result = service.generateSingleEvent(action);

    assertThat(result).startsWith("BEGIN:VCALENDAR\r\n");
    assertThat(result).endsWith("END:VCALENDAR\r\n");
    assertThat(result).contains("SUMMARY:공결 신청");
    assertThat(result).contains("LOCATION:사이버캠퍼스");

    int eventCount = result.split("BEGIN:VEVENT").length - 1;
    assertThat(eventCount).isEqualTo(1);
  }

  @Test
  void generateCalendar_escapes_special_characters() {
    OffsetDateTime dueAt = OffsetDateTime.of(2026, 3, 15, 18, 0, 0, 0, ZoneOffset.ofHours(9));
    ExtractedActionEntity action = createAction("콤마, 포함; 제목", "줄바꿈\n포함 설명", dueAt, null);

    String result = service.generateCalendar(List.of(action));

    assertThat(result).contains("SUMMARY:콤마\\, 포함\\; 제목");
    assertThat(result).contains("DESCRIPTION:줄바꿈\\n포함 설명");
  }

  @Test
  void generateCalendar_omits_location_when_systemHint_is_null() {
    OffsetDateTime dueAt = OffsetDateTime.of(2026, 3, 15, 18, 0, 0, 0, ZoneOffset.ofHours(9));
    ExtractedActionEntity action = createAction("테스트", "요약", dueAt, null);

    String result = service.generateCalendar(List.of(action));

    assertThat(result).doesNotContain("LOCATION:");
  }
}
