package com.cuk.notice2action.extraction.service.notice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.cuk.notice2action.extraction.api.dto.NoticeAttachmentDto;
import com.cuk.notice2action.extraction.api.dto.NoticeFeedResponse;
import com.cuk.notice2action.extraction.api.dto.PersonalizedNoticeDetailDto;
import com.cuk.notice2action.extraction.persistence.entity.ExtractedActionEntity;
import com.cuk.notice2action.extraction.persistence.entity.NoticeSourceEntity;
import com.cuk.notice2action.extraction.persistence.repository.NoticeSourceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.cuk.notice2action.extraction.service.extractor.ActionSummaryBuilder;
import com.cuk.notice2action.extraction.service.extractor.TaskPhraseExtractor;

@ExtendWith(MockitoExtension.class)
class NoticeFeedServiceTest {

  @Mock
  private NoticeSourceRepository noticeSourceRepository;

  private NoticeFeedService service;

  @BeforeEach
  void setUp() {
    service = new NoticeFeedService(
        noticeSourceRepository,
        new ObjectMapper(),
        new TaskPhraseExtractor(),
        new ActionSummaryBuilder()
    );
  }

  @Test
  void ranksProfileMatchedUrgentNoticeAboveGenericInformationalNotice() {
    NoticeSourceEntity matched = NoticeFixtures.noticeSource(
        "신입생 학생증 신청 안내",
        "신입생은 TRINITY에서 동의 후 학생증을 신청합니다.",
        LocalDate.of(2026, 2, 27),
        "action_required",
        OffsetDateTime.now(ZoneOffset.ofHours(9)).plusDays(3),
        List.of(NoticeFixtures.action("학생증 신청", "신입생", 0.93))
    );
    NoticeSourceEntity generic = NoticeFixtures.noticeSource(
        "도서관 이용 안내",
        "전체 학생 대상 도서관 이용 시간 안내입니다.",
        LocalDate.of(2026, 2, 28),
        "informational",
        null,
        List.of()
    );

    when(noticeSourceRepository.findAllAutoCollectedNotices()).thenReturn(List.of(generic, matched));

    NoticeFeedResponse response = service.getFeed(new NoticeProfile("컴퓨터공학과", 1, "신입생", List.of("학생증")), 0, 20);

    assertThat(response.notices()).hasSize(2);
    assertThat(response.notices().get(0).title()).isEqualTo("신입생 학생증 신청 안내");
    assertThat(response.notices().get(0).importanceReasons())
        .contains("신입생 해당", "학생증 키워드", "7일 이내 마감", "행동 필요 공지");
    assertThat(response.notices().get(0).relevanceScore()).isGreaterThan(response.notices().get(1).relevanceScore());
  }

  @Test
  void marksUnconfiguredProfileButStillUsesFreshnessAndActionability() {
    NoticeSourceEntity notice = NoticeFixtures.noticeSource(
        "수강신청 일정 안내",
        "TRINITY에서 수강신청을 진행합니다.",
        LocalDate.now(),
        "action_required",
        OffsetDateTime.now(ZoneOffset.ofHours(9)).plusDays(10),
        List.of(NoticeFixtures.action("수강신청", null, 0.8))
    );
    when(noticeSourceRepository.findAllAutoCollectedNotices()).thenReturn(List.of(notice));

    NoticeFeedResponse response = service.getFeed(new NoticeProfile(null, null, null, List.of()), 0, 20);

    assertThat(response.notices()).singleElement().satisfies(summary -> {
      assertThat(summary.importanceReasons()).contains("프로필 미설정", "14일 이내 마감", "행동 필요 공지");
      assertThat(summary.relevanceScore()).isGreaterThan(0);
    });
  }

  @Test
  void returnsDetailWithBodyAttachmentsAndActionBlocks() {
    UUID noticeId = UUID.randomUUID();
    NoticeSourceEntity notice = NoticeFixtures.noticeSource(
        noticeId,
        "학생증 신청 안내",
        "원문 본문",
        LocalDate.of(2026, 2, 27),
        "https://example.com/notices/268986",
        List.of(new CukNoticeAttachment("학생증 발급 신청서.hwp", "https://example.com/download/1")),
        "action_required",
        OffsetDateTime.now(ZoneOffset.ofHours(9)).plusDays(3),
        List.of(NoticeFixtures.action("학생증 신청", "신입생", 0.88))
    );
    when(noticeSourceRepository.findDetailById(noticeId)).thenReturn(java.util.Optional.of(notice));

    PersonalizedNoticeDetailDto detail = service.getDetail(noticeId, new NoticeProfile(null, null, "신입생", List.of("학생증")));

    assertThat(detail.body()).isEqualTo("원문 본문");
    assertThat(detail.attachments()).extracting(NoticeAttachmentDto::name).containsExactly("학생증 발급 신청서.hwp");
    assertThat(detail.actionBlocks()).singleElement().satisfies(block -> {
      assertThat(block.title()).isEqualTo("학생증 신청");
      assertThat(block.confidenceScore()).isEqualTo(0.88);
    });
    assertThat(detail.importanceReasons()).contains("신입생 해당", "학생증 키워드");
  }

