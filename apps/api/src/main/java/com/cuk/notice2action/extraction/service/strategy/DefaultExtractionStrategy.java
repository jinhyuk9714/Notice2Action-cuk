package com.cuk.notice2action.extraction.service.strategy;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DefaultExtractionStrategy implements ExtractionStrategy {

  @Override
  public List<String> extraActionVerbs() {
    return List.of();
  }

  @Override
  public int maxActions() {
    return 5;
  }
}
