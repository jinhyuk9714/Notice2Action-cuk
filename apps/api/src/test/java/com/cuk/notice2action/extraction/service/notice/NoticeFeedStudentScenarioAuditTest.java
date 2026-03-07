package com.cuk.notice2action.extraction.service.notice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cuk.notice2action.extraction.api.dto.ActionExtractionRequest;
import com.cuk.notice2action.extraction.api.dto.ActionExtractionResponse;
import com.cuk.notice2action.extraction.api.dto.EvidenceSnippetDto;
import com.cuk.notice2action.extraction.api.dto.ExtractedActionDto;
import com.cuk.notice2action.extraction.api.dto.NoticeFeedResponse;
import com.cuk.notice2action.extraction.domain.SourceCategory;
import com.cuk.notice2action.extraction.persistence.entity.EvidenceSnippetEntity;
import com.cuk.notice2action.extraction.persistence.entity.ExtractedActionEntity;
import com.cuk.notice2action.extraction.persistence.entity.NoticeSourceEntity;
import com.cuk.notice2action.extraction.persistence.repository.NoticeSourceRepository;
import com.cuk.notice2action.extraction.service.HeuristicActionExtractionService;
import com.cuk.notice2action.extraction.service.extractor.ActionSegmenter;
import com.cuk.notice2action.extraction.service.extractor.ActionSummaryBuilder;
import com.cuk.notice2action.extraction.service.extractor.ActionVerbExtractor;
import com.cuk.notice2action.extraction.service.extractor.DateExtractor;
import com.cuk.notice2action.extraction.service.extractor.EligibilityExtractor;
import com.cuk.notice2action.extraction.service.extractor.RequiredItemExtractor;
import com.cuk.notice2action.extraction.service.extractor.StructuredEligibilityParser;
import com.cuk.notice2action.extraction.service.extractor.SystemHintExtractor;
import com.cuk.notice2action.extraction.service.extractor.TaskPhraseExtractor;
import com.cuk.notice2action.extraction.service.extractor.TextNormalizer;
import com.cuk.notice2action.extraction.service.extractor.TitleDeriver;
import com.cuk.notice2action.extraction.service.model.DateMatch;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
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

class NoticeFeedStudentScenarioAuditTest {

  private static final ZoneOffset APP_OFFSET = ZoneOffset.ofHours(9);
  private static final LocalDate AUDIT_DATE = LocalDate.of(2026, 3, 6);
  private static final Map<String, String> BOARD_LABELS = Map.ofEntries(
      Map.entry("269011", "학사"),
      Map.entry("268869", "취창업"),
      Map.entry("268768", "일반"),
      Map.entry("268629", "학사"),
      Map.entry("268630", "학사"),
      Map.entry("268547", "학사"),
      Map.entry("268396", "학사"),
      Map.entry("268391", "학사"),
      Map.entry("268242", "일반"),
      Map.entry("268226", "일반"),
      Map.entry("268212", "일반"),
      Map.entry("268584", "학사"),
      Map.entry("268989", "학사"),
      Map.entry("269089", "학사")
  );

  private EvaluationSet evaluationSet;
  private StudentScenarioProfiles scenarioProfiles;
  private DateExtractor dateExtractor;
  private HeuristicActionExtractionService extractionService;
  private NoticeFeedService noticeFeedService;
  private NoticeSourceRepository noticeSourceRepository;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() throws IOException {
    objectMapper = new ObjectMapper();
    try (InputStream stream = Objects.requireNonNull(
        getClass().getResourceAsStream("/fixtures/notice-feed/quality/evaluation-set.json"),
        "missing evaluation-set.json"
    )) {
      evaluationSet = objectMapper.readValue(stream, EvaluationSet.class);
    }
    try (InputStream stream = Objects.requireNonNull(
        getClass().getResourceAsStream("/fixtures/notice-feed/quality/ranking-student-scenarios.json"),
        "missing ranking-student-scenarios.json"
    )) {
      scenarioProfiles = objectMapper.readValue(stream, StudentScenarioProfiles.class);
    }

    dateExtractor = new DateExtractor() {
      @Override
      protected LocalDate today() {
        return AUDIT_DATE;
      }
    };

    ActionVerbExtractor actionVerbExtractor = new ActionVerbExtractor();
    TaskPhraseExtractor taskPhraseExtractor = new TaskPhraseExtractor();
    extractionService = new HeuristicActionExtractionService(
        new TextNormalizer(),
        dateExtractor,
        new SystemHintExtractor(),
        new RequiredItemExtractor(),
        actionVerbExtractor,
        new EligibilityExtractor(),
        new ActionSegmenter(actionVerbExtractor, taskPhraseExtractor),
        new ActionSummaryBuilder(),
        new TitleDeriver(),
        taskPhraseExtractor,
        new StructuredEligibilityParser()
    );
    noticeSourceRepository = mock(NoticeSourceRepository.class);
    noticeFeedService = new NoticeFeedService(
        noticeSourceRepository,
        objectMapper,
        new TaskPhraseExtractor(),
        new ActionSummaryBuilder(),
        new NoticeActionabilityClassifier()
    );
  }

