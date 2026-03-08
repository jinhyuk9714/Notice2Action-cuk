package com.cuk.notice2action.extraction.service.notice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cuk.notice2action.extraction.api.dto.NoticeFeedSyncStatusDto;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

class NoticeFeedSyncHealthIndicatorTest {

  @Test
  void mapsHealthySyncStateToUp() {
    Health health = indicatorFor(
        new NoticeFeedSyncStatusDto(
            "healthy",
            "2026-03-09T10:00:00+09:00",
            "2026-03-09T10:00:00+09:00",
            null,
            29
        )
    ).health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertCommonDetails(health, "healthy", 29);
  }

  @Test
  void mapsStaleSyncStateToOutOfService() {
    Health health = indicatorFor(
        new NoticeFeedSyncStatusDto(
            "stale",
            "2026-03-09T08:00:00+09:00",
            "2026-03-09T09:00:00+09:00",
            null,
            29
        )
    ).health();

    assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
    assertCommonDetails(health, "stale", 29);
  }

  @Test
  void mapsFailedSyncStateToDown() {
    Health health = indicatorFor(
        new NoticeFeedSyncStatusDto(
            "failed",
            null,
            "2026-03-09T09:00:00+09:00",
            "fetch failed",
            0
        )
    ).health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertCommonDetails(health, "failed", 0);
  }

  @Test
  void mapsBootstrappingSyncStateToUnknown() {
    Health health = indicatorFor(
        new NoticeFeedSyncStatusDto(
            "bootstrapping",
            null,
            null,
            null,
            0
        )
    ).health();

    assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
    assertCommonDetails(health, "bootstrapping", 0);
  }

  private NoticeFeedSyncHealthIndicator indicatorFor(NoticeFeedSyncStatusDto syncStatus) {
    NoticeFeedService service = mock(NoticeFeedService.class);
    when(service.getSyncStatus()).thenReturn(syncStatus);
    return new NoticeFeedSyncHealthIndicator(service);
  }

  private void assertCommonDetails(Health health, String state, int noticeCount) {
    assertThat(health.getDetails())
        .containsEntry("feedKey", NoticeFeedSyncStateService.FEED_KEY)
        .containsEntry("syncState", state)
        .containsEntry("noticeCount", noticeCount)
        .containsKeys("lastSuccessfulSyncAt", "lastAttemptedSyncAt");
  }
}
