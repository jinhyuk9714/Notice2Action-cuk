package com.cuk.notice2action.extraction.service.extractor;

import com.cuk.notice2action.extraction.api.dto.EvidenceSnippetDto;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class EligibilityExtractor {

  // Longer/more-specific signals first to avoid partial matches
  private static final List<String> ELIGIBILITY_SIGNALS = List.of(
      "지원자격", "참여자격", "전체 학생",
      "졸업예정자", "대학원생", "해당 학과",
      "대상", "학년", "전공",
      "재학생", "복학생", "외국인", "유학생", "신입생", "수료자", "휴학생"
  );

  public String extract(String text, List<EvidenceSnippetDto> evidence) {
    for (String signal : ELIGIBILITY_SIGNALS) {
      int index = text.indexOf(signal);
      if (index >= 0) {
        String sentence = extractSentenceContaining(text, index);
        double confidence = computeConfidence(signal);
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

  private double computeConfidence(String signal) {
    return switch (signal) {
      case "대상", "지원자격", "참여자격" -> 0.82;
      case "학년", "전공", "해당 학과" -> 0.75;
      case "전체 학생", "수료자" -> 0.65;
      default -> 0.72; // 재학생, 복학생, 신입생, etc.
    };
  }
}
