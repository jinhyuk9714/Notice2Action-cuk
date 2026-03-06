package com.cuk.notice2action.extraction.service.notice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.notice-feed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NoticeFeedScheduler {

  private static final Logger log = LoggerFactory.getLogger(NoticeFeedScheduler.class);

  private final NoticeFeedIngestionService noticeFeedIngestionService;

  public NoticeFeedScheduler(NoticeFeedIngestionService noticeFeedIngestionService) {
    this.noticeFeedIngestionService = noticeFeedIngestionService;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void bootstrapOnStartup() {
    try {
      noticeFeedIngestionService.bootstrapIfNeeded();
    } catch (Exception exception) {
      log.warn("Initial notice bootstrap failed", exception);
    }
  }

  @Scheduled(fixedDelayString = "${app.notice-feed.fixed-delay:3600000}")
  public void refreshOnSchedule() {
    try {
      noticeFeedIngestionService.refreshCollectedNotices();
    } catch (Exception exception) {
      log.warn("Scheduled notice refresh failed", exception);
    }
  }
}
