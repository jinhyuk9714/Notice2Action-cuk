package com.cuk.notice2action.extraction.api.dto;

import java.util.List;

public record ActionUpdateRequest(
    String title,
    String actionSummary,
    String dueAtIso,
    String dueAtLabel,
    String eligibility,
    List<String> requiredItems,
    String systemHint,
    List<String> revertFields
) {}
