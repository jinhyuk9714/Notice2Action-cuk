package com.cuk.notice2action.extraction.service.notice;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notice-feed")
public record NoticeFeedProperties(
    boolean enabled,
    String listUrl,
    long fixedDelay,
    int maxPagesPerRun,
    boolean bootstrapOnStartup
) {}
