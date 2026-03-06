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
      "업로드", "수령"
  );

  private static final Pattern NON_ACTION_CONTEXT = Pattern.compile(
      "(신청|제출|완료|등록|참석|납부|수강|접수|지원|확인|작성|출석|발급|예약|다운로드)"
          + "\\s*(대상|기간|방법|안내|절차|현황|결과|일정|서류|목록|내역|자격|이 필요한)"
  );

  public String extract(String text, List<EvidenceSnippetDto> evidence) {
    for (String verb : ACTION_VERBS) {
      int index = text.indexOf(verb);
      if (index >= 0) {
        int contextEnd = Math.min(text.length(), index + verb.length() + 5);
        String afterVerb = text.substring(index, contextEnd);
        if (afterVerb.startsWith("수강과목")) {
          continue;
        }
        if (NON_ACTION_CONTEXT.matcher(afterVerb).find()) {
          continue;
        }
        String contextSnippet = SystemHintExtractor.extractContext(text, index, verb.length(), 30);
        evidence.add(new EvidenceSnippetDto("actionVerb", contextSnippet, 0.76));
        return verb;
      }
    }
    return null;
  }

  /**
   * Find the first action verb without collecting evidence.
   * Used by ActionSegmenter for text segmentation.
   */
  public String findVerb(String text) {
    for (String verb : ACTION_VERBS) {
      int index = text.indexOf(verb);
      if (index >= 0) {
        int contextEnd = Math.min(text.length(), index + verb.length() + 5);
        String afterVerb = text.substring(index, contextEnd);
        if (afterVerb.startsWith("수강과목")) {
          continue;
        }
        if (NON_ACTION_CONTEXT.matcher(afterVerb).find()) {
          continue;
        }
        return verb;
      }
    }
    return null;
  }
}