  @Test
  void studentScenarioHighTierNoticesRankAboveMidAndLow() {
    for (StudentScenarioProfile profile : scenarioProfiles.profiles()) {
      NoticeFeedResponse feed = feedFor(profile);
      Map<String, Integer> orderById = indexByExternalNoticeId(feed);

      List<StudentScenarioExpectation> highs = expectationsByTier(profile, "high");
      List<StudentScenarioExpectation> mids = expectationsByTier(profile, "mid");
      List<StudentScenarioExpectation> lows = expectationsByTier(profile, "low");

      for (StudentScenarioExpectation high : highs) {
        for (StudentScenarioExpectation lower : concat(mids, lows)) {
          assertThat(orderById.get(high.noticeId()))
              .as("%s high-tier %s should rank ahead of %s", profile.name(), high.noticeId(), lower.noticeId())
              .isLessThan(orderById.get(lower.noticeId()));
        }
      }
    }
  }

  @Test
  void studentScenarioExpectedReasonSubsetsAppear() {
    for (StudentScenarioProfile profile : scenarioProfiles.profiles()) {
      NoticeFeedResponse feed = feedFor(profile);
      Map<String, com.cuk.notice2action.extraction.api.dto.PersonalizedNoticeSummaryDto> byId = feed.notices().stream()
          .collect(Collectors.toMap(summary -> externalNoticeId(summary.sourceUrl()), summary -> summary));

      for (StudentScenarioExpectation expectation : profile.expectations()) {
        assertThat(byId.get(expectation.noticeId()).importanceReasons())
            .as("%s reasons for notice %s", profile.name(), expectation.noticeId())
            .containsAll(expectation.expectedImportanceReasonSubset());
      }
    }
  }

  @Test
  void studentScenarioOtherAudienceNoticesStayBelowMatchedActionableNotices() {
    for (StudentScenarioProfile profile : scenarioProfiles.profiles()) {
      NoticeFeedResponse feed = feedFor(profile);
      Map<String, com.cuk.notice2action.extraction.api.dto.PersonalizedNoticeSummaryDto> byId = feed.notices().stream()
          .collect(Collectors.toMap(summary -> externalNoticeId(summary.sourceUrl()), summary -> summary));
      Map<String, Integer> orderById = indexByExternalNoticeId(feed);

      List<StudentScenarioExpectation> lows = expectationsByTier(profile, "low");
      List<StudentScenarioExpectation> matched = concat(expectationsByTier(profile, "high"), expectationsByTier(profile, "mid"));

      for (StudentScenarioExpectation low : lows) {
        if (!byId.get(low.noticeId()).importanceReasons().contains("다른 대상 공지")) {
          continue;
        }
        for (StudentScenarioExpectation higher : matched) {
          assertThat(orderById.get(low.noticeId()))
              .as("%s low-tier other-audience notice %s should rank below %s", profile.name(), low.noticeId(), higher.noticeId())
              .isGreaterThan(orderById.get(higher.noticeId()));
        }
      }
    }
  }

