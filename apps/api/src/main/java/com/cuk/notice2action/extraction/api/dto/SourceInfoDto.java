package com.cuk.notice2action.extraction.api.dto;

import com.cuk.notice2action.extraction.domain.SourceCategory;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SourceInfoDto(
    UUID id,
    String title,
    SourceCategory sourceCategory,
    OffsetDateTime createdAt
) {}
