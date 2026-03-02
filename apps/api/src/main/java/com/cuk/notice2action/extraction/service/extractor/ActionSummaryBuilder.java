package com.cuk.notice2action.extraction.service.extractor;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ActionSummaryBuilder {

  public String build(
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
}
