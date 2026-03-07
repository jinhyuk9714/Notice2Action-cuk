package com.cuk.notice2action.extraction.service.notice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cuk.notice2action.extraction.api.dto.ActionExtractionRequest;
import com.cuk.notice2action.extraction.api.dto.ActionExtractionResponse;
import com.cuk.notice2action.extraction.api.dto.ExtractedActionDto;
import com.cuk.notice2action.extraction.domain.SourceCategory;
import com.cuk.notice2action.extraction.persistence.entity.NoticeSourceEntity;
import com.cuk.notice2action.extraction.persistence.repository.NoticeSourceRepository;
import com.cuk.notice2action.extraction.service.ActionExtractionService;
import com.cuk.notice2action.extraction.service.ActionPersistenceService;
import com.cuk.notice2action.extraction.service.extractor.DateExtractor;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NoticeFeedIngestionServiceTest {

  @Mock
  private NoticeSourceRepository noticeSourceRepository;
  @Mock
  private CukNoticeClient cukNoticeClient;
  @Mock
  private ActionExtractionService actionExtractionService;
  @Mock
  private ActionPersistenceService actionPersistenceService;

  private NoticeFeedIngestionService service;

  @BeforeEach
  void setUp() {
    service = new NoticeFeedIngestionService(
        noticeSourceRepository,
        cukNoticeClient,
        actionExtractionService,
        actionPersistenceService,
        new NoticeFeedProperties(
            true,
            "https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=list&srCategoryId=&srSearchKey=&srSearchVal=",
            3600000,
            2,
            true
        ),
        new NoticeActionabilityClassifier(),
        new DateExtractor()
    );
  }

  @Test
  void ingestsNewNoticeAndPersistsActions() {
    CukNoticeDetail detail = new CukNoticeDetail(
        "268986",
        "학생증 신청 안내",
        LocalDate.of(2026, 2, 27),
        "본문",
        List.of(),
        "https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=268986",
        "장학"
    );
    when(cukNoticeClient.fetchLatestNotices(2)).thenReturn(List.of(detail));
    when(noticeSourceRepository.findByExternalNoticeId("268986")).thenReturn(Optional.empty());
    when(actionExtractionService.extract(any(ActionExtractionRequest.class)))
        .thenReturn(new ActionExtractionResponse(List.of(new ExtractedActionDto(
            null, null, "학생증 신청", "신청", null, null, "신입생", List.of(), "TRINITY", SourceCategory.NOTICE, List.of(), false, 0.9, null
        ))));

    service.refreshCollectedNotices();

    verify(noticeSourceRepository).save(org.mockito.ArgumentMatchers.argThat(source ->
        "장학".equals(source.getNoticeBoardLabel())
    ));
    verify(actionPersistenceService).replaceSourceActions(any(NoticeSourceEntity.class), any(ActionExtractionResponse.class));
  }

  @Test
  void skipsActionRebuildWhenExistingNoticeHashIsUnchanged() {
    CukNoticeDetail detail = new CukNoticeDetail(
        "268986",
        "학생증 신청 안내",
        LocalDate.of(2026, 2, 27),
        "본문",
        List.of(),
        "https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=268986",
        "장학"
    );
    NoticeSourceEntity existing = new NoticeSourceEntity(UUID.randomUUID(), "학생증 신청 안내", SourceCategory.NOTICE, "본문", detail.detailUrl(), java.time.OffsetDateTime.now());
    existing.setExternalNoticeId("268986");
    existing.setContentHash(service.computeContentHashForTest(detail));

    when(cukNoticeClient.fetchLatestNotices(2)).thenReturn(List.of(detail));
    when(noticeSourceRepository.findByExternalNoticeId("268986")).thenReturn(Optional.of(existing));

    service.refreshCollectedNotices();

    verify(actionExtractionService, never()).extract(any(ActionExtractionRequest.class));
    verify(actionPersistenceService, never()).replaceSourceActions(any(NoticeSourceEntity.class), any(ActionExtractionResponse.class));
  }

  @Test
  void fallsBackToNoticeLevelDueWhenActionsDoNotContainDeadline() {
    CukNoticeDetail detail = new CukNoticeDetail(
        "269011",
        "[학사지원팀] 2026-1학기 수강과목 취소 기간 안내",
        LocalDate.of(2026, 3, 3),
        "수강과목 취소 신청기간 : 2026. 3. 24.(화) 09:00 ~ 3. 25.(수) 17:00",
        List.of(),
        "https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=269011",
        "학사"
    );
    when(cukNoticeClient.fetchLatestNotices(2)).thenReturn(List.of(detail));
    when(noticeSourceRepository.findByExternalNoticeId("269011")).thenReturn(Optional.empty());
    when(actionExtractionService.extract(any(ActionExtractionRequest.class)))
        .thenReturn(new ActionExtractionResponse(List.of(new ExtractedActionDto(
            null, null, "수강과목 취소", "[수강취소]", null, null, null, List.of(), null, SourceCategory.NOTICE, List.of(), false, 0.82, null
        ))));

    service.refreshCollectedNotices();

    verify(actionPersistenceService).replaceSourceActions(any(NoticeSourceEntity.class), any(ActionExtractionResponse.class));
    verify(noticeSourceRepository).save(org.mockito.ArgumentMatchers.argThat(source ->
        source.getPrimaryDueAt() != null
            && OffsetDateTime.parse("2026-03-25T17:00:00+09:00").isEqual(source.getPrimaryDueAt())
            && "3. 25. (수) 17:00".equals(source.getPrimaryDueLabel())
            && "action_required".equals(source.getActionability())
    ));
  }

  @Test
  void clearsPrimaryDueForInformationalNoticeEvenWhenBodyContainsDates() {
    CukNoticeDetail detail = new CukNoticeDetail(
        "268679",
        "[학사지원팀] 2026학년도 학점이월제도 안내",
        LocalDate.of(2026, 2, 23),
        "2026학년도 학점이월제도 안내\n확인방법 : [트리니티] - [학사정보] - [수업/성적] - [수강신청_학생(성심)]",
        List.of(),
        "https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=268679",
        "학사"
    );
    when(cukNoticeClient.fetchLatestNotices(2)).thenReturn(List.of(detail));
    when(noticeSourceRepository.findByExternalNoticeId("268679")).thenReturn(Optional.empty());
    when(actionExtractionService.extract(any(ActionExtractionRequest.class)))
        .thenReturn(new ActionExtractionResponse(List.of(new ExtractedActionDto(
            null, null, "확인", "[확인]", null, null, null, List.of(), "TRINITY", SourceCategory.NOTICE, List.of(), false, 0.72, null
        ))));

    service.refreshCollectedNotices();

    verify(noticeSourceRepository).save(org.mockito.ArgumentMatchers.argThat(source ->
        source.getPrimaryDueAt() == null
            && source.getPrimaryDueLabel() == null
            && "informational".equals(source.getActionability())
    ));
  }

  @Test
  void updatesExistingNoticeBoardLabelWhenNoticeIsReingested() {
    CukNoticeDetail detail = new CukNoticeDetail(
        "268986",
        "학생증 신청 안내",
        LocalDate.of(2026, 2, 27),
        "수정된 본문",
        List.of(),
        "https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=268986",
        "장학"
    );
    NoticeSourceEntity existing = new NoticeSourceEntity(
        UUID.randomUUID(),
        "학생증 신청 안내",
        SourceCategory.NOTICE,
        "기존 본문",
        detail.detailUrl(),
        OffsetDateTime.now()
    );
    existing.setExternalNoticeId("268986");
    existing.setNoticeBoardLabel(null);
    existing.setContentHash("stale-hash");

    when(cukNoticeClient.fetchLatestNotices(2)).thenReturn(List.of(detail));
    when(noticeSourceRepository.findByExternalNoticeId("268986")).thenReturn(Optional.of(existing));
    when(actionExtractionService.extract(any(ActionExtractionRequest.class)))
        .thenReturn(new ActionExtractionResponse(List.of()));

    service.refreshCollectedNotices();

    verify(noticeSourceRepository).save(org.mockito.ArgumentMatchers.argThat(source ->
        "장학".equals(source.getNoticeBoardLabel())
    ));
  }
}