  @Test
  void studentScenarioExplicitOrderingPairsHold() {
    for (StudentScenarioProfile profile : scenarioProfiles.profiles()) {
      NoticeFeedResponse feed = feedFor(profile);
      Map<String, Integer> orderById = indexByExternalNoticeId(feed);

      for (StudentScenarioExpectation expectation : profile.expectations()) {
        for (String lowerId : expectation.expectedMustAppearAbove()) {
          assertThat(orderById.get(expectation.noticeId()))
              .as("%s notice %s should rank above %s", profile.name(), expectation.noticeId(), lowerId)
              .isLessThan(orderById.get(lowerId));
        }
      }
    }
  }

  private NoticeFeedResponse feedFor(StudentScenarioProfile profile) {
    List<NoticeSourceEntity> notices = new ArrayList<>(evaluationSet.notices().stream()
        .map(this::toNoticeSource)
        .toList());
    notices.add(createNationalScholarshipNotice());

    when(noticeSourceRepository.findAllAutoCollectedNotices()).thenReturn(notices);
    return noticeFeedService.getFeed(
        new NoticeProfile(
            profile.department(),
            profile.year(),
            profile.status(),
            profile.keywords(),
            profile.preferredBoards()
        ),
        0,
        100
    );
  }

  private NoticeSourceEntity createNationalScholarshipNotice() {
    NoticeSourceEntity source = new NoticeSourceEntity(
        UUID.nameUUIDFromBytes("notice-269110".getBytes(StandardCharsets.UTF_8)),
        "[학생지원팀](한국장학재단)2026년 한국장학재단 국가장학사업(인문100년) 학생 사전신청 안내(3/6~3/25)",
        SourceCategory.NOTICE,
        "국가장학금 신청 공지입니다. 신청기간은 2026.03.06 ~ 2026.03.25 입니다.",
        "https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=269110",
        OffsetDateTime.now(APP_OFFSET)
    );
    source.setAutoCollected(true);
    source.setExternalNoticeId("269110");
    source.setPublishedAt(LocalDate.of(2026, 3, 6));
    source.setActionability("action_required");
    source.setPrimaryDueAt(OffsetDateTime.of(2026, 3, 25, 23, 59, 0, 0, APP_OFFSET));
    source.setPrimaryDueLabel("3. 25. (수) 23:59");
    source.setNoticeBoardLabel("장학");
    source.setAttachmentsJson("[]");

    ExtractedActionEntity entity = new ExtractedActionEntity(
        UUID.randomUUID(),
        source,
        "국가장학금 사전신청",
        "할 일: 국가장학금 사전신청. 마감: 2026.03.06 ~ 2026.03.25.",
        OffsetDateTime.of(2026, 3, 25, 23, 59, 0, 0, APP_OFFSET),
        "2026.03.06 ~ 2026.03.25",
        null,
        "[]",
        null,
        false,
        0.92,
        OffsetDateTime.now(APP_OFFSET)
    );
    entity.addEvidence(new EvidenceSnippetEntity(
        UUID.randomUUID(),
        entity,
        "dueAt",
        "국가장학금 신청 공지입니다. 신청기간은 2026.03.06 ~ 2026.03.25 입니다.",
        0.9,
        OffsetDateTime.now(APP_OFFSET)
    ));
    source.getActions().add(entity);
    return source;
  }

  private Map<String, Integer> indexByExternalNoticeId(NoticeFeedResponse feed) {
    LinkedHashMap<String, Integer> indexes = new LinkedHashMap<>();
    for (int index = 0; index < feed.notices().size(); index++) {
      indexes.put(externalNoticeId(feed.notices().get(index).sourceUrl()), index);
    }
    return indexes;
  }

  private String externalNoticeId(String sourceUrl) {
    Matcher matcher = Pattern.compile("articleNo=(\\d+)").matcher(sourceUrl == null ? "" : sourceUrl);
    return matcher.find() ? matcher.group(1) : sourceUrl;
  }

  private List<StudentScenarioExpectation> expectationsByTier(StudentScenarioProfile profile, String tier) {
    return profile.expectations().stream().filter(expectation -> tier.equals(expectation.expectedPriorityTier())).toList();
  }

  private <T> List<T> concat(List<T> left, List<T> right) {
    List<T> combined = new ArrayList<>(left);
    combined.addAll(right);
    return combined;
  }

  private ActionExtractionResponse extract(EvaluationNotice notice) {
    return extractionService.extract(new ActionExtractionRequest(
        notice.body(),
        notice.sourceUrl(),
        notice.title(),
        SourceCategory.NOTICE,
        List.of()
    ));
  }

