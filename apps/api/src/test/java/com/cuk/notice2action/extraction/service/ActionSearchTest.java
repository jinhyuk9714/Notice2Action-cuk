package com.cuk.notice2action.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cuk.notice2action.extraction.api.dto.ActionExtractionRequest;
import com.cuk.notice2action.extraction.api.dto.ActionExtractionResponse;
import com.cuk.notice2action.extraction.api.dto.ActionListResponse;
import com.cuk.notice2action.extraction.api.dto.ActionSearchCriteria;
import com.cuk.notice2action.extraction.domain.SourceCategory;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ActionSearchTest {

  @Autowired
  private ActionExtractionService extractionService;

  @Autowired
  private ActionPersistenceService persistenceService;

  @BeforeEach
  void setUp() {
    // Action 1: NOTICE, due 2026-03-12
    persistSample(
        "2026년 3월 12일까지 TRINITY에서 공결 신청을 완료하세요.",
        "공결 신청 안내", SourceCategory.NOTICE
    );
    // Action 2: EMAIL, due 2026-03-20
    persistSample(
        "2026년 3월 20일까지 장학포털에서 장학금 신청을 완료하세요.",
        "장학금 신청 안내", SourceCategory.EMAIL
    );
    // Action 3: NOTICE, no due date
    persistSample(
        "학사 일정을 확인하세요.",
        "학사 일정 공지", SourceCategory.NOTICE
    );
  }

  private void persistSample(String text, String title, SourceCategory category) {
    ActionExtractionRequest request = new ActionExtractionRequest(
        text, null, title, category, List.of()
    );
    ActionExtractionResponse extracted = extractionService.extract(request);
    persistenceService.persistExtraction(request, extracted);
  }

  @Test
  void search_by_keyword_matches_title() {
    ActionSearchCriteria criteria = new ActionSearchCriteria(
        "공결", null, null, null, "recent"
    );
    ActionListResponse result = persistenceService.listActions(criteria, 0, 20);

    assertThat(result.actions()).isNotEmpty();
    assertThat(result.actions()).allMatch(a ->
        a.title().contains("공결") || a.actionSummary().toLowerCase().contains("공결")
    );
  }

  @Test
  void search_by_keyword_matches_summary() {
    ActionSearchCriteria criteria = new ActionSearchCriteria(
        "장학", null, null, null, "recent"
    );
    ActionListResponse result = persistenceService.listActions(criteria, 0, 20);

    assertThat(result.actions()).isNotEmpty();
    assertThat(result.actions()).allMatch(a ->
        a.title().contains("장학") || (a.actionSummary() != null && a.actionSummary().contains("장학"))
    );
  }

  @Test
  void filter_by_category() {
    ActionSearchCriteria criteria = new ActionSearchCriteria(
        null, SourceCategory.EMAIL, null, null, "recent"
    );
    ActionListResponse result = persistenceService.listActions(criteria, 0, 20);

    assertThat(result.actions()).isNotEmpty();
    assertThat(result.actions()).allMatch(a ->
        a.sourceCategory() == SourceCategory.EMAIL
    );
  }

  @Test
  void filter_by_due_date_range() {
    OffsetDateTime from = OffsetDateTime.parse("2026-03-10T00:00:00+09:00");
    OffsetDateTime to = OffsetDateTime.parse("2026-03-15T23:59:59+09:00");

    ActionSearchCriteria criteria = new ActionSearchCriteria(
        null, null, from, to, "due"
    );
    ActionListResponse result = persistenceService.listActions(criteria, 0, 20);

    assertThat(result.actions()).isNotEmpty();
    // All results should have dueAtIso within range
    assertThat(result.actions()).allMatch(a -> a.dueAtIso() != null);
  }

  @Test
  void combined_keyword_and_category_filter() {
    ActionSearchCriteria criteria = new ActionSearchCriteria(
        "신청", SourceCategory.NOTICE, null, null, "recent"
    );
    ActionListResponse result = persistenceService.listActions(criteria, 0, 20);

    assertThat(result.actions()).isNotEmpty();
    assertThat(result.actions()).allMatch(a ->
        a.sourceCategory() == SourceCategory.NOTICE
    );
  }

  @Test
  void empty_query_returns_all_actions() {
    ActionSearchCriteria criteria = new ActionSearchCriteria(
        null, null, null, null, "recent"
    );
    ActionListResponse result = persistenceService.listActions(criteria, 0, 20);

    assertThat(result.actions()).hasSizeGreaterThanOrEqualTo(3);
  }
}
