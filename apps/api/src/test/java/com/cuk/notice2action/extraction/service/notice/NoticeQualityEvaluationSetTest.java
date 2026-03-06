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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoticeQualityEvaluationSetTest {

  private static final ZoneOffset APP_OFFSET = ZoneOffset.ofHours(9);
  private static final LocalDate AUDIT_DATE = LocalDate.of(2026, 3, 6);

  private EvaluationSet evaluationSet;
  private DateExtractor dateExtractor;
  private HeuristicActionExtractionService extractionService;
  private NoticeActionabilityClassifier classifier;
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
    classifier = new NoticeActionabilityClassifier();
    noticeSourceRepository = mock(NoticeSourceRepository.class);
    noticeFeedService = new NoticeFeedService(
        noticeSourceRepository,
        objectMapper,
        new TaskPhraseExtractor(),
        new ActionSummaryBuilder(),
        classifier
    );
  }

  @Test
  void evaluationSetHasThirtyNoticesAndBalancedLabels() {
    assertThat(evaluationSet.notices()).hasSize(30);
    assertThat(evaluationSet.notices().stream()
        .filter(notice -> "action_required".equals(notice.expectedActionability()))
        .count()).isEqualTo(11);
    assertThat(evaluationSet.notices().stream()
        .filter(notice -> "informational".equals(notice.expectedActionability()))
        .count()).isEqualTo(19);
  }

  @Test
  void evaluationSetUsesRepresentativeProfile() {
    assertThat(evaluationSet.profile().department()).isEqualTo("컴퓨터정보공학부");
    assertThat(evaluationSet.profile().year()).isEqualTo(3);
    assertThat(evaluationSet.profile().status()).isEqualTo("재학생");
    assertThat(evaluationSet.profile().keywords()).isEmpty();
  }

  @Test
  void actionabilityClassifierMatchesEvaluationSet() {
    for (EvaluationNotice notice : evaluationSet.notices()) {
      ActionExtractionResponse response = extract(notice);

      String actual = classifier.classify(notice.title(), notice.body(), response.actions());

      assertThat(actual)
          .as("actionability for notice %s (%s)", notice.id(), notice.title())
          .isEqualTo(notice.expectedActionability());
    }
  }

  @Test
  void futureDueHintsMatchEvaluationSetWhenSpecified() {
    for (EvaluationNotice notice : evaluationSet.notices()) {
      ActionExtractionResponse response = extract(notice);
      String actionability = classifier.classify(notice.title(), notice.body(), response.actions());

      String actualDueLabel = resolveFutureDueLabel(actionability, notice.body(), response.actions());

      assertThat(actualDueLabel)
          .as("due label for notice %s (%s)", notice.id(), notice.title())
          .isEqualTo(notice.expectedPrimaryDueLabel());
    }
  }

  @Test
  void expectedActionsStayFocusedAndDocumented() {
    for (EvaluationNotice notice : evaluationSet.notices()) {
      assertThat(notice.expectedActions())
          .as("expected actions for notice %s", notice.id())
          .hasSizeLessThanOrEqualTo(3);

      for (ExpectedAction action : notice.expectedActions()) {
        assertThat(action.title()).isNotBlank();
        assertThat(action.evidence()).isNotEmpty();
      }
    }
  }

  @Test
  void representativeActionTitlesAndSummariesMatchEvaluationSubset() {
    List<String> representativeIds = List.of(
        "269011", "268869", "268547", "268226",
        "268242", "268391", "268212"
    );

    for (EvaluationNotice notice : evaluationSet.notices()) {
      if (!representativeIds.contains(notice.id())) {
        continue;
      }

      ActionExtractionResponse response = extract(notice);
      String actionability = classifier.classify(notice.title(), notice.body(), response.actions());

      assertThat(actionability).isEqualTo("action_required");
      assertThat(response.actions()).isNotEmpty();

      ExpectedAction expected = notice.expectedActions().getFirst();
      ExtractedActionDto actual = response.actions().getFirst();

      assertThat(actual.title())
          .as("title for notice %s", notice.id())
          .isEqualTo(expected.title());
      assertThat(actual.actionSummary())
          .as("summary for notice %s", notice.id())
          .contains("할 일: " + expected.title());
    }
  }

  @Test
  void representativeProfileOrdersHighMidLowTiersInFeed() {
    NoticeFeedResponse feed = representativeFeed();
    Map<String, EvaluationNotice> noticeByUrl = evaluationSet.notices().stream()
        .collect(java.util.stream.Collectors.toMap(EvaluationNotice::sourceUrl, notice -> notice));

    List<Integer> tierRanks = feed.notices().stream()
        .map(summary -> tierRank(noticeByUrl.get(summary.sourceUrl()).expectedPriorityTier()))
        .toList();

    assertThat(tierRanks)
        .as("feed tier ordering for representative profile")
        .isSorted();
  }

  @Test
  void representativeProfileIncludesExpectedReasonSubsets() {
    NoticeFeedResponse feed = representativeFeed();
    Map<String, EvaluationNotice> noticeByUrl = evaluationSet.notices().stream()
        .collect(java.util.stream.Collectors.toMap(EvaluationNotice::sourceUrl, notice -> notice));

    feed.notices().forEach(summary -> {
      EvaluationNotice expected = noticeByUrl.get(summary.sourceUrl());
      if (!expected.expectedImportanceReasons().isEmpty()) {
        assertThat(summary.importanceReasons())
            .as("importance reasons for notice %s", expected.id())
            .containsAll(expected.expectedImportanceReasons());
      }
    });
  }

  @Test
  void representativeProfileKeepsOtherAudienceNoticesBelowMatchedActionableTier() {
    NoticeFeedResponse feed = representativeFeed();
    Map<String, EvaluationNotice> noticeByUrl = evaluationSet.notices().stream()
        .collect(java.util.stream.Collectors.toMap(EvaluationNotice::sourceUrl, notice -> notice));

    int lastMatchedActionableIndex = -1;
    int firstOtherAudienceLowIndex = Integer.MAX_VALUE;

    for (int index = 0; index < feed.notices().size(); index++) {
      var summary = feed.notices().get(index);
      EvaluationNotice expected = noticeByUrl.get(summary.sourceUrl());
      if ("action_required".equals(expected.expectedActionability())
          && !"low".equals(expected.expectedPriorityTier())) {
        lastMatchedActionableIndex = index;
      }
      if ("low".equals(expected.expectedPriorityTier())
          && summary.importanceReasons().contains("다른 대상 공지")) {
        firstOtherAudienceLowIndex = Math.min(firstOtherAudienceLowIndex, index);
      }
    }

    assertThat(firstOtherAudienceLowIndex)
        .as("first explicit other-audience low-tier notice should appear after matched actionable tier")
        .isGreaterThan(lastMatchedActionableIndex);
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

  private NoticeFeedResponse representativeFeed() {
    List<NoticeSourceEntity> notices = evaluationSet.notices().stream()
        .map(this::toNoticeSource)
        .toList();
    when(noticeSourceRepository.findAllAutoCollectedNotices()).thenReturn(notices);
    EvaluationProfile profile = evaluationSet.profile();
    return noticeFeedService.getFeed(
        new NoticeProfile(profile.department(), profile.year(), profile.status(), profile.keywords()),
        0,
        100
    );
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

  private int tierRank(String tier) {
    return switch (tier) {
      case "high" -> 0;
      case "mid" -> 1;
      default -> 2;
    };
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
}
