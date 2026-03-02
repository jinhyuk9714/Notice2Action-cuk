package com.cuk.notice2action.extraction.service.extractor;

import org.springframework.stereotype.Component;

@Component
public class TitleDeriver {

  public String derive(String sourceTitle, String text, int actionIndex, int totalActions) {
    String base;
    if (sourceTitle != null && !sourceTitle.isBlank()) {
      base = sourceTitle.trim();
    } else {
      base = text.lines()
          .map(String::trim)
          .filter(line -> !line.isBlank())
          .findFirst()
          .orElse("추출된 액션");
      if (base.length() > 60) {
        base = base.substring(0, 60).trim() + "...";
      }
    }

    if (totalActions > 1) {
      return base + " (" + actionIndex + "/" + totalActions + ")";
    }
    return base;
  }
}
