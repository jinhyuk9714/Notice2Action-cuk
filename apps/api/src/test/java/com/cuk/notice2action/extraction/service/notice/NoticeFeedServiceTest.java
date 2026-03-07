package com.cuk.notice2action.extraction.service.notice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.cuk.notice2action.extraction.api.dto.NoticeAttachmentDto;
import com.cuk.notice2action.extraction.api.dto.NoticeFeedResponse;
import com.cuk.notice2action.extraction.api.dto.PersonalizedNoticeDetailDto;
import com.cuk.notice2action.extraction.persistence.entity.EvidenceSnippetEntity;
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
        new ActionSummaryBuilder(),
        new NoticeActionabilityClassifier()
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
        .containsExactly("신입생 공지", "학생증 관련", "행동 필요");
    assertThat(response.notices().get(0).importanceReasons()).hasSizeLessThanOrEqualTo(3);
    assertThat(response.notices().get(0).relevanceScore()).isGreaterThan(response.notices().get(1).relevanceScore());
  }

  @Test
  void omitsProfileReasonWhenProfileIsUnconfigured() {
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
      assertThat(summary.importanceReasons()).containsExactly("행동 필요", "14일 안에 마감");
      assertThat(summary.importanceReasons()).doesNotContain("프로필 미설정");
      assertThat(summary.relevanceScore()).isGreaterThan(0);
    });
  }

  @Test
  void omitsProfileReasonWhenProfileIsConfiguredButEvidenceIsInsufficient() {
    NoticeSourceEntity notice = NoticeFixtures.noticeSource(
        "수강신청 일정 안내",
        "TRINITY에서 수강신청을 진행합니다.",
        LocalDate.now(),
        "action_required",
        OffsetDateTime.now(ZoneOffset.ofHours(9)).plusDays(5),
        List.of(NoticeFixtures.action("수강신청", null, 0.8))
    );
    when(noticeSourceRepository.findAllAutoCollectedNotices()).thenReturn(List.of(notice));

    NoticeFeedResponse response = service.getFeed(new NoticeProfile("컴퓨터공학과", 3, "재학생", List.of()), 0, 20);

    assertThat(response.notices()).singleElement().satisfies(summary -> {
      assertThat(summary.importanceReasons()).containsExactly("행동 필요", "7일 안에 마감");
      assertThat(summary.importanceReasons()).doesNotContain("프로필 추가 확인 필요", "다른 대상 공지");
      assertThat(summary.relevanceScore()).isEqualTo(35);
    });
  }

  @Test
  void pushesExplicitlyExcludedNoticeDownAndUsesOtherAudienceReason() {
    NoticeSourceEntity excluded = NoticeFixtures.noticeSource(
        "2학년 대상 학생증 신청 안내",
        "2학년 대상 학생증 신청 공지입니다.",
        LocalDate.of(2026, 3, 4),
        "action_required",
        OffsetDateTime.now(ZoneOffset.ofHours(9)).plusDays(3),
        List.of(NoticeFixtures.action("학생증 신청", "2학년", 0.9))
    );
    NoticeSourceEntity matched = NoticeFixtures.noticeSource(
        "3학년 학생증 신청 안내",
        "3학년 대상 학생증 신청 공지입니다.",
        LocalDate.of(2026, 3, 1),
        "action_required",
        OffsetDateTime.now(ZoneOffset.ofHours(9)).plusDays(4),
        List.of(NoticeFixtures.action("학생증 신청", "3학년", 0.9))
    );

    when(noticeSourceRepository.findAllAutoCollectedNotices()).thenReturn(List.of(excluded, matched));

    NoticeFeedResponse response = service.getFeed(new NoticeProfile(null, 3, null, List.of("학생증")), 0, 20);

    assertThat(response.notices()).extracting(summary -> summary.title())
        .containsExactly("3학년 학생증 신청 안내", "2학년 대상 학생증 신청 안내");
    assertThat(response.notices().get(1).importanceReasons()).contains("다른 대상 공지");
    assertThat(response.notices().get(1).relevanceScore()).isLessThan(response.notices().get(0).relevanceScore());
  }


  @Test
  void treatsCombinedFreshmanTransferAudienceAsMatchForFreshmanProfile() {
    NoticeSourceEntity combinedAudience = NoticeFixtures.noticeSource(
        "2026학년도 신·편입생(등록완료자) 학번조회 안내",
        "신·편입생 대상 학번조회 및 회원가입 안내입니다.",
        LocalDate.of(2026, 2, 24),
        "action_required",
        null,
        List.of(NoticeFixtures.action("학번 조회", "신·편입생", 0.9))
    );
    NoticeSourceEntity generic = NoticeFixtures.noticeSource(
        "도서관 이용 안내",
        "전체 학생 대상 도서관 이용 시간 안내입니다.",
        LocalDate.of(2026, 2, 28),
        "informational",
        null,
        List.of()
    );

    when(noticeSourceRepository.findAllAutoCollectedNotices()).thenReturn(List.of(generic, combinedAudience));

    NoticeFeedResponse response = service.getFeed(new NoticeProfile("컴퓨터정보공학부", 1, "신입생", List.of()), 0, 20);

    assertThat(response.notices().get(0).title()).isEqualTo("2026학년도 신·편입생(등록완료자) 학번조회 안내");
    assertThat(response.notices().get(0).importanceReasons()).contains("신입생 공지");
    assertThat(response.notices().get(0).importanceReasons()).doesNotContain("다른 대상 공지");
  }

  @Test
  void treatsGraduationCandidateAudienceAsMatchForGraduatingProfile() {
    NoticeSourceEntity graduationAudience = NoticeFixtures.noticeSource(
        "[학사지원팀] 2025학년도 후기(2026년 8월) 졸업대상자 예비 졸업사정 일정 안내",
        "졸업대상자 예비 졸업사정 일정을 확인하시기 바랍니다.",
        LocalDate.of(2026, 2, 20),
        "informational",
        null,
        List.of()
    );
    NoticeSourceEntity generic = NoticeFixtures.noticeSource(
        "수강과목 취소 기간 안내",
        "수강과목 취소 안내입니다.",
        LocalDate.of(2026, 3, 3),
        "action_required",
        OffsetDateTime.now(ZoneOffset.ofHours(9)).plusDays(4),
        List.of(NoticeFixtures.action("수강과목 취소 신청", null, 0.8))
    );

    when(noticeSourceRepository.findAllAutoCollectedNotices()).thenReturn(List.of(generic, graduationAudience));

    NoticeFeedResponse response = service.getFeed(new NoticeProfile(null, 4, "졸업예정자", List.of()), 0, 20);

    assertThat(response.notices().get(0).title()).isEqualTo("[학사지원팀] 2025학년도 후기(2026년 8월) 졸업대상자 예비 졸업사정 일정 안내");
    assertThat(response.notices().get(0).importanceReasons()).contains("졸업예정자 공지");
    assertThat(response.notices().get(0).importanceReasons()).doesNotContain("다른 대상 공지");
  }

  @Test
  void keepsOtherAudienceNoticeBelowMatchedNoticeEvenWhenBodyMentionsDepartment() {
    NoticeSourceEntity excluded = NoticeFixtures.noticeSource(
        "2026학년도 신입생 수강신청 안내",
        "컴퓨터정보공학부 분반 안내가 포함된 신입생 수강신청 공지입니다.",
        LocalDate.of(2026, 2, 13),
        "action_required",
        OffsetDateTime.now(ZoneOffset.ofHours(9)).plusDays(3),
        List.of(NoticeFixtures.action("신입생 수강신청", "컴퓨터정보공학부", 0.9))
    );
    NoticeSourceEntity matched = NoticeFixtures.noticeSource(
        "[학부대학] 2026학년도 1학기 <I-DESIGN> 수강신청 관련 안내",
        "컴퓨터정보공학부 학생 분반 수강신청 안내입니다.",
        LocalDate.of(2026, 2, 20),
        "action_required",
        OffsetDateTime.now(ZoneOffset.ofHours(9)).plusDays(4),
        List.of(NoticeFixtures.action("I-DESIGN 수강신청", "컴퓨터정보공학부", 0.92))
    );

    when(noticeSourceRepository.findAllAutoCollectedNotices()).thenReturn(List.of(excluded, matched));

    NoticeFeedResponse response = service.getFeed(
        new NoticeProfile("컴퓨터정보공학부", 3, "재학생", List.of()),
        0,
        20
    );

    assertThat(response.notices()).extracting(summary -> summary.title())
        .containsExactly(
            "[학부대학] 2026학년도 1학기 <I-DESIGN> 수강신청 관련 안내",
            "2026학년도 신입생 수강신청 안내"
        );
    assertThat(response.notices().get(1).importanceReasons()).contains("다른 대상 공지");
    assertThat(response.notices().get(1).importanceReasons()).doesNotContain("컴퓨터정보공학부 공지");
  }


  @Test
  void matchesKeywordFromActionTitleWhenBodyAndTitleDoNotContainIt() {
    NoticeSourceEntity notice = NoticeFixtures.noticeSource(
        "교과목 운영 변경 안내",
        "프로젝트 진행 절차를 확인하시기 바랍니다.",
        LocalDate.of(2026, 2, 20),
        "action_required",
        OffsetDateTime.now(ZoneOffset.ofHours(9)).plusDays(4),
        List.of(NoticeFixtures.action("학생증 신청", null, 0.92))
    );

    when(noticeSourceRepository.findAllAutoCollectedNotices()).thenReturn(List.of(notice));

    NoticeFeedResponse response = service.getFeed(
        new NoticeProfile(null, null, null, List.of("학생증")),
        0,
        20
    );

    assertThat(response.notices()).singleElement().satisfies(summary ->
        assertThat(summary.importanceReasons()).contains("학생증 관련")
    );
  }

  @Test
  void matchesKeywordFromAttachmentNamesWhenBodyAndTitleDoNotContainIt() {
    NoticeSourceEntity notice = NoticeFixtures.noticeSource(
        UUID.randomUUID(),
        "교과목 운영 변경 안내",
        "프로젝트 진행 절차를 확인하시기 바랍니다.",
        LocalDate.of(2026, 2, 20),
        "https://example.com/notices/attachment-keyword",
        List.of(new CukNoticeAttachment("학생증 발급 신청서.hwp", "https://example.com/files/1")),
        "informational",
        null,
        List.of()
    );

    when(noticeSourceRepository.findAllAutoCollectedNotices()).thenReturn(List.of(notice));

    NoticeFeedResponse response = service.getFeed(
        new NoticeProfile(null, null, null, List.of("학생증")),
        0,
        20
    );

    assertThat(response.notices()).singleElement().satisfies(summary ->
        assertThat(summary.importanceReasons()).contains("학생증 관련")
    );
  }

  @Test
  void usesBodyAudienceSignalWhenTitleIsImplicit() {
    NoticeSourceEntity notice = NoticeFixtures.noticeSource(
        "[학부대학] 2026학년도 1학기 <I-DESIGN> 수강신청 관련 안내",
        "1학년 신입생 최초 수강신청 기간 안내와 컴퓨터정보공학부 분반 수강신청 안내입니다.",
        LocalDate.of(2026, 2, 20),
        "action_required",
        OffsetDateTime.now(ZoneOffset.ofHours(9)).plusDays(4),
        List.of(NoticeFixtures.action("I-DESIGN 수강신청", "컴퓨터정보공학부", 0.92))
    );

    when(noticeSourceRepository.findAllAutoCollectedNotices()).thenReturn(List.of(notice));

    NoticeFeedResponse response = service.getFeed(
        new NoticeProfile("컴퓨터정보공학부", 1, "신입생", List.of()),
        0,
        20
    );

    assertThat(response.notices()).singleElement().satisfies(summary -> {
      assertThat(summary.importanceReasons()).contains("신입생 공지");
      assertThat(summary.importanceReasons()).contains("행동 필요");
    });
  }

  @Test
  void usesFreshnessOnlyAsFallbackReason() {
    NoticeSourceEntity informational = NoticeFixtures.noticeSource(
        "도서관 휴관 안내",
        "전체 학생 대상 도서관 휴관 안내입니다.",
        LocalDate.now().minusDays(1),
        "informational",
        null,
        List.of()
    );
    NoticeSourceEntity actionRequired = NoticeFixtures.noticeSource(
        "수강신청 일정 안내",
        "TRINITY에서 수강신청을 진행합니다.",
        LocalDate.now(),
        "action_required",
        OffsetDateTime.now(ZoneOffset.ofHours(9)).plusDays(5),
        List.of(NoticeFixtures.action("수강신청", null, 0.8))
    );

    when(noticeSourceRepository.findAllAutoCollectedNotices()).thenReturn(List.of(informational, actionRequired));

    NoticeFeedResponse response = service.getFeed(new NoticeProfile(null, null, null, List.of()), 0, 20);

    assertThat(response.notices()).extracting(summary -> summary.title())
        .containsExactly("수강신청 일정 안내", "도서관 휴관 안내");
    assertThat(response.notices().get(0).importanceReasons()).doesNotContain("최근 등록", "이번 주 등록");
    assertThat(response.notices().get(1).importanceReasons()).containsExactly("최근 등록");
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
    assertThat(detail.importanceReasons()).contains("신입생 공지", "학생증 관련");
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

  @Test
  void expandsDetailDueLabelAndFiltersEvidenceToContextualTopThree() {
    UUID noticeId = UUID.randomUUID();
    ExtractedActionEntity action = new ExtractedActionEntity(
        UUID.randomUUID(),
        null,
        "신입생 수강신청",
        "할 일: 신입생 수강신청. 마감: ~ 3/9.",
        OffsetDateTime.parse("2026-03-08T15:00:00Z"),
        "~ 3/9",
        "2026학년도 신입생 수강신청 안내",
        "[]",
        null,
        false,
        0.79,
        OffsetDateTime.now(ZoneOffset.ofHours(9))
    );
    action.addEvidence(new EvidenceSnippetEntity(UUID.randomUUID(), action, "dueAtLabel", "~ 3/9", 0.85, OffsetDateTime.now(ZoneOffset.ofHours(9))));
    action.addEvidence(new EvidenceSnippetEntity(UUID.randomUUID(), action, "actionVerb", "이수 - 자세한 내용은 [2026 학사제도 안내책자] 확인바랍니다.", 0.76, OffsetDateTime.now(ZoneOffset.ofHours(9))));
    action.addEvidence(new EvidenceSnippetEntity(UUID.randomUUID(), action, "eligibility", "2026학년도 신입생 수강신청 안내", 0.75, OffsetDateTime.now(ZoneOffset.ofHours(9))));

    NoticeSourceEntity notice = NoticeFixtures.noticeSource(
        noticeId,
        "2026학년도 신입생 수강신청 안내",
        """
        2026학년도 신입생 수강신청 안내
        수강신청 변경기간:
        2026.03.03.(화) ~ 03.9.(월) 09:00 ~ 17:00
        (주말 및 공휴일 제외)
        STEP 1. 교양영역에서 필수로 수강해야할 과목을 우선적으로 수강신청합니다.
        """,
        LocalDate.of(2026, 2, 13),
        "https://example.com/notices/268547",
        List.of(),
        "action_required",
        OffsetDateTime.parse("2026-03-08T15:00:00Z"),
        List.of(action)
    );

    when(noticeSourceRepository.findDetailById(noticeId)).thenReturn(java.util.Optional.of(notice));

    PersonalizedNoticeDetailDto detail = service.getDetail(noticeId, new NoticeProfile("컴퓨터정보공학부", 1, null, List.of()));

    assertThat(detail.actionBlocks()).singleElement().satisfies(block -> {
      assertThat(block.dueAtLabel())
          .startsWith("수강신청 변경기간:")
          .contains("2026.03.03.(화) ~ 03.9.(월) 09:00 ~ 17:00")
          .contains("(주말 및 공휴일 제외)");
      assertThat(block.summary()).contains("마감: 2026.03.03.(화) ~ 03.9.(월) 09:00 ~ 17:00 (주말 및 공휴일 제외).");
      assertThat(block.evidence()).hasSizeLessThanOrEqualTo(3);
      assertThat(block.evidence()).extracting(evidence -> evidence.snippet())
          .anySatisfy(snippet -> assertThat(snippet).contains("수강신청 변경기간"))
          .noneSatisfy(snippet -> assertThat(snippet).isEqualTo("~ 3/9"))
          .noneSatisfy(snippet -> assertThat(snippet).contains("자세한 내용은"))
          .noneSatisfy(snippet -> assertThat(snippet).isEqualTo("2026학년도 신입생 수강신청 안내"));
    });
  }

  @Test
  void prefersContextualDueAndSystemEvidenceForDropNoticeDetail() {
    UUID noticeId = UUID.randomUUID();
    ExtractedActionEntity action = new ExtractedActionEntity(
        UUID.randomUUID(),
        null,
        "수강과목 취소 신청",
        "할 일: 수강과목 취소 신청. 마감: 2026. 3. 24.(화) 09:00 ~ 3. 25.(수) 17:00. 시스템: TRINITY.",
        OffsetDateTime.parse("2026-03-25T17:00:00+09:00"),
        "3. 25. (수) 17:00",
        null,
        "[]",
        "TRINITY",
        false,
        0.88,
        OffsetDateTime.now(ZoneOffset.ofHours(9))
    );
    action.addEvidence(new EvidenceSnippetEntity(
        UUID.randomUUID(),
        action,
        "summary",
        "2026-1학기 수강과목 취소 기간과 절차를 안내드리오니, 신청이 필요한 학생은 기간 내 신청을 완료하시기 바랍니다. 1. 수강과목 취소 신청기간 : 2026. 3. 24.(화) 09:00 ~ 3. 25.(수) 17:00",
        0.83,
        OffsetDateTime.now(ZoneOffset.ofHours(9))
    ));
    action.addEvidence(new EvidenceSnippetEntity(
        UUID.randomUUID(),
        action,
        "systemHint",
        "으며, 취소 완료 후 복원 절대 불가함 2. 수강과목 취소 절차 가. [트리니티] - [수업/성적] - [수강신청]",
        0.79,
        OffsetDateTime.now(ZoneOffset.ofHours(9))
    ));

    NoticeSourceEntity notice = NoticeFixtures.noticeSource(
        noticeId,
        "[학사지원팀] 2026-1학기 수강과목 취소 기간 안내",
        """
        2026-1학기 수강과목 취소 기간과 절차를 안내드리오니, 신청이 필요한 학생은 기간 내 신청을 완료하시기 바랍니다.
        1. 수강과목 취소 신청기간 : 2026. 3. 24.(화) 09:00 ~ 3. 25.(수) 17:00
        ※ 신청기간에만 접수 받으며, 취소 완료 후 복원 절대 불가함
        2. 수강과목 취소 절차
        가. [트리니티] - [수업/성적] - [수강신청] - [수강취소신청]
        나. 수강신청 내역 확인 후, 우측의 “취소신청” 버튼 클릭
        """,
        LocalDate.of(2026, 3, 3),
        "https://example.com/notices/269011",
        List.of(),
        "action_required",
        OffsetDateTime.parse("2026-03-25T17:00:00+09:00"),
        List.of(action)
    );

    when(noticeSourceRepository.findDetailById(noticeId)).thenReturn(java.util.Optional.of(notice));

    PersonalizedNoticeDetailDto detail = service.getDetail(noticeId, new NoticeProfile(null, null, null, List.of()));

    assertThat(detail.actionBlocks()).singleElement().satisfies(block -> {
      assertThat(block.dueAtLabel()).isEqualTo("수강과목 취소 신청기간: 2026. 3. 24.(화) 09:00 ~ 3. 25.(수) 17:00");
      assertThat(block.summary()).contains("마감: 2026. 3. 24.(화) 09:00 ~ 3. 25.(수) 17:00.");
      assertThat(block.evidence()).extracting(evidence -> evidence.snippet())
          .anySatisfy(snippet -> assertThat(snippet).contains("수강과목 취소 신청기간 : 2026. 3. 24.(화) 09:00 ~ 3. 25.(수) 17:00"))
          .contains("가. [트리니티] - [수업/성적] - [수강신청] - [수강취소신청]")
          .noneSatisfy(snippet -> assertThat(snippet).startsWith("으며,"));
    });
  }

  @Test
  void prefersExplicitDueLineOverExplanatorySentenceForIdesignDetail() {
    UUID noticeId = UUID.randomUUID();
    ExtractedActionEntity action = new ExtractedActionEntity(
        UUID.randomUUID(),
        null,
        "I-DESIGN 수강신청",
        "할 일: I-DESIGN 수강신청. 마감: ~ 3/9.",
        OffsetDateTime.parse("2026-03-08T15:00:00+09:00"),
        "~ 3/9",
        null,
        "[]",
        null,
        false,
        0.86,
        OffsetDateTime.now(ZoneOffset.ofHours(9))
    );
    action.addEvidence(new EvidenceSnippetEntity(
        UUID.randomUUID(),
        action,
        "eligibility",
        "2026학년도 1학기 <I-DESIGN> 수강신청 관련 안내 <I-DESIGN> 교과목 학과별 수강 가",
        0.7,
        OffsetDateTime.now(ZoneOffset.ofHours(9))
    ));

    NoticeSourceEntity notice = NoticeFixtures.noticeSource(
        noticeId,
        "[학부대학] 2026학년도 1학기 <I-DESIGN> 수강신청 관련 안내",
        """
        2026학년도 1학기 <I-DESIGN> 수강신청 관련 안내
        ◎ 수강신청 변경기간[3/3(화) ~ 3/9(월)]에는 본인 학과가 아닌 다른 분반에 수강신청 가능합니다.
        (단, 자유전공학부/인문사회계열/자연공학계열 분반은 수강신청 변경기간에도 본인 계열 분반만 수강 가능)
        ◎ 수강신청 기간
        - 재수강 분반 수강신청: 2/3(화)~2/5(목)
        - 신입생 분반 수강신청: 2/25(수)~2/26(목)
        - 수강신청 변경기간: 3/3(화)~3/9(월), 09:00 ~ 17:00
        ◎ 문의처: 학부대학운영팀 02-2164-4647, 4992
        """,
        LocalDate.of(2026, 2, 28),
        "https://example.com/notices/268226",
        List.of(),
        "action_required",
        OffsetDateTime.parse("2026-03-08T15:00:00+09:00"),
        List.of(action)
    );

    when(noticeSourceRepository.findDetailById(noticeId)).thenReturn(java.util.Optional.of(notice));

    PersonalizedNoticeDetailDto detail = service.getDetail(noticeId, new NoticeProfile("컴퓨터정보공학부", 1, null, List.of()));

    assertThat(detail.actionBlocks()).singleElement().satisfies(block -> {
      assertThat(block.title()).isEqualTo("I-DESIGN 수강신청");
      assertThat(block.dueAtLabel()).isEqualTo("수강신청 변경기간: 3/3(화)~3/9(월), 09:00 ~ 17:00");
      assertThat(block.summary()).contains("마감: 3/3(화)~3/9(월), 09:00 ~ 17:00.");
      assertThat(block.evidence()).extracting(evidence -> evidence.snippet())
          .contains("수강신청 변경기간: 3/3(화)~3/9(월), 09:00 ~ 17:00")
          .noneSatisfy(snippet -> assertThat(snippet).contains("수강신청 가능합니다"))
          .noneSatisfy(snippet -> assertThat(snippet).contains("교과목 학과별 수강 가"));
    });
  }

  @Test
  void prefersProceduralSystemEvidenceOverGenericSystemMention() {
    UUID noticeId = UUID.randomUUID();
    ExtractedActionEntity action = new ExtractedActionEntity(
        UUID.randomUUID(),
        null,
        "수강과목 취소 신청",
        "할 일: 수강과목 취소 신청. 시스템: TRINITY.",
        null,
        null,
        null,
        "[]",
        "TRINITY",
        false,
        0.8,
        OffsetDateTime.now(ZoneOffset.ofHours(9))
    );

    NoticeSourceEntity notice = NoticeFixtures.noticeSource(
        noticeId,
        "[학사지원팀] 2026-1학기 수강과목 취소 기간 안내",
        """
        TRINITY에서 확인하시기 바랍니다.
        수강과목 취소 절차
        가. [TRINITY] - [수업/성적] - [수강신청] - [수강취소신청]
        """,
        LocalDate.of(2026, 3, 3),
        "https://example.com/notices/269011",
        List.of(),
        "action_required",
        null,
        List.of(action)
    );

    when(noticeSourceRepository.findDetailById(noticeId)).thenReturn(java.util.Optional.of(notice));

    PersonalizedNoticeDetailDto detail = service.getDetail(noticeId, new NoticeProfile(null, null, null, List.of()));

    assertThat(detail.actionBlocks()).singleElement().satisfies(block -> {
      assertThat(block.evidence()).extracting(evidence -> evidence.snippet())
          .contains("가. [TRINITY] - [수업/성적] - [수강신청] - [수강취소신청]")
          .noneSatisfy(snippet -> assertThat(snippet).contains("확인하시기 바랍니다"));
    });
  }

  @Test
  void excludesPipeDelimitedTableRowsFromDetailEvidence() {
    UUID noticeId = UUID.randomUUID();
    ExtractedActionEntity action = new ExtractedActionEntity(
        UUID.randomUUID(),
        null,
        "신입생 수강신청",
        "할 일: 신입생 수강신청.",
        null,
        null,
        null,
        "[]",
        null,
        false,
        0.77,
        OffsetDateTime.now(ZoneOffset.ofHours(9))
    );
    action.addEvidence(new EvidenceSnippetEntity(
        UUID.randomUUID(),
        action,
        "summary",
        "분반 | 계열 / 학과 | 강의시간표",
        0.75,
        OffsetDateTime.now(ZoneOffset.ofHours(9))
    ));

    NoticeSourceEntity notice = NoticeFixtures.noticeSource(
        noticeId,
        "2026학년도 신입생 수강신청 안내",
        """
        2026학년도 신입생 수강신청 안내
        분반 | 계열 / 학과 | 강의시간표
        05 | 컴퓨터정보공학부 | 월1-3교시
        STEP 1. 교양영역에서 필수로 수강해야할 과목을 우선적으로 수강신청합니다.
        """,
        LocalDate.of(2026, 2, 13),
        "https://example.com/notices/268547",
        List.of(),
        "action_required",
        null,
        List.of(action)
    );

    when(noticeSourceRepository.findDetailById(noticeId)).thenReturn(java.util.Optional.of(notice));

    PersonalizedNoticeDetailDto detail = service.getDetail(noticeId, new NoticeProfile(null, null, null, List.of()));

    assertThat(detail.actionBlocks()).singleElement().satisfies(block ->
        assertThat(block.evidence()).extracting(evidence -> evidence.snippet())
            .noneSatisfy(snippet -> assertThat(snippet).contains("|"))
    );
  }

  @Test
  void downgradesImageOnlyNoticeWithoutEvidenceToInformationalOnRead() {
    UUID noticeId = UUID.randomUUID();
    NoticeSourceEntity notice = NoticeFixtures.noticeSource(
        noticeId,
        "[2~4학년] 2026학년도 1학기 부전공(2차) 신청/변경 안내",
        "본문이 이미지로만 제공된 공지입니다.",
        LocalDate.of(2026, 2, 27),
        "https://example.com/notices/268989",
        List.of(),
        "action_required",
        null,
        List.of(NoticeFixtures.action(
            "부전공 신청 또는 변경",
            "할 일: 부전공 신청 또는 변경.",
            null,
            null,
            "[]",
            null,
            null,
            0.78
        ))
    );
    when(noticeSourceRepository.findDetailById(noticeId)).thenReturn(java.util.Optional.of(notice));
    when(noticeSourceRepository.findAllAutoCollectedNotices()).thenReturn(List.of(notice));

    PersonalizedNoticeDetailDto detail = service.getDetail(noticeId, new NoticeProfile(null, 3, null, List.of()));
    NoticeFeedResponse feed = service.getFeed(new NoticeProfile(null, 3, null, List.of()), 0, 20);

    assertThat(detail.actionability()).isEqualTo("informational");
    assertThat(detail.actionBlocks()).isEmpty();
    assertThat(feed.notices()).singleElement().satisfies(summary -> {
      assertThat(summary.actionability()).isEqualTo("informational");
      assertThat(summary.dueHint()).isNull();
      assertThat(summary.importanceReasons()).doesNotContain("행동 필요");
    });
  }

  @Test
  void buildsSingleConservativeBlockForImageOnlyNoticeWithAttachmentForms() {
    UUID noticeId = UUID.randomUUID();
    NoticeSourceEntity notice = NoticeFixtures.noticeSource(
        noticeId,
        "[학사지원팀] 2026학년도 1학기 학기 중 취업학생 출결 사항 안내",
        "본문이 이미지로만 제공된 공지입니다.",
        LocalDate.of(2026, 2, 27),
        "https://example.com/notices/269154",
        List.of(
            new CukNoticeAttachment("1. 공결허가원(취업).hwp", "https://example.com/files/1"),
            new CukNoticeAttachment("2. 개인정보 수집활용 동의서(재직조회).hwp", "https://example.com/files/2"),
            new CukNoticeAttachment("3. 취업공결 확인서(학기 중 취업학생).hwp", "https://example.com/files/3")
        ),
        "informational",
        null,
        List.of()
    );

    when(noticeSourceRepository.findDetailById(noticeId)).thenReturn(java.util.Optional.of(notice));

    PersonalizedNoticeDetailDto detail = service.getDetail(noticeId, new NoticeProfile(null, null, null, List.of()));

    assertThat(detail.actionability()).isEqualTo("action_required");
    assertThat(detail.actionBlocks()).singleElement().satisfies(block -> {
      assertThat(block.title()).isEqualTo("취업공결 관련 서류 준비 및 제출");
      assertThat(block.summary()).contains("할 일: 취업공결 관련 서류 준비 및 제출.");
      assertThat(block.summary()).contains("준비물:");
      assertThat(block.summary()).doesNotContain("마감:");
      assertThat(block.summary()).doesNotContain("시스템:");
      assertThat(block.dueAtIso()).isNull();
      assertThat(block.dueAtLabel()).isNull();
      assertThat(block.systemHint()).isNull();
      assertThat(block.requiredItems())
          .containsExactly(
              "1. 공결허가원(취업).hwp",
              "2. 개인정보 수집활용 동의서(재직조회).hwp",
              "3. 취업공결 확인서(학기 중 취업학생).hwp"
          );
      assertThat(block.evidence()).hasSizeLessThanOrEqualTo(3);
      assertThat(block.evidence()).extracting(evidence -> evidence.snippet())
          .contains("[학사지원팀] 2026학년도 1학기 학기 중 취업학생 출결 사항 안내")
          .anySatisfy(snippet -> assertThat(snippet).contains("공결허가원"));
    });
  }
}
