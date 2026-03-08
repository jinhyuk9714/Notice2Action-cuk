package com.cuk.notice2action.extraction.service.notice;

import com.cuk.notice2action.extraction.api.dto.NoticeFeedSyncStatusDto;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

@Component
public class NoticeFeedSyncHealthIndicator implements HealthIndicator {

  private final NoticeFeedService noticeFeedService;

  public NoticeFeedSyncHealthIndicator(NoticeFeedService noticeFeedService) {
    this.noticeFeedService = noticeFeedService;
  }

  @Override
  public Health health() {
    NoticeFeedSyncStatusDto syncStatus = noticeFeedService.getSyncStatus();
    return switch (syncStatus.state()) {
      case "healthy" -> baseHealth(syncStatus, Health.up()).build();
      case "stale" -> baseHealth(syncStatus, Health.status(Status.OUT_OF_SERVICE)).build();
      case "failed" -> baseHealth(syncStatus, Health.down()).build();
      default -> baseHealth(syncStatus, Health.unknown()).build();
    };
  }

  private Health.Builder baseHealth(NoticeFeedSyncStatusDto syncStatus, Health.Builder builder) {
    return builder
        .withDetail("feedKey", NoticeFeedSyncStateService.FEED_KEY)
        .withDetail("syncState", syncStatus.state())
        .withDetail("noticeCount", syncStatus.noticeCount())
        .withDetail("lastSuccessfulSyncAt", syncStatus.lastSuccessfulSyncAt())
        .withDetail("lastAttemptedSyncAt", syncStatus.lastAttemptedSyncAt());
  }
}
