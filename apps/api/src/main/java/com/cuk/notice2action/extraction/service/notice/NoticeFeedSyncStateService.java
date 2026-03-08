package com.cuk.notice2action.extraction.service.notice;

import com.cuk.notice2action.extraction.persistence.entity.NoticeFeedSyncStateEntity;
import com.cuk.notice2action.extraction.persistence.repository.NoticeFeedSyncStateRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoticeFeedSyncStateService {

  public static final String FEED_KEY = "cuk-overall-notices";

  private static final ZoneOffset APP_OFFSET = ZoneOffset.ofHours(9);

  private final NoticeFeedSyncStateRepository repository;

  public NoticeFeedSyncStateService(NoticeFeedSyncStateRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public void markAttemptStarted(String feedKey, long noticeCount) {
    OffsetDateTime now = OffsetDateTime.now(APP_OFFSET);
    NoticeFeedSyncStateEntity state = repository.findById(feedKey)
        .orElseGet(() -> new NoticeFeedSyncStateEntity(feedKey, "bootstrapping"));
    state.setLastAttemptedSyncAt(now);
    state.setNoticeCount(noticeCount);
    if (state.getLastSuccessfulSyncAt() == null && !"failed".equals(state.getState())) {
      state.setState("bootstrapping");
    }
    repository.save(state);
  }

  @Transactional
  public void markHealthy(String feedKey, long noticeCount) {
    OffsetDateTime now = OffsetDateTime.now(APP_OFFSET);
    NoticeFeedSyncStateEntity state = repository.findById(feedKey)
        .orElseGet(() -> new NoticeFeedSyncStateEntity(feedKey, "healthy"));
    state.setState("healthy");
    state.setLastAttemptedSyncAt(now);
    state.setLastSuccessfulSyncAt(now);
    state.setLastErrorMessage(null);
    state.setNoticeCount(noticeCount);
    repository.save(state);
  }

  @Transactional
  public void markFailed(String feedKey, String errorMessage, long noticeCount) {
    OffsetDateTime now = OffsetDateTime.now(APP_OFFSET);
    NoticeFeedSyncStateEntity state = repository.findById(feedKey)
        .orElseGet(() -> new NoticeFeedSyncStateEntity(feedKey, "failed"));
    state.setState("failed");
    state.setLastAttemptedSyncAt(now);
    state.setLastErrorMessage(summarizeError(errorMessage));
    state.setNoticeCount(noticeCount);
    repository.save(state);
  }

  @Transactional
  public void ensureHealthyIfMissing(String feedKey, long noticeCount) {
    if (repository.existsById(feedKey)) {
      return;
    }
    markHealthy(feedKey, noticeCount);
  }

  @Transactional(readOnly = true)
  public Optional<NoticeFeedSyncStateEntity> find(String feedKey) {
    return repository.findById(feedKey);
  }

  private String summarizeError(String errorMessage) {
    if (errorMessage == null || errorMessage.isBlank()) {
      return "알 수 없는 동기화 오류";
    }
    String normalized = errorMessage.replaceAll("\\s+", " ").trim();
    return normalized.length() <= 160 ? normalized : normalized.substring(0, 160);
  }
}
