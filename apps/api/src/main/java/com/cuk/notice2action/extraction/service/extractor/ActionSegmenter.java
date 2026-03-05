package com.cuk.notice2action.extraction.service.extractor;

import com.cuk.notice2action.extraction.service.model.ActionSegment;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ActionSegmenter {

  private final ActionVerbExtractor verbExtractor;
  private final TaskPhraseExtractor taskPhraseExtractor;

  public ActionSegmenter(ActionVerbExtractor verbExtractor, TaskPhraseExtractor taskPhraseExtractor) {
    this.verbExtractor = verbExtractor;
    this.taskPhraseExtractor = taskPhraseExtractor;
  }

  public List<ActionSegment> segment(String text) {
    String[] sentences = text.split("\\n+");
    List<ActionSegment> segments = new ArrayList<>();
    StringBuilder currentSegment = new StringBuilder();
    String currentVerb = null;
    String currentTaskPhrase = null;

    for (String sentence : sentences) {
      String trimmed = sentence.trim();
      if (trimmed.isEmpty()) {
        continue;
      }

      boolean proceduralStep = isProceduralStep(trimmed);
      String foundVerb = verbExtractor.findVerb(trimmed);
      String foundTaskPhrase = taskPhraseExtractor.extractForSegmentation(trimmed, foundVerb);

      if (shouldStartNewSegment(
          proceduralStep,
          foundVerb,
          foundTaskPhrase,
          currentVerb,
          currentTaskPhrase,
          currentSegment
      )) {
        segments.add(new ActionSegment(currentSegment.toString().trim(), currentVerb, currentTaskPhrase));
        currentSegment = new StringBuilder();
        currentTaskPhrase = null;
      }
      if (foundVerb != null) {
        currentVerb = foundVerb;
      }
      if (foundTaskPhrase != null) {
        currentTaskPhrase = proceduralStep
            ? keepCurrentTaskPhrase(currentTaskPhrase, foundTaskPhrase)
            : selectPreferredTaskPhrase(currentTaskPhrase, foundTaskPhrase);
      }
      currentSegment.append(trimmed).append(" ");
    }

    if (currentVerb != null && !currentSegment.isEmpty()) {
      segments.add(new ActionSegment(currentSegment.toString().trim(), currentVerb, currentTaskPhrase));
    }

    return segments;
  }

  private boolean shouldStartNewSegment(
      boolean proceduralStep,
      String foundVerb,
      String foundTaskPhrase,
      String currentVerb,
      String currentTaskPhrase,
      StringBuilder currentSegment
  ) {
    if (foundVerb == null || currentVerb == null || currentSegment.isEmpty()) {
      return false;
    }
    if (proceduralStep) {
      return false;
    }
    if (foundTaskPhrase == null) {
      return false;
    }
    if (currentTaskPhrase == null) {
      return true;
    }
    return !isSameTaskFamily(foundTaskPhrase, currentTaskPhrase);
  }

  private boolean isProceduralStep(String sentence) {
    String trimmed = sentence.trim();
    return trimmed.matches("^(?:STEP\\s*\\d+\\.|\\d+\\.|[가-힣]\\.|[➊➋➌➍➎➏➐➑➒➓]|[-•▶◎※]).*");
  }

  private boolean isSameTaskFamily(String left, String right) {
    String normalizedLeft = left.replaceAll("\\s+", "").toLowerCase();
    String normalizedRight = right.replaceAll("\\s+", "").toLowerCase();
    if (normalizedLeft.equals(normalizedRight)
        || normalizedLeft.contains(normalizedRight)
        || normalizedRight.contains(normalizedLeft)) {
      return true;
    }

    String coreLeft = extractTaskCore(left);
    String coreRight = extractTaskCore(right);
    return coreLeft != null && coreLeft.equals(coreRight);
  }

  private String keepCurrentTaskPhrase(String currentTaskPhrase, String foundTaskPhrase) {
    return currentTaskPhrase == null ? foundTaskPhrase : currentTaskPhrase;
  }

  private String selectPreferredTaskPhrase(String currentTaskPhrase, String foundTaskPhrase) {
    if (currentTaskPhrase == null) {
      return foundTaskPhrase;
    }
    if (foundTaskPhrase == null) {
      return currentTaskPhrase;
    }
    if (!isSameTaskFamily(currentTaskPhrase, foundTaskPhrase)) {
      return foundTaskPhrase;
    }
    return taskQualityScore(foundTaskPhrase) > taskQualityScore(currentTaskPhrase)
        ? foundTaskPhrase
        : currentTaskPhrase;
  }

  private String extractTaskCore(String taskPhrase) {
    String normalized = taskPhrase.replaceAll("\\s+", " ").trim().toLowerCase();
    for (String suffix : List.of("수강신청", "신청 또는 변경", "수요조사 응답")) {
      if (normalized.endsWith(suffix)) {
        return suffix;
      }
    }
    return null;
  }

  private int taskQualityScore(String taskPhrase) {
    String normalized = taskPhrase.replaceAll("\\s+", " ").trim();
    int score = Math.min(normalized.length(), 12);
    if (normalized.length() > 24) {
      score -= 10;
    }
    if (normalized.matches(".*(우선적|반드시|꼼꼼히|필수로|해야|하시기 바랍니다|확인하시어|본인의 학과|선택할 학과|기존 수강신청).*")) {
      score -= 20;
    }
    if (normalized.matches(".*(신입생|편입생|부전공|I-DESIGN|학번|취업공결|수강과목|공결|조기졸업|융복합트랙|군 e-러닝).*")) {
      score += 15;
    }
    return score;
  }
}
