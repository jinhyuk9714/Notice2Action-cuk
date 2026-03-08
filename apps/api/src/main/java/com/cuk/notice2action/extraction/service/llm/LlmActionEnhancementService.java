package com.cuk.notice2action.extraction.service.llm;

import com.cuk.notice2action.extraction.api.dto.EvidenceSnippetDto;
import com.cuk.notice2action.extraction.api.dto.ExtractedActionDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@ConditionalOnProperty(name = "app.anthropic.enabled", havingValue = "true")
public class LlmActionEnhancementService {

  private static final String MESSAGES_PATH = "/v1/messages";
  private static final String MODEL = "claude-haiku-4-5-20251001";
  private static final String ANTHROPIC_VERSION = "2023-06-01";
  private static final int MAX_SOURCE_CHARS = 2000;

  private static final String SYSTEM_PROMPT = """
      당신은 한국 대학 공지문에서 행동 항목을 추출하는 도우미입니다.
      반드시 아래 JSON 형식으로만 응답하세요 (다른 설명 없이):
      {
        "title": "간결한 행동 제목 (모르면 null)",
        "actionSummary": "지금 해야 할 일 한 문장 (모르면 null)",
        "dueAtLabel": "마감일 표현 (예: 3월 15일까지, 모르면 null)",
        "dueAtIso": "ISO-8601 날짜 문자열 (예: 2026-03-15T00:00:00+09:00, 모르면 null)",
        "eligibility": "대상 조건 (모르면 null)",
        "requiredItems": ["제출 서류1", "제출 서류2"],
        "systemHint": "신청 시스템명 (예: TRINITY, 사이버캠퍼스, 모르면 null)"
      }
      requiredItems가 없으면 빈 배열 [] 을 반환하세요.
      """;

  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public LlmActionEnhancementService(
      @Value("${app.anthropic.api-key}") String apiKey,
      ObjectMapper objectMapper
  ) {
    this(
        RestClient.builder()
            .baseUrl("https://api.anthropic.com")
            .defaultHeader("x-api-key", apiKey)
            .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
            .defaultHeader("content-type", "application/json")
            .build(),
        objectMapper
    );
  }

  // Package-private constructor for testing
  LlmActionEnhancementService(RestClient restClient, ObjectMapper objectMapper) {
    this.restClient = restClient;
    this.objectMapper = objectMapper;
  }

