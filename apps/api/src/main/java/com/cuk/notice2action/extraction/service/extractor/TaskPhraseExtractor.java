package com.cuk.notice2action.extraction.service.extractor;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class TaskPhraseExtractor {

  private static final Pattern BRACKET_PREFIX = Pattern.compile("^\\[[^\\]]+\\]\\s*");
  private static final Pattern LEADING_YEAR_TERM = Pattern.compile(
      "^(?:20\\d{2}(?:학년도)?(?:\\s*[12]학기|-[12]학기)?\\s*)+",
      Pattern.CASE_INSENSITIVE
  );
  private static final Pattern TRAILING_META = Pattern.compile(
      "\\s*(?:관련 안내|기간 안내|변경 안내|재안내|안내|알림|공지)$"
  );
  private static final Pattern ATTACHMENT_LINE = Pattern.compile("첨부파일\\s*:\\s*(.+)$");
  private static final Pattern GENERIC_TASK_PATTERN = Pattern.compile(
      "([A-Za-z가-힣0-9<>()·「」'’\\-\\s]{2,60}?)\\s*(신청/변경|수강신청|제출|신청|조회|확인|모집|수요조사|참석)"
  );
  private static final Pattern PROCEDURAL_PREFIX = Pattern.compile(
      "^(?:STEP\\s*\\d+\\.|\\d+\\.|[가-힣]\\.|[➊➋➌➍➎➏➐➑➒➓]|[-•▶◎※])\\s*",
      Pattern.CASE_INSENSITIVE
  );

  public String extract(
      String sourceTitle,
      String text,
      String actionVerb,
      List<String> requiredItems
  ) {
    String mappedFromTitle = extractMappedTask(normalizeForMatching(sourceTitle));
    if (hasText(mappedFromTitle)) {
      return mappedFromTitle;
    }

    String combined = normalizeForMatching(sourceTitle) + "\n" + normalizeForMatching(text);

    String mappedFromCombined = extractMappedTask(combined);
    if (hasText(mappedFromCombined)) {
      return mappedFromCombined;
    }

    String genericFromText = extractGenericTaskPhrase(text, actionVerb);
    if (hasText(genericFromText)) {
      return genericFromText;
    }

    String genericFromTitle = extractGenericTaskPhrase(sourceTitle, actionVerb);
    if (hasText(genericFromTitle)) {
      return genericFromTitle;
    }

    String fallback = cleanSourceTitle(sourceTitle);
    if (hasText(fallback)) {
      return fallback;
    }

    if (hasText(actionVerb)) {
      return normalizeVerb(actionVerb);
    }

    return null;
  }

  String extractForSegmentation(String text, String actionVerb) {
    String mapped = extractMappedTask(normalizeForMatching(text));
    if (hasText(mapped)) {
      return mapped;
    }
    return extractGenericTaskPhrase(text, actionVerb);
  }

  private String extractMappedTask(String combined) {
    String lower = combined.toLowerCase(Locale.ROOT);

    if (lower.contains("수강과목 취소 신청")) {
      return "수강과목 취소 신청";
    }
    if (lower.contains("부전공") && lower.contains("신청/변경")) {
      return "부전공 신청 또는 변경";
    }
    if (lower.contains("취업학생 출결 사항 안내")
        && (lower.contains("공결허가원") || lower.contains("동의서") || lower.contains("확인서"))) {
      return "취업공결 관련 서류 준비 및 제출";
    }
    if (lower.contains("학번조회")) {
      return "학번 조회";
    }
    if (lower.contains("i-design") && lower.contains("수강신청")) {
      return "I-DESIGN 수강신청";
    }
    if (lower.contains("융복합트랙") && lower.contains("이수여부 확인")) {
      return "융복합트랙 이수여부 확인";
    }
    if (lower.contains("군 e-러닝") && lower.contains("수강신청")) {
      return "군 e-러닝 수강신청";
    }
    if (lower.contains("신입생") && lower.contains("수강신청")) {
      return "신입생 수강신청";
    }
    if (lower.contains("편입생") && lower.contains("학점인정") && lower.contains("수강신청")) {
      return "편입생 학점인정 및 수강신청";
    }
    if (lower.contains("조기졸업") && lower.contains("신청")) {
      return "조기졸업 신청";
    }
    if (lower.contains("제한인원 상향") && lower.contains("수요조사")) {
      return "제한인원 상향 요청과목 수요조사 응답";
    }
    if (lower.contains("self-making project portfolio") && (lower.contains("모집") || lower.contains("신청"))) {
      return "Self-making Project Portfolio 참여 신청";
    }
    if (lower.contains("trinity에서 신청")) {
      return "신청";
    }

    return null;
  }

  private String extractGenericTaskPhrase(String rawText, String actionVerb) {
    if (!hasText(rawText)) {
      return null;
    }

    for (String line : rawText.split("\\n")) {
      String cleanedLine = cleanSentence(line);
      if (!hasText(cleanedLine)) {
        continue;
      }
      Matcher matcher = GENERIC_TASK_PATTERN.matcher(cleanedLine);
      while (matcher.find()) {
        String candidate = normalizeTaskCandidate(matcher.group(1) + " " + matcher.group(2), actionVerb);
        if (hasText(candidate)) {
          return candidate;
        }
      }
    }

    return null;
  }

  private String normalizeTaskCandidate(String rawCandidate, String actionVerb) {
    if (!hasText(rawCandidate)) {
      return null;
    }

    String candidate = cleanSentence(rawCandidate);
    candidate = candidate.replaceFirst("^해당 내용을 확인하시어\\s*", "");
    candidate = candidate.replaceFirst("^.*?하시어\\s*", "");
    candidate = candidate.replaceFirst("^.*?하여\\s*", "");
    if (candidate.startsWith("신청이 필요한") || candidate.startsWith("해당 내용을 확인")) {
      return null;
    }
    candidate = candidate.replaceAll("\\s*기간$", "");
    candidate = candidate.replaceAll("\\s*절차$", "");
    candidate = candidate.replaceAll("\\s*방법$", "");
    candidate = candidate.replaceAll("\\s*관련$", "");
    candidate = candidate.replaceAll("\\s*하시기 바랍니다$", "");
    candidate = candidate.replaceAll("\\s*해주세요$", "");
    candidate = candidate.replaceAll("\\s*하세요$", "");
    candidate = candidate.replaceAll("^(?:TRINITY|트리니티|사이버캠퍼스|장병e음|우리WON)에서\\s*", "");
    candidate = candidate.replaceAll("^([A-Za-z가-힣0-9<>\\-]+)(?:에|에서)\\s+(신청|제출|조회|확인|참석)$", "$2");
    candidate = candidate.replaceAll("([가-힣A-Za-z0-9<>\\-])(?:을|를|에)\\s+(신청|제출|조회|확인|참석)$", "$1 $2");
    candidate = candidate.replaceAll("\\s*수강 신청$", " 수강신청");
    candidate = candidate.replaceAll("\\s+", " ").trim();

    if (candidate.endsWith("신청/변경")) {
      candidate = candidate.replace("신청/변경", "신청 또는 변경");
    }

    if ("학번조회".equals(candidate)) {
      return "학번 조회";
    }

    if (!hasText(candidate)) {
      return hasText(actionVerb) ? normalizeVerb(actionVerb) : null;
    }

    return candidate;
  }

  private String cleanSourceTitle(String sourceTitle) {
    if (!hasText(sourceTitle)) {
      return null;
    }

    String cleaned = BRACKET_PREFIX.matcher(sourceTitle.trim()).replaceFirst("");
    cleaned = LEADING_YEAR_TERM.matcher(cleaned).replaceFirst("");
    cleaned = TRAILING_META.matcher(cleaned).replaceFirst("");
    cleaned = cleaned.replaceAll("\\s+", " ").trim();

    if (!hasText(cleaned)) {
      return null;
    }

    return cleaned;
  }

  private String cleanSentence(String sentence) {
    if (!hasText(sentence)) {
      return "";
    }

    String cleaned = sentence.trim();
    cleaned = PROCEDURAL_PREFIX.matcher(cleaned).replaceFirst("");

    cleaned = BRACKET_PREFIX.matcher(cleaned).replaceFirst("");
    cleaned = ATTACHMENT_LINE.matcher(cleaned).replaceFirst("$1");
    cleaned = cleaned.replaceAll("^첨부파일\\s*:\\s*", "");
    cleaned = cleaned.replaceAll("^(?:20\\d{2}(?:학년도)?(?:\\s*[12]학기|-[12]학기)?\\s*)+", "");
    cleaned = cleaned.replaceAll("^\\d{1,2}월\\s*\\d{1,2}일(?:\\([^)]*\\))?까지\\s*", "");
    cleaned = cleaned.replaceAll("^20\\d{2}[./-]\\s*\\d{1,2}[./-]\\s*\\d{1,2}(?:\\.)?(?:\\([^)]*\\))?(?:\\s*\\d{1,2}:\\d{2})?\\s*", "");
    cleaned = cleaned.replaceAll("^[^가-힣A-Za-z<]*", "");
    cleaned = cleaned.replaceAll("[“”\"'`]", "");
    cleaned = cleaned.replaceAll("\\s+", " ").trim();
    return cleaned;
  }

  private String normalizeVerb(String actionVerb) {
    if (!hasText(actionVerb)) {
      return null;
    }
    return switch (actionVerb) {
      case "학번조회" -> "학번 조회";
      default -> actionVerb.trim();
    };
  }

  private String normalizeForMatching(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
