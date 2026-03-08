package com.cuk.notice2action.extraction.service.strategy;

import com.cuk.notice2action.extraction.domain.SourceCategory;
import org.springframework.stereotype.Component;

@Component
public class ExtractionStrategyFactory {

  private final DefaultExtractionStrategy defaultStrategy;
  private final SyllabusExtractionStrategy syllabusStrategy;

  public ExtractionStrategyFactory(
      DefaultExtractionStrategy defaultStrategy,
      SyllabusExtractionStrategy syllabusStrategy) {
    this.defaultStrategy = defaultStrategy;
    this.syllabusStrategy = syllabusStrategy;
  }

  public ExtractionStrategy forCategory(SourceCategory category) {
    if (category == SourceCategory.SYLLABUS) {
      return syllabusStrategy;
    }
    return defaultStrategy;
  }
}
