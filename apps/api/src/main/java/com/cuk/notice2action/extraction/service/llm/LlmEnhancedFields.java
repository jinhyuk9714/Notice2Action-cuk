package com.cuk.notice2action.extraction.service.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmEnhancedFields(
    String title,
    String actionSummary,
    String dueAtLabel,
    String dueAtIso,
    String eligibility,
    List<String> requiredItems,
    String systemHint
) {}
