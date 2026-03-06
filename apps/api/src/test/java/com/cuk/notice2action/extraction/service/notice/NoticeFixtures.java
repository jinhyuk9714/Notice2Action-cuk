package com.cuk.notice2action.extraction.service.notice;

import com.cuk.notice2action.extraction.domain.SourceCategory;
import com.cuk.notice2action.extraction.persistence.entity.EvidenceSnippetEntity;
import com.cuk.notice2action.extraction.persistence.entity.ExtractedActionEntity;
import com.cuk.notice2action.extraction.persistence.entity.NoticeSourceEntity;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

final class NoticeFixtures {

  private NoticeFixtures() {}

  static NoticeSourceEntity noticeSource(
      String title,
      String body,
      LocalDate publishedAt,
      String actionability,
      OffsetDateTime primaryDueAt,
      List<ExtractedActionEntity> actions
  ) {
    return noticeSource(UUID.randomUUID(), title, body, publishedAt, null, List.of(), actionability, primaryDueAt, actions);
  }

  static NoticeSourceEntity noticeSource(
      UUID id,
      String title,
      String body,
      LocalDate publishedAt,
      String sourceUrl,
      List<CukNoticeAttachment> attachments,
      String actionability,
      OffsetDateTime primaryDueAt,
      List<ExtractedActionEntity> actions
  ) {
    NoticeSourceEntity entity = new NoticeSourceEntity(
        id,
        title,
        SourceCategory.NOTICE,
        body,
        sourceUrl,
        OffsetDateTime.now(ZoneOffset.ofHours(9))
    );
    entity.setAutoCollected(true);
    entity.setExternalNoticeId(id.toString());
    entity.setPublishedAt(publishedAt);
    entity.setActionability(actionability);
    entity.setPrimaryDueAt(primaryDueAt);
    entity.setPrimaryDueLabel(primaryDueAt != null ? "마감" : null);
    entity.setAttachmentsJson(NoticeFeedService.toAttachmentsJsonForTest(attachments));
    actions.forEach(entity.getActions()::add);
    actions.forEach(action -> action.setSource(entity));
    return entity;
  }

  static ExtractedActionEntity action(String title, String eligibility, double confidence) {
    return action(title, title + " 요약", OffsetDateTime.now(ZoneOffset.ofHours(9)).plusDays(3), "3일 후", "[]", "TRINITY", eligibility, confidence);
  }

  static ExtractedActionEntity action(
      String title,
      String summary,
      OffsetDateTime dueAtIso,
      String dueAtLabel,
      String requiredItemsJson,
      String systemHint,
      String eligibility,
      double confidence
  ) {
    ExtractedActionEntity entity = new ExtractedActionEntity(
        UUID.randomUUID(),
        null,
        title,
        summary,
        dueAtIso,
        dueAtLabel,
        eligibility,
        requiredItemsJson,
        systemHint,
        false,
        confidence,
        OffsetDateTime.now(ZoneOffset.ofHours(9))
    );
    entity.addEvidence(new EvidenceSnippetEntity(UUID.randomUUID(), entity, "eligibility", eligibility == null ? "" : eligibility, confidence, OffsetDateTime.now(ZoneOffset.ofHours(9))));
    return entity;
  }
}
