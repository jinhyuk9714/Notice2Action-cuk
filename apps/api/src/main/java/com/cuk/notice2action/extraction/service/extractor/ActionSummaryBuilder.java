package com.cuk.notice2action.extraction.service.extractor;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ActionSummaryBuilder {

  public String build(
      String taskTitle,
      String dueAtLabel,
      String systemHint,
      List<String> requiredItems,
      String text
  ) {
    StringBuilder sb = new StringBuilder();

    String resolvedTitle = taskTitle;
    if (resolvedTitle == null || resolvedTitle.isBlank()) {
      String firstSentence = text.split("(?<=[.!?。]|\\n)", 2)[0].trim();
      resolvedTitle = firstSentence.length() > 120
          ? firstSentence.substring(0, 120).trim() + "..."
          : firstSentence;
    }

    sb.append("할 일: ").append(resolvedTitle).append(".");

    if (dueAtLabel != null) {
      sb.append(" 마감: ").append(dueAtLabel).append(".");
    }

    if (systemHint != null) {
      sb.append(" 시스템: ").append(systemHint).append(".");
    }

    if (!requiredItems.isEmpty()) {
      sb.append(" 준비물: ").append(String.join(", ", requiredItems)).append(".");
    }

    return sb.toString().trim();
  }
}
