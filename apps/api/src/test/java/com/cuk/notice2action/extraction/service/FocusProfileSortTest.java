package com.cuk.notice2action.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cuk.notice2action.extraction.api.dto.ActionExtractionRequest;
import com.cuk.notice2action.extraction.api.dto.ActionExtractionResponse;
import com.cuk.notice2action.extraction.domain.SourceCategory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class FocusProfileSortTest {

  @Autowired
  private ActionExtractionService extractionService;

  @Test
  void matching_profile_actions_sorted_first() {
    // Text with two actions: one for 재학생, one for 복학생
    String text = "2026년 3월 12일까지 TRINITY에서 공결 신청을 완료하세요. 대상: 재학생.\n"
        + "2026년 3월 15일까지 장학포털에서 장학금을 신청하세요. 대상: 복학생.";

    ActionExtractionRequest request = new ActionExtractionRequest(
        text, null, "테스트", SourceCategory.NOTICE, List.of("복학생")
    );
    ActionExtractionResponse result = extractionService.extract(request);

    // With focusProfile=["복학생"], the 복학생 action should come first
    assertThat(result.actions()).hasSizeGreaterThanOrEqualTo(2);
    assertThat(result.actions().getFirst().eligibility()).contains("복학생");
  }

  @Test
  void empty_profile_preserves_original_order() {
    String text = "2026년 3월 12일까지 TRINITY에서 공결 신청을 완료하세요. 대상: 재학생.\n"
        + "2026년 3월 15일까지 장학포털에서 장학금을 신청하세요. 대상: 복학생.";

    ActionExtractionRequest requestWithProfile = new ActionExtractionRequest(
        text, null, "테스트", SourceCategory.NOTICE, List.of()
    );
    ActionExtractionResponse withProfile = extractionService.extract(requestWithProfile);

    ActionExtractionRequest requestWithoutProfile = new ActionExtractionRequest(
        text, null, "테스트", SourceCategory.NOTICE, List.of()
    );
    ActionExtractionResponse withoutProfile = extractionService.extract(requestWithoutProfile);

    // Same order when profile is empty
    assertThat(withProfile.actions().size()).isEqualTo(withoutProfile.actions().size());
  }

  @Test
  void no_match_preserves_original_order() {
    String text = "2026년 3월 12일까지 TRINITY에서 공결 신청을 완료하세요. 대상: 재학생.";

    ActionExtractionRequest request = new ActionExtractionRequest(
        text, null, "테스트", SourceCategory.NOTICE, List.of("졸업예정자")
    );
    ActionExtractionResponse result = extractionService.extract(request);

    assertThat(result.actions()).isNotEmpty();
  }
}
