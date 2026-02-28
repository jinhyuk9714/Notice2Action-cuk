package com.cuk.notice2action.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cuk.notice2action.extraction.api.dto.ActionExtractionRequest;
import com.cuk.notice2action.extraction.api.dto.ActionExtractionResponse;
import com.cuk.notice2action.extraction.api.dto.ActionListResponse;
import com.cuk.notice2action.extraction.api.dto.SavedActionDetailDto;
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
class ActionPersistenceServiceTest {

  @Autowired
  private ActionExtractionService extractionService;

  @Autowired
  private ActionPersistenceService persistenceService;

  private ActionExtractionRequest sampleRequest() {
    return new ActionExtractionRequest(
        "2026년 3월 12일 18시까지 TRINITY에서 공결 신청을 완료하고 증빙서류를 업로드해야 합니다. 신청 대상은 재학생입니다.",
        null,
        "공결 신청 안내",
        SourceCategory.NOTICE,
        List.of("복학생")
    );
  }

  @Test
  void persistExtraction_saves_and_returns_ids() {
    ActionExtractionRequest request = sampleRequest();
    ActionExtractionResponse extracted = extractionService.extract(request);
    ActionExtractionResponse saved = persistenceService.persistExtraction(request, extracted);

    assertThat(saved.actions()).hasSize(1);
    assertThat(saved.actions().getFirst().id()).isNotNull();
    assertThat(saved.actions().getFirst().sourceId()).isNotNull();
    assertThat(saved.actions().getFirst().createdAt()).isNotNull();
    assertThat(saved.actions().getFirst().title()).isEqualTo("공결 신청 안내");
  }

  @Test
  void listActions_returns_saved_actions_newest_first() {
    ActionExtractionRequest request = sampleRequest();
    ActionExtractionResponse extracted = extractionService.extract(request);
    persistenceService.persistExtraction(request, extracted);

    ActionListResponse list = persistenceService.listActions();

    assertThat(list.actions()).isNotEmpty();
    assertThat(list.actions().getFirst().title()).isEqualTo("공결 신청 안내");
    assertThat(list.actions().getFirst().sourceCategory()).isEqualTo(SourceCategory.NOTICE);
  }

  @Test
  void getActionDetail_returns_full_detail_with_evidence() {
    ActionExtractionRequest request = sampleRequest();
    ActionExtractionResponse extracted = extractionService.extract(request);
    ActionExtractionResponse saved = persistenceService.persistExtraction(request, extracted);
    UUID actionId = saved.actions().getFirst().id();

    SavedActionDetailDto detail = persistenceService.getActionDetail(actionId);

    assertThat(detail.id()).isEqualTo(actionId);
    assertThat(detail.title()).isEqualTo("공결 신청 안내");
    assertThat(detail.evidence()).isNotEmpty();
    assertThat(detail.source()).isNotNull();
    assertThat(detail.source().sourceCategory()).isEqualTo(SourceCategory.NOTICE);
  }

  @Test
  void getActionDetail_throws_for_unknown_id() {
    UUID unknownId = UUID.randomUUID();

    assertThatThrownBy(() -> persistenceService.getActionDetail(unknownId))
        .isInstanceOf(NoSuchElementException.class)
        .hasMessageContaining(unknownId.toString());
  }
}
