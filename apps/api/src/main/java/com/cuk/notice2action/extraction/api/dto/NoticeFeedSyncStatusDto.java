package com.cuk.notice2action.extraction.api.dto;

public record NoticeFeedSyncStatusDto(
    String state,
    String lastSuccessfulSyncAt,
    String lastAttemptedSyncAt,
    String lastErrorMessage,
    long noticeCount
) {}
