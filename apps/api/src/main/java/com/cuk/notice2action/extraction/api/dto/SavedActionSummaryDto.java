package com.cuk.notice2action.extraction.api.dto;

import com.cuk.notice2action.extraction.domain.SourceCategory;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SavedActionSummaryDto(
    UUID id,
    String title,
    String actionSummary,
    String dueAtIso,
    String dueAtLabel,
    String eligibility,
    SourceCategory sourceCategory,
    String sourceTitle,
    OffsetDateTime createdAt
) {}
