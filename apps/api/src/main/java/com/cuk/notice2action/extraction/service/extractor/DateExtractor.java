package com.cuk.notice2action.extraction.service.extractor;

import com.cuk.notice2action.extraction.api.dto.EvidenceSnippetDto;
import com.cuk.notice2action.extraction.service.model.DateComponents;
import com.cuk.notice2action.extraction.service.model.DateMatch;
import com.cuk.notice2action.extraction.service.model.ScoredDateMatch;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class DateExtractor {

  // P1: Korean full date — 2026년 3월 12일(수) 오후 6시 30분
  private static final Pattern KOREAN_FULL_DATE = Pattern.compile(
      "(20\\d{2})\\s*년\\s*(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일"
          + "(?:\\s*\\([가-힣]\\))?"
          + "(?:\\s*(?:(오전|오후)\\s*)?(\\d{1,2})\\s*시(?:\\s*(\\d{1,2})\\s*분?)?)?"
  );

  // P2: ISO date — 2026.3.12(수) 18:00
  private static final Pattern ISO_DATE = Pattern.compile(
      "(20\\d{2})\\s*[./-]\\s*(\\d{1,2})\\s*[./-]\\s*(\\d{1,2})(?:\\.)?"
          + "(?:\\s*\\([가-힣]\\))?"
          + "(?:\\s*(\\d{1,2}):(\\d{2}))?"
  );

  // P2-1: ISO range end with time — 2026. 3. 24.(화) 09:00 ~ 3. 25.(수) 17:00
  private static final Pattern ISO_RANGE_END_WITH_TIME = Pattern.compile(
      "(20\\d{2})\\s*[./-]\\s*(\\d{1,2})\\s*[./-]\\s*(\\d{1,2})(?:\\.)?"
          + "(?:\\s*\\([가-힣]\\))?"
          + "\\s*(\\d{1,2}):(\\d{2})"
          + "\\s*[~～\\-]\\s*"
          + "(\\d{1,2})\\s*[./-]\\s*(\\d{1,2})(?:\\.)?"
          + "(?:\\s*\\([가-힣]\\))?"
          + "\\s*(\\d{1,2}):(\\d{2})"
  );

  // P3: Korean month-day with optional Korean time — 3월 12일(수) 오후 6시 30분
  private static final Pattern KOREAN_MONTH_DAY_TIME = Pattern.compile(
      "(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일"
          + "(?:\\s*\\([가-힣]\\))?"
          + "(?:\\s*(?:(오전|오후)\\s*)?(\\d{1,2})\\s*시(?:\\s*(\\d{1,2})\\s*분?)?)?"
  );

  // P4: Korean month-day with 24h clock — 3월 12일(수) 18:00
  private static final Pattern KOREAN_MONTH_DAY_24H = Pattern.compile(
      "(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일"
          + "(?:\\s*\\([가-힣]\\))?"
          + "\\s+(\\d{1,2}):(\\d{2})"
  );

  // P5: Short slash/dot — 3/12(수) 18:00
  private static final Pattern SHORT_SLASH_DOT = Pattern.compile(
      "(?<!\\d)(\\d{1,2})[./](\\d{1,2})"
          + "(?:\\s*\\([가-힣]\\))?"
          + "(?:\\s*(\\d{1,2}):(\\d{2}))?"
  );

  // P6: Range-end Korean — ~ 3월 15일
  private static final Pattern RANGE_END_KOREAN = Pattern.compile(
      "~\\s*(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일"
  );

  // P7: Range-end short — ~3/15
  private static final Pattern RANGE_END_SHORT = Pattern.compile(
      "~\\s*(\\d{1,2})[./](\\d{1,2})"
  );

  // P8: Relative day — 내일, 모레, 글피
  private static final Pattern RELATIVE_DAY = Pattern.compile(
      "(내일|모레|글피)"
  );

  // P9: Relative week day — 다음 주 금요일, 이번 주 수요일
  private static final Pattern RELATIVE_WEEK_DAY = Pattern.compile(
      "(이번|다음|차주)\\s*주\\s*(월|화|수|목|금|토|일)요일"
  );

  // P10: N units relative — 3일 이내, 2주 후, 1개월 이내
  private static final Pattern RELATIVE_N_UNIT = Pattern.compile(
      "(\\d{1,3})\\s*(일|주|주일|개월|달)\\s*(이내|내|후|뒤|안)"
  );

  // P11: Period end — 이번 달 말, 이번 주 말
  private static final Pattern RELATIVE_PERIOD_END = Pattern.compile(
      "(이번|이|금)\\s*(달|월|주)\\s*말"
  );

  // Deadline proximity keywords
  private static final Pattern DEADLINE_PROXIMITY = Pattern.compile(
      "(까지|마감|기한|이전|이내|내로|내에|자정)"
  );
  private static final List<String> NON_DEADLINE_SCHEDULE_KEYWORDS = List.of(
      "강의일정", "수업일정", "강의시간", "시간표", "매주", "주간 진행", "온라인 강의"
  );

  public DateMatch extract(String text, List<EvidenceSnippetDto> evidence) {
    List<ScoredDateMatch> candidates = new ArrayList<>();

    collectIsoRangeEndWithTime(text, candidates);
    collectKoreanFullDates(text, candidates);
    collectIsoDates(text, candidates);
    collectKoreanMonthDay24H(text, candidates);
    collectKoreanMonthDayTime(text, candidates);
    collectShortSlashDot(text, candidates);
    collectRangeEndKorean(text, candidates);
    collectRangeEndShort(text, candidates);

    if (!candidates.isEmpty()) {
      candidates.sort((a, b) -> Double.compare(b.score(), a.score()));
      ScoredDateMatch best = candidates.getFirst();
      evidence.add(new EvidenceSnippetDto("dueAtLabel", contextualDateSnippet(text, best.dateMatch().label()), best.score()));
      return best.dateMatch();
    }

    // Fallback to relative dates (first-match)
    DateMatch match;

    match = tryRelativeDay(text);
    if (match != null) {
      double confidence = isNearDeadlineKeyword(text, match.label()) ? 0.58 : 0.50;
      evidence.add(new EvidenceSnippetDto("dueAtLabel", contextualDateSnippet(text, match.label()), confidence));
      return match;
    }

    match = tryRelativeWeekDay(text);
    if (match != null) {
      double confidence = isNearDeadlineKeyword(text, match.label()) ? 0.55 : 0.48;
      evidence.add(new EvidenceSnippetDto("dueAtLabel", contextualDateSnippet(text, match.label()), confidence));
      return match;
    }

    match = tryRelativeNUnit(text);
    if (match != null) {
      double confidence = isNearDeadlineKeyword(text, match.label()) ? 0.55 : 0.48;
      evidence.add(new EvidenceSnippetDto("dueAtLabel", contextualDateSnippet(text, match.label()), confidence));
      return match;
    }

    match = tryRelativePeriodEnd(text);
    if (match != null) {
      double confidence = isNearDeadlineKeyword(text, match.label()) ? 0.52 : 0.45;
      evidence.add(new EvidenceSnippetDto("dueAtLabel", contextualDateSnippet(text, match.label()), confidence));
      return match;
    }

    return null;
  }

  public String formatIso(DateComponents dc) {
    return String.format(Locale.ROOT, "%04d-%02d-%02dT%02d:%02d:00+09:00",
        dc.year(), dc.month(), dc.day(), dc.hour(), dc.minute());
  }

  // --- Absolute date collectors ---

  private void collectKoreanFullDates(String text, List<ScoredDateMatch> candidates) {
    Matcher m = KOREAN_FULL_DATE.matcher(text);
    while (m.find()) {
      int year = Integer.parseInt(m.group(1));
      int month = Integer.parseInt(m.group(2));
      int day = Integer.parseInt(m.group(3));
      if (!isValidDate(year, month, day)) continue;
      String ampm = m.group(4);
      boolean hasTime = m.group(5) != null;
      int hour = m.group(5) == null ? 0 : resolveHour(Integer.parseInt(m.group(5)), ampm);
      int minute = m.group(6) == null ? 0 : Integer.parseInt(m.group(6));
      if (hasTime && !isValidTime(hour, minute)) continue;
      String label = m.group(0).trim();
      if (isNonDeadlineScheduleContext(text, label)) continue;
      double score = computeDateConfidence(true, hasTime, text, label);
      if (isStartDateContext(text, label)) score -= 0.15;
      candidates.add(new ScoredDateMatch(new DateMatch(label, new DateComponents(year, month, day, hour, minute)), Math.min(score, 0.95)));
    }
  }

  private void collectIsoRangeEndWithTime(String text, List<ScoredDateMatch> candidates) {
    Matcher m = ISO_RANGE_END_WITH_TIME.matcher(text);
    while (m.find()) {
      int year = Integer.parseInt(m.group(1));
      int endMonth = Integer.parseInt(m.group(6));
      int endDay = Integer.parseInt(m.group(7));
      int endHour = Integer.parseInt(m.group(8));
      int endMinute = Integer.parseInt(m.group(9));
      if (!isValidDate(year, endMonth, endDay) || !isValidTime(endHour, endMinute)) {
        continue;
      }
      String label = normalizeDateLabel(text.substring(m.start(6), m.end(9)).trim());
      if (isNonDeadlineScheduleContext(text, label)) continue;
      candidates.add(new ScoredDateMatch(
          new DateMatch(label, new DateComponents(year, endMonth, endDay, endHour, endMinute)),
          0.92
      ));
    }
  }

  private void collectIsoDates(String text, List<ScoredDateMatch> candidates) {
    Matcher m = ISO_DATE.matcher(text);
    while (m.find()) {
      int year = Integer.parseInt(m.group(1));
      int month = Integer.parseInt(m.group(2));
      int day = Integer.parseInt(m.group(3));
      if (!isValidDate(year, month, day)) continue;
      boolean hasTime = m.group(4) != null;
      int hour = m.group(4) == null ? 0 : Integer.parseInt(m.group(4));
      int minute = m.group(5) == null ? 0 : Integer.parseInt(m.group(5));
      if (hasTime && !isValidTime(hour, minute)) continue;
      String label = normalizeDateLabel(m.group(0).trim());
      if (isNonDeadlineScheduleContext(text, label)) continue;
      double score = computeDateConfidence(true, hasTime, text, label);
      if (isStartDateContext(text, label)) score -= 0.15;
      candidates.add(new ScoredDateMatch(new DateMatch(label, new DateComponents(year, month, day, hour, minute)), Math.min(score, 0.95)));
    }
  }

  private void collectKoreanMonthDay24H(String text, List<ScoredDateMatch> candidates) {
    Matcher m = KOREAN_MONTH_DAY_24H.matcher(text);
    while (m.find()) {
      if (isPartOfFullYearKoreanDate(text, m.start())) continue;
      int month = Integer.parseInt(m.group(1));
      int day = Integer.parseInt(m.group(2));
      if (!isValidDate(currentYear(), month, day)) continue;
      int hour = Integer.parseInt(m.group(3));
      int minute = Integer.parseInt(m.group(4));
      if (!isValidTime(hour, minute)) continue;
      String label = normalizeDateLabel(m.group(0).trim());
      if (isNonDeadlineScheduleContext(text, label)) continue;
      double score = computeDateConfidence(false, true, text, label);
      if (isStartDateContext(text, label)) score -= 0.15;
      candidates.add(new ScoredDateMatch(new DateMatch(label, new DateComponents(currentYear(), month, day, hour, minute)), Math.min(score, 0.95)));
    }
  }

  private void collectKoreanMonthDayTime(String text, List<ScoredDateMatch> candidates) {
    Matcher m = KOREAN_MONTH_DAY_TIME.matcher(text);
    while (m.find()) {
      if (isPartOfFullYearKoreanDate(text, m.start())) continue;
      int month = Integer.parseInt(m.group(1));
      int day = Integer.parseInt(m.group(2));
      if (!isValidDate(currentYear(), month, day)) continue;
      String ampm = m.group(3);
      boolean hasTime = m.group(4) != null;
      int hour = m.group(4) == null ? 0 : resolveHour(Integer.parseInt(m.group(4)), ampm);
      int minute = m.group(5) == null ? 0 : Integer.parseInt(m.group(5));
      if (hasTime && !isValidTime(hour, minute)) continue;
      String label = normalizeDateLabel(m.group(0).trim());
      if (isNonDeadlineScheduleContext(text, label)) continue;
      double score = computeDateConfidence(false, hasTime, text, label);
      if (isStartDateContext(text, label)) score -= 0.15;
      candidates.add(new ScoredDateMatch(new DateMatch(label, new DateComponents(currentYear(), month, day, hour, minute)), Math.min(score, 0.95)));
    }
  }

  private void collectShortSlashDot(String text, List<ScoredDateMatch> candidates) {
    Matcher m = SHORT_SLASH_DOT.matcher(text);
    while (m.find()) {
      int month = Integer.parseInt(m.group(1));
      int day = Integer.parseInt(m.group(2));
      if (!isValidDate(currentYear(), month, day)) continue;
      boolean hasTime = m.group(3) != null;
      int hour = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
      int minute = m.group(4) == null ? 0 : Integer.parseInt(m.group(4));
      if (hasTime && !isValidTime(hour, minute)) continue;
      String label = normalizeDateLabel(m.group(0).trim());
      if (isNonDeadlineScheduleContext(text, label)) continue;
      boolean nearDeadline = isNearDeadlineKeyword(text, label);
      double score = nearDeadline ? 0.70 : 0.60;
      if (isStartDateContext(text, label)) score -= 0.15;
      candidates.add(new ScoredDateMatch(new DateMatch(label, new DateComponents(currentYear(), month, day, hour, minute)), Math.min(score, 0.95)));
    }
  }

  private void collectRangeEndKorean(String text, List<ScoredDateMatch> candidates) {
    Matcher m = RANGE_END_KOREAN.matcher(text);
    while (m.find()) {
      int month = Integer.parseInt(m.group(1));
      int day = Integer.parseInt(m.group(2));
      if (!isValidDate(currentYear(), month, day)) continue;
      String label = m.group(0).trim();
      if (isNonDeadlineScheduleContext(text, label)) continue;
      candidates.add(new ScoredDateMatch(
          new DateMatch(label, new DateComponents(currentYear(), month, day, 0, 0)),
          0.85 + contextPriorityBoost(text, label)
      ));
    }
  }

  private void collectRangeEndShort(String text, List<ScoredDateMatch> candidates) {
    Matcher m = RANGE_END_SHORT.matcher(text);
    while (m.find()) {
      int month = Integer.parseInt(m.group(1));
      int day = Integer.parseInt(m.group(2));
      if (!isValidDate(currentYear(), month, day)) continue;
      String label = "~ " + month + "/" + day;
      if (isNonDeadlineScheduleContext(text, label)) continue;
      candidates.add(new ScoredDateMatch(
          new DateMatch(label, new DateComponents(currentYear(), month, day, 0, 0)),
          0.85 + contextPriorityBoost(text, label)
      ));
    }
  }

  // --- Relative date matchers ---

  private DateMatch tryRelativeDay(String text) {
    Matcher m = RELATIVE_DAY.matcher(text);
    if (!m.find()) return null;
    String keyword = m.group(1);
    LocalDate target = switch (keyword) {
      case "내일" -> today().plusDays(1);
      case "모레" -> today().plusDays(2);
      case "글피" -> today().plusDays(3);
      default -> null;
    };
    if (target == null) return null;
    return new DateMatch(m.group(0).trim(),
        new DateComponents(target.getYear(), target.getMonthValue(), target.getDayOfMonth(), 0, 0));
  }

  private DateMatch tryRelativeWeekDay(String text) {
    Matcher m = RELATIVE_WEEK_DAY.matcher(text);
    if (!m.find()) return null;
    String weekRef = m.group(1);
    DayOfWeek targetDow = koreanDayOfWeek(m.group(2));
    if (targetDow == null) return null;

    LocalDate target;
    if ("이번".equals(weekRef)) {
      target = today().with(TemporalAdjusters.nextOrSame(targetDow));
    } else {
      LocalDate nextMonday = today().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
      target = nextMonday.with(TemporalAdjusters.nextOrSame(targetDow));
    }
    return new DateMatch(m.group(0).trim(),
        new DateComponents(target.getYear(), target.getMonthValue(), target.getDayOfMonth(), 0, 0));
  }

  private DateMatch tryRelativeNUnit(String text) {
    Matcher m = RELATIVE_N_UNIT.matcher(text);
    if (!m.find()) return null;
    int n = Integer.parseInt(m.group(1));
    String unit = m.group(2);
    LocalDate target = switch (unit) {
      case "일" -> today().plusDays(n);
      case "주", "주일" -> today().plusWeeks(n);
      case "개월", "달" -> today().plusMonths(n);
      default -> null;
    };
    if (target == null) return null;
    return new DateMatch(m.group(0).trim(),
        new DateComponents(target.getYear(), target.getMonthValue(), target.getDayOfMonth(), 0, 0));
  }

  private DateMatch tryRelativePeriodEnd(String text) {
    Matcher m = RELATIVE_PERIOD_END.matcher(text);
    if (!m.find()) return null;
    String period = m.group(2);
    LocalDate target = switch (period) {
      case "달", "월" -> today().withDayOfMonth(today().lengthOfMonth());
      case "주" -> today().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
      default -> null;
    };
    if (target == null) return null;
    return new DateMatch(m.group(0).trim(),
        new DateComponents(target.getYear(), target.getMonthValue(), target.getDayOfMonth(), 0, 0));
  }

  // --- Helpers ---

  // Protected for test override
  protected LocalDate today() {
    return LocalDate.now();
  }

  private int currentYear() {
    return today().getYear();
  }

  private boolean isStartDateContext(String text, String matchLabel) {
    int idx = text.indexOf(matchLabel);
    if (idx < 0) return false;
    int afterEnd = idx + matchLabel.length();
    int searchEnd = Math.min(text.length(), afterEnd + 8);
    String after = text.substring(afterEnd, searchEnd);
    return after.contains("부터") || after.trim().startsWith("~") || after.trim().startsWith("～");
  }

  private boolean isNearDeadlineKeyword(String text, String matchLabel) {
    int idx = text.indexOf(matchLabel);
    if (idx < 0) return false;
    int searchStart = Math.max(0, idx - 15);
    int searchEnd = Math.min(text.length(), idx + matchLabel.length() + 10);
    String vicinity = text.substring(searchStart, searchEnd);
    return DEADLINE_PROXIMITY.matcher(vicinity).find();
  }

  private double contextPriorityBoost(String text, String matchLabel) {
    int idx = text.indexOf(matchLabel);
    if (idx < 0 && matchLabel.startsWith("~ ")) {
      idx = text.indexOf("~" + matchLabel.substring(2));
    }
    if (idx < 0) return 0.0;
    int searchStart = Math.max(0, idx - 24);
    int searchEnd = Math.min(text.length(), idx + matchLabel.length() + 12);
    String vicinity = text.substring(searchStart, searchEnd);
    if (vicinity.contains("변경기간")) {
      return 0.12;
    }
    if (vicinity.contains("신청기간")) {
      return 0.08;
    }
    return 0.0;
  }

  private boolean isNonDeadlineScheduleContext(String text, String matchLabel) {
    if (isNearDeadlineKeyword(text, matchLabel)) {
      return false;
    }
    int idx = text.indexOf(matchLabel);
    if (idx < 0) return false;
    int searchStart = Math.max(0, idx - 20);
    int searchEnd = Math.min(text.length(), idx + matchLabel.length() + 20);
    String vicinity = text.substring(searchStart, searchEnd);
    return NON_DEADLINE_SCHEDULE_KEYWORDS.stream().anyMatch(vicinity::contains);
  }

  private double computeDateConfidence(boolean hasYear, boolean hasTime, String text, String matchLabel) {
    double base;
    if (hasYear && hasTime) {
      base = 0.84;
    } else if (hasYear) {
      base = 0.80;
    } else if (hasTime) {
      base = 0.72;
    } else {
      base = 0.68;
    }
    if (isNearDeadlineKeyword(text, matchLabel)) {
      base += 0.08;
    }
    return Math.min(base, 0.95);
  }

  private String normalizeDateLabel(String label) {
    return label.replaceAll("\\.\\(", ". (").replaceAll("\\s+", " ").trim();
  }

  private String contextualDateSnippet(String text, String label) {
    List<String> lines = text == null ? List.of() : text.lines()
        .map(String::trim)
        .filter(line -> !line.isBlank())
        .toList();
    if (lines.isEmpty()) {
      return normalizeDateLabel(label);
    }

    int matchedIndex = -1;
    for (int index = 0; index < lines.size(); index++) {
      if (lineMatchesLabel(lines.get(index), label)) {
        matchedIndex = index;
        break;
      }
    }
    if (matchedIndex < 0) {
      return normalizeDateLabel(label);
    }

    String snippet = lines.get(matchedIndex);
    if (matchedIndex > 0) {
      String previous = lines.get(matchedIndex - 1);
      if (previous.endsWith(":") || previous.contains("기간")) {
        snippet = previous + " " + snippet;
      }
    }
    if (matchedIndex + 1 < lines.size()) {
      String next = lines.get(matchedIndex + 1);
      if (isTrailingConditionLine(next)) {
        snippet = snippet + " " + next;
      }
    }
    return normalizeWhitespace(snippet);
  }

  private boolean lineMatchesLabel(String line, String label) {
    String normalizedLine = normalizeContextValue(line);
    String normalizedLabel = normalizeContextValue(label);
    return !normalizedLabel.isEmpty() && normalizedLine.contains(normalizedLabel);
  }

  private boolean isTrailingConditionLine(String line) {
    String normalized = normalizeWhitespace(line);
    return normalized.startsWith("(")
        || normalized.contains("주말")
        || normalized.contains("공휴일")
        || normalized.contains("제외");
  }

  private String normalizeContextValue(String value) {
    return value == null ? "" : value
        .replaceAll("\\s+", "")
        .replaceAll("[()\\[\\].,:~～\\-_/]", "")
        .replaceAll("^0+", "")
        .toLowerCase(Locale.ROOT);
  }

  private String normalizeWhitespace(String value) {
    return value == null ? "" : value.replaceAll("\\s+", " ").trim();
  }

  private boolean isPartOfFullYearKoreanDate(String text, int matchStart) {
    int lookBack = Math.max(0, matchStart - 10);
    String prefix = text.substring(lookBack, matchStart);
    return prefix.contains("년");
  }

  private static int resolveHour(int rawHour, String ampm) {
    if (ampm == null) return rawHour;
    if ("오후".equals(ampm) && rawHour < 12) return rawHour + 12;
    if ("오전".equals(ampm) && rawHour == 12) return 0;
    return rawHour;
  }

  private static DayOfWeek koreanDayOfWeek(String name) {
    return switch (name) {
      case "월" -> DayOfWeek.MONDAY;
      case "화" -> DayOfWeek.TUESDAY;
      case "수" -> DayOfWeek.WEDNESDAY;
      case "목" -> DayOfWeek.THURSDAY;
      case "금" -> DayOfWeek.FRIDAY;
      case "토" -> DayOfWeek.SATURDAY;
      case "일" -> DayOfWeek.SUNDAY;
      default -> null;
    };
  }

  private static boolean isValidDate(int year, int month, int day) {
    try {
      LocalDate.of(year, month, day);
      return true;
    } catch (DateTimeException e) {
      return false;
    }
  }

  private static boolean isValidTime(int hour, int minute) {
    return hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59;
  }
}
