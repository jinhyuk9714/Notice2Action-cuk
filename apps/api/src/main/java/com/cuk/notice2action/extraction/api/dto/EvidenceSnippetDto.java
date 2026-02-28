package com.cuk.notice2action.extraction.api.dto;

public record EvidenceSnippetDto(
    String fieldName,
    String snippet,
    double confidence
) {}
