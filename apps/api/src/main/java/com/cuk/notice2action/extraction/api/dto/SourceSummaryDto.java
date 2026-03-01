package com.cuk.notice2action.extraction.api.dto;

import com.cuk.notice2action.extraction.domain.SourceCategory;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SourceSummaryDto(
    UUID id,
    String title,
    SourceCategory sourceCategory,
    String sourceUrl,
    OffsetDateTime createdAt,
    int actionCount
) {}
