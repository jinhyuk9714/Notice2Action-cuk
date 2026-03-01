package com.cuk.notice2action.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cuk.notice2action.extraction.api.dto.ActionExtractionRequest;
import com.cuk.notice2action.extraction.api.dto.ActionExtractionResponse;
import com.cuk.notice2action.extraction.api.dto.ExtractedActionDto;
import com.cuk.notice2action.extraction.api.dto.SavedActionDetailDto;
import com.cuk.notice2action.extraction.domain.SourceCategory;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ConfidenceScoreTest {

  @Autowired
  private ActionExtractionService extractionService;

  @Autowired
  private ActionPersistenceService persistenceService;

  @Test
  void confidenceScore_is_average_of_evidence_confidences() {
    String text = "2026년 3월 12일까지 TRINITY에서 공결 신청을 완료하세요. 대상: 재학생. 증빙서류를 제출해야 합니다.";
    ActionExtractionRequest request = new ActionExtractionRequest(
        text, null, "테스트", SourceCategory.NOTICE, List.of()
    );
    ActionExtractionResponse result = extractionService.extract(request);

    assertThat(result.actions()).isNotEmpty();
    ExtractedActionDto action = result.actions().getFirst();
    assertThat(action.confidenceScore()).isGreaterThan(0.0);
    assertThat(action.confidenceScore()).isLessThanOrEqualTo(1.0);

    // Verify it's the average of evidence confidences
    double expectedAvg = action.evidence().stream()
        .mapToDouble(e -> e.confidence())
        .average()
        .orElse(0.0);
    assertThat(action.confidenceScore()).isEqualTo(expectedAvg);
  }

  @Test
  void confidenceScore_is_zero_when_no_evidence() {
    // Very minimal text that produces no evidence
    String text = "안내사항입니다.";
    ActionExtractionRequest request = new ActionExtractionRequest(
        text, null, "테스트", SourceCategory.NOTICE, List.of()
    );
    ActionExtractionResponse result = extractionService.extract(request);

    assertThat(result.actions()).isNotEmpty();
    ExtractedActionDto action = result.actions().getFirst();
    if (action.evidence().isEmpty()) {
      assertThat(action.confidenceScore()).isEqualTo(0.0);
    }
  }

  @Test
  void confidenceScore_persisted_and_returned_in_detail() {
    String text = "2026년 3월 12일까지 TRINITY에서 공결 신청을 완료하세요. 대상: 재학생.";
    ActionExtractionRequest request = new ActionExtractionRequest(
        text, null, "신뢰도 테스트", SourceCategory.NOTICE, List.of()
    );
    ActionExtractionResponse extracted = extractionService.extract(request);
    ActionExtractionResponse persisted = persistenceService.persistExtraction(request, extracted);

    UUID actionId = persisted.actions().getFirst().id();
    SavedActionDetailDto detail = persistenceService.getActionDetail(actionId);

    assertThat(detail.confidenceScore()).isGreaterThan(0.0);
    assertThat(detail.confidenceScore()).isEqualTo(extracted.actions().getFirst().confidenceScore());
  }
}
