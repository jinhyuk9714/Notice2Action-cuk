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
class DuplicateDetectionTest {

  @Autowired
  private ActionExtractionService extractionService;

  @Autowired
  private ActionPersistenceService persistenceService;

  private ActionExtractionResponse extractAndPersist(String text, String title, String url) {
    ActionExtractionRequest request = new ActionExtractionRequest(
        text, url, title, SourceCategory.NOTICE, List.of()
    );
    ActionExtractionResponse extracted = extractionService.extract(request);
    return persistenceService.persistExtraction(request, extracted);
  }

  @Test
  void same_text_is_detected_as_duplicate() {
    String text = "2026년 3월 12일까지 TRINITY에서 공결 신청을 완료하세요.";

    ActionExtractionResponse first = extractAndPersist(text, "첫 번째", null);
    assertThat(first.duplicate()).isFalse();

    ActionExtractionResponse second = extractAndPersist(text, "두 번째", null);
    assertThat(second.duplicate()).isTrue();
    assertThat(second.actions().getFirst().id())
        .isEqualTo(first.actions().getFirst().id());
  }

  @Test
  void same_url_is_detected_as_duplicate() {
    String url = "https://example.com/notice/123";

    ActionExtractionResponse first = extractAndPersist(
        "첫 번째 내용입니다. 2026년 3월 12일까지 신청하세요.", "첫 번째", url
    );
    assertThat(first.duplicate()).isFalse();

    ActionExtractionResponse second = extractAndPersist(
        "다른 내용이지만 URL이 같음. 2026년 3월 15일까지 신청하세요.", "두 번째", url
    );
    assertThat(second.duplicate()).isTrue();
  }

  @Test
  void different_text_creates_new_source() {
    ActionExtractionResponse first = extractAndPersist(
        "2026년 3월 12일까지 TRINITY에서 공결 신청을 완료하세요.", "A", null
    );
    ActionExtractionResponse second = extractAndPersist(
        "2026년 3월 20일까지 장학포털에서 장학금 신청을 완료하세요.", "B", null
    );

    assertThat(first.duplicate()).isFalse();
    assertThat(second.duplicate()).isFalse();
    assertThat(second.actions().getFirst().id())
        .isNotEqualTo(first.actions().getFirst().id());
  }

  @Test
  void hash_is_consistent() {
    String text = "동일한 텍스트";
    String hash1 = ContentHashUtil.sha256(text);
    String hash2 = ContentHashUtil.sha256(text);
    assertThat(hash1).isEqualTo(hash2);
    assertThat(hash1).hasSize(64);
  }
}
