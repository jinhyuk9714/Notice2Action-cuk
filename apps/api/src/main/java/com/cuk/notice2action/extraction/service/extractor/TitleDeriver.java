package com.cuk.notice2action.extraction.service.extractor;

import org.springframework.stereotype.Component;

@Component
public class TitleDeriver {

  public String derive(String taskPhrase, String sourceTitle, String text) {
    if (taskPhrase != null && !taskPhrase.isBlank()) {
      return taskPhrase.trim();
    }
    if (sourceTitle != null && !sourceTitle.isBlank()) {
      return sourceTitle.trim();
    }

    String base = text.lines()
        .map(String::trim)
        .filter(line -> !line.isBlank())
        .findFirst()
        .orElse("추출된 액션");
    if (base.length() > 60) {
      base = base.substring(0, 60).trim() + "...";
    }
    return base;
  }
}
