package com.cuk.notice2action.extraction.service.notice;

public record CukNoticeListItem(
    String externalNoticeId,
    String title,
    String detailUrl
) {}