  private NoticeSourceEntity toNoticeSource(EvaluationNotice notice) {
    ActionExtractionResponse response = extract(notice);
    DueCandidate dueCandidate = resolveExpectedDueCandidate(notice, response.actions());

    NoticeSourceEntity source = new NoticeSourceEntity(
        UUID.nameUUIDFromBytes(("notice-" + notice.id()).getBytes(StandardCharsets.UTF_8)),
        notice.title(),
        SourceCategory.NOTICE,
        notice.body(),
        notice.sourceUrl(),
        OffsetDateTime.now(APP_OFFSET)
    );
    source.setAutoCollected(true);
    source.setExternalNoticeId(notice.id());
    source.setPublishedAt(LocalDate.parse(notice.publishedAt()));
    source.setActionability(notice.expectedActionability());
    source.setPrimaryDueAt(dueCandidate == null ? null : dueCandidate.dueAt());
    source.setPrimaryDueLabel(notice.expectedPrimaryDueLabel());
    source.setAttachmentsJson("[]");
    source.setNoticeBoardLabel(BOARD_LABELS.get(notice.id()));

    for (ExtractedActionDto action : response.actions()) {
      ExtractedActionEntity entity = new ExtractedActionEntity(
          action.id() == null ? UUID.randomUUID() : action.id(),
          source,
          action.title(),
          action.actionSummary(),
          parseDue(action.dueAtIso()),
          action.dueAtLabel(),
          action.eligibility(),
          toJson(action.requiredItems()),
          action.systemHint(),
          action.inferred(),
          action.confidenceScore(),
          action.createdAt()
      );
      if (action.structuredEligibility() != null) {
        entity.setStructuredEligibilityJson(toJson(action.structuredEligibility()));
      }
      if (action.additionalDates() != null) {
        entity.setAdditionalDatesJson(toJson(action.additionalDates()));
      }
      for (EvidenceSnippetDto evidence : action.evidence()) {
        entity.addEvidence(new EvidenceSnippetEntity(
            UUID.randomUUID(),
            entity,
            evidence.fieldName(),
            evidence.snippet(),
            evidence.confidence(),
            OffsetDateTime.now(APP_OFFSET)
        ));
      }
      source.getActions().add(entity);
    }

    return source;
  }

  private DueCandidate resolveExpectedDueCandidate(EvaluationNotice notice, List<ExtractedActionDto> actions) {
    if (notice.expectedPrimaryDueLabel() == null) {
      return null;
    }
    return actions.stream()
        .map(this::toDueCandidate)
        .filter(Objects::nonNull)
        .filter(candidate -> notice.expectedPrimaryDueLabel().equals(candidate.label()))
        .min(Comparator.comparing(DueCandidate::dueAt))
        .orElse(null);
  }

  private DueCandidate toDueCandidate(ExtractedActionDto action) {
    OffsetDateTime dueAt = parseDue(action.dueAtIso());
    if (dueAt == null || action.dueAtLabel() == null) {
      return null;
    }
    return new DueCandidate(dueAt, action.dueAtLabel());
  }

  private OffsetDateTime parseDue(String dueAtIso) {
    if (dueAtIso == null || dueAtIso.isBlank()) {
      return null;
    }
    return OffsetDateTime.parse(dueAtIso);
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to serialize test payload", e);
    }
  }

  private record DueCandidate(OffsetDateTime dueAt, String label) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record EvaluationSet(List<EvaluationNotice> notices) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record EvaluationNotice(
      String id,
      String title,
      String sourceUrl,
      String publishedAt,
      String expectedActionability,
      String expectedPrimaryDueLabel,
      String body
  ) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record StudentScenarioProfiles(List<StudentScenarioProfile> profiles) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record StudentScenarioProfile(
      String name,
      String department,
      Integer year,
      String status,
      List<String> keywords,
      List<String> preferredBoards,
      List<StudentScenarioExpectation> expectations
  ) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record StudentScenarioExpectation(
      String noticeId,
      String expectedPriorityTier,
      List<String> expectedImportanceReasonSubset,
      List<String> expectedMustAppearAbove
  ) {}
}
