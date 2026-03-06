package com.cuk.notice2action.extraction.service.notice;

import static org.assertj.core.api.Assertions.assertThat;

import com.cuk.notice2action.extraction.api.dto.ActionExtractionRequest;
import com.cuk.notice2action.extraction.api.dto.ActionExtractionResponse;
import com.cuk.notice2action.extraction.api.dto.ExtractedActionDto;
import com.cuk.notice2action.extraction.domain.SourceCategory;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoticeQualityEvaluationSetTest {

  private static final ZoneOffset APP_OFFSET = ZoneOffset.ofHours(9);
  private static final LocalDate AUDIT_DATE = LocalDate.of(2026, 3, 6);

  private EvaluationSet evaluationSet;
  private DateExtractor dateExtractor;
  private HeuristicActionExtractionService extractionService;
  private NoticeActionabilityClassifier classifier;

  @BeforeEach
  void setUp() throws IOException {
    try (InputStream stream = Objects.requireNonNull(
        getClass().getResourceAsStream("/fixtures/notice-feed/quality/evaluation-set.json"),
        "missing evaluation-set.json"
    )) {
      evaluationSet = new ObjectMapper().readValue(stream, EvaluationSet.class);
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
      return fromActions.get().label();
    }

    DateMatch match = dateExtractor.extract(body, new ArrayList<>());
    if (match == null) {
      return null;
    }

    OffsetDateTime fallbackDue = parseDue(dateExtractor.formatIso(match.components()));
    return fallbackDue != null && fallbackDue.isAfter(now) ? match.label() : null;
  }

  private OffsetDateTime parseDue(String value) {
    try {
      return OffsetDateTime.parse(value);
    } catch (Exception exception) {
      return null;
    }
  }

  private record DueCandidate(OffsetDateTime dueAt, String label) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record EvaluationSet(String evaluationDate, NoticeProfile profile, List<EvaluationNotice> notices) {}

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
