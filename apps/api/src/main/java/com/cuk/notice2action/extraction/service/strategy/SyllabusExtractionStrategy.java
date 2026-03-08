package com.cuk.notice2action.extraction.service.strategy;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SyllabusExtractionStrategy implements ExtractionStrategy {

  @Override
  public List<String> extraActionVerbs() {
    return List.of("중간고사", "기말고사", "과제");
  }

  @Override
  public int maxActions() {
    return 5;
  }
}
