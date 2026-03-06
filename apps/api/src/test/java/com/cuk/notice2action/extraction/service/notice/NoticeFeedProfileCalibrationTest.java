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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoticeFeedProfileCalibrationTest {

  private static final ZoneOffset APP_OFFSET = ZoneOffset.ofHours(9);
  private static final LocalDate AUDIT_DATE = LocalDate.of(2026, 3, 6);

  private EvaluationSet evaluationSet;
  private RankingProfiles rankingProfiles;
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
        getClass().getResourceAsStream("/fixtures/notice-feed/quality/ranking-profiles.json"),
        "missing ranking-profiles.json"
    )) {
      rankingProfiles = objectMapper.readValue(stream, RankingProfiles.class);
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
  void highTierNoticesRankAboveMidAndLowForEachRepresentativeProfile() {
    for (RankingProfile profile : rankingProfiles.profiles()) {
      NoticeFeedResponse feed = feedFor(profile);
      Map<String, Integer> orderById = indexByExternalNoticeId(feed);

      List<RankingExpectation> highs = expectationsByTier(profile, "high");
      List<RankingExpectation> mids = expectationsByTier(profile, "mid");
      List<RankingExpectation> lows = expectationsByTier(profile, "low");

      for (RankingExpectation high : highs) {
        for (RankingExpectation lower : concat(mids, lows)) {
          assertThat(orderById.get(high.noticeId()))
              .as("%s high-tier %s should rank ahead of %s", profile.name(), high.noticeId(), lower.noticeId())
              .isLessThan(orderById.get(lower.noticeId()));
        }
      }
    }
  }

  @Test
  void expectedReasonSubsetsAppearInFeedForEachRepresentativeProfile() {
    for (RankingProfile profile : rankingProfiles.profiles()) {
      NoticeFeedResponse feed = feedFor(profile);
      Map<String, com.cuk.notice2action.extraction.api.dto.PersonalizedNoticeSummaryDto> byId = feed.notices().stream()
          .collect(Collectors.toMap(summary -> externalNoticeId(summary.sourceUrl()), summary -> summary));

      for (RankingExpectation expectation : profile.expectations()) {
        assertThat(byId.get(expectation.noticeId()).importanceReasons())
            .as("%s reasons for notice %s", profile.name(), expectation.noticeId())
            .containsAll(expectation.expectedImportanceReasonSubset());
      }
    }
  }

  @Test
  void explicitOtherAudienceNoticesStayBelowMatchedActionableNotices() {
    for (RankingProfile profile : rankingProfiles.profiles()) {
      NoticeFeedResponse feed = feedFor(profile);
      Map<String, com.cuk.notice2action.extraction.api.dto.PersonalizedNoticeSummaryDto> byId = feed.notices().stream()
          .collect(Collectors.toMap(summary -> externalNoticeId(summary.sourceUrl()), summary -> summary));
      Map<String, Integer> orderById = indexByExternalNoticeId(feed);

      List<RankingExpectation> lows = expectationsByTier(profile, "low");
      List<RankingExpectation> matched = concat(expectationsByTier(profile, "high"), expectationsByTier(profile, "mid"));

      for (RankingExpectation low : lows) {
        if (!byId.get(low.noticeId()).importanceReasons().contains("다른 대상 공지")) {
          continue;
        }
        for (RankingExpectation higher : matched) {
          assertThat(orderById.get(low.noticeId()))
              .as("%s low-tier other-audience notice %s should rank below %s", profile.name(), low.noticeId(), higher.noticeId())
              .isGreaterThan(orderById.get(higher.noticeId()));
        }
      }
    }
  }

  @Test
  void explicitOrderingPairsHoldForEachProfile() {
    for (RankingProfile profile : rankingProfiles.profiles()) {
      NoticeFeedResponse feed = feedFor(profile);
      Map<String, Integer> orderById = indexByExternalNoticeId(feed);

      for (RankingExpectation expectation : profile.expectations()) {
        for (String lowerId : expectation.expectedMustAppearAbove()) {
          assertThat(orderById.get(expectation.noticeId()))
              .as("%s notice %s should rank above %s", profile.name(), expectation.noticeId(), lowerId)
              .isLessThan(orderById.get(lowerId));
        }
      }
    }
  }

  private NoticeFeedResponse feedFor(RankingProfile profile) {
    List<NoticeSourceEntity> notices = evaluationSet.notices().stream()
        .map(this::toNoticeSource)
        .toList();
    when(noticeSourceRepository.findAllAutoCollectedNotices()).thenReturn(notices);
    return noticeFeedService.getFeed(
        new NoticeProfile(profile.department(), profile.year(), profile.status(), profile.keywords()),
        0,
        100
    );
  }

  private Map<String, Integer> indexByExternalNoticeId(NoticeFeedResponse feed) {
    java.util.LinkedHashMap<String, Integer> indexes = new java.util.LinkedHashMap<>();
    for (int index = 0; index < feed.notices().size(); index++) {
      indexes.put(externalNoticeId(feed.notices().get(index).sourceUrl()), index);
    }
    return indexes;
  }

  private String externalNoticeId(String sourceUrl) {
    Matcher matcher = Pattern.compile("articleNo=(\\d+)").matcher(sourceUrl == null ? "" : sourceUrl);
    return matcher.find() ? matcher.group(1) : sourceUrl;
  }

  private List<RankingExpectation> expectationsByTier(RankingProfile profile, String tier) {
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
    String expectedLabel = notice.expectedPrimaryDueLabel();
    if (expectedLabel == null || expectedLabel.isBlank() || !"action_required".equals(notice.expectedActionability())) {
      return null;
    }

    DueCandidate extracted = resolveFutureDueCandidate(notice.expectedActionability(), notice.body(), actions);
    if (extracted != null && expectedLabel.equals(extracted.label())) {
      return extracted;
    }

    OffsetDateTime fallback = parseExpectedDueLabel(expectedLabel);
    if (fallback != null) {
      return new DueCandidate(fallback, expectedLabel);
    }
    return extracted;
  }

  private String resolveFutureDueLabel(String actionability, String body, List<ExtractedActionDto> actions) {
    DueCandidate candidate = resolveFutureDueCandidate(actionability, body, actions);
    return candidate == null ? null : candidate.label();
  }

  private DueCandidate resolveFutureDueCandidate(String actionability, String body, List<ExtractedActionDto> actions) {
    if (!"action_required".equals(actionability)) {
      return null;
    }

    OffsetDateTime now = AUDIT_DATE.atStartOfDay().atOffset(APP_OFFSET);
    var fromActions = actions.stream()
        .filter(action -> action.dueAtIso() != null && !action.dueAtIso().isBlank())
        .map(action -> new DueCandidate(parseDue(action.dueAtIso()), action.dueAtLabel()))
        .filter(candidate -> candidate.dueAt() != null && candidate.dueAt().isAfter(now))
        .min(Comparator.comparing(DueCandidate::dueAt));

    if (fromActions.isPresent()) {
      return fromActions.get();
    }

    DateMatch match = dateExtractor.extract(body, new ArrayList<>());
    if (match == null) {
      return null;
    }

    OffsetDateTime fallbackDue = parseDue(dateExtractor.formatIso(match.components()));
    return fallbackDue != null && fallbackDue.isAfter(now) ? new DueCandidate(fallbackDue, match.label()) : null;
  }

  private OffsetDateTime parseDue(String value) {
    try {
      return OffsetDateTime.parse(value);
    } catch (Exception exception) {
      return null;
    }
  }

  private OffsetDateTime parseExpectedDueLabel(String label) {
    if ("3일 이내".equals(label)) {
      return AUDIT_DATE.plusDays(3).atTime(23, 59, 59).atOffset(APP_OFFSET);
    }

    Matcher compactRange = Pattern.compile("^~\\s*(\\d{1,2})/(\\d{1,2})$").matcher(label);
    if (compactRange.matches()) {
      int month = Integer.parseInt(compactRange.group(1));
      int day = Integer.parseInt(compactRange.group(2));
      return LocalDate.of(AUDIT_DATE.getYear(), month, day)
          .atTime(23, 59, 59)
          .atOffset(APP_OFFSET);
    }

    Matcher datedTime = Pattern.compile("^(\\d{1,2})\\.\\s*(\\d{1,2})\\.\\s*\\([^)]*\\)\\s*(\\d{1,2}):(\\d{2})$")
        .matcher(label);
    if (datedTime.matches()) {
      int month = Integer.parseInt(datedTime.group(1));
      int day = Integer.parseInt(datedTime.group(2));
      int hour = Integer.parseInt(datedTime.group(3));
      int minute = Integer.parseInt(datedTime.group(4));
      return LocalDate.of(AUDIT_DATE.getYear(), month, day)
          .atTime(hour, minute)
          .atOffset(APP_OFFSET);
    }

    return null;
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (IOException exception) {
      throw new IllegalStateException("failed to serialize test value", exception);
    }
  }

  private record DueCandidate(OffsetDateTime dueAt, String label) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record EvaluationSet(String evaluationDate, EvaluationProfile profile, List<EvaluationNotice> notices) {}


  @JsonIgnoreProperties(ignoreUnknown = true)
  private record EvaluationProfile(String department, Integer year, String status, List<String> keywords) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record EvaluationNotice(
      String id,
      String title,
      String publishedAt,
      String sourceUrl,
      String body,
      String expectedActionability,
      String expectedPrimaryDueLabel,
      List<String> expectedImportanceReasons,
      String expectedPriorityTier,
      List<ExpectedAction> expectedActions
  ) {}


  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ExpectedAction(
      String title,
      String dueLabel,
      String systemHint,
      List<String> evidence
  ) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record RankingProfiles(List<RankingProfile> profiles) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record RankingProfile(
      String name,
      String department,
      Integer year,
      String status,
      List<String> keywords,
      List<RankingExpectation> expectations
  ) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record RankingExpectation(
      String noticeId,
      String expectedPriorityTier,
      List<String> expectedImportanceReasonSubset,
      List<String> expectedMustAppearAbove
  ) {}
}
