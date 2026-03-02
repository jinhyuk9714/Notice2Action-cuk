package com.cuk.notice2action.extraction.service.extractor;

import com.cuk.notice2action.extraction.service.model.ActionSegment;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ActionSegmenter {

  private final ActionVerbExtractor verbExtractor;

  public ActionSegmenter(ActionVerbExtractor verbExtractor) {
    this.verbExtractor = verbExtractor;
  }

  public List<ActionSegment> segment(String text) {
    String[] sentences = text.split("(?<=[.!?。\\n])");
    List<ActionSegment> segments = new ArrayList<>();
    StringBuilder currentSegment = new StringBuilder();
    String currentVerb = null;

    for (String sentence : sentences) {
      String trimmed = sentence.trim();
      if (trimmed.isEmpty()) {
        continue;
      }

      String foundVerb = verbExtractor.findVerb(trimmed);
      if (foundVerb != null && currentVerb != null && !currentSegment.isEmpty()) {
        segments.add(new ActionSegment(currentSegment.toString().trim(), currentVerb));
        currentSegment = new StringBuilder();
      }
      if (foundVerb != null) {
        currentVerb = foundVerb;
      }
      currentSegment.append(trimmed).append(" ");
    }

    if (currentVerb != null && !currentSegment.isEmpty()) {
      segments.add(new ActionSegment(currentSegment.toString().trim(), currentVerb));
    }

    return segments;
  }
}
