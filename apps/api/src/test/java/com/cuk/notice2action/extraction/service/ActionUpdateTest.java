package com.cuk.notice2action.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cuk.notice2action.extraction.api.dto.ActionExtractionRequest;
import com.cuk.notice2action.extraction.api.dto.ActionExtractionResponse;
import com.cuk.notice2action.extraction.api.dto.ActionUpdateRequest;
import com.cuk.notice2action.extraction.api.dto.FieldOverrideInfoDto;
import com.cuk.notice2action.extraction.api.dto.SavedActionDetailDto;
import com.cuk.notice2action.extraction.domain.SourceCategory;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
        "수정된 제목", "수정된 요약", null, null, null, null, null, null
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
        "새 제목", null, null, null, null, null, null, null
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
        "제목", null, null, null, null, null, null, null
    );

    assertThatThrownBy(() -> persistenceService.updateAction(unknownId, update))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void updateAction_rejects_blank_title() {
    UUID id = persistAndGetId();
    ActionUpdateRequest update = new ActionUpdateRequest(
        "   ", null, null, null, null, null, null, null
    );

    assertThatThrownBy(() -> persistenceService.updateAction(id, update))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("제목");
  }

  @Test
  void updateAction_rejects_invalid_dueAtIso() {
    UUID id = persistAndGetId();
    ActionUpdateRequest update = new ActionUpdateRequest(
        null, null, "invalid-datetime", null, null, null, null, null
    );

    assertThatThrownBy(() -> persistenceService.updateAction(id, update))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dueAtIso");
  }

  @Test
  void updateAction_updates_status_when_valid() {
    UUID id = persistAndGetId();

    SavedActionDetailDto result = persistenceService.updateAction(
        id,
        new ActionUpdateRequest(null, null, null, null, null, null, null, null, "completed")
    );

    assertThat(result.status()).isEqualTo("completed");
  }

  @Test
  void updateAction_rejects_invalid_status() {
    UUID id = persistAndGetId();

    assertThatThrownBy(() -> persistenceService.updateAction(
        id,
        new ActionUpdateRequest(null, null, null, null, null, null, null, null, "done")
    ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("status");
  }

  // --- Override tracking tests ---

  @Test
  void updateAction_no_overrides_initially() {
    UUID id = persistAndGetId();
    SavedActionDetailDto detail = persistenceService.getActionDetail(id);

    assertThat(detail.overrides()).isEmpty();
  }

  @Test
  void updateAction_tracks_override_for_title() {
    UUID id = persistAndGetId();
    SavedActionDetailDto before = persistenceService.getActionDetail(id);
    String originalTitle = before.title();

    ActionUpdateRequest update = new ActionUpdateRequest(
        "사용자 수정 제목", null, null, null, null, null, null, null
    );
    SavedActionDetailDto result = persistenceService.updateAction(id, update);

    assertThat(result.title()).isEqualTo("사용자 수정 제목");
    assertThat(result.overrides()).hasSize(1);
    assertThat(result.overrides().getFirst().fieldName()).isEqualTo("title");
    assertThat(result.overrides().getFirst().machineValue()).isEqualTo(originalTitle);
  }

  @Test
  void updateAction_preserves_machine_value_on_second_edit() {
    UUID id = persistAndGetId();
    SavedActionDetailDto before = persistenceService.getActionDetail(id);
    String originalTitle = before.title();

    // First edit
    persistenceService.updateAction(id, new ActionUpdateRequest(
        "첫 번째 수정", null, null, null, null, null, null, null
    ));
    // Second edit — machine value should still be the original
    SavedActionDetailDto result = persistenceService.updateAction(id, new ActionUpdateRequest(
        "두 번째 수정", null, null, null, null, null, null, null
    ));

    assertThat(result.title()).isEqualTo("두 번째 수정");
    assertThat(result.overrides()).hasSize(1);
    assertThat(result.overrides().getFirst().machineValue()).isEqualTo(originalTitle);
  }

  @Test
  void updateAction_revert_restores_machine_value() {
    UUID id = persistAndGetId();
    SavedActionDetailDto before = persistenceService.getActionDetail(id);
    String originalTitle = before.title();

    // Edit title
    persistenceService.updateAction(id, new ActionUpdateRequest(
        "수정된 제목", null, null, null, null, null, null, null
    ));
    // Revert title
    SavedActionDetailDto result = persistenceService.updateAction(id, new ActionUpdateRequest(
        null, null, null, null, null, null, null, List.of("title")
    ));

    assertThat(result.title()).isEqualTo(originalTitle);
    assertThat(result.overrides()).isEmpty();
  }

  @Test
  void updateAction_revert_unknown_field_throws() {
    UUID id = persistAndGetId();
    ActionUpdateRequest update = new ActionUpdateRequest(
        null, null, null, null, null, null, null, List.of("nonExistentField")
    );

    assertThatThrownBy(() -> persistenceService.updateAction(id, update))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("되돌릴 수 없는 필드");
  }

  @Test
  void updateAction_tracks_multiple_field_overrides() {
    UUID id = persistAndGetId();

    ActionUpdateRequest update = new ActionUpdateRequest(
        "수정 제목", "수정 요약", null, null, "전체 학생", null, null, null
    );
    SavedActionDetailDto result = persistenceService.updateAction(id, update);

    assertThat(result.overrides()).hasSize(3);
    Set<String> overriddenFields = result.overrides().stream()
        .map(FieldOverrideInfoDto::fieldName)
        .collect(Collectors.toSet());
    assertThat(overriddenFields).containsExactlyInAnyOrder("title", "actionSummary", "eligibility");
  }

  @Test
  void updateAction_recomputes_structured_eligibility_when_eligibility_changes() {
    UUID id = persistAndGetId();

    SavedActionDetailDto result = persistenceService.updateAction(id, new ActionUpdateRequest(
        null,
        null,
        null,
        null,
        "컴퓨터정보공학부 3학년 재학생 대상",
        null,
        null,
        null
    ));

    assertThat(result.structuredEligibility()).isNotNull();
    assertThat(result.structuredEligibility().statuses()).contains("재학생");
    assertThat(result.structuredEligibility().years()).contains(3);
    assertThat(result.structuredEligibility().department()).isEqualTo("컴퓨터정보공학");
  }
}