  /**
   * 현재 heuristic 결과를 Claude API로 보완한다.
   * 실패 시 Optional.empty() 반환 (graceful degradation).
   */
  public Optional<LlmEnhancedFields> enhance(
      String sourceText,
      String sourceCategory,
      ExtractedActionDto current
  ) {
    try {
      String requestBody = buildRequestBody(sourceText, sourceCategory, current);
      String responseBody = restClient.post()
          .uri(MESSAGES_PATH)
          .body(Objects.requireNonNull(requestBody))
          .retrieve()
          .body(String.class);
      return parseResponse(responseBody);
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  /**
   * LLM 결과를 heuristic 결과와 병합한다.
   * 각 필드: LLM non-null + 해당 필드 evidence 신뢰도 < 0.70 → LLM 값 사용.
   * title은 LLM 값이 있으면 항상 사용한다 (evidence backing 없음).
   */
  public ExtractedActionDto mergeWithHeuristic(
      ExtractedActionDto heuristic,
      LlmEnhancedFields llm
  ) {
    List<EvidenceSnippetDto> evidence = new ArrayList<>(heuristic.evidence());

    String title = llm.title() != null ? llm.title() : heuristic.title();

    String actionSummary = (llm.actionSummary() != null
        && fieldConfidence(evidence, "actionVerb") < 0.70)
        ? llm.actionSummary()
        : heuristic.actionSummary();

    boolean dueWeak = fieldConfidence(evidence, "dueAtLabel") < 0.70;
    String dueAtLabel = (llm.dueAtLabel() != null && dueWeak) ? llm.dueAtLabel() : heuristic.dueAtLabel();
    String dueAtIso = (llm.dueAtIso() != null && dueWeak) ? llm.dueAtIso() : heuristic.dueAtIso();

    boolean eligibilityChanged = false;
    String eligibility = heuristic.eligibility();
    if (llm.eligibility() != null && fieldConfidence(evidence, "eligibility") < 0.70) {
      eligibility = llm.eligibility();
      eligibilityChanged = true;
    }

    List<String> requiredItems = heuristic.requiredItems();
    if (llm.requiredItems() != null && !llm.requiredItems().isEmpty()
        && fieldConfidence(evidence, "requiredItems") < 0.70) {
      requiredItems = llm.requiredItems();
    }

    String systemHint = (llm.systemHint() != null
        && fieldConfidence(evidence, "systemHint") < 0.70)
        ? llm.systemHint()
        : heuristic.systemHint();

    evidence.add(new EvidenceSnippetDto("llmEnhancement", "AI 보완 적용", 0.80));

    double newScore = computeConfidenceScore(evidence);
    boolean newInferred = computeInferred(evidence);

    return new ExtractedActionDto(
        heuristic.id(),
        heuristic.sourceId(),
        title,
        actionSummary,
        dueAtIso,
        dueAtLabel,
        heuristic.additionalDates(),
        eligibility,
        eligibilityChanged ? null : heuristic.structuredEligibility(),
        requiredItems,
        systemHint,
        heuristic.sourceCategory(),
        evidence,
        newInferred,
        newScore,
        heuristic.createdAt(),
        heuristic.status()
    );
  }

  private double fieldConfidence(List<EvidenceSnippetDto> evidence, String fieldName) {
    return evidence.stream()
        .filter(e -> fieldName.equals(e.fieldName()))
        .mapToDouble(EvidenceSnippetDto::confidence)
        .max()
        .orElse(0.0);
  }

  String buildRequestBody(String sourceText, String sourceCategory, ExtractedActionDto current) {
    String text = sourceText.length() > MAX_SOURCE_CHARS
        ? sourceText.substring(0, MAX_SOURCE_CHARS)
        : sourceText;

    String userMessage = String.format(
        "[카테고리: %s] 공지문에서 액션을 추출하세요.%n%n"
            + "현재 추출 결과 (신뢰도 낮음 — 보완 필요):%n"
            + "- 제목: %s%n"
            + "- 요약: %s%n"
            + "- 마감일: %s%n%n"
            + "원문:%n%s",
        sourceCategory,
        current.title() != null ? current.title() : "없음",
        current.actionSummary() != null ? current.actionSummary() : "없음",
        current.dueAtLabel() != null ? current.dueAtLabel() : "없음",
        text
    );

    try {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("model", MODEL);
      body.put("max_tokens", 512);
      body.put("system", SYSTEM_PROMPT);
      body.put("messages", List.of(Map.of("role", "user", "content", userMessage)));
      return objectMapper.writeValueAsString(body);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to build LLM request body", e);
    }
  }

  Optional<LlmEnhancedFields> parseResponse(String responseBody) {
    try {
      JsonNode root = objectMapper.readTree(responseBody);
      String text = root.path("content").get(0).path("text").asText("");
      text = extractJson(text);
      return Optional.of(objectMapper.readValue(text, LlmEnhancedFields.class));
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  private String extractJson(String text) {
    text = text.strip();
    if (text.startsWith("```")) {
      int newline = text.indexOf('\n');
      int closing = text.lastIndexOf("```");
      if (newline >= 0 && closing > newline) {
        text = text.substring(newline + 1, closing).strip();
      }
    }
    return text;
  }

  private static double computeConfidenceScore(List<EvidenceSnippetDto> evidence) {
    if (evidence.isEmpty()) return 0.0;
    return evidence.stream().mapToDouble(EvidenceSnippetDto::confidence).average().orElse(0.0);
  }

  private static boolean computeInferred(List<EvidenceSnippetDto> evidence) {
    if (evidence.isEmpty()) return true;
    return evidence.stream().anyMatch(e -> e.confidence() < 0.75);
  }
}
