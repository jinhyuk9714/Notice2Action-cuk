package com.cuk.notice2action.extraction.service.strategy;

import java.util.List;

public interface ExtractionStrategy {

  /** Additional action verbs recognized for this category. */
  List<String> extraActionVerbs();

  /** Cap on number of extracted actions. */
  int maxActions();
}