  @Test
  void hidesActionBlocksForInformationalNoticeDetail() {
    UUID noticeId = UUID.randomUUID();
    NoticeSourceEntity notice = NoticeFixtures.noticeSource(
        noticeId,
        "2026학년도 학점이월제도 안내",
        "수강신청 및 학사관리 업무에 참고하시기 바랍니다.",
        LocalDate.of(2026, 2, 23),
        "https://example.com/notices/268679",
        List.of(),
        "informational",
        null,
        List.of(NoticeFixtures.action("확인", null, 0.7))
    );
    when(noticeSourceRepository.findDetailById(noticeId)).thenReturn(java.util.Optional.of(notice));

    PersonalizedNoticeDetailDto detail = service.getDetail(noticeId, new NoticeProfile(null, null, null, List.of()));

    assertThat(detail.actionBlocks()).isEmpty();
    assertThat(detail.actionability()).isEqualTo("informational");
  }

  @Test
  void prioritizesSourceTitleTaskAndMergesSameFamilyBlocks() {
    UUID noticeId = UUID.randomUUID();
    NoticeSourceEntity notice = NoticeFixtures.noticeSource(
        noticeId,
        "[학부대학운영팀] 2026학년도 1학기 군 복무 학점(군 e-러닝) 수강신청 안내",
        "원문 본문",
        LocalDate.of(2026, 2, 10),
        "https://example.com/notices/268242",
        List.of(),
        "action_required",
        null,
        List.of(
            NoticeFixtures.action(
                "군 e-러닝 수강신청",
                "할 일: 군 e-러닝 수강신청. 마감: 2026. 2. 12. (목).",
                OffsetDateTime.parse("2026-02-12T00:00:00+09:00"),
                "2026. 2. 12. (목)",
                "[]",
                null,
                null,
                0.88
            ),
            NoticeFixtures.action(
                "군 e-러닝 수강신청 (2/3)",
                "할 일: 군 e-러닝 수강신청. 시스템: TRINITY. 준비물: 계획서.",
                null,
                null,
                "[\"계획서\"]",
                "TRINITY",
                null,
                0.84
            ),
            NoticeFixtures.action(
                "군 e-러닝 수강신청 (3/3)",
                "할 일: 군 e-러닝 수강신청. 준비물: 성적증명서.",
                null,
                null,
                "[\"성적증명서\"]",
                "TRINITY",
                null,
                0.82
            )
        )
    );
    when(noticeSourceRepository.findDetailById(noticeId)).thenReturn(java.util.Optional.of(notice));

    PersonalizedNoticeDetailDto detail = service.getDetail(noticeId, new NoticeProfile(null, null, null, List.of()));

    assertThat(detail.actionBlocks()).singleElement().satisfies(block -> {
      assertThat(block.title()).isEqualTo("군 e-러닝 수강신청");
      assertThat(block.systemHint()).isEqualTo("TRINITY");
      assertThat(block.requiredItems()).containsExactly("계획서", "성적증명서");
      assertThat(block.summary()).contains("할 일: 군 e-러닝 수강신청.");
    });
  }

  @Test
  void keepsSourceTitleTaskFirstAndLimitsReturnedBlocks() {
    UUID noticeId = UUID.randomUUID();
    NoticeSourceEntity notice = NoticeFixtures.noticeSource(
        noticeId,
        "[학부대학] 2026학년도 1학기 「Self-making Project Portfolio」 교과목 참여 학생 모집 안내",
        "원문 본문",
        LocalDate.of(2026, 2, 2),
        "https://example.com/notices/268212",
        List.of(),
        "action_required",
        null,
        List.of(
            NoticeFixtures.action(
                "결과물을 대외공모전 제출",
                "할 일: 결과물을 대외공모전 제출. 마감: 2026년 2월 2일(월).",
                OffsetDateTime.parse("2026-02-02T00:00:00+09:00"),
                "2026년 2월 2일(월)",
                "[]",
                null,
                null,
                0.89
            ),
            NoticeFixtures.action(
                "Self-making Project Portfolio 참여 신청",
                "할 일: Self-making Project Portfolio 참여 신청. 준비물: 신청서.",
                null,
                null,
                "[\"신청서\"]",
                null,
                null,
                0.92
            ),
            NoticeFixtures.action(
                "Self-making Project Portfolio 참여 신청 (2/3)",
                "할 일: Self-making Project Portfolio 참여 신청.",
                null,
                null,
                "[]",
                null,
                null,
                0.82
            ),
            NoticeFixtures.action(
                "참여학생 결과 확인",
                "할 일: 참여학생 결과 확인.",
                null,
                null,
                "[]",
                null,
                null,
                0.65
            )
        )
    );
    when(noticeSourceRepository.findDetailById(noticeId)).thenReturn(java.util.Optional.of(notice));

    PersonalizedNoticeDetailDto detail = service.getDetail(noticeId, new NoticeProfile(null, null, null, List.of()));

    assertThat(detail.actionBlocks()).hasSizeLessThanOrEqualTo(3);
    assertThat(detail.actionBlocks().getFirst().title()).isEqualTo("Self-making Project Portfolio 참여 신청");
    assertThat(detail.actionBlocks()).extracting(block -> block.title())
        .doesNotContain("참여학생 결과 확인");
  }
}
