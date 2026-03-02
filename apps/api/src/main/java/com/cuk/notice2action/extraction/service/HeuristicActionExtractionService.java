package com.cuk.notice2action.extraction.service;

import com.cuk.notice2action.extraction.api.dto.ActionExtractionRequest;
import com.cuk.notice2action.extraction.api.dto.ActionExtractionResponse;
import com.cuk.notice2action.extraction.api.dto.EvidenceSnippetDto;
import com.cuk.notice2action.extraction.api.dto.ExtractedActionDto;
import com.cuk.notice2action.extraction.service.extractor.ActionSegmenter;
import com.cuk.notice2action.extraction.service.extractor.ActionSummaryBuilder;
import com.cuk.notice2action.extraction.service.extractor.ActionVerbExtractor;
import com.cuk.notice2action.extraction.service.extractor.DateExtractor;
import com.cuk.notice2action.extraction.service.extractor.EligibilityExtractor;
import com.cuk.notice2action.extraction.service.extractor.RequiredItemExtractor;
import com.cuk.notice2action.extraction.service.extractor.SystemHintExtractor;
import com.cuk.notice2action.extraction.service.extractor.TextNormalizer;
import com.cuk.notice2action.extraction.service.extractor.TitleDeriver;
import com.cuk.notice2action.extraction.service.model.ActionSegment;
import com.cuk.notice2action.extraction.service.model.DateMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

  public HeuristicActionExtractionService(
      TextNormalizer textNormalizer,
      DateExtractor dateExtractor,
      SystemHintExtractor systemHintExtractor,
      RequiredItemExtractor requiredItemExtractor,
      ActionVerbExtractor actionVerbExtractor,
      EligibilityExtractor eligibilityExtractor,
      ActionSegmenter actionSegmenter,
      ActionSummaryBuilder actionSummaryBuilder,
      TitleDeriver titleDeriver
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
  }

  @Override
  public ActionExtractionResponse extract(ActionExtractionRequest request) {
    String normalizedText = textNormalizer.normalize(request.sourceText());
    if (normalizedText.isBlank()) {
      throw new IllegalArgumentException("sourceText must not be blank");
    }

    List<ActionSegment> segments = actionSegmenter.segment(normalizedText);

    if (segments.size() <= 1) {
      List<ExtractedActionDto> result =
          List.of(extractSingleAction(request, normalizedText, null, 0, 1));
      return new ActionExtractionResponse(sortByProfile(result, request.focusProfile()));
    }

    List<ExtractedActionDto> actions = new ArrayList<>();
    int total = Math.min(segments.size(), MAX_ACTIONS);
    for (int i = 0; i < total; i++) {
      ActionSegment seg = segments.get(i);
      actions.add(extractSingleAction(request, seg.text(), seg.primaryVerb(), i + 1, total));
    }
    return new ActionExtractionResponse(sortByProfile(actions, request.focusProfile()));
  }

  private ExtractedActionDto extractSingleAction(
      ActionExtractionRequest request,
      String text,
      String overrideVerb,
      int actionIndex,
      int totalActions
  ) {
    List<EvidenceSnippetDto> evidence = new ArrayList<>();

    DateMatch dateMatch = dateExtractor.extract(text, evidence);
    String dueAtLabel = dateMatch != null ? dateMatch.label() : null;
    String dueAtIso = dateMatch != null ? dateExtractor.formatIso(dateMatch.components()) : null;
    String systemHint = systemHintExtractor.extract(text, evidence);
    List<String> requiredItems = requiredItemExtractor.extract(text, evidence);
    String actionVerb = overrideVerb != null ? overrideVerb : actionVerbExtractor.extract(text, evidence);
    String eligibility = eligibilityExtractor.extract(text, evidence);
    String title = titleDeriver.derive(request.sourceTitle(), text, actionIndex, totalActions);
    String actionSummary = actionSummaryBuilder.build(actionVerb, dueAtLabel, systemHint, requiredItems, text);

    double confidenceScore = computeConfidenceScore(evidence);

    return new ExtractedActionDto(
        null, null,
        title, actionSummary,
        dueAtIso, dueAtLabel,
        eligibility, requiredItems, systemHint,
        request.sourceCategory(),
        evidence, computeInferred(evidence), confidenceScore, null
    );
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
}
