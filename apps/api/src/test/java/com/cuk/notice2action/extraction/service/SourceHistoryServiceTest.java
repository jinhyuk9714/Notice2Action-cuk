package com.cuk.notice2action.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cuk.notice2action.extraction.api.dto.ActionExtractionRequest;
import com.cuk.notice2action.extraction.api.dto.ActionExtractionResponse;
import com.cuk.notice2action.extraction.api.dto.SourceDetailDto;
import com.cuk.notice2action.extraction.api.dto.SourceListResponse;
import com.cuk.notice2action.extraction.domain.SourceCategory;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SourceHistoryServiceTest {

  @Autowired
  private ActionExtractionService extractionService;

  @Autowired
  private ActionPersistenceService persistenceService;

  @Autowired
  private SourceHistoryService sourceHistoryService;

  private void persistSample(String text, String title) {
    ActionExtractionRequest request = new ActionExtractionRequest(
        text, null, title, SourceCategory.NOTICE, List.of()
    );
    ActionExtractionResponse extracted = extractionService.extract(request);
    persistenceService.persistExtraction(request, extracted);
  }

  @Test
  void listSources_returns_paginated_sources() {
    persistSample(
        "2026년 3월 12일까지 TRINITY에서 공결 신청을 완료하세요.",
        "공결 신청 안내"
    );
    persistSample(
        "2026년 3월 20일까지 장학포털에서 장학금 신청을 완료하세요.",
        "장학금 안내"
    );

    SourceListResponse response = sourceHistoryService.listSources(0, 10);

    assertThat(response.sources()).hasSize(2);
    assertThat(response.currentPage()).isZero();
    assertThat(response.totalElements()).isEqualTo(2);
  }

  @Test
  void listSources_returns_action_count() {
    persistSample(
        "2026년 3월 12일까지 TRINITY에서 공결 신청을 완료하세요.",
        "공결 신청 안내"
    );

    SourceListResponse response = sourceHistoryService.listSources(0, 10);

    assertThat(response.sources()).hasSize(1);
    assertThat(response.sources().getFirst().actionCount()).isGreaterThanOrEqualTo(1);
  }

  @Test
  void getSourceDetail_returns_source_with_actions() {
    persistSample(
        "2026년 3월 12일까지 TRINITY에서 공결 신청을 완료하세요.",
        "공결 신청 안내"
    );

    SourceListResponse list = sourceHistoryService.listSources(0, 10);
    UUID sourceId = list.sources().getFirst().id();

    SourceDetailDto detail = sourceHistoryService.getSourceDetail(sourceId);

    assertThat(detail.id()).isEqualTo(sourceId);
    assertThat(detail.title()).isEqualTo("공결 신청 안내");
    assertThat(detail.sourceCategory()).isEqualTo(SourceCategory.NOTICE);
    assertThat(detail.actions()).isNotEmpty();
  }

  @Test
  void getSourceDetail_throws_for_unknown_id() {
    UUID unknownId = UUID.randomUUID();

    assertThatThrownBy(() -> sourceHistoryService.getSourceDetail(unknownId))
        .isInstanceOf(NoSuchElementException.class)
        .hasMessageContaining(unknownId.toString());
  }
}
