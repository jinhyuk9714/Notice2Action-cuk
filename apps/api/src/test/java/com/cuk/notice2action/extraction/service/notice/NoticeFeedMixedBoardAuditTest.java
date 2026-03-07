package com.cuk.notice2action.extraction.service.notice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cuk.notice2action.extraction.api.dto.NoticeFeedResponse;
import com.cuk.notice2action.extraction.persistence.entity.NoticeSourceEntity;
import com.cuk.notice2action.extraction.persistence.repository.NoticeSourceRepository;
import com.cuk.notice2action.extraction.service.extractor.ActionSummaryBuilder;
import com.cuk.notice2action.extraction.service.extractor.TaskPhraseExtractor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoticeFeedMixedBoardAuditTest {

  private NoticeFeedService service;
  private NoticeSourceRepository repository;
  private MixedBoardProfiles profiles;

  @BeforeEach
  void setUp() throws Exception {
    try (InputStream stream = Objects.requireNonNull(
        getClass().getResourceAsStream("/fixtures/notice-feed/quality/mixed-board-ranking.json"),
        "missing mixed-board-ranking.json"
    )) {
      profiles = new ObjectMapper().readValue(stream, MixedBoardProfiles.class);
    }
    repository = mock(NoticeSourceRepository.class);
    service = new NoticeFeedService(
        repository,
        new ObjectMapper(),
        new TaskPhraseExtractor(),
        new ActionSummaryBuilder(),
        new NoticeActionabilityClassifier()
    );
  }

  @Test
  void keepsMatchedActionableNoticesAboveGenericInformationalNoticesAcrossBoards() {
    NoticeSourceEntity matchedActionable = NoticeFixtures.noticeSource(
        UUID.nameUUIDFromBytes("269011".getBytes()),
        "[학사지원팀] 2026-1학기 수강과목 취소 기간 안내",
        "수강과목 취소 기간 안내",
        LocalDate.of(2026, 3, 3),
        "https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=269011",
        List.of(),
        "action_required",
        OffsetDateTime.now(ZoneOffset.ofHours(9)).plusDays(7),
        List.of(NoticeFixtures.action("수강과목 취소 신청", null, 0.9))
    );
    matchedActionable.setExternalNoticeId("269011");
    matchedActionable.setNoticeBoardLabel("학사");

    NoticeSourceEntity matchedByProfile = NoticeFixtures.noticeSource(
        UUID.nameUUIDFromBytes("268989".getBytes()),
        "[장학팀] 2026학년도 1학기 3학년 장학 신청 안내",
        "3학년 대상 장학 신청 안내",
        LocalDate.of(2026, 3, 2),
        "https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=268989",
        List.of(),
        "action_required",
        null,
        List.of(NoticeFixtures.action("장학 신청", "3학년", 0.88))
    );
    matchedByProfile.setExternalNoticeId("268989");
    matchedByProfile.setNoticeBoardLabel("장학");

    NoticeSourceEntity genericInfo = NoticeFixtures.noticeSource(
        UUID.nameUUIDFromBytes("900001".getBytes()),
        "[일반] 2026학년도 봄 축제 안내",
        "전체 학생 대상 축제 안내입니다.",
        LocalDate.of(2026, 3, 5),
        "https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=900001",
        List.of(),
        "informational",
        null,
        List.of()
    );
    genericInfo.setExternalNoticeId("900001");
    genericInfo.setNoticeBoardLabel("일반");

    NoticeSourceEntity otherAudience = NoticeFixtures.noticeSource(
        UUID.nameUUIDFromBytes("900002".getBytes()),
        "[취창업] 1학년 대상 취업 기초캠프 신청 안내",
        "1학년 대상 취업 기초캠프 신청 안내",
        LocalDate.of(2026, 3, 4),
        "https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=900002",
        List.of(),
        "action_required",
        null,
        List.of(NoticeFixtures.action("취업 기초캠프 신청", "1학년", 0.83))
    );
    otherAudience.setExternalNoticeId("900002");
    otherAudience.setNoticeBoardLabel("취창업");

    when(repository.findAllAutoCollectedNotices()).thenReturn(List.of(genericInfo, otherAudience, matchedByProfile, matchedActionable));

    MixedBoardProfile profile = profiles.profiles().getFirst();
    NoticeFeedResponse feed = service.getFeed(new NoticeProfile(profile.department(), profile.year(), profile.status(), profile.keywords()), 0, 20);

    Map<String, Integer> orderById = new LinkedHashMap<>();
    for (int i = 0; i < feed.notices().size(); i++) {
      Matcher matcher = Pattern.compile("articleNo=(\\d+)").matcher(feed.notices().get(i).sourceUrl());
      String id = matcher.find() ? matcher.group(1) : feed.notices().get(i).sourceUrl();
      orderById.put(id, i);
    }

    Map<String, com.cuk.notice2action.extraction.api.dto.PersonalizedNoticeSummaryDto> byId = feed.notices().stream()
        .collect(Collectors.toMap(summary -> {
          Matcher matcher = Pattern.compile("articleNo=(\\d+)").matcher(summary.sourceUrl());
          return matcher.find() ? matcher.group(1) : summary.sourceUrl();
        }, summary -> summary));

    assertThat(orderById.get("269011")).isLessThan(orderById.get("900001"));
    assertThat(orderById.get("268989")).isLessThan(orderById.get("900001"));
    assertThat(orderById.get("900002")).isGreaterThan(orderById.get("269011"));
    assertThat(byId.get("269011").boardLabel()).isEqualTo("학사");
    assertThat(byId.get("268989").boardLabel()).isEqualTo("장학");
    assertThat(byId.get("900001").boardLabel()).isEqualTo("일반");
    assertThat(byId.get("900002").importanceReasons()).contains("다른 대상 공지");
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record MixedBoardProfiles(List<MixedBoardProfile> profiles) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record MixedBoardProfile(String name, String department, Integer year, String status, List<String> keywords) {}
}
