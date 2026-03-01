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

    ActionListResponse list = persistenceService.listActions("recent");

    assertThat(list.actions()).isNotEmpty();
    assertThat(list.actions().getFirst().title()).isEqualTo("공결 신청 안내");
    assertThat(list.actions().getFirst().sourceCategory()).isEqualTo(SourceCategory.NOTICE);
  }

  @Test
  void listActions_with_due_sort_returns_soonest_first() {
    // Action with earlier due date
    ActionExtractionRequest earlyReq = new ActionExtractionRequest(
        "2026년 3월 5일까지 TRINITY에서 신청을 완료하세요.",
        null, "조기 마감", SourceCategory.NOTICE, List.of()
    );
    ActionExtractionResponse earlyExtracted = extractionService.extract(earlyReq);
    persistenceService.persistExtraction(earlyReq, earlyExtracted);

    // Action with later due date
    ActionExtractionRequest lateReq = new ActionExtractionRequest(
        "2026년 3월 20일까지 장학포털에서 장학금 신청을 완료하세요.",
        null, "늦은 마감", SourceCategory.NOTICE, List.of()
    );
    ActionExtractionResponse lateExtracted = extractionService.extract(lateReq);
    persistenceService.persistExtraction(lateReq, lateExtracted);

    // Action with no due date
    ActionExtractionRequest noDueReq = new ActionExtractionRequest(
        "학교 공지사항을 확인하세요.",
        null, "마감 없음", SourceCategory.NOTICE, List.of()
    );
    ActionExtractionResponse noDueExtracted = extractionService.extract(noDueReq);
    persistenceService.persistExtraction(noDueReq, noDueExtracted);

    ActionListResponse list = persistenceService.listActions("due");

    assertThat(list.actions()).hasSizeGreaterThanOrEqualTo(3);
    // Earlier due date comes first
    assertThat(list.actions().get(0).title()).isEqualTo("조기 마감");
    assertThat(list.actions().get(1).title()).isEqualTo("늦은 마감");
    // Null due date comes last
    assertThat(list.actions().get(list.actions().size() - 1).dueAtIso()).isNull();
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
