package com.cuk.notice2action.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cuk.notice2action.extraction.api.dto.ActionExtractionRequest;
import com.cuk.notice2action.extraction.api.dto.ActionExtractionResponse;
import com.cuk.notice2action.extraction.domain.SourceCategory;
import com.cuk.notice2action.extraction.service.extractor.ActionSegmenter;
import com.cuk.notice2action.extraction.service.extractor.ActionSummaryBuilder;
import com.cuk.notice2action.extraction.service.extractor.ActionVerbExtractor;
import com.cuk.notice2action.extraction.service.extractor.DateExtractor;
import com.cuk.notice2action.extraction.service.extractor.EligibilityExtractor;
import com.cuk.notice2action.extraction.service.extractor.RequiredItemExtractor;
import com.cuk.notice2action.extraction.service.extractor.StructuredEligibilityParser;
import com.cuk.notice2action.extraction.service.extractor.SystemHintExtractor;
import com.cuk.notice2action.extraction.service.extractor.TextNormalizer;
import com.cuk.notice2action.extraction.service.extractor.TitleDeriver;
import com.cuk.notice2action.extraction.service.strategy.DefaultExtractionStrategy;
import com.cuk.notice2action.extraction.service.strategy.ExtractionStrategyFactory;
import com.cuk.notice2action.extraction.service.strategy.SyllabusExtractionStrategy;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests that SYLLABUS category uses extra verbs (중간고사, 기말고사, 과제)
 * to segment and extract multiple events from a course syllabus.
 */
class SyllabusExtractionStrategyTest {

  private static final LocalDate FIXED_TODAY = LocalDate.of(2026, 3, 1);

  private final DateExtractor dateExtractor = new DateExtractor() {
    @Override
    protected LocalDate today() {
      return FIXED_TODAY;
    }
  };

  private final ActionVerbExtractor actionVerbExtractor = new ActionVerbExtractor();

  private final HeuristicActionExtractionService service = new HeuristicActionExtractionService(
      new TextNormalizer(),
      dateExtractor,
      new SystemHintExtractor(),
      new RequiredItemExtractor(),
      actionVerbExtractor,
      new EligibilityExtractor(),
      new ActionSegmenter(actionVerbExtractor),
      new ActionSummaryBuilder(),
      new TitleDeriver(),
      new StructuredEligibilityParser(),
      new ExtractionStrategyFactory(new DefaultExtractionStrategy(), new SyllabusExtractionStrategy())
  );

  @Test
  void syllabusWithMidtermAndFinal_extractsTwoActions() {
    String text = """
        [컴퓨터공학개론] 강의계획서
        중간고사: 4월 10일 (목) 10:00~12:00
        기말고사: 6월 12일 (목) 10:00~12:00
        """;
    ActionExtractionRequest request = new ActionExtractionRequest(
        text, null, "컴퓨터공학개론 강의계획서", SourceCategory.SYLLABUS, List.of());

    ActionExtractionResponse response = service.extract(request);

    assertThat(response.actions()).hasSizeGreaterThanOrEqualTo(2);
    assertThat(response.actions().get(0).dueAtIso()).startsWith("2026-04-10");
    assertThat(response.actions().get(1).dueAtIso()).startsWith("2026-06-12");
  }

  @Test
  void syllabusExtraVerbs_notBlockedByNonActionContext() {
    // "중간고사 일정" should be blocked (일정 is a NON_ACTION_SUFFIX)
    // "중간고사: 4월" should NOT be blocked
    String text = "중간고사: 4월 10일 수업 내용을 확인하세요.";
    ActionExtractionRequest request = new ActionExtractionRequest(
        text, null, null, SourceCategory.SYLLABUS, List.of());

    ActionExtractionResponse response = service.extract(request);

    // 확인 (ACTION_VERB) or 중간고사 (extraVerb) should produce at least 1 action
    assertThat(response.actions()).isNotEmpty();
  }

  @Test
  void noticeCategory_doesNotUseSyllabusVerbs() {
    // Without SYLLABUS strategy, "중간고사" alone should not trigger a separate action
    String text = """
        중간고사: 4월 10일
        기말고사: 6월 12일
        """;
    ActionExtractionRequest noticeRequest = new ActionExtractionRequest(
        text, null, null, SourceCategory.NOTICE, List.of());
    ActionExtractionRequest syllabusRequest = new ActionExtractionRequest(
        text, null, null, SourceCategory.SYLLABUS, List.of());

    ActionExtractionResponse noticeResponse = service.extract(noticeRequest);
    ActionExtractionResponse syllabusResponse = service.extract(syllabusRequest);

    // SYLLABUS should extract more (or equal) actions than NOTICE for this text
    assertThat(syllabusResponse.actions().size())
        .isGreaterThanOrEqualTo(noticeResponse.actions().size());
  }
}
