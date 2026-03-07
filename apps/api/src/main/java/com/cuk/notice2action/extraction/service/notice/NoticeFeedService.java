package com.cuk.notice2action.extraction.service.notice;

import com.cuk.notice2action.extraction.api.dto.EvidenceSnippetDto;
import com.cuk.notice2action.extraction.api.dto.NoticeActionBlockDto;
import com.cuk.notice2action.extraction.api.dto.NoticeAttachmentDto;
import com.cuk.notice2action.extraction.api.dto.NoticeDueHintDto;
import com.cuk.notice2action.extraction.api.dto.NoticeFeedResponse;
import com.cuk.notice2action.extraction.api.dto.PersonalizedNoticeDetailDto;
import com.cuk.notice2action.extraction.api.dto.PersonalizedNoticeSummaryDto;
import com.cuk.notice2action.extraction.api.dto.ExtractedActionDto;
import com.cuk.notice2action.extraction.persistence.entity.ExtractedActionEntity;
import com.cuk.notice2action.extraction.persistence.entity.NoticeSourceEntity;
import com.cuk.notice2action.extraction.persistence.repository.NoticeSourceRepository;
import com.cuk.notice2action.extraction.service.extractor.ActionSummaryBuilder;
import com.cuk.notice2action.extraction.service.extractor.TaskPhraseExtractor;
import com.cuk.notice2action.extraction.service.model.StructuredEligibility;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoticeFeedService {

  private static final ZoneOffset APP_OFFSET = ZoneOffset.ofHours(9);
  private static final List<String> STUDENT_STATUSES = List.of("재학생", "복학생", "휴학생", "졸업예정자", "신입생", "대학원생", "편입생");
  private static final Map<String, List<String>> STATUS_ALIASES = createStatusAliases();
  private static final List<String> EXCLUSION_KEYWORDS = List.of("제외", "불가", "불포함", "해당없음", "해당 없음");
  private static final Pattern DUPLICATE_SUFFIX = Pattern.compile("\\s*\\((?:\\d+/\\d+)\\)$");
  private static final List<String> ACTION_FAMILY_SUFFIXES = List.of("수강신청", "참여 신청", "수요조사 응답", "신청 또는 변경", "신청", "제출", "조회", "응답");

  private final NoticeSourceRepository noticeSourceRepository;
  private final ObjectMapper objectMapper;
  private final TaskPhraseExtractor taskPhraseExtractor;
  private final ActionSummaryBuilder actionSummaryBuilder;
  private final NoticeActionabilityClassifier noticeActionabilityClassifier;

  public NoticeFeedService(
      NoticeSourceRepository noticeSourceRepository,
      ObjectMapper objectMapper,
      TaskPhraseExtractor taskPhraseExtractor,
      ActionSummaryBuilder actionSummaryBuilder,
      NoticeActionabilityClassifier noticeActionabilityClassifier
  ) {
    this.noticeSourceRepository = noticeSourceRepository;
    this.objectMapper = objectMapper;
    this.taskPhraseExtractor = taskPhraseExtractor;
    this.actionSummaryBuilder = actionSummaryBuilder;
    this.noticeActionabilityClassifier = noticeActionabilityClassifier;
  }

  @Transactional(readOnly = true)
  public NoticeFeedResponse getFeed(NoticeProfile profile, int page, int size) {
    List<ScoredNotice> scored = noticeSourceRepository.findAllAutoCollectedNotices().stream()
        .map(source -> scoreNotice(source, normalizeProfile(profile)))
        .sorted(Comparator
            .comparingInt((ScoredNotice notice) -> notice.summary().relevanceScore()).reversed()
            .thenComparing((ScoredNotice notice) -> notice.summary().publishedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(notice -> notice.summary().id().toString(), Comparator.reverseOrder()))
        .toList();

    int safePage = Math.max(page, 0);
    int safeSize = Math.max(size, 1);
    int fromIndex = Math.min(safePage * safeSize, scored.size());
    int toIndex = Math.min(fromIndex + safeSize, scored.size());
    List<PersonalizedNoticeSummaryDto> pageItems = scored.subList(fromIndex, toIndex).stream()
        .map(ScoredNotice::summary)
        .toList();
    int totalPages = scored.isEmpty() ? 1 : (int) Math.ceil((double) scored.size() / safeSize);

    return new NoticeFeedResponse(pageItems, safePage, safeSize, scored.size(), totalPages, toIndex < scored.size());
  }

  @Transactional(readOnly = true)
  public PersonalizedNoticeDetailDto getDetail(UUID id, NoticeProfile profile) {
    NoticeSourceEntity source = noticeSourceRepository.findDetailById(id)
        .orElseThrow(() -> new NoSuchElementException("Notice not found: " + id));
    ScoredNotice scored = scoreNotice(source, normalizeProfile(profile));

    return new PersonalizedNoticeDetailDto(
        source.getId(),
        source.getTitle(),
        scored.summary().publishedAt(),
        source.getSourceUrl(),
        scored.summary().importanceReasons(),
        scored.summary().actionability(),
        scored.summary().dueHint(),
        scored.summary().relevanceScore(),
        source.getRawText(),
        parseAttachments(source.getAttachmentsJson()).stream()
            .map(attachment -> new NoticeAttachmentDto(attachment.name(), attachment.url()))
            .toList(),
        toActionBlocks(source, scored.summary().actionability())
    );
  }

  static String toAttachmentsJsonForTest(List<CukNoticeAttachment> attachments) {
    return toAttachmentsJson(attachments);
  }

  static String toAttachmentsJson(List<CukNoticeAttachment> attachments) {
    try {
      return new ObjectMapper().writeValueAsString(attachments == null ? List.of() : attachments);
    } catch (JsonProcessingException e) {
      return "[]";
    }
  }

  private ScoredNotice scoreNotice(NoticeSourceEntity source, NoticeProfile profile) {
    String effectiveActionability = resolveActionability(source);
    NoticeDueHintDto effectiveDueHint = resolveDueHint(source, effectiveActionability);
    List<String> reasons = new ArrayList<>();
    int score = 0;

    ProfileRelevance relevance = computeProfileRelevance(source, profile);
    score += relevance.scoreDelta();
    reasons.addAll(relevance.reasons());

    Set<String> keywordReasons = keywordReasons(source, profile.keywords());
    score += Math.min(keywordReasons.size(), 2) * 10;
    reasons.addAll(keywordReasons.stream().limit(2).toList());

    if ("action_required".equals(effectiveActionability)) {
      score += 15;
      reasons.add("행동 필요");
    }

    if (effectiveDueHint != null) {
      long dueDays = ChronoUnit.DAYS.between(OffsetDateTime.now(APP_OFFSET), OffsetDateTime.parse(effectiveDueHint.dueAtIso()));
      if (dueDays <= 7) {
        score += 20;
        reasons.add("7일 안에 마감");
      } else if (dueDays <= 14) {
        score += 10;
        reasons.add("14일 안에 마감");
      }
    }

    String freshnessReason = resolveFreshnessReason(source.getPublishedAt());
    if (freshnessReason != null && reasons.size() < 2) {
      score += "최근 등록".equals(freshnessReason) ? 5 : 2;
      reasons.add(freshnessReason);
    }

    List<String> dedupedReasons = orderDisplayReasons(reasons);
    PersonalizedNoticeSummaryDto summary = new PersonalizedNoticeSummaryDto(
        source.getId(),
        source.getTitle(),
        toPublishedAtString(source.getPublishedAt()),
        source.getSourceUrl(),
        dedupedReasons,
        effectiveActionability,
        effectiveDueHint,
        score
    );
    return new ScoredNotice(source, summary);
  }

  private ProfileRelevance computeProfileRelevance(NoticeSourceEntity source, NoticeProfile profile) {
    if (!profile.isConfigured()) {
      return new ProfileRelevance(0, List.of());
    }
    ProfileSignals signals = collectProfileSignals(source);
    String profileSearchText = signals.titleText() + "\n" + signals.actionEligibilityText();
    String explicitAudienceText = signals.titleText();
    String bodyAudienceText = profileSearchText + "\n" + signals.rawBodyText();
    String departmentSearchText = bodyAudienceText;
    List<String> reasons = new ArrayList<>();
    boolean statusMatched = false;
    boolean statusExcluded = false;
    boolean yearMatched = false;
    boolean yearExcluded = false;
    boolean departmentMatched = false;

    if (hasText(profile.status())) {
      if (signals.includedStatuses().contains(profile.status())
          || containsStatusSignal(profileSearchText, profile.status())
          || containsStatusSignal(bodyAudienceText, profile.status())) {
        reasons.add(profile.status() + " 공지");
        statusMatched = true;
      } else if (containsAnyOtherStatus(explicitAudienceText, profile.status())) {
        statusExcluded = true;
      }
    }

    if (profile.year() != null) {
      YearMatch yearMatch = matchYear(profileSearchText, profile.year());
      YearMatch bodyYearMatch = matchYear(bodyAudienceText, profile.year());
      YearMatch explicitYearMatch = matchYear(explicitAudienceText, profile.year());
      if (signals.years().contains(profile.year()) || yearMatch.relevant() || bodyYearMatch.relevant()) {
        reasons.add(profile.year() + "학년 공지");
        yearMatched = true;
      } else if (explicitYearMatch.explicitlyExcluded()) {
        yearExcluded = true;
      }
    }

    if (hasText(profile.department())
        && (signals.departments().contains(normalizeDepartment(profile.department()))
        || containsDepartment(departmentSearchText, profile.department()))) {
      reasons.add(profile.department() + " 공지");
      departmentMatched = true;
    }

    if ((statusExcluded && !statusMatched) || (yearExcluded && !yearMatched)) {
      return new ProfileRelevance(-40, List.of("다른 대상 공지"));
    }
    if (statusMatched || yearMatched || departmentMatched) {
      return new ProfileRelevance(60, new ArrayList<>(new LinkedHashSet<>(reasons)));
    }
    return new ProfileRelevance(0, List.of());
  }

  private Set<String> keywordReasons(NoticeSourceEntity source, List<String> keywords) {
    Set<String> reasons = new LinkedHashSet<>();
    List<String> matchedKeywords = new ArrayList<>();
    if (keywords == null || keywords.isEmpty()) {
      return reasons;
    }
    String searchable = (source.getTitle() + "\n" + source.getRawText()).toLowerCase(Locale.ROOT);
    for (String keyword : keywords) {
      if (!hasText(keyword)) {
        continue;
      }
      String normalized = keyword.trim().toLowerCase(Locale.ROOT);
      if (searchable.contains(normalized)) {
        if (matchedKeywords.stream().anyMatch(existing -> existing.contains(normalized) || normalized.contains(existing))) {
          continue;
        }
        matchedKeywords.add(normalized);
        reasons.add(keyword.trim() + " 관련");
      }
    }
    return reasons;
  }

  private String resolveFreshnessReason(LocalDate publishedAt) {
    if (publishedAt == null) {
      return null;
    }
    long freshnessDays = ChronoUnit.DAYS.between(publishedAt, LocalDate.now(APP_OFFSET));
    if (freshnessDays <= 3) {
      return "최근 등록";
    }
    if (freshnessDays <= 7) {
      return "이번 주 등록";
    }
    return null;
  }

  private List<String> orderDisplayReasons(List<String> reasons) {
    List<String> deduped = new ArrayList<>(new LinkedHashSet<>(reasons));
    boolean hasExclusion = deduped.contains("다른 대상 공지");

    List<String> collapsedProfileReasons = new ArrayList<>();
    boolean keptProfileReason = false;
    for (String reason : deduped) {
      if ("다른 대상 공지".equals(reason)) {
        continue;
      }
      if (isPositiveProfileReason(reason)) {
        if (keptProfileReason) {
          continue;
        }
        keptProfileReason = true;
      }
      collapsedProfileReasons.add(reason);
    }

    List<String> ordered = collapsedProfileReasons.stream()
        .sorted(Comparator.comparingInt(this::reasonPriority))
        .toList();

    List<String> selected = new ArrayList<>();
    int limit = hasExclusion ? 2 : 3;
    for (String reason : ordered) {
      if (selected.size() == limit) {
        break;
      }
      selected.add(reason);
    }
    if (hasExclusion && selected.size() < 3) {
      selected.add("다른 대상 공지");
    }
    return selected;
  }

  private boolean isPositiveProfileReason(String reason) {
    return reason.endsWith("공지")
        && !"다른 대상 공지".equals(reason)
        && !"최근 등록".equals(reason)
        && !"이번 주 등록".equals(reason);
  }

  private int reasonPriority(String reason) {
    if ("다른 대상 공지".equals(reason)) {
      return 99;
    }
    if (reason.endsWith("공지")) {
      return 0;
    }
    if (reason.endsWith("관련")) {
      return 1;
    }
    if ("행동 필요".equals(reason)) {
      return 2;
    }
    if (reason.contains("마감")) {
      return 3;
    }
    if ("최근 등록".equals(reason) || "이번 주 등록".equals(reason)) {
      return 4;
    }
    return 10;
  }

  private static Map<String, List<String>> createStatusAliases() {
    Map<String, List<String>> aliases = new HashMap<>();
    aliases.put("신입생", List.of("신입생", "신·편입생"));
    aliases.put("편입생", List.of("편입생", "신·편입생"));
    aliases.put("졸업예정자", List.of("졸업예정자", "졸업대상자"));
    return Map.copyOf(aliases);
  }

  private boolean containsStatusSignal(String searchable, String status) {
    for (String term : STATUS_ALIASES.getOrDefault(status, List.of(status))) {
      if (containsIncludedTerm(searchable, term)) {
        return true;
      }
    }
    return false;
  }

  private void addCanonicalStatuses(Set<String> statuses, String rawStatusText) {
    if (!hasText(rawStatusText)) {
      return;
    }
    boolean matched = false;
    for (String canonical : STUDENT_STATUSES) {
      if (containsStatusSignal(rawStatusText, canonical)) {
        statuses.add(canonical);
        matched = true;
      }
    }
    if (!matched) {
      statuses.add(rawStatusText.trim());
    }
  }

  private boolean containsIncludedTerm(String searchable, String term) {
    if (!searchable.contains(term)) {
      return false;
    }
    for (String exclusionKeyword : EXCLUSION_KEYWORDS) {
      if (searchable.contains(term + exclusionKeyword) || searchable.contains(term + " " + exclusionKeyword)) {
        return false;
      }
    }
    return true;
  }

  private boolean containsAnyOtherStatus(String searchable, String currentStatus) {
    return STUDENT_STATUSES.stream()
        .filter(status -> !status.equals(currentStatus))
        .anyMatch(status -> containsStatusSignal(searchable, status));
  }

  private YearMatch matchYear(String searchable, int year) {
    Matcher rangeMatcher = Pattern.compile("(?<!\\d)(\\d)\\s*[~\\-–]\\s*(\\d)\\s*학년(?!도)").matcher(searchable);
    while (rangeMatcher.find()) {
      int start = Integer.parseInt(rangeMatcher.group(1));
      int end = Integer.parseInt(rangeMatcher.group(2));
      if (year >= start && year <= end) {
        return YearMatch.relevantMatch();
      }
      return YearMatch.excludedMatch();
    }

    Matcher singleMatcher = Pattern.compile("(?<!\\d)(\\d)\\s*학년(?!도)").matcher(searchable);
    boolean anyYearMention = false;
    while (singleMatcher.find()) {
      anyYearMention = true;
      int mentionedYear = Integer.parseInt(singleMatcher.group(1));
      if (mentionedYear == year) {
        return YearMatch.relevantMatch();
      }
    }
    return anyYearMention ? YearMatch.excludedMatch() : YearMatch.unknownMatch();
  }

  private boolean containsDepartment(String searchable, String department) {
    String normalizedDepartment = normalizeDepartment(department);
    return searchable.contains(department) || searchable.contains(normalizedDepartment);
  }

  private ProfileSignals collectProfileSignals(NoticeSourceEntity source) {
    Set<String> includedStatuses = new LinkedHashSet<>();
    Set<String> excludedStatuses = new LinkedHashSet<>();
    Set<Integer> years = new LinkedHashSet<>();
    Set<String> departments = new LinkedHashSet<>();
    boolean hasExplicitYears = false;
    StringBuilder eligibilityText = new StringBuilder();

    for (ExtractedActionEntity action : source.getActions()) {
      StructuredEligibility structured = parseStructuredEligibility(action.getStructuredEligibilityJson());
      if (structured != null) {
        structured.statuses().forEach(status -> addCanonicalStatuses(includedStatuses, status));
        structured.excludedStatuses().forEach(status -> addCanonicalStatuses(excludedStatuses, status));
        if (!structured.years().isEmpty()) {
          years.addAll(structured.years());
          hasExplicitYears = true;
        }
        if (hasText(structured.department())) {
          departments.add(normalizeDepartment(structured.department()));
        }
      }

      if (hasText(action.getEligibility())) {
        if (eligibilityText.length() > 0) {
          eligibilityText.append('\n');
        }
        eligibilityText.append(action.getEligibility());
        for (String status : STUDENT_STATUSES) {
          if (containsStatusSignal(action.getEligibility(), status)) {
            includedStatuses.add(status);
          }
        }
      }
    }

    return new ProfileSignals(
        source.getTitle() == null ? "" : source.getTitle(),
        source.getRawText() == null ? "" : source.getRawText(),
        eligibilityText.toString(),
        includedStatuses,
        excludedStatuses,
        years,
        departments,
        hasExplicitYears
    );
  }

  private StructuredEligibility parseStructuredEligibility(String value) {
    if (!hasText(value) || "null".equalsIgnoreCase(value)) {
      return null;
    }
    try {
      return objectMapper.readValue(value, StructuredEligibility.class);
    } catch (JsonProcessingException exception) {
      return null;
    }
  }

  private NoticeActionBlockDto toActionBlock(ExtractedActionEntity action) {
    return new NoticeActionBlockDto(
        action.getTitle(),
        action.getActionSummary(),
        action.getDueAtIso() != null ? action.getDueAtIso().toString() : null,
        action.getDueAtLabel(),
        parseRequiredItems(action.getRequiredItemsJson()),
        action.getSystemHint(),
        action.getEvidenceSnippets().stream()
            .map(evidence -> new EvidenceSnippetDto(evidence.getFieldName(), evidence.getSnippet(), evidence.getConfidence()))
            .toList(),
        action.getConfidenceScore()
    );
  }

  private List<NoticeActionBlockDto> toActionBlocks(NoticeSourceEntity source, String actionability) {
    if (!"action_required".equals(actionability)) {
      return List.of();
    }
    List<CukNoticeAttachment> attachments = parseAttachments(source.getAttachmentsJson());
    if (isImageOnlyBody(source.getRawText())) {
      return buildImageOnlyActionBlocks(source, attachments);
    }
    String sourceTitleTask = taskPhraseExtractor.extract(source.getTitle(), source.getRawText(), null, List.of());
    List<ActionBlockCandidate> candidates = new ArrayList<>();
    int index = 0;
    for (ExtractedActionEntity action : source.getActions()) {
      candidates.add(toActionCandidate(action, sourceTitleTask, index++));
    }

    Map<String, List<ActionBlockCandidate>> grouped = candidates.stream()
        .collect(Collectors.groupingBy(
            candidate -> resolveFamilyKey(candidate, sourceTitleTask),
            java.util.LinkedHashMap::new,
            Collectors.toList()
        ));

    return grouped.values().stream()
        .map(group -> mergeActionFamily(group, sourceTitleTask, source.getTitle(), source.getRawText()))
        .filter(candidate -> shouldRetain(candidate, sourceTitleTask))
        .sorted(Comparator
            .comparingInt(ActionBlockCandidate::representativeScore).reversed()
            .thenComparingInt(ActionBlockCandidate::originalIndex))
        .limit(3)
        .map(ActionBlockCandidate::toDto)
        .toList();
  }

  private List<NoticeActionBlockDto> buildImageOnlyActionBlocks(NoticeSourceEntity source, List<CukNoticeAttachment> attachments) {
    String imageOnlyText = appendAttachmentNames(source.getRawText(), attachments);
    List<String> requiredItems = extractAttachmentFormNames(attachments);
    String taskTitle = taskPhraseExtractor.extract(source.getTitle(), imageOnlyText, null, requiredItems);
    if (!hasText(taskTitle)) {
      return List.of();
    }

    List<EvidenceSnippetDto> evidence = new ArrayList<>();
    if (hasText(source.getTitle())) {
      evidence.add(new EvidenceSnippetDto("title", normalizeInlineText(source.getTitle()), 0.95));
    }
    for (String item : requiredItems.stream().limit(2).toList()) {
      evidence.add(new EvidenceSnippetDto("requiredItems", item, 0.9));
    }
    if (evidence.isEmpty()) {
      return List.of();
    }

    String summary = actionSummaryBuilder.build(taskTitle, null, null, requiredItems, source.getTitle());
    double confidence = source.getActions().stream()
        .mapToDouble(ExtractedActionEntity::getConfidenceScore)
        .max()
        .orElse(requiredItems.isEmpty() ? 0.72 : 0.82);

    return List.of(new NoticeActionBlockDto(
        taskTitle,
        summary,
        null,
        null,
        requiredItems,
        null,
        evidence,
        confidence
    ));
  }

  private ActionBlockCandidate toActionCandidate(ExtractedActionEntity action, String sourceTitleTask, int originalIndex) {
    List<String> requiredItems = parseRequiredItems(action.getRequiredItemsJson());
    List<EvidenceSnippetDto> evidence = action.getEvidenceSnippets().stream()
        .map(item -> new EvidenceSnippetDto(item.getFieldName(), item.getSnippet(), item.getConfidence()))
        .toList();
    String normalizedTitle = stripDuplicateSuffix(action.getTitle());
    return new ActionBlockCandidate(
        normalizedTitle,
        action.getActionSummary(),
        action.getDueAtIso(),
        action.getDueAtLabel(),
        requiredItems,
        action.getSystemHint(),
        evidence,
        action.getConfidenceScore(),
        originalIndex,
        scoreRepresentativeAction(normalizedTitle, action.getActionSummary(), action.getDueAtIso(), action.getDueAtLabel(), action.getSystemHint(), requiredItems, sourceTitleTask)
    );
  }

  private ActionBlockCandidate mergeActionFamily(
      List<ActionBlockCandidate> group,
      String sourceTitleTask,
      String sourceTitle,
      String rawText
  ) {
    ActionBlockCandidate representative = group.stream()
        .max(Comparator.comparingInt(ActionBlockCandidate::representativeScore)
            .thenComparingInt(candidate -> -candidate.originalIndex()))
        .orElseThrow();

    String finalTitle = chooseFamilyTitle(group, representative, sourceTitleTask);
    OffsetDateTime dueAtIso = representative.dueAtIso();
    String dueAtLabel = representative.dueAtLabel();
    if (dueAtIso == null && !hasText(dueAtLabel)) {
      ActionBlockCandidate dueSource = group.stream()
          .filter(candidate -> candidate.dueAtIso() != null || hasText(candidate.dueAtLabel()))
          .max(Comparator.comparingInt(ActionBlockCandidate::representativeScore)
              .thenComparingInt(candidate -> -candidate.originalIndex()))
          .orElse(null);
      if (dueSource != null) {
        dueAtIso = dueSource.dueAtIso();
        dueAtLabel = dueSource.dueAtLabel();
      }
    }

    String systemHint = hasText(representative.systemHint())
        ? representative.systemHint()
        : group.stream().map(ActionBlockCandidate::systemHint).filter(NoticeFeedService::hasText).findFirst().orElse(null);

    String detailDueAtLabel = expandDetailDueLabel(rawText, group, sourceTitleTask, dueAtIso, dueAtLabel);

    List<String> requiredItems = new ArrayList<>();
    Set<String> seenRequiredItems = new LinkedHashSet<>();
    for (ActionBlockCandidate candidate : group) {
      for (String item : candidate.requiredItems()) {
        if (seenRequiredItems.add(item)) {
          requiredItems.add(item);
        }
      }
    }

    List<EvidenceSnippetDto> mergedEvidence = new ArrayList<>();
    Set<String> seenEvidence = new LinkedHashSet<>();
    for (ActionBlockCandidate candidate : group) {
      for (EvidenceSnippetDto evidence : candidate.evidence()) {
        String key = evidence.fieldName() + "::" + evidence.snippet();
        if (seenEvidence.add(key)) {
          mergedEvidence.add(evidence);
        }
      }
    }

    List<EvidenceSnippetDto> refinedEvidence = refineDetailEvidence(
        rawText,
        sourceTitle,
        finalTitle,
        dueAtIso,
        detailDueAtLabel,
        group,
        systemHint,
        requiredItems,
        mergedEvidence
    );

    String summary = actionSummaryBuilder.build(finalTitle, detailDueAtLabel, systemHint, requiredItems, representative.summary());
    double confidenceScore = group.stream().mapToDouble(ActionBlockCandidate::confidenceScore).max().orElse(representative.confidenceScore());
    int score = scoreRepresentativeAction(finalTitle, summary, dueAtIso, detailDueAtLabel, systemHint, requiredItems, sourceTitleTask);

    return new ActionBlockCandidate(
        finalTitle,
        summary,
        dueAtIso,
        detailDueAtLabel,
        requiredItems,
        systemHint,
        refinedEvidence,
        confidenceScore,
        group.stream().mapToInt(ActionBlockCandidate::originalIndex).min().orElse(representative.originalIndex()),
        score
    );
  }

  private String chooseFamilyTitle(List<ActionBlockCandidate> group, ActionBlockCandidate representative, String sourceTitleTask) {
    if (matchesSourceTitleTask(representative.title(), sourceTitleTask)) {
      return sourceTitleTask;
    }
    return group.stream()
        .map(ActionBlockCandidate::title)
        .sorted(Comparator
            .comparingInt((String title) -> scoreRepresentativeAction(title, title, null, null, null, List.of(), sourceTitleTask))
            .reversed()
            .thenComparingInt(String::length))
        .findFirst()
        .orElse(representative.title());
  }

  private boolean shouldRetain(ActionBlockCandidate candidate, String sourceTitleTask) {
    if (matchesSourceTitleTask(candidate.title(), sourceTitleTask)) {
      return true;
    }
    if (isWeakSupplementalTitle(candidate.title()) && !hasSupportSignal(candidate)) {
      return false;
    }
    return candidate.representativeScore() >= 25;
  }

  private String resolveFamilyKey(ActionBlockCandidate candidate, String sourceTitleTask) {
    if (matchesSourceTitleTask(candidate.title(), sourceTitleTask)) {
      return "source::" + normalizeActionTitle(sourceTitleTask);
    }
    return normalizeActionTitle(candidate.title());
  }

  private int scoreRepresentativeAction(
      String title,
      String summary,
      OffsetDateTime dueAtIso,
      String dueAtLabel,
      String systemHint,
      List<String> requiredItems,
      String sourceTitleTask
  ) {
    int score = 0;
    if (matchesSourceTitleTask(title, sourceTitleTask)) {
      score += 120;
      if (normalizeActionTitle(title).equals(normalizeActionTitle(sourceTitleTask))) {
        score += 20;
      }
    }
    if (hasStrongActionTitle(title)) {
      score += 45;
    }
    if (dueAtIso != null || hasText(dueAtLabel)) {
      score += 20;
    }
    if (hasText(systemHint)) {
      score += 12;
    }
    if (!requiredItems.isEmpty()) {
      score += Math.min(requiredItems.size(), 2) * 6;
    }
    if (isWeakSupplementalTitle(title)) {
      score -= 30;
    }
    if (hasText(summary) && summary.contains("참고")) {
      score -= 15;
    }
    return score;
  }

  private boolean hasStrongActionTitle(String title) {
    String normalized = stripDuplicateSuffix(title);
    return ACTION_FAMILY_SUFFIXES.stream().anyMatch(normalized::endsWith);
  }

  private boolean matchesSourceTitleTask(String title, String sourceTitleTask) {
    if (!hasText(title) || !hasText(sourceTitleTask)) {
      return false;
    }
    String normalizedTitle = normalizeActionTitle(title);
    String normalizedSource = normalizeActionTitle(sourceTitleTask);
    if (normalizedTitle.equals(normalizedSource)
        || normalizedTitle.contains(normalizedSource)
        || normalizedSource.contains(normalizedTitle)) {
      return true;
    }
    String titleFamily = extractActionFamily(normalizedTitle);
    String sourceFamily = extractActionFamily(normalizedSource);
    return titleFamily != null && titleFamily.equals(sourceFamily);
  }

  private String extractActionFamily(String normalizedTitle) {
    for (String suffix : ACTION_FAMILY_SUFFIXES) {
      String normalizedSuffix = normalizeActionTitle(suffix);
      if (normalizedTitle.endsWith(normalizedSuffix)) {
        return normalizedSuffix;
      }
    }
    return null;
  }

  private boolean isWeakSupplementalTitle(String title) {
    String normalized = stripDuplicateSuffix(title);
    return normalized.contains("비고")
        || normalized.contains("유의")
        || normalized.contains("참고")
        || normalized.contains("안내")
        || normalized.endsWith("확인");
  }

  private boolean hasSupportSignal(ActionBlockCandidate candidate) {
    return candidate.dueAtIso() != null
        || hasText(candidate.dueAtLabel())
        || hasText(candidate.systemHint())
        || !candidate.requiredItems().isEmpty();
  }

  private String normalizeActionTitle(String value) {
    return stripDuplicateSuffix(value).replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
  }

  private String stripDuplicateSuffix(String value) {
    if (!hasText(value)) {
      return "";
    }
    return DUPLICATE_SUFFIX.matcher(value.trim()).replaceFirst("").trim();
  }

  private String expandDetailDueLabel(
      String rawText,
      List<ActionBlockCandidate> group,
      String sourceTitleTask,
      OffsetDateTime dueAtIso,
      String compactDueAtLabel
  ) {
    if (!hasText(rawText) || !hasText(compactDueAtLabel)) {
      return compactDueAtLabel;
    }
    List<String> lines = splitDetailLines(rawText);
    int dueLineIndex = findDueLineIndex(lines, dueAtIso, compactDueAtLabel, buildTaskTerms(group, sourceTitleTask));
    if (dueLineIndex < 0) {
      return compactDueAtLabel;
    }
    String expanded = lines.get(dueLineIndex);
    if (dueLineIndex > 0
        && !isExplicitDueLine(expanded)
        && isDueHeadingLine(lines.get(dueLineIndex - 1))) {
      expanded = lines.get(dueLineIndex - 1) + " " + expanded;
    }
    if (dueLineIndex + 1 < lines.size() && isTrailingConditionLine(lines.get(dueLineIndex + 1))) {
      expanded = expanded + " " + lines.get(dueLineIndex + 1);
    }
    return normalizeDetailDueLabel(expanded);
  }

  private List<EvidenceSnippetDto> refineDetailEvidence(
      String rawText,
      String sourceTitle,
      String finalTitle,
      OffsetDateTime dueAtIso,
      String detailDueAtLabel,
      List<ActionBlockCandidate> group,
      String systemHint,
      List<String> requiredItems,
      List<EvidenceSnippetDto> mergedEvidence
  ) {
    List<EvidenceSnippetDto> candidates = new ArrayList<>();
    String dueContextSnippet = buildDueContextSnippet(rawText, group, dueAtIso, detailDueAtLabel);
    if (hasText(dueContextSnippet)) {
      candidates.add(new EvidenceSnippetDto("dueAtLabel", dueContextSnippet, 0.95));
    }
    String systemContextSnippet = buildSystemContextSnippet(rawText, systemHint);
    if (hasText(systemContextSnippet)) {
      candidates.add(new EvidenceSnippetDto("systemHint", systemContextSnippet, 0.9));
    }
    for (EvidenceSnippetDto evidence : mergedEvidence) {
      if ("dueAtLabel".equals(evidence.fieldName()) && hasText(dueContextSnippet)) {
        continue;
      }
      if ("systemHint".equals(evidence.fieldName()) && hasText(systemContextSnippet)) {
        continue;
      }
      String snippet = normalizeInlineText(evidence.snippet());
      if (!isUsefulEvidence(evidence.fieldName(), snippet, sourceTitle, finalTitle, detailDueAtLabel, systemHint, requiredItems)) {
        continue;
      }
      candidates.add(new EvidenceSnippetDto(evidence.fieldName(), snippet, evidence.confidence()));
    }

    List<EvidenceSnippetDto> sorted = candidates.stream()
        .sorted(Comparator
            .comparingInt((EvidenceSnippetDto evidence) -> evidencePriority(evidence.fieldName()))
            .thenComparing((EvidenceSnippetDto evidence) -> !hasPreferredEvidenceLength(evidence.snippet()))
            .thenComparing(EvidenceSnippetDto::confidence, Comparator.reverseOrder())
            .thenComparing((EvidenceSnippetDto evidence) -> evidence.snippet().length(), Comparator.reverseOrder()))
        .toList();

    List<EvidenceSnippetDto> refined = new ArrayList<>();
    for (EvidenceSnippetDto evidence : sorted) {
      if (isRedundantEvidence(refined, evidence.snippet())) {
        continue;
      }
      refined.add(evidence);
      if (refined.size() == 3) {
        break;
      }
    }
    return refined;
  }

  private String buildDueContextSnippet(
      String rawText,
      List<ActionBlockCandidate> group,
      OffsetDateTime dueAtIso,
      String detailDueAtLabel
  ) {
    if (!hasText(rawText) || !hasText(detailDueAtLabel)) {
      return null;
    }
    List<String> lines = splitDetailLines(rawText);
    for (int index = 0; index < lines.size(); index++) {
      String line = lines.get(index);
      if (!isExplicitDueLine(line) || isExplanatoryDueLine(line)) {
        continue;
      }
      if (!lineMatchesDueInstant(line, dueAtIso) && !lineMatchesDueLabel(line, detailDueAtLabel)) {
        continue;
      }
      String snippet = line;
      if (index + 1 < lines.size() && isTrailingConditionLine(lines.get(index + 1))) {
        snippet = snippet + " " + lines.get(index + 1);
      }
      return sanitizeDueDisplayLine(snippet);
    }
    int dueLineIndex = findDueLineIndex(lines, dueAtIso, detailDueAtLabel, buildTaskTerms(group, null));
    if (dueLineIndex < 0) {
      return detailDueAtLabel;
    }

    String snippet = lines.get(dueLineIndex);
    if (dueLineIndex > 0 && isDueHeadingLine(lines.get(dueLineIndex - 1))) {
      snippet = lines.get(dueLineIndex - 1) + " " + snippet;
    }
    if (dueLineIndex + 1 < lines.size() && isTrailingConditionLine(lines.get(dueLineIndex + 1))) {
      snippet = snippet + " " + lines.get(dueLineIndex + 1);
    }
    return sanitizeDueDisplayLine(snippet);
  }

  private String buildSystemContextSnippet(String rawText, String systemHint) {
    if (!hasText(rawText) || !hasText(systemHint)) {
      return null;
    }
    List<String> lines = splitDetailLines(rawText);
    List<String> terms = buildSystemSearchTerms(systemHint);
    String fallback = null;
    for (String line : lines) {
      String normalizedLine = normalizeMatchValue(line);
      boolean matches = terms.stream()
          .map(this::normalizeMatchValue)
          .filter(NoticeFeedService::hasText)
          .anyMatch(normalizedLine::contains);
      if (!matches) {
        continue;
      }
      if (isProceduralSystemLine(line)) {
        return sanitizeDueDisplayLine(line);
      }
      if (!isExplanatoryDueLine(line) && fallback == null) {
        fallback = sanitizeDueDisplayLine(line);
      }
    }
    return fallback;
  }

  private int findDueLineIndex(
      List<String> lines,
      OffsetDateTime dueAtIso,
      String dueAtLabel,
      List<String> taskTerms
  ) {
    for (int index = 0; index < lines.size(); index++) {
      String line = lines.get(index);
      if (isExplanatoryDueLine(line) || !isExplicitDueLine(line)) {
        continue;
      }
      if (lineMatchesDueInstant(line, dueAtIso)
          || lineMatchesDueLabel(line, dueAtLabel)
          || (looksLikeDueLine(line) && matchesTaskWindow(lines, index, taskTerms))) {
        return index;
      }
    }
    for (int index = 0; index < lines.size(); index++) {
      String line = lines.get(index);
      if (!isExplanatoryDueLine(line) && lineMatchesDueInstant(line, dueAtIso)) {
        return index;
      }
    }
    for (int index = 0; index < lines.size(); index++) {
      String line = lines.get(index);
      if (!isExplanatoryDueLine(line) && lineMatchesDueLabel(line, dueAtLabel)) {
        return index;
      }
    }
    for (int index = 0; index < lines.size(); index++) {
      String line = lines.get(index);
      if (isExplanatoryDueLine(line) || !looksLikeDueLine(line)) {
        continue;
      }
      if (matchesTaskWindow(lines, index, taskTerms)) {
        return index;
      }
    }
    return -1;
  }

  private boolean lineMatchesDueInstant(String line, OffsetDateTime dueAtIso) {
    if (dueAtIso == null) {
      return false;
    }
    OffsetDateTime localDueAt = dueAtIso.withOffsetSameInstant(APP_OFFSET);
    String normalizedLine = normalizeMatchValue(line);
    List<String> tokens = List.of(
        String.format(Locale.ROOT, "%04d%02d%02d", localDueAt.getYear(), localDueAt.getMonthValue(), localDueAt.getDayOfMonth()),
        String.format(Locale.ROOT, "%02d%d", localDueAt.getMonthValue(), localDueAt.getDayOfMonth()),
        String.format(Locale.ROOT, "%d%02d", localDueAt.getMonthValue(), localDueAt.getDayOfMonth()),
        String.format(Locale.ROOT, "%02d%d%02d%02d", localDueAt.getMonthValue(), localDueAt.getDayOfMonth(), localDueAt.getHour(), localDueAt.getMinute())
    );
    return tokens.stream()
        .filter(NoticeFeedService::hasText)
        .anyMatch(normalizedLine::contains);
  }

  private List<String> buildTaskTerms(List<ActionBlockCandidate> group, String sourceTitleTask) {
    Set<String> terms = new LinkedHashSet<>();
    if (hasText(sourceTitleTask)) {
      terms.add(sourceTitleTask);
      String family = extractActionFamily(normalizeActionTitle(sourceTitleTask));
      if (family != null) {
        terms.add(family);
      }
    }
    for (ActionBlockCandidate candidate : group) {
      if (hasText(candidate.title())) {
        terms.add(stripDuplicateSuffix(candidate.title()));
        String family = extractActionFamily(normalizeActionTitle(candidate.title()));
        if (family != null) {
          terms.add(family);
        }
      }
    }
    return new ArrayList<>(terms);
  }

  private boolean matchesTaskWindow(List<String> lines, int index, List<String> taskTerms) {
    if (taskTerms == null || taskTerms.isEmpty()) {
      return false;
    }
    String current = normalizeMatchValue(lines.get(index));
    String previous = index > 0 ? normalizeMatchValue(lines.get(index - 1)) : "";
    for (String term : taskTerms) {
      String normalizedTerm = normalizeMatchValue(term);
      if (normalizedTerm.isEmpty()) {
        continue;
      }
      if (current.contains(normalizedTerm) || previous.contains(normalizedTerm)) {
        return true;
      }
    }
    return index > 0 && isDueHeadingLine(lines.get(index - 1));
  }

  private boolean lineMatchesDueLabel(String line, String dueAtLabel) {
    String normalizedLine = normalizeMatchValue(line);
    String normalizedDueLabel = normalizeMatchValue(dueAtLabel);
    return hasText(normalizedDueLabel) && normalizedLine.contains(normalizedDueLabel);
  }

  private boolean looksLikeDueLine(String line) {
    String normalized = normalizeInlineText(line);
    return normalized.contains("~")
        || normalized.matches(".*20\\d{2}.*\\d{1,2}:\\d{2}.*")
        || normalized.matches(".*\\d{1,2}[./]\\d{1,2}.*");
  }

  private boolean isDueHeadingLine(String line) {
    String normalized = normalizeInlineText(line);
    return normalized.endsWith(":")
        || normalized.contains("기간")
        || normalized.contains("마감");
  }

  private boolean isExplicitDueLine(String line) {
    String normalized = normalizeInlineText(line);
    return normalized.contains("신청기간")
        || normalized.contains("변경기간")
        || normalized.contains("제출기간")
        || normalized.contains("수강신청 기간")
        || normalized.contains("수강신청 변경기간")
        || normalized.contains("마감")
        || normalized.matches(".*기간\\s*[:：].*");
  }

  private boolean isExplanatoryDueLine(String line) {
    String normalized = normalizeInlineText(line);
    return normalized.contains("가능합니다")
        || normalized.contains("불가능")
        || normalized.contains("바랍니다")
        || normalized.contains("안내합니다")
        || normalized.contains("해당")
        || normalized.contains("확인하시기")
        || normalized.contains("확인 바랍니다");
  }

  private boolean isTrailingConditionLine(String line) {
    String normalized = normalizeInlineText(line);
    return normalized.startsWith("(")
        || normalized.contains("주말")
        || normalized.contains("공휴일")
        || normalized.contains("제외");
  }

  private boolean isProceduralSystemLine(String line) {
    String normalized = normalizeInlineText(line);
    return normalized.contains("[")
        || normalized.contains("]")
        || normalized.contains(" - ")
        || normalized.contains(" > ")
        || normalized.contains("→")
        || normalized.contains("/")
        || normalized.startsWith("가.")
        || normalized.startsWith("나.")
        || normalized.startsWith("다.");
  }

  private List<String> splitDetailLines(String rawText) {
    return rawText == null ? List.of() : rawText.lines()
        .map(NoticeFeedService::normalizeInlineText)
        .filter(NoticeFeedService::hasText)
        .toList();
  }

  private boolean isUsefulEvidence(
      String fieldName,
      String snippet,
      String sourceTitle,
      String finalTitle,
      String detailDueAtLabel,
      String systemHint,
      List<String> requiredItems
  ) {
    if (!hasText(snippet) || snippet.length() < 12) {
      return false;
    }
    String normalizedSnippet = normalizeMatchValue(snippet);
    String normalizedSourceTitle = normalizeMatchValue(sourceTitle);
    String normalizedBareSourceTitle = normalizeMatchValue(stripTitlePrefix(sourceTitle));
    String normalizedFinalTitle = normalizeMatchValue(finalTitle);
    if (normalizedSnippet.equals(normalizeMatchValue(sourceTitle))
        || normalizedSnippet.equals(normalizeMatchValue(finalTitle))
        || normalizedSnippet.equals(normalizeMatchValue(detailDueAtLabel))) {
      return false;
    }
    if (!"dueAtLabel".equals(fieldName)
        && !normalizedSourceTitle.isEmpty()
        && normalizedSnippet.contains(normalizedSourceTitle)) {
      return false;
    }
    if (!"dueAtLabel".equals(fieldName)
        && !normalizedBareSourceTitle.isEmpty()
        && normalizedSnippet.contains(normalizedBareSourceTitle)) {
      return false;
    }
    if (!"dueAtLabel".equals(fieldName)
        && !normalizedFinalTitle.isEmpty()
        && normalizedSnippet.contains(normalizedFinalTitle)) {
      return false;
    }
    if (snippet.contains("자세한 내용은")
        || snippet.contains("확인바랍니다")
        || snippet.contains("참고")
        || snippet.contains("이수")
        || snippet.contains("안내드리오니")
        || snippet.contains("완료하시기 바랍니다")
        || snippet.contains("문의처")
        || looksLikeTableRow(snippet)
        || isExplanatoryDueLine(snippet)) {
      return false;
    }
    if (!hasPreferredEvidenceLength(snippet)
        && !(hasText(systemHint) && snippet.contains(systemHint))
        && requiredItems.stream().noneMatch(snippet::contains)) {
      return false;
    }
    return true;
  }

  private boolean hasPreferredEvidenceLength(String snippet) {
    return snippet.length() >= 12 && snippet.length() <= 140;
  }

  private boolean looksLikeTableRow(String snippet) {
    String normalized = normalizeInlineText(snippet);
    return normalized.contains(" | ")
        || normalized.matches(".*\\|.*\\|.*");
  }

  private int evidencePriority(String fieldName) {
    return switch (fieldName) {
      case "dueAtLabel" -> 0;
      case "systemHint" -> 1;
      case "requiredItems" -> 2;
      default -> 3;
    };
  }

  private boolean isRedundantEvidence(List<EvidenceSnippetDto> existing, String candidateSnippet) {
    String normalizedCandidate = normalizeMatchValue(candidateSnippet);
    for (EvidenceSnippetDto evidence : existing) {
      String normalizedExisting = normalizeMatchValue(evidence.snippet());
      if (normalizedExisting.contains(normalizedCandidate) || normalizedCandidate.contains(normalizedExisting)) {
        return true;
      }
    }
    return false;
  }

  private static String normalizeInlineText(String value) {
    return value == null ? "" : value.replaceAll("\\s+", " ").trim();
  }

  private String sanitizeDueDisplayLine(String value) {
    return normalizeInlineText(value).replaceFirst("^[\\-•·▪■□▶◎]+\\s*", "").trim();
  }

  private String normalizeDetailDueLabel(String value) {
    String normalized = sanitizeDueDisplayLine(value)
        .replaceFirst("^\\d+\\.\\s*", "")
        .trim();
    int headingSeparatorIndex = findDueHeadingSeparatorIndex(normalized);
    if (headingSeparatorIndex < 0) {
      return normalized;
    }
    String prefix = normalized.substring(0, headingSeparatorIndex).trim();
    String suffix = normalized.substring(headingSeparatorIndex + 1).trim();
    return prefix + ": " + suffix;
  }

  private int findDueHeadingSeparatorIndex(String value) {
    for (int index = 0; index < value.length(); index++) {
      char ch = value.charAt(index);
      if (ch != ':' && ch != '：') {
        continue;
      }
      String prefix = value.substring(0, index).trim();
      if (prefix.contains("기간") || prefix.contains("마감")) {
        return index;
      }
    }
    return -1;
  }

  private String stripTitlePrefix(String value) {
    return normalizeInlineText(value).replaceFirst("^\\[[^\\]]+\\]\\s*", "").trim();
  }

  private List<String> buildSystemSearchTerms(String systemHint) {
    List<String> terms = new ArrayList<>();
    terms.add(systemHint);
    String normalized = systemHint.trim().toUpperCase(Locale.ROOT);
    if ("TRINITY".equals(normalized)) {
      terms.add("트리니티");
    }
    if ("사이버캠퍼스".equals(systemHint) || "CYBERCAMPUS".equals(normalized)) {
      terms.add("사이버캠퍼스");
    }
    return terms;
  }

  private String normalizeMatchValue(String value) {
    return normalizeInlineText(value)
        .replaceAll("[()\\[\\].,:~～\\-_/]", "")
        .toLowerCase(Locale.ROOT);
  }

  private List<String> parseRequiredItems(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<>() {});
    } catch (Exception exception) {
      return List.of();
    }
  }

  private List<CukNoticeAttachment> parseAttachments(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<>() {});
    } catch (Exception exception) {
      return List.of();
    }
  }

  private NoticeDueHintDto toDueHint(NoticeSourceEntity source) {
    if (source.getPrimaryDueAt() == null) {
      return null;
    }
    return new NoticeDueHintDto(source.getPrimaryDueAt().toString(), source.getPrimaryDueLabel());
  }

  private NoticeDueHintDto resolveDueHint(NoticeSourceEntity source, String actionability) {
    if (!"action_required".equals(actionability)) {
      return null;
    }
    return toDueHint(source);
  }

  private String resolveActionability(NoticeSourceEntity source) {
    List<CukNoticeAttachment> attachments = parseAttachments(source.getAttachmentsJson());
    return noticeActionabilityClassifier.classify(
        source.getTitle(),
        appendAttachmentNames(source.getRawText(), attachments),
        source.getActions().stream().map(this::toExtractedActionDto).toList()
    );
  }

  private boolean isImageOnlyBody(String rawText) {
    return normalizeInlineText(rawText).contains("본문이 이미지로만 제공된 공지입니다");
  }

  private String appendAttachmentNames(String rawText, List<CukNoticeAttachment> attachments) {
    String baseText = rawText == null ? "" : rawText;
    if (attachments == null || attachments.isEmpty()) {
      return baseText;
    }
    if (baseText.contains("첨부파일:")) {
      return baseText;
    }
    String attachmentText = attachments.stream()
        .map(CukNoticeAttachment::name)
        .filter(NoticeFeedService::hasText)
        .distinct()
        .reduce((a, b) -> a + ", " + b)
        .orElse("");
    if (!hasText(attachmentText)) {
      return baseText;
    }
    return baseText + "\n첨부파일: " + attachmentText;
  }

  private List<String> extractAttachmentFormNames(List<CukNoticeAttachment> attachments) {
    if (attachments == null || attachments.isEmpty()) {
      return List.of();
    }
    List<String> formKeywords = List.of("신청서", "허가원", "동의서", "확인서");
    Set<String> names = new LinkedHashSet<>();
    for (CukNoticeAttachment attachment : attachments) {
      String name = attachment == null ? null : attachment.name();
      if (!hasText(name)) {
        continue;
      }
      String normalized = normalizeInlineText(name);
      boolean matches = formKeywords.stream().anyMatch(normalized::contains);
      if (matches) {
        names.add(name.trim());
      }
    }
    return List.copyOf(names);
  }

  private ExtractedActionDto toExtractedActionDto(ExtractedActionEntity action) {
    return new ExtractedActionDto(
        action.getId(),
        action.getSource() != null ? action.getSource().getId() : null,
        action.getTitle(),
        action.getActionSummary(),
        action.getDueAtIso() != null ? action.getDueAtIso().toString() : null,
        action.getDueAtLabel(),
        action.getEligibility(),
        parseRequiredItems(action.getRequiredItemsJson()),
        action.getSystemHint(),
        action.getSource() != null ? action.getSource().getSourceCategory() : null,
        action.getEvidenceSnippets().stream()
            .map(evidence -> new EvidenceSnippetDto(evidence.getFieldName(), evidence.getSnippet(), evidence.getConfidence()))
            .toList(),
        action.isInferred(),
        action.getConfidenceScore(),
        action.getCreatedAt()
    );
  }

  private String toPublishedAtString(LocalDate publishedAt) {
    if (publishedAt == null) {
      return null;
    }
    return publishedAt.atStartOfDay().atOffset(APP_OFFSET).toString();
  }

  private NoticeProfile normalizeProfile(NoticeProfile profile) {
    return profile == null ? new NoticeProfile(null, null, null, List.of()) : profile;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static String normalizeDepartment(String department) {
    String value = department == null ? "" : department.trim();
    for (String suffix : List.of("학부", "학과", "전공", "과")) {
      if (value.endsWith(suffix) && value.length() > suffix.length()) {
        return value.substring(0, value.length() - suffix.length());
      }
    }
    return value;
  }

  private record ProfileRelevance(int scoreDelta, List<String> reasons) {
    private ProfileRelevance {
      reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
  }

  private record YearMatch(boolean relevant, boolean explicitlyExcluded) {
    static YearMatch relevantMatch() {
      return new YearMatch(true, false);
    }

    static YearMatch excludedMatch() {
      return new YearMatch(false, true);
    }

    static YearMatch unknownMatch() {
      return new YearMatch(false, false);
    }
  }

  private record ProfileSignals(
      String titleText,
      String rawBodyText,
      String actionEligibilityText,
      Set<String> includedStatuses,
      Set<String> excludedStatuses,
      Set<Integer> years,
      Set<String> departments,
      boolean hasExplicitYears
  ) {}

  private record ScoredNotice(NoticeSourceEntity source, PersonalizedNoticeSummaryDto summary) {}

  private record ActionBlockCandidate(
      String title,
      String summary,
      OffsetDateTime dueAtIso,
      String dueAtLabel,
      List<String> requiredItems,
      String systemHint,
      List<EvidenceSnippetDto> evidence,
      double confidenceScore,
      int originalIndex,
      int representativeScore
  ) {
    private ActionBlockCandidate {
      requiredItems = requiredItems == null ? List.of() : List.copyOf(requiredItems);
      evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    private NoticeActionBlockDto toDto() {
      return new NoticeActionBlockDto(
          title,
          summary,
          dueAtIso != null ? dueAtIso.toString() : null,
          dueAtLabel,
          requiredItems,
          systemHint,
          evidence,
          confidenceScore
      );
    }
  }
}
