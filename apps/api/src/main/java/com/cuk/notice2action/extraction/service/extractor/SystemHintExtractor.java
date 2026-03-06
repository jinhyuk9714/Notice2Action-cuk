package com.cuk.notice2action.extraction.service.extractor;

import com.cuk.notice2action.extraction.api.dto.EvidenceSnippetDto;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class SystemHintExtractor {

  // Longer keywords first to avoid partial matches (e.g., "장학포털" before "포털")
  private static final List<String> SYSTEM_HINTS = List.of(
      "TRINITY", "트리니티", "사이버캠퍼스", "웹메일", "우리WON",
      "종정넷", "LMS", "e-class", "e-Campus",
      "학생생활관", "국제교류원",
      "취업지원센터", "장학포털", "학과사무실", "통합정보시스템",
      "도서관", "포털", "portal"
  );

  public String extract(String text, List<EvidenceSnippetDto> evidence) {
    String lowerText = text.toLowerCase(Locale.ROOT);
    for (String candidate : SYSTEM_HINTS) {
      int index = lowerText.indexOf(candidate.toLowerCase(Locale.ROOT));
      if (index >= 0) {
        String contextSnippet = extractContext(text, index, candidate.length(), 40);
        evidence.add(new EvidenceSnippetDto("systemHint", contextSnippet, 0.78));
        return "트리니티".equals(candidate) ? "TRINITY" : candidate;
      }
    }
    return null;
  }

  static String extractContext(String text, int matchStart, int matchLength, int contextSize) {
    int start = Math.max(0, matchStart - contextSize);
    int end = Math.min(text.length(), matchStart + matchLength + contextSize);
    return text.substring(start, end).trim();
  }
}
