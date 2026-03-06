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

    String summaryDueLabel = simplifyDueLabelForSummary(dueAtLabel);
    if (summaryDueLabel != null) {
      sb.append(" 마감: ").append(summaryDueLabel).append(".");
    }

    if (systemHint != null) {
      sb.append(" 시스템: ").append(systemHint).append(".");
    }

    if (!requiredItems.isEmpty()) {
      sb.append(" 준비물: ").append(String.join(", ", requiredItems)).append(".");
    }

    return sb.toString().trim();
  }

  private String simplifyDueLabelForSummary(String dueAtLabel) {
    if (dueAtLabel == null || dueAtLabel.isBlank()) {
      return null;
    }
    int separatorIndex = dueAtLabel.indexOf(':');
    if (separatorIndex > 0) {
      String prefix = dueAtLabel.substring(0, separatorIndex).trim();
      if (prefix.contains("기간") || prefix.contains("마감")) {
        return dueAtLabel.substring(separatorIndex + 1).trim();
      }
    }
    return dueAtLabel.trim();
  }
}
