package com.cuk.notice2action.extraction.service;

import com.cuk.notice2action.extraction.persistence.entity.ExtractedActionEntity;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ICalendarService {

  private static final String PRODID = "-//Notice2Action CUK//V2//KO";
  private static final String DOMAIN = "notice2action.cuk";
  private static final DateTimeFormatter ICAL_DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

  public String generateCalendar(List<ExtractedActionEntity> actions) {
    StringBuilder sb = new StringBuilder();
    appendCalendarHeader(sb);

    for (ExtractedActionEntity action : actions) {
      if (action.getDueAtIso() != null) {
        appendEvent(sb, action);
      }
    }

    appendCalendarFooter(sb);
    return sb.toString();
  }

  public String generateSingleEvent(ExtractedActionEntity action) {
    StringBuilder sb = new StringBuilder();
    appendCalendarHeader(sb);
    appendEvent(sb, action);
    appendCalendarFooter(sb);
    return sb.toString();
  }

  private void appendCalendarHeader(StringBuilder sb) {
    sb.append("BEGIN:VCALENDAR\r\n");
    sb.append("VERSION:2.0\r\n");
    sb.append("PRODID:").append(PRODID).append("\r\n");
    sb.append("CALSCALE:GREGORIAN\r\n");
    sb.append("METHOD:PUBLISH\r\n");
  }

  private void appendCalendarFooter(StringBuilder sb) {
    sb.append("END:VCALENDAR\r\n");
  }

  private void appendEvent(StringBuilder sb, ExtractedActionEntity action) {
    OffsetDateTime dueAt = action.getDueAtIso();
    String uid = action.getId().toString() + "@" + DOMAIN;
    String dtStart = formatUtc(dueAt);
    String dtEnd = formatUtc(dueAt.plusHours(1));
    String now = formatUtc(OffsetDateTime.now());

    sb.append("BEGIN:VEVENT\r\n");
    sb.append("UID:").append(uid).append("\r\n");
    sb.append("DTSTAMP:").append(now).append("\r\n");
    sb.append("DTSTART:").append(dtStart).append("\r\n");
    sb.append("DTEND:").append(dtEnd).append("\r\n");
    sb.append("SUMMARY:").append(escapeIcalText(action.getTitle())).append("\r\n");
    sb.append("DESCRIPTION:").append(escapeIcalText(action.getActionSummary())).append("\r\n");

    if (action.getSystemHint() != null && !action.getSystemHint().isBlank()) {
      sb.append("LOCATION:").append(escapeIcalText(action.getSystemHint())).append("\r\n");
    }

    appendAlarm(sb, "P7D");
    appendAlarm(sb, "P3D");
    appendAlarm(sb, "P1D");

    sb.append("END:VEVENT\r\n");
  }

  private void appendAlarm(StringBuilder sb, String triggerDuration) {
    sb.append("BEGIN:VALARM\r\n");
    sb.append("TRIGGER:-").append(triggerDuration).append("\r\n");
    sb.append("ACTION:DISPLAY\r\n");
    sb.append("DESCRIPTION:Reminder\r\n");
    sb.append("END:VALARM\r\n");
  }

  private String formatUtc(OffsetDateTime dateTime) {
    return dateTime.toInstant().atOffset(java.time.ZoneOffset.UTC).format(ICAL_DATE_FORMAT);
  }

  private static String escapeIcalText(String text) {
    if (text == null) return "";
    return text
        .replace("\\", "\\\\")
        .replace(",", "\\,")
        .replace(";", "\\;")
        .replace("\n", "\\n")
        .replace("\r", "");
  }
}
