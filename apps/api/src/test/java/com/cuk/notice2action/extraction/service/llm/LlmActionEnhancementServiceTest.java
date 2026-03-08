package com.cuk.notice2action.extraction.service.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cuk.notice2action.extraction.api.dto.EvidenceSnippetDto;
import com.cuk.notice2action.extraction.api.dto.ExtractedActionDto;
import com.cuk.notice2action.extraction.domain.SourceCategory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class LlmActionEnhancementServiceTest {

  private ObjectMapper objectMapper;
  private LlmActionEnhancementService service;

  @BeforeEach
  @SuppressWarnings("null") // Mockito any() matchers return @Nullable; safe at runtime
  void setUp() {
    objectMapper = new ObjectMapper();
    // Use a mock RestClient that throws by default for HTTP tests
    RestClient mockRestClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
    when(mockRestClient.post().uri(any(String.class)).body(any()).retrieve().body(String.class))
        .thenThrow(new RuntimeException("Network error"));
    service = new LlmActionEnhancementService(mockRestClient, objectMapper);
  }

  // ── parseResponse ──────────────────────────────────────────────────────────

  @Test
  void parseResponse_succeeds_withValidAnthropicResponse() throws Exception {
    String innerJson = """
        {"title":"수강신청","actionSummary":"TRINITY에서 수강신청을 완료하세요.",\
        "dueAtLabel":"3월 15일까지","dueAtIso":"2026-03-15T00:00:00+09:00",\
        "eligibility":"재학생","requiredItems":[],"systemHint":"TRINITY"}""";
    String responseBody = anthropicResponse(innerJson);

    Optional<LlmEnhancedFields> result = service.parseResponse(responseBody);

    assertThat(result).isPresent();
    assertThat(result.get().title()).isEqualTo("수강신청");
    assertThat(result.get().dueAtLabel()).isEqualTo("3월 15일까지");
    assertThat(result.get().systemHint()).isEqualTo("TRINITY");
  }

  @Test
  void parseResponse_succeeds_withMarkdownCodeBlock() throws Exception {
    String withCodeBlock = "```json\n"
        + "{\"title\":\"테스트\",\"actionSummary\":\"요약\","
        + "\"dueAtLabel\":null,\"dueAtIso\":null,"
        + "\"eligibility\":null,\"requiredItems\":[],\"systemHint\":null}\n```";
    String responseBody = anthropicResponse(withCodeBlock);

    Optional<LlmEnhancedFields> result = service.parseResponse(responseBody);

    assertThat(result).isPresent();
    assertThat(result.get().title()).isEqualTo("테스트");
  }

  @Test
  void parseResponse_returnsEmpty_withInvalidInnerJson() throws Exception {
    String responseBody = anthropicResponse("이것은 JSON이 아닙니다.");
    Optional<LlmEnhancedFields> result = service.parseResponse(responseBody);
    assertThat(result).isEmpty();
  }

  @Test
  void parseResponse_returnsEmpty_whenContentMissing() {
    // Anthropic response without content block → NPE caught gracefully
    Optional<LlmEnhancedFields> result = service.parseResponse("{}");
    assertThat(result).isEmpty();
  }

  // ── enhance (HTTP layer) ───────────────────────────────────────────────────

  @Test
  void enhance_returnsEmpty_onHttpFailure() {
    ExtractedActionDto dto = makeDto(List.of());
    Optional<LlmEnhancedFields> result = service.enhance("텍스트", "NOTICE", dto);
    assertThat(result).isEmpty();
  }

  // ── mergeWithHeuristic ────────────────────────────────────────────────────

  @Test
  void mergeWithHeuristic_addsLlmEvidenceSnippet() {
    ExtractedActionDto heuristic = makeDto(List.of());
    LlmEnhancedFields llm = new LlmEnhancedFields(
        "AI 제목", "AI 요약", "3월 20일까지", "2026-03-20T00:00:00+09:00",
        null, List.of(), null
    );
    ExtractedActionDto merged = service.mergeWithHeuristic(heuristic, llm);

    assertThat(merged.evidence())
        .anyMatch(e -> "llmEnhancement".equals(e.fieldName()));
  }

  @Test
  void mergeWithHeuristic_usesLlmTitle_whenProvided() {
    ExtractedActionDto heuristic = makeDto(List.of());
    LlmEnhancedFields llm = new LlmEnhancedFields(
        "더 나은 제목", null, null, null, null, null, null
    );
    ExtractedActionDto merged = service.mergeWithHeuristic(heuristic, llm);

    assertThat(merged.title()).isEqualTo("더 나은 제목");
  }

  @Test
  void mergeWithHeuristic_keepsheuristic_whenLlmNull() {
    List<EvidenceSnippetDto> evidence = List.of(
        new EvidenceSnippetDto("dueAtLabel", "3월 10일", 0.95)
    );
    ExtractedActionDto heuristic = makeDtoWithDue(evidence, "3월 10일", "2026-03-10T00:00:00+09:00");
    LlmEnhancedFields llm = new LlmEnhancedFields(null, null, null, null, null, null, null);

    ExtractedActionDto merged = service.mergeWithHeuristic(heuristic, llm);

    assertThat(merged.dueAtLabel()).isEqualTo("3월 10일");
    assertThat(merged.dueAtIso()).isEqualTo("2026-03-10T00:00:00+09:00");
  }

  @Test
  void mergeWithHeuristic_keepsheuristic_dueDate_whenConfidenceHigh() {
    // Due date evidence confidence = 0.95 (high) → keep heuristic even if LLM provides value
    List<EvidenceSnippetDto> evidence = List.of(
        new EvidenceSnippetDto("dueAtLabel", "3월 10일", 0.95)
    );
    ExtractedActionDto heuristic = makeDtoWithDue(evidence, "3월 10일", "2026-03-10T00:00:00+09:00");
    LlmEnhancedFields llm = new LlmEnhancedFields(null, null, "4월 1일까지", "2026-04-01T00:00:00+09:00",
        null, null, null);

    ExtractedActionDto merged = service.mergeWithHeuristic(heuristic, llm);

    assertThat(merged.dueAtLabel()).isEqualTo("3월 10일");
  }

  @Test
  void mergeWithHeuristic_usesLlmDueDate_whenConfidenceLow() {
    // Due date evidence confidence = 0.50 (low) → take LLM value
    List<EvidenceSnippetDto> evidence = List.of(
        new EvidenceSnippetDto("dueAtLabel", "이번 주", 0.50)
    );
    ExtractedActionDto heuristic = makeDtoWithDue(evidence, "이번 주", null);
    LlmEnhancedFields llm = new LlmEnhancedFields(null, null, "3월 20일까지", "2026-03-20T00:00:00+09:00",
        null, null, null);

    ExtractedActionDto merged = service.mergeWithHeuristic(heuristic, llm);

    assertThat(merged.dueAtLabel()).isEqualTo("3월 20일까지");
    assertThat(merged.dueAtIso()).isEqualTo("2026-03-20T00:00:00+09:00");
  }

  @Test
  void mergeWithHeuristic_recalculatesConfidenceScore() {
    ExtractedActionDto heuristic = makeDto(List.of());
    LlmEnhancedFields llm = new LlmEnhancedFields("제목", "요약", null, null, null, null, null);

    ExtractedActionDto merged = service.mergeWithHeuristic(heuristic, llm);

    // After adding llmEnhancement evidence (0.80), score should be > 0
    assertThat(merged.confidenceScore()).isGreaterThan(0.0);
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private ExtractedActionDto makeDto(List<EvidenceSnippetDto> evidence) {
    return new ExtractedActionDto(
        null, null,
        "기존 제목", "기존 요약",
        null, null,
        List.of(),
        null, null, List.of(), null,
        SourceCategory.NOTICE,
        evidence, true, 0.0, null, "pending"
    );
  }

  private ExtractedActionDto makeDtoWithDue(
      List<EvidenceSnippetDto> evidence, String dueLabel, String dueIso
  ) {
    return new ExtractedActionDto(
        null, null,
        "기존 제목", "기존 요약",
        dueIso, dueLabel,
        List.of(),
        null, null, List.of(), null,
        SourceCategory.NOTICE,
        evidence, true, 0.50, null, "pending"
    );
  }

  /** Wraps inner text into a minimal Anthropic Messages API response JSON. */
  private String anthropicResponse(String innerText) throws Exception {
    Map<String, Object> response = Map.of(
        "content", List.of(Map.of("type", "text", "text", innerText))
    );
    return objectMapper.writeValueAsString(response);
  }
}
