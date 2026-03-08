package com.cuk.notice2action.extraction.service.extractor;

import com.cuk.notice2action.extraction.api.dto.EvidenceSnippetDto;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ActionVerbExtractor {

  private static final List<String> ACTION_VERBS = List.of(
      "신청", "제출", "완료", "등록", "참석",
      "납부", "수강", "접수", "지원", "확인",
      "작성", "출석", "발급", "예약", "다운로드",
      "업로드", "수령", "변경", "선택"
  );

  private static final Pattern NON_ACTION_CONTEXT = Pattern.compile(
      "(신청|제출|완료|등록|참석|납부|수강|접수|지원|확인|작성|출석|발급|예약|다운로드|업로드|수령|변경|선택)"
          + "\\s*(대상|기간|기한|방법|안내|절차|현황|결과|일정|서류|목록|내역|자격|이 필요한)"
  );

  private static final List<String> NON_ACTION_SUFFIXES = List.of(
      "대상", "기간", "기한", "방법", "안내", "절차", "현황", "결과", "일정", "서류", "목록", "내역", "자격"
  );

  public String extract(String text, List<EvidenceSnippetDto> evidence) {
    return extract(text, evidence, List.of());
  }

  public String extract(String text, List<EvidenceSnippetDto> evidence, List<String> extraVerbs) {
    String verb = findVerb(text, extraVerbs);
    if (verb != null) {
      int index = text.indexOf(verb);
      String contextSnippet = SystemHintExtractor.extractContext(text, index, verb.length(), 30);
      evidence.add(new EvidenceSnippetDto("actionVerb", contextSnippet, 0.76));
    }
    return verb;
  }

  /**
   * Find the first action verb without collecting evidence.
   * Used by ActionSegmenter for text segmentation.
   */
  public String findVerb(String text) {
    return findVerb(text, List.of());
  }

  public String findVerb(String text, List<String> extraVerbs) {
    String result = findVerbFrom(text, ACTION_VERBS);
    if (result != null || extraVerbs.isEmpty()) {
      return result;
    }
    return findVerbFrom(text, extraVerbs);
  }

  private String findVerbFrom(String text, List<String> verbs) {
    for (String verb : verbs) {
      int index = text.indexOf(verb);
      if (index < 0) {
        continue;
      }
      if (isNonActionContext(text, index, verb.length())) {
        continue;
      }
      return verb;
    }
    return null;
  }

  private boolean isNonActionContext(String text, int index, int verbLength) {
    int contextEnd = Math.min(text.length(), index + verbLength + 5);
    String afterVerb = text.substring(index, contextEnd);
    if (afterVerb.startsWith("수강과목")) {
      return true;
    }
    if (NON_ACTION_CONTEXT.matcher(afterVerb).find()) {
      return true;
    }
    return NON_ACTION_SUFFIXES.stream().anyMatch(afterVerb::contains);
  }
}
