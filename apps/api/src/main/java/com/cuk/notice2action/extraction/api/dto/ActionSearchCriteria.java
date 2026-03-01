package com.cuk.notice2action.extraction.api.dto;

import com.cuk.notice2action.extraction.domain.SourceCategory;
import java.time.OffsetDateTime;

public record ActionSearchCriteria(
    String q,
    SourceCategory category,
    OffsetDateTime dueDateFrom,
    OffsetDateTime dueDateTo,
    String sort
) {}
