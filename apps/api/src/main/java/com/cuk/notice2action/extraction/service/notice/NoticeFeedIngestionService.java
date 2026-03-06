package com.cuk.notice2action.extraction.service.notice;

import com.cuk.notice2action.extraction.api.dto.ActionExtractionRequest;
import com.cuk.notice2action.extraction.api.dto.ActionExtractionResponse;
import com.cuk.notice2action.extraction.api.dto.ExtractedActionDto;
import com.cuk.notice2action.extraction.domain.SourceCategory;
import com.cuk.notice2action.extraction.persistence.entity.NoticeSourceEntity;
import com.cuk.notice2action.extraction.persistence.repository.NoticeSourceRepository;
import com.cuk.notice2action.extraction.service.ActionExtractionService;
import com.cuk.notice2action.extraction.service.ActionPersistenceService;
import com.cuk.notice2action.extraction.service.ContentHashUtil;
import com.cuk.notice2action.extraction.service.extractor.DateExtractor;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoticeFeedIngestionService {

  private static final Logger log = LoggerFactory.getLogger(NoticeFeedIngestionService.class);
  private static final ZoneOffset APP_OFFSET = ZoneOffset.ofHours(9);
  private final NoticeSourceRepository noticeSourceRepository;
  private final CukNoticeClient cukNoticeClient;
  private final ActionExtractionService actionExtractionService;
  private final ActionPersistenceService actionPersistenceService;
  private final NoticeFeedProperties properties;
  private final NoticeActionabilityClassifier noticeActionabilityClassifier;
  private final DateExtractor dateExtractor;

  public NoticeFeedIngestionService(
      NoticeSourceRepository noticeSourceRepository,
      CukNoticeClient cukNoticeClient,
      ActionExtractionService actionExtractionService,
      ActionPersistenceService actionPersistenceService,
      NoticeFeedProperties properties,
      NoticeActionabilityClassifier noticeActionabilityClassifier,
      DateExtractor dateExtractor
  ) {
    this.noticeSourceRepository = noticeSourceRepository;
    this.cukNoticeClient = cukNoticeClient;
    this.actionExtractionService = actionExtractionService;
    this.actionPersistenceService = actionPersistenceService;
    this.properties = properties;
    this.noticeActionabilityClassifier = noticeActionabilityClassifier;
    this.dateExtractor = dateExtractor;
  }

  @Transactional
  public void refreshCollectedNotices() {
    List<CukNoticeDetail> notices = cukNoticeClient.fetchLatestNotices(properties.maxPagesPerRun());
    for (CukNoticeDetail notice : notices) {
      ingestNotice(notice);
    }
    log.info("Refreshed {} collected notices", notices.size());
  }

  public void bootstrapIfNeeded() {
    if (!properties.bootstrapOnStartup() || noticeSourceRepository.existsByAutoCollectedTrue()) {
      return;
    }
    refreshCollectedNotices();
  }

  String computeContentHashForTest(CukNoticeDetail detail) {
    return computeContentHash(detail);
  }

  private void ingestNotice(CukNoticeDetail detail) {
    String contentHash = computeContentHash(detail);
    Optional<NoticeSourceEntity> existing = noticeSourceRepository.findByExternalNoticeId(detail.externalNoticeId());
    if (existing.isPresent() && contentHash.equals(existing.get().getContentHash())) {
      return;
    }

    NoticeSourceEntity source = existing.orElseGet(() -> new NoticeSourceEntity(
        UUID.randomUUID(),
        detail.title(),
        SourceCategory.NOTICE,
        detail.body(),
        detail.detailUrl(),
        OffsetDateTime.now(APP_OFFSET)
    ));

    source.setTitle(detail.title());
    source.setRawText(detail.body());
    source.setSourceUrl(detail.detailUrl());
    source.setPublishedAt(detail.publishedAt());
    source.setExternalNoticeId(detail.externalNoticeId());
    source.setAutoCollected(true);
    source.setAttachmentsJson(NoticeFeedService.toAttachmentsJson(detail.attachments()));
    source.setContentHash(contentHash);

    ActionExtractionResponse extraction = actionExtractionService.extract(new ActionExtractionRequest(
        detail.body(),
        detail.detailUrl(),
        detail.title(),
        SourceCategory.NOTICE,
        List.of()
    ));

    String actionability = noticeActionabilityClassifier.classify(detail.title(), detail.body(), extraction.actions());
    source.setActionability(actionability);
    if ("action_required".equals(actionability)) {
      applyPrimaryDue(source, detail.body(), extraction.actions());
    } else {
      clearPrimaryDue(source);
    }
    noticeSourceRepository.save(source);
    actionPersistenceService.replaceSourceActions(source, extraction);
  }

  private String computeContentHash(CukNoticeDetail detail) {
    String attachmentPart = detail.attachments().stream()
        .map(attachment -> attachment.name() + "|" + attachment.url())
        .reduce("", (left, right) -> left + "\n" + right);
    return ContentHashUtil.sha256(String.join("\n\n",
        detail.externalNoticeId(),
        detail.title(),
        detail.publishedAt() != null ? detail.publishedAt().toString() : "",
        detail.body(),
        attachmentPart
    ));
  }

  private void applyPrimaryDue(NoticeSourceEntity source, String noticeBody, List<ExtractedActionDto> actions) {
    OffsetDateTime now = OffsetDateTime.now(APP_OFFSET);
    Optional<NoticeDueCandidate> fromActions = actions.stream()
        .filter(action -> action.dueAtIso() != null && !action.dueAtIso().isBlank())
        .map(action -> new NoticeDueCandidate(parseDue(action.dueAtIso()), action.dueAtLabel()))
        .filter(candidate -> candidate.dueAt() != null && candidate.dueAt().isAfter(now))
        .min(Comparator.comparing(NoticeDueCandidate::dueAt));

    Optional<NoticeDueCandidate> resolved = fromActions.or(() -> extractFromNoticeBody(noticeBody, now));

    resolved.ifPresentOrElse(candidate -> {
          source.setPrimaryDueAt(candidate.dueAt());
          source.setPrimaryDueLabel(candidate.label());
        }, () -> {
          source.setPrimaryDueAt(null);
          source.setPrimaryDueLabel(null);
        });
  }

  private Optional<NoticeDueCandidate> extractFromNoticeBody(String noticeBody, OffsetDateTime now) {
    if (noticeBody == null || noticeBody.isBlank()) {
      return Optional.empty();
    }
    var match = dateExtractor.extract(noticeBody, new ArrayList<>());
    if (match == null) {
      return Optional.empty();
    }
    OffsetDateTime dueAt = parseDue(dateExtractor.formatIso(match.components()));
    if (dueAt == null || !dueAt.isAfter(now)) {
      return Optional.empty();
    }
    return Optional.of(new NoticeDueCandidate(dueAt, match.label()));
  }

  private OffsetDateTime parseDue(String dueAtIso) {
    try {
      return OffsetDateTime.parse(dueAtIso);
    } catch (Exception e) {
      return null;
    }
  }

  private void clearPrimaryDue(NoticeSourceEntity source) {
    source.setPrimaryDueAt(null);
    source.setPrimaryDueLabel(null);
  }

  private record NoticeDueCandidate(OffsetDateTime dueAt, String label) {}
}
