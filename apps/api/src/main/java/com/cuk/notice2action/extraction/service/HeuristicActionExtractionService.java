package com.cuk.notice2action.extraction.service;

import com.cuk.notice2action.extraction.api.dto.ActionExtractionRequest;
import com.cuk.notice2action.extraction.api.dto.ActionExtractionResponse;
import com.cuk.notice2action.extraction.api.dto.EvidenceSnippetDto;
import com.cuk.notice2action.extraction.api.dto.ExtractedActionDto;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class HeuristicActionExtractionService implements ActionExtractionService {

  // --- Date patterns (ordered: year-full first, then year-less, then range-end) ---

  // P1: Korean full date — 2026년 3월 12일(수) 오후 6시 30분
  private static final Pattern KOREAN_FULL_DATE = Pattern.compile(
      "(20\\d{2})\\s*년\\s*(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일"
          + "(?:\\s*\\([가-힣]\\))?"
          + "(?:\\s*(?:(오전|오후)\\s*)?(\\d{1,2})\\s*시(?:\\s*(\\d{1,2})\\s*분?)?)?"
  );

  // P2: ISO date — 2026.3.12(수) 18:00
  private static final Pattern ISO_DATE = Pattern.compile(
      "(20\\d{2})[./-](\\d{1,2})[./-](\\d{1,2})"
          + "(?:\\s*\\([가-힣]\\))?"
          + "(?:\\s*(\\d{1,2}):(\\d{2}))?"
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

  // --- Keyword lists ---

  // Longer keywords first to avoid partial matches (e.g., "장학포털" before "포털")
  private static final List<String> SYSTEM_HINTS = List.of(
      "TRINITY", "사이버캠퍼스", "웹메일", "우리WON",
      "종정넷", "LMS", "e-class", "e-Campus",
      "학생생활관", "국제교류원",
      "취업지원센터", "장학포털", "학과사무실", "통합정보시스템",
      "도서관", "포털", "portal"
  );

  private static final List<String> REQUIRED_ITEM_KEYWORDS = List.of(
      "신청서", "성적증명서", "증빙서류", "재학증명서", "학생증", "통장사본", "사유서",
      "여권사본", "등록금납입증명서", "졸업증명서", "추천서", "자기소개서",
      "이력서", "포트폴리오", "사진", "지원서", "동의서", "서약서",
      "계획서", "보고서", "반명함판", "가족관계증명서"
  );

  private static final List<String> ACTION_VERBS = List.of(
      "신청", "제출", "완료", "등록", "참석",
      "납부", "수강", "접수", "지원", "확인",
      "작성", "출석", "발급", "예약", "다운로드"
  );

  // Longer/more-specific signals first to avoid partial matches
  private static final List<String> ELIGIBILITY_SIGNALS = List.of(
      "지원자격", "참여자격", "전체 학생",
      "졸업예정자", "대학원생", "해당 학과",
      "대상", "학년", "전공",
      "재학생", "복학생", "신입생", "수료자", "휴학생"
  );

  private static final int MAX_ACTIONS = 5;

  // --- Internal records ---

  private record DateComponents(int year, int month, int day, int hour, int minute) {}

  private record DateMatch(String label, DateComponents components) {}

  private record ScoredDateMatch(DateMatch dateMatch, double score) {}

  private record ActionSegment(String text, String primaryVerb) {}

  // --- Main extraction ---

  @Override
  public ActionExtractionResponse extract(ActionExtractionRequest request) {
    String normalizedText = normalizeText(request.sourceText());
    if (normalizedText.isBlank()) {
      throw new IllegalArgumentException("sourceText must not be blank");
    }

    List<ActionSegment> segments = segmentIntoActions(normalizedText);

    if (segments.size() <= 1) {
      List<ExtractedActionDto> result =
          List.of(extractSingleAction(request, normalizedText, null, 0, 1));
      return new ActionExtractionResponse(sortByProfile(result, request.focusProfile()));
    }

    List<ExtractedActionDto> actions = new ArrayList<>();
    int total = Math.min(segments.size(), MAX_ACTIONS);
    for (int i = 0; i < total; i++) {
      ActionSegment seg = segments.get(i);
      actions.add(extractSingleAction(request, seg.text(), seg.primaryVerb(), i + 1, total));
    }
    return new ActionExtractionResponse(sortByProfile(actions, request.focusProfile()));
  }

  private List<ExtractedActionDto> sortByProfile(
      List<ExtractedActionDto> actions, List<String> focusProfile) {
    if (focusProfile == null || focusProfile.isEmpty()) {
      return actions;
    }
    List<String> lowerKeywords = focusProfile.stream()
        .filter(k -> k != null && !k.isBlank())
        .map(k -> k.toLowerCase(Locale.ROOT))
        .toList();
    if (lowerKeywords.isEmpty()) {
      return actions;
    }
    // Stable sort: profile-matching actions first
    List<ExtractedActionDto> sorted = new ArrayList<>(actions);
    sorted.sort((a, b) -> {
      boolean aMatch = matchesProfile(a, lowerKeywords);
      boolean bMatch = matchesProfile(b, lowerKeywords);
      return Boolean.compare(bMatch, aMatch); // true first
    });
    return sorted;
  }

  private boolean matchesProfile(ExtractedActionDto action, List<String> lowerKeywords) {
    String eligibility = action.eligibility();
    if (eligibility == null || eligibility.isBlank()) {
      return false;
    }
    String lowerEligibility = eligibility.toLowerCase(Locale.ROOT);
    return lowerKeywords.stream().anyMatch(lowerEligibility::contains);
  }

  private ExtractedActionDto extractSingleAction(
      ActionExtractionRequest request,
      String text,
      String overrideVerb,
      int actionIndex,
      int totalActions
  ) {
    List<EvidenceSnippetDto> evidence = new ArrayList<>();

    DateMatch dateMatch = extractDateMatch(text, evidence);
    String dueAtLabel = dateMatch != null ? dateMatch.label() : null;
    String dueAtIso = dateMatch != null ? formatIso(dateMatch.components()) : null;
    String systemHint = extractSystemHint(text, evidence);
    List<String> requiredItems = extractRequiredItems(text, evidence);
    String actionVerb = overrideVerb != null ? overrideVerb : extractActionVerb(text, evidence);
    String eligibility = extractEligibility(text, evidence);
    String title = deriveTitle(request.sourceTitle(), text, actionIndex, totalActions);
    String actionSummary = buildActionSummary(actionVerb, dueAtLabel, systemHint, requiredItems, text);

    return new ExtractedActionDto(
        null, null,
        title, actionSummary,
        dueAtIso, dueAtLabel,
        eligibility, requiredItems, systemHint,
        request.sourceCategory(),
        evidence, computeInferred(evidence), null
    );
  }

  // --- Text normalization ---

  private String normalizeText(String text) {
    String result = text;

    // 1. Remove zero-width characters (ZWJ, ZWNJ, ZWSP, BOM, etc.)
    result = result.replaceAll("[\\u200B-\\u200F\\u2028-\\u202F\\uFEFF]", "");

    // 2. Convert full-width digits/letters/punctuation to ASCII
    result = convertFullWidthToAscii(result);

    // 3. Normalize bullet-like characters at line start to "- "
    result = result.replaceAll("(?m)^\\s*[ㅇ○●•★※▶▷◆◇◈]\\s*", "- ");

    // 4. Normalize whitespace characters
    result = result.replace('\u00A0', ' ');
    result = result.replace('\t', ' ');

    // 5. Table-row reconstruction: 3+ consecutive spaces → " | "
    result = result.replaceAll(" {3,}", " | ");

    // 6. Collapse remaining multiple spaces to single space
    result = result.replaceAll(" {2,}", " ");

    // 7. Collapse excessive newlines (3+) to double newline
    result = result.replaceAll("\\n{3,}", "\n\n");

    // 8. Trim each line
    result = result.lines()
        .map(String::trim)
        .reduce((a, b) -> a + "\n" + b)
        .orElse("");

    return result.trim();
  }

  private static String convertFullWidthToAscii(String text) {
    StringBuilder sb = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c >= '\uFF01' && c <= '\uFF5E') {
        sb.append((char) (c - 0xFF01 + 0x0021));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  // --- Title derivation ---

  private String deriveTitle(String sourceTitle, String text, int actionIndex, int totalActions) {
    String base;
    if (sourceTitle != null && !sourceTitle.isBlank()) {
      base = sourceTitle.trim();
    } else {
      base = text.lines()
          .map(String::trim)
          .filter(line -> !line.isBlank())
          .findFirst()
          .orElse("추출된 액션");
      if (base.length() > 60) {
        base = base.substring(0, 60).trim() + "...";
      }
    }

    if (totalActions > 1) {
      return base + " (" + actionIndex + "/" + totalActions + ")";
    }
    return base;
  }

  // --- Date extraction (collect-all-rank approach for absolute dates) ---

  private DateMatch extractDateMatch(String text, List<EvidenceSnippetDto> evidence) {
    List<ScoredDateMatch> candidates = new ArrayList<>();

    // Collect all absolute date matches from all patterns
    collectKoreanFullDates(text, candidates);
    collectIsoDates(text, candidates);
    collectKoreanMonthDay24H(text, candidates);
    collectKoreanMonthDayTime(text, candidates);
    collectShortSlashDot(text, candidates);
    collectRangeEndKorean(text, candidates);
    collectRangeEndShort(text, candidates);

    // Pick highest-scored absolute date if any
    if (!candidates.isEmpty()) {
      candidates.sort((a, b) -> Double.compare(b.score(), a.score()));
      ScoredDateMatch best = candidates.getFirst();
      evidence.add(new EvidenceSnippetDto("dueAtLabel", best.dateMatch().label(), best.score()));
      return best.dateMatch();
    }

    // Fallback to relative dates (first-match)
    DateMatch match;

    match = tryRelativeDay(text);
    if (match != null) {
      double confidence = isNearDeadlineKeyword(text, match.label()) ? 0.58 : 0.50;
      evidence.add(new EvidenceSnippetDto("dueAtLabel", match.label(), confidence));
      return match;
    }

    match = tryRelativeWeekDay(text);
    if (match != null) {
      double confidence = isNearDeadlineKeyword(text, match.label()) ? 0.55 : 0.48;
      evidence.add(new EvidenceSnippetDto("dueAtLabel", match.label(), confidence));
      return match;
    }

    match = tryRelativeNUnit(text);
    if (match != null) {
      double confidence = isNearDeadlineKeyword(text, match.label()) ? 0.55 : 0.48;
      evidence.add(new EvidenceSnippetDto("dueAtLabel", match.label(), confidence));
      return match;
    }

    match = tryRelativePeriodEnd(text);
    if (match != null) {
      double confidence = isNearDeadlineKeyword(text, match.label()) ? 0.52 : 0.45;
      evidence.add(new EvidenceSnippetDto("dueAtLabel", match.label(), confidence));
      return match;
    }

    return null;
  }

  // --- Absolute date collectors (loop all matches, score each) ---

  private void collectKoreanFullDates(String text, List<ScoredDateMatch> candidates) {
    Matcher m = KOREAN_FULL_DATE.matcher(text);
    while (m.find()) {
      int year = Integer.parseInt(m.group(1));
      int month = Integer.parseInt(m.group(2));
      int day = Integer.parseInt(m.group(3));
      if (!isValidDate(year, month, day)) continue;
      String ampm = m.group(4);
      int hour = m.group(5) == null ? 0 : resolveHour(Integer.parseInt(m.group(5)), ampm);
      int minute = m.group(6) == null ? 0 : Integer.parseInt(m.group(6));
      if (hour > 0 && !isValidTime(hour, minute)) continue;
      String label = m.group(0).trim();
      double score = computeDateConfidence(true, hour > 0, text, label);
      if (isStartDateContext(text, label)) score -= 0.15;
      candidates.add(new ScoredDateMatch(new DateMatch(label, new DateComponents(year, month, day, hour, minute)), Math.min(score, 0.95)));
    }
  }

  private void collectIsoDates(String text, List<ScoredDateMatch> candidates) {
    Matcher m = ISO_DATE.matcher(text);
    while (m.find()) {
      int year = Integer.parseInt(m.group(1));
      int month = Integer.parseInt(m.group(2));
      int day = Integer.parseInt(m.group(3));
      if (!isValidDate(year, month, day)) continue;
      int hour = m.group(4) == null ? 0 : Integer.parseInt(m.group(4));
      int minute = m.group(5) == null ? 0 : Integer.parseInt(m.group(5));
      if (hour > 0 && !isValidTime(hour, minute)) continue;
      String label = m.group(0).trim();
      double score = computeDateConfidence(true, hour > 0, text, label);
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
      String label = m.group(0).trim();
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
      int hour = m.group(4) == null ? 0 : resolveHour(Integer.parseInt(m.group(4)), ampm);
      int minute = m.group(5) == null ? 0 : Integer.parseInt(m.group(5));
      if (hour > 0 && !isValidTime(hour, minute)) continue;
      String label = m.group(0).trim();
      double score = computeDateConfidence(false, hour > 0, text, label);
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
      int hour = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
      int minute = m.group(4) == null ? 0 : Integer.parseInt(m.group(4));
      if (hour > 0 && !isValidTime(hour, minute)) continue;
      String label = m.group(0).trim();
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
      candidates.add(new ScoredDateMatch(new DateMatch(label, new DateComponents(currentYear(), month, day, 0, 0)), 0.85));
    }
  }

  private void collectRangeEndShort(String text, List<ScoredDateMatch> candidates) {
    Matcher m = RANGE_END_SHORT.matcher(text);
    while (m.find()) {
      int month = Integer.parseInt(m.group(1));
      int day = Integer.parseInt(m.group(2));
      if (!isValidDate(currentYear(), month, day)) continue;
      String label = m.group(0).trim();
      candidates.add(new ScoredDateMatch(new DateMatch(label, new DateComponents(currentYear(), month, day, 0, 0)), 0.85));
    }
  }

  private boolean isStartDateContext(String text, String matchLabel) {
    int idx = text.indexOf(matchLabel);
    if (idx < 0) return false;
    int afterEnd = idx + matchLabel.length();
    int searchEnd = Math.min(text.length(), afterEnd + 8);
    String after = text.substring(afterEnd, searchEnd);
    return after.contains("부터") || after.trim().startsWith("~") || after.trim().startsWith("～");
  }

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
      // 다음 or 차주: next week
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

  private boolean isPartOfFullYearKoreanDate(String text, int matchStart) {
    // Check if "년" appears shortly before this match (within 10 chars before the month digit)
    int lookBack = Math.max(0, matchStart - 10);
    String prefix = text.substring(lookBack, matchStart);
    return prefix.contains("년");
  }

  // Package-private for test override
  LocalDate today() {
    return LocalDate.now();
  }

  private int currentYear() {
    return today().getYear();
  }

  private static int resolveHour(int rawHour, String ampm) {
    if (ampm == null) {
      return rawHour;
    }
    if ("오후".equals(ampm) && rawHour < 12) {
      return rawHour + 12;
    }
    if ("오전".equals(ampm) && rawHour == 12) {
      return 0;
    }
    return rawHour;
  }

  private static boolean computeInferred(List<EvidenceSnippetDto> evidence) {
    if (evidence.isEmpty()) return true;
    return evidence.stream().anyMatch(e -> e.confidence() < 0.75);
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

  private boolean isNearDeadlineKeyword(String text, String matchLabel) {
    int idx = text.indexOf(matchLabel);
    if (idx < 0) {
      return false;
    }
    int searchStart = Math.max(0, idx - 15);
    int searchEnd = Math.min(text.length(), idx + matchLabel.length() + 10);
    String vicinity = text.substring(searchStart, searchEnd);
    return DEADLINE_PROXIMITY.matcher(vicinity).find();
  }

  private String formatIso(DateComponents dc) {
    return String.format(Locale.ROOT, "%04d-%02d-%02dT%02d:%02d:00+09:00",
        dc.year(), dc.month(), dc.day(), dc.hour(), dc.minute());
  }

  // --- System hint extraction ---

  private String extractSystemHint(String text, List<EvidenceSnippetDto> evidence) {
    String lowerText = text.toLowerCase(Locale.ROOT);
    for (String candidate : SYSTEM_HINTS) {
      int index = lowerText.indexOf(candidate.toLowerCase(Locale.ROOT));
      if (index >= 0) {
        String contextSnippet = extractContext(text, index, candidate.length(), 40);
        evidence.add(new EvidenceSnippetDto("systemHint", contextSnippet, 0.78));
        return candidate;
      }
    }
    return null;
  }

  private String extractContext(String text, int matchStart, int matchLength, int contextSize) {
    int start = Math.max(0, matchStart - contextSize);
    int end = Math.min(text.length(), matchStart + matchLength + contextSize);
    return text.substring(start, end).trim();
  }

  // --- Required items extraction ---

  private List<String> extractRequiredItems(String text, List<EvidenceSnippetDto> evidence) {
    List<String> results = new ArrayList<>();
    for (String keyword : REQUIRED_ITEM_KEYWORDS) {
      if (text.contains(keyword)) {
        results.add(keyword);
        evidence.add(new EvidenceSnippetDto("requiredItems", keyword, 0.72));
      }
    }
    return results;
  }

  // --- Action verb extraction ---

  private String extractActionVerb(String text, List<EvidenceSnippetDto> evidence) {
    for (String verb : ACTION_VERBS) {
      int index = text.indexOf(verb);
      if (index >= 0) {
        String contextSnippet = extractContext(text, index, verb.length(), 30);
        evidence.add(new EvidenceSnippetDto("actionVerb", contextSnippet, 0.76));
        return verb;
      }
    }
    return null;
  }

  private static final Pattern NON_ACTION_CONTEXT = Pattern.compile(
      "(신청|제출|완료|등록|참석|납부|수강|접수|지원|확인|작성|출석|발급|예약|다운로드)"
          + "\\s*(대상|기간|방법|안내|절차|현황|결과|일정|서류|목록|내역|자격)"
  );

  private String findActionVerb(String text) {
    for (String verb : ACTION_VERBS) {
      int index = text.indexOf(verb);
      if (index >= 0) {
        // Check if this verb is used in a non-action context (e.g., "신청 대상", "제출 기간")
        int contextEnd = Math.min(text.length(), index + verb.length() + 5);
        String afterVerb = text.substring(index, contextEnd);
        if (NON_ACTION_CONTEXT.matcher(afterVerb).find()) {
          continue;
        }
        return verb;
      }
    }
    return null;
  }

  // --- Eligibility extraction ---

  private String extractEligibility(String text, List<EvidenceSnippetDto> evidence) {
    for (String signal : ELIGIBILITY_SIGNALS) {
      int index = text.indexOf(signal);
      if (index >= 0) {
        String sentence = extractSentenceContaining(text, index);
        double confidence = computeEligibilityConfidence(signal);
        evidence.add(new EvidenceSnippetDto("eligibility", sentence, confidence));
        return sentence;
      }
    }
    return null;
  }

  private String extractSentenceContaining(String text, int signalIndex) {
    int start = signalIndex;
    while (start > 0 && text.charAt(start - 1) != '\n' && text.charAt(start - 1) != '.') {
      start--;
    }
    int end = signalIndex;
    while (end < text.length() && text.charAt(end) != '\n' && text.charAt(end) != '.') {
      end++;
    }
    if (end < text.length() && text.charAt(end) == '.') {
      end++;
    }
    // Cap at 200 chars
    if (end - start > 200) {
      end = start + 200;
    }
    String sentence = text.substring(start, end).trim();
    if (sentence.isEmpty()) {
      int fallbackEnd = Math.min(text.length(), signalIndex + 50);
      return text.substring(signalIndex, fallbackEnd).trim();
    }
    return sentence;
  }

  private double computeEligibilityConfidence(String signal) {
    return switch (signal) {
      case "대상", "지원자격", "참여자격" -> 0.82;
      case "학년", "전공", "해당 학과" -> 0.75;
      case "전체 학생", "수료자" -> 0.65;
      default -> 0.72; // 재학생, 복학생, 신입생, etc.
    };
  }

  // --- Action summary ---

  private String buildActionSummary(
      String actionVerb,
      String dueAtLabel,
      String systemHint,
      List<String> requiredItems,
      String text
  ) {
    StringBuilder sb = new StringBuilder();

    if (actionVerb != null) {
      sb.append("[").append(actionVerb).append("] ");
    }

    if (dueAtLabel != null) {
      sb.append(dueAtLabel).append("까지 ");
    }

    if (systemHint != null) {
      sb.append(systemHint).append("에서 ");
    }

    if (!requiredItems.isEmpty()) {
      sb.append("준비물: ").append(String.join(", ", requiredItems)).append(". ");
    }

    if (sb.isEmpty()) {
      String firstSentence = text.split("(?<=[.!?。]|\\n)", 2)[0].trim();
      return firstSentence.length() > 120
          ? firstSentence.substring(0, 120).trim() + "..."
          : firstSentence;
    }

    return sb.toString().trim();
  }

  // --- Multi-action segmentation ---

  private List<ActionSegment> segmentIntoActions(String text) {
    String[] sentences = text.split("(?<=[.!?。\\n])");
    List<ActionSegment> segments = new ArrayList<>();
    StringBuilder currentSegment = new StringBuilder();
    String currentVerb = null;

    for (String sentence : sentences) {
      String trimmed = sentence.trim();
      if (trimmed.isEmpty()) {
        continue;
      }

      String foundVerb = findActionVerb(trimmed);
      if (foundVerb != null && currentVerb != null && !currentSegment.isEmpty()) {
        segments.add(new ActionSegment(currentSegment.toString().trim(), currentVerb));
        currentSegment = new StringBuilder();
      }
      if (foundVerb != null) {
        currentVerb = foundVerb;
      }
      currentSegment.append(trimmed).append(" ");
    }

    if (currentVerb != null && !currentSegment.isEmpty()) {
      segments.add(new ActionSegment(currentSegment.toString().trim(), currentVerb));
    }

    return segments;
  }
}
