package com.cuk.notice2action.extraction.service;

import com.cuk.notice2action.extraction.api.dto.ActionExtractionRequest;
import com.cuk.notice2action.extraction.api.dto.ActionExtractionResponse;
import com.cuk.notice2action.extraction.api.dto.AdditionalDateDto;
import com.cuk.notice2action.extraction.api.dto.EvidenceSnippetDto;
import com.cuk.notice2action.extraction.api.dto.ExtractedActionDto;
import com.cuk.notice2action.extraction.service.extractor.ActionSegmenter;
import com.cuk.notice2action.extraction.service.extractor.ActionSummaryBuilder;
import com.cuk.notice2action.extraction.service.extractor.ActionVerbExtractor;
import com.cuk.notice2action.extraction.service.extractor.DateExtractor;
import com.cuk.notice2action.extraction.service.extractor.EligibilityExtractor;
import com.cuk.notice2action.extraction.service.extractor.RequiredItemExtractor;
import com.cuk.notice2action.extraction.service.extractor.StructuredEligibilityParser;
import com.cuk.notice2action.extraction.service.extractor.SystemHintExtractor;
import com.cuk.notice2action.extraction.service.extractor.TaskPhraseExtractor;
import com.cuk.notice2action.extraction.service.extractor.TextNormalizer;
import com.cuk.notice2action.extraction.service.extractor.TitleDeriver;
import com.cuk.notice2action.extraction.service.model.ActionSegment;
import com.cuk.notice2action.extraction.service.model.DateMatch;
import com.cuk.notice2action.extraction.service.model.StructuredEligibility;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class HeuristicActionExtractionService implements ActionExtractionService {

  private static final int MAX_ACTIONS = 5;

  private final TextNormalizer textNormalizer;
  private final DateExtractor dateExtractor;
  private final SystemHintExtractor systemHintExtractor;
  private final RequiredItemExtractor requiredItemExtractor;
  private final ActionVerbExtractor actionVerbExtractor;
  private final EligibilityExtractor eligibilityExtractor;
  private final ActionSegmenter actionSegmenter;
  private final ActionSummaryBuilder actionSummaryBuilder;
  private final TitleDeriver titleDeriver;
  private final TaskPhraseExtractor taskPhraseExtractor;
  private final StructuredEligibilityParser structuredEligibilityParser;

  public HeuristicActionExtractionService(
      TextNormalizer textNormalizer,
      DateExtractor dateExtractor,
      SystemHintExtractor systemHintExtractor,
      RequiredItemExtractor requiredItemExtractor,
      ActionVerbExtractor actionVerbExtractor,
      EligibilityExtractor eligibilityExtractor,
      ActionSegmenter actionSegmenter,
      ActionSummaryBuilder actionSummaryBuilder,
      TitleDeriver titleDeriver,
      TaskPhraseExtractor taskPhraseExtractor,
      StructuredEligibilityParser structuredEligibilityParser
  ) {
    this.textNormalizer = textNormalizer;
    this.dateExtractor = dateExtractor;
    this.systemHintExtractor = systemHintExtractor;
    this.requiredItemExtractor = requiredItemExtractor;
    this.actionVerbExtractor = actionVerbExtractor;
    this.eligibilityExtractor = eligibilityExtractor;
    this.actionSegmenter = actionSegmenter;
    this.actionSummaryBuilder = actionSummaryBuilder;
    this.titleDeriver = titleDeriver;
    this.taskPhraseExtractor = taskPhraseExtractor;
    this.structuredEligibilityParser = structuredEligibilityParser;
  }

  @Override
  public ActionExtractionResponse extract(ActionExtractionRequest request) {
    String normalizedText = textNormalizer.normalize(request.sourceText());
    if (normalizedText.isBlank()) {
      throw new IllegalArgumentException("sourceText must not be blank");
    }

    List<ActionSegment> segments = actionSegmenter.segment(normalizedText);

    if (segments.size() <= 1) {
      ActionSegment singleSegment = segments.isEmpty() ? null : segments.getFirst();
      List<ExtractedActionDto> result =
          List.of(
              extractSingleAction(
                  request,
                  normalizedText,
                  null,
                  singleSegment != null ? singleSegment.taskPhrase() : null,
                  0,
                  1
              )
          );
      return new ActionExtractionResponse(
          sortByProfile(prioritizeSourceTitleTask(resolveDuplicateTitles(result), request.sourceTitle()), request.focusProfile())
      );
    }

    List<ExtractedActionDto> actions = new ArrayList<>();
    int total = Math.min(segments.size(), MAX_ACTIONS);
    for (int i = 0; i < total; i++) {
      ActionSegment seg = segments.get(i);
      actions.add(extractSingleAction(request, seg.text(), seg.primaryVerb(), seg.taskPhrase(), i + 1, total));
    }
    return new ActionExtractionResponse(
        sortByProfile(prioritizeSourceTitleTask(resolveDuplicateTitles(actions), request.sourceTitle()), request.focusProfile())
    );
  }

  private ExtractedActionDto extractSingleAction(
      ActionExtractionRequest request,
      String text,
      String overrideVerb,
      String overrideTaskPhrase,
      int actionIndex,
      int totalActions
  ) {
    List<EvidenceSnippetDto> evidence = new ArrayList<>();

    List<DateMatch> allDates = dateExtractor.extractAll(text, evidence);
    DateMatch dateMatch = allDates.isEmpty() ? null : allDates.getFirst();
    String dueAtLabel = dateMatch != null ? dateMatch.label() : null;
    String dueAtIso = dateMatch != null ? dateExtractor.formatIso(dateMatch.components()) : null;
    List<AdditionalDateDto> additionalDates = allDates.size() > 1
        ? allDates.subList(1, allDates.size()).stream()
            .map(match -> new AdditionalDateDto(dateExtractor.formatIso(match.components()), match.label()))
            .toList()
        : List.of();
    String systemHint = systemHintExtractor.extract(text, evidence);
    List<String> requiredItems = requiredItemExtractor.extract(text, evidence);
    String actionVerb = overrideVerb != null ? overrideVerb : actionVerbExtractor.extract(text, evidence);
    String eligibility = eligibilityExtractor.extract(text, evidence);
    StructuredEligibility structuredEligibility = structuredEligibilityParser.parse(eligibility);
    String extractedTaskPhrase = taskPhraseExtractor.extract(request.sourceTitle(), text, actionVerb, requiredItems);
    String taskPhrase = resolveTaskPhrase(overrideTaskPhrase, extractedTaskPhrase);
    String title = titleDeriver.derive(taskPhrase, request.sourceTitle(), text);
    String actionSummary = actionSummaryBuilder.build(title, dueAtLabel, systemHint, requiredItems, text);

    double confidenceScore = computeConfidenceScore(evidence);

    return new ExtractedActionDto(
        null, null,
        title, actionSummary,
        dueAtIso, dueAtLabel,
        additionalDates,
        eligibility, structuredEligibility, requiredItems, systemHint,
        request.sourceCategory(),
        evidence, computeInferred(evidence), confidenceScore, null
    );
  }

  private List<ExtractedActionDto> resolveDuplicateTitles(List<ExtractedActionDto> actions) {
    Map<String, Integer> counts = new HashMap<>();
    for (ExtractedActionDto action : actions) {
      counts.merge(action.title(), 1, Integer::sum);
    }

    Map<String, Integer> seen = new HashMap<>();
    List<ExtractedActionDto> normalized = new ArrayList<>(actions.size());
    for (ExtractedActionDto action : actions) {
      String title = action.title();
      if (counts.getOrDefault(title, 0) > 1) {
        int index = seen.merge(title, 1, Integer::sum);
        if (index > 1) {
          title = title + " (" + index + "/" + counts.get(title) + ")";
        }
      }
      normalized.add(new ExtractedActionDto(
          action.id(),
          action.sourceId(),
          title,
          action.actionSummary(),
          action.dueAtIso(),
          action.dueAtLabel(),
          action.additionalDates(),
          action.eligibility(),
          action.structuredEligibility(),
          action.requiredItems(),
          action.systemHint(),
          action.sourceCategory(),
          action.evidence(),
          action.inferred(),
          action.confidenceScore(),
          action.createdAt()
      ));
    }
    return normalized;
  }

  private List<ExtractedActionDto> prioritizeSourceTitleTask(List<ExtractedActionDto> actions, String sourceTitle) {
    String sourceTitleTask = taskPhraseExtractor.extract(sourceTitle, "", null, List.of());
    if (!hasText(sourceTitleTask) || actions.size() <= 1) {
      return actions;
    }

    List<ExtractedActionDto> sorted = new ArrayList<>(actions);
    sorted.sort((left, right) -> Boolean.compare(
        !matchesSourceTitleTask(left.title(), sourceTitleTask),
        !matchesSourceTitleTask(right.title(), sourceTitleTask)
    ));
    return sorted;
  }

  private List<ExtractedActionDto> sortByProfile(
      List<ExtractedActionDto> actions, List<String> focusProfile) {
    if (focusProfile == null || focusProfile.isEmpty()) {
      return actions;
    }
    List<String> lowerKeywords = focusProfile.stream()
        .filter(k -> k != null && !k.isBlank())
        .map(k -> k.toLowerCase(Locale.ROOT))
        .toList();
    if (lowerKeywords.isEmpty()) {
      return actions;
    }
    List<ExtractedActionDto> sorted = new ArrayList<>(actions);
    sorted.sort((a, b) -> {
      boolean aMatch = matchesProfile(a, lowerKeywords);
      boolean bMatch = matchesProfile(b, lowerKeywords);
      return Boolean.compare(bMatch, aMatch);
    });
    return sorted;
  }

  private boolean matchesProfile(ExtractedActionDto action, List<String> lowerKeywords) {
    String eligibility = action.eligibility();
    if (eligibility == null || eligibility.isBlank()) {
      return false;
    }
    String lowerEligibility = eligibility.toLowerCase(Locale.ROOT);
    return lowerKeywords.stream().anyMatch(lowerEligibility::contains);
  }

  private static double computeConfidenceScore(List<EvidenceSnippetDto> evidence) {
    if (evidence.isEmpty()) return 0.0;
    return evidence.stream().mapToDouble(EvidenceSnippetDto::confidence).average().orElse(0.0);
  }

  private static boolean computeInferred(List<EvidenceSnippetDto> evidence) {
    if (evidence.isEmpty()) return true;
    return evidence.stream().anyMatch(e -> e.confidence() < 0.75);
  }

  private String resolveTaskPhrase(String overrideTaskPhrase, String extractedTaskPhrase) {
    if (!hasText(overrideTaskPhrase)) {
      return extractedTaskPhrase;
    }
    if (!hasText(extractedTaskPhrase)) {
      return overrideTaskPhrase;
    }
    if (isSameTaskFamily(overrideTaskPhrase, extractedTaskPhrase)) {
      return taskPhraseQualityScore(extractedTaskPhrase) > taskPhraseQualityScore(overrideTaskPhrase)
          ? extractedTaskPhrase
          : overrideTaskPhrase;
    }
    return taskPhraseQualityScore(extractedTaskPhrase) >= taskPhraseQualityScore(overrideTaskPhrase) + 8
        ? extractedTaskPhrase
        : overrideTaskPhrase;
  }

  private boolean isSameTaskFamily(String left, String right) {
    String normalizedLeft = left.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    String normalizedRight = right.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    if (normalizedLeft.equals(normalizedRight)
        || normalizedLeft.contains(normalizedRight)
        || normalizedRight.contains(normalizedLeft)) {
      return true;
    }

    String coreLeft = extractTaskCore(left);
    String coreRight = extractTaskCore(right);
    return coreLeft != null && coreLeft.equals(coreRight);
  }

  private String extractTaskCore(String taskPhrase) {
    String normalized = taskPhrase.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    for (String suffix : List.of("수강신청", "신청 또는 변경", "수요조사 응답")) {
      if (normalized.endsWith(suffix)) {
        return suffix;
      }
    }
    return null;
  }

  private int taskPhraseQualityScore(String taskPhrase) {
    String normalized = taskPhrase.replaceAll("\\s+", " ").trim();
    int score = Math.min(normalized.length(), 12);
    if (normalized.length() > 24) {
      score -= 10;
    }
    if (normalized.matches(".*(우선적|반드시|꼼꼼히|필수로|해야|하시기 바랍니다|확인하시어|본인의 학과|선택할 학과|기존 수강신청).*")) {
      score -= 20;
    }
    if (normalized.matches(".*(신입생|편입생|부전공|I-DESIGN|학번|취업공결|수강과목|공결|조기졸업|융복합트랙|군 e-러닝|Self-making).*")) {
      score += 15;
    }
    return score;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private boolean matchesSourceTitleTask(String title, String sourceTitleTask) {
    return title.equals(sourceTitleTask) || title.startsWith(sourceTitleTask + " (");
  }
}
