package com.cuk.notice2action.extraction.service.extractor;

import com.cuk.notice2action.extraction.service.model.StructuredEligibility;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class StructuredEligibilityParser {

  private static final List<String> UNIVERSAL_PATTERNS = List.of(
      "전체 학생", "전체학생", "모든 학생", "모든학생", "전 학생",
      "재학생 전체", "전 학년", "전학년", "전체 학년"
  );

  private static final List<String> STATUS_KEYWORDS = List.of(
      "졸업예정자", "대학원생", "재학생", "신입생", "복학생", "외국인", "유학생", "수료자", "휴학생"
  );

  private static final Pattern EXCLUSION = Pattern.compile("제외|불가|불포함");
  private static final Pattern YEAR_RANGE = Pattern.compile("([1-4])[~～\\-]([1-4])학년");
  private static final Pattern YEAR_ENUM = Pattern.compile("([1-4])[,·및]\\s*([1-4])학년");
  private static final Pattern YEAR_AND_UP = Pattern.compile("([1-4])학년\\s*이상");
  private static final Pattern YEAR_AND_DOWN = Pattern.compile("([1-4])학년\\s*이하");
  private static final Pattern YEAR_SINGLE = Pattern.compile("([1-4])학년");
  private static final Pattern DEPARTMENT = Pattern.compile("([가-힣A-Za-z0-9·&\\-]{2,}(?:학부|학과|전공|과))");
  private static final Set<String> DEPARTMENT_EXCLUDE = Set.of(
      "해당", "각해", "모든", "전체", "각각", "각자", "기타", "참여", "지원", "신청"
  );

  public StructuredEligibility parse(String text) {
    if (text == null || text.isBlank()) {
      return new StructuredEligibility(false, List.of(), List.of(), List.of(), null);
    }
    boolean universal = isUniversal(text);
    List<String> statuses = new ArrayList<>();
    List<String> excludedStatuses = new ArrayList<>();
    extractStatuses(text, statuses, excludedStatuses);
    List<Integer> years = extractYears(text);
    String department = extractDepartment(text);
    return new StructuredEligibility(universal, statuses, excludedStatuses, years, department);
  }

  private boolean isUniversal(String text) {
    return UNIVERSAL_PATTERNS.stream().anyMatch(text::contains);
  }

  private void extractStatuses(String text, List<String> statuses, List<String> excludedStatuses) {
    for (String keyword : STATUS_KEYWORDS) {
      int idx = text.indexOf(keyword);
      if (idx < 0) {
        continue;
      }
      int afterStart = idx + keyword.length();
      int afterEnd = Math.min(text.length(), afterStart + 10);
      String after = text.substring(afterStart, afterEnd);
      if (EXCLUSION.matcher(after).find()) {
        excludedStatuses.add(keyword);
      } else {
        statuses.add(keyword);
      }
    }
  }

  private List<Integer> extractYears(String text) {
    Matcher rangeMatcher = YEAR_RANGE.matcher(text);
    if (rangeMatcher.find()) {
      int from = Integer.parseInt(rangeMatcher.group(1));
      int to = Integer.parseInt(rangeMatcher.group(2));
      return rangeList(Math.min(from, to), Math.max(from, to));
    }
    Matcher enumMatcher = YEAR_ENUM.matcher(text);
    if (enumMatcher.find()) {
      return List.of(Integer.parseInt(enumMatcher.group(1)), Integer.parseInt(enumMatcher.group(2)));
    }
    Matcher andUpMatcher = YEAR_AND_UP.matcher(text);
    if (andUpMatcher.find()) {
      return rangeList(Integer.parseInt(andUpMatcher.group(1)), 4);
    }
    Matcher andDownMatcher = YEAR_AND_DOWN.matcher(text);
    if (andDownMatcher.find()) {
      return rangeList(1, Integer.parseInt(andDownMatcher.group(1)));
    }
    if (text.contains("고학년")) {
      return List.of(3, 4);
    }
    if (text.contains("저학년")) {
      return List.of(1, 2);
    }
    Matcher singleMatcher = YEAR_SINGLE.matcher(text);
    if (singleMatcher.find()) {
      return List.of(Integer.parseInt(singleMatcher.group(1)));
    }
    return List.of();
  }

  private String extractDepartment(String text) {
    Matcher matcher = DEPARTMENT.matcher(text);
    while (matcher.find()) {
      String department = normalizeDepartment(matcher.group(1));
      if (!DEPARTMENT_EXCLUDE.contains(department)) {
        return department;
      }
    }
    return null;
  }

  private String normalizeDepartment(String token) {
    if (token.endsWith("학부") && token.length() > 1) {
      return token.substring(0, token.length() - 1);
    }
    if (token.endsWith("학과") && token.length() > 1) {
      return token.substring(0, token.length() - 1);
    }
    if (token.endsWith("과") && token.length() > 1) {
      return token.substring(0, token.length() - 1);
    }
    if (token.endsWith("전공") && token.length() > 2) {
      return token.substring(0, token.length() - 2);
    }
    return token;
  }

  private List<Integer> rangeList(int from, int to) {
    List<Integer> list = new ArrayList<>();
    for (int i = from; i <= to; i++) {
      list.add(i);
    }
    return list;
  }
}
