package com.cuk.notice2action.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cuk.notice2action.extraction.api.dto.ActionExtractionRequest;
import com.cuk.notice2action.extraction.api.dto.ActionExtractionResponse;
import com.cuk.notice2action.extraction.api.dto.ActionUpdateRequest;
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
class ActionUpdateTest {

  @Autowired
  private ActionExtractionService extractionService;

  @Autowired
  private ActionPersistenceService persistenceService;

  private UUID persistAndGetId() {
    ActionExtractionRequest request = new ActionExtractionRequest(
        "2026년 3월 12일까지 TRINITY에서 공결 신청을 완료하세요.",
        null, "공결 신청 안내", SourceCategory.NOTICE, List.of()
    );
    ActionExtractionResponse extracted = extractionService.extract(request);
    ActionExtractionResponse saved = persistenceService.persistExtraction(request, extracted);
    return saved.actions().getFirst().id();
  }

  @Test
  void updateAction_updates_non_null_fields() {
    UUID id = persistAndGetId();

    ActionUpdateRequest update = new ActionUpdateRequest(
        "수정된 제목", "수정된 요약", null, null, null, null, null
    );
    SavedActionDetailDto result = persistenceService.updateAction(id, update);

    assertThat(result.title()).isEqualTo("수정된 제목");
    assertThat(result.actionSummary()).isEqualTo("수정된 요약");
    // Unchanged fields
    assertThat(result.dueAtLabel()).isNotNull();
  }

  @Test
  void updateAction_preserves_null_fields() {
    UUID id = persistAndGetId();
    SavedActionDetailDto before = persistenceService.getActionDetail(id);

    // Only update title — everything else stays
    ActionUpdateRequest update = new ActionUpdateRequest(
        "새 제목", null, null, null, null, null, null
    );
    SavedActionDetailDto result = persistenceService.updateAction(id, update);

    assertThat(result.title()).isEqualTo("새 제목");
    assertThat(result.actionSummary()).isEqualTo(before.actionSummary());
    assertThat(result.eligibility()).isEqualTo(before.eligibility());
  }

  @Test
  void updateAction_throws_for_unknown_id() {
    UUID unknownId = UUID.randomUUID();
    ActionUpdateRequest update = new ActionUpdateRequest(
        "제목", null, null, null, null, null, null
    );

    assertThatThrownBy(() -> persistenceService.updateAction(unknownId, update))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void updateAction_rejects_blank_title() {
    UUID id = persistAndGetId();
    ActionUpdateRequest update = new ActionUpdateRequest(
        "   ", null, null, null, null, null, null
    );

    assertThatThrownBy(() -> persistenceService.updateAction(id, update))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("제목");
  }
}
