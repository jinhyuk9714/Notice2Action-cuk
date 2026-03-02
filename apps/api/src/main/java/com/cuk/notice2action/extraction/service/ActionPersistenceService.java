package com.cuk.notice2action.extraction.service;

import com.cuk.notice2action.extraction.api.dto.ActionExtractionRequest;
import com.cuk.notice2action.extraction.api.dto.ActionExtractionResponse;
import com.cuk.notice2action.extraction.api.dto.ActionListResponse;
import com.cuk.notice2action.extraction.api.dto.ActionSearchCriteria;
import com.cuk.notice2action.extraction.api.dto.ActionUpdateRequest;
import com.cuk.notice2action.extraction.api.dto.EvidenceSnippetDto;
import com.cuk.notice2action.extraction.api.dto.ExtractedActionDto;
import com.cuk.notice2action.extraction.api.dto.FieldOverrideInfoDto;
import com.cuk.notice2action.extraction.api.dto.SavedActionDetailDto;
import com.cuk.notice2action.extraction.api.dto.SavedActionSummaryDto;
import com.cuk.notice2action.extraction.api.dto.SourceInfoDto;
import com.cuk.notice2action.extraction.persistence.entity.EvidenceSnippetEntity;
import com.cuk.notice2action.extraction.persistence.entity.ExtractedActionEntity;
import com.cuk.notice2action.extraction.persistence.entity.NoticeSourceEntity;
import com.cuk.notice2action.extraction.persistence.repository.ActionSpecifications;
import com.cuk.notice2action.extraction.persistence.repository.ExtractedActionRepository;
import com.cuk.notice2action.extraction.persistence.repository.NoticeSourceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActionPersistenceService {

  private final NoticeSourceRepository sourceRepository;
  private final ExtractedActionRepository actionRepository;
  private final ObjectMapper objectMapper;

  public ActionPersistenceService(NoticeSourceRepository sourceRepository,
      ExtractedActionRepository actionRepository,
      ObjectMapper objectMapper) {
    this.sourceRepository = sourceRepository;
    this.actionRepository = actionRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public ActionExtractionResponse persistExtraction(
      ActionExtractionRequest request,
      ActionExtractionResponse extractionResult) {

    String contentHash = ContentHashUtil.sha256(request.sourceText());
    ActionExtractionResponse duplicateByHash = resolveDuplicateByContentHash(contentHash);
    if (duplicateByHash != null) {
      return duplicateByHash;
    }

    ActionExtractionResponse duplicateByUrl = resolveDuplicateBySourceUrl(request.sourceUrl());
    if (duplicateByUrl != null) {
      return duplicateByUrl;
    }

    OffsetDateTime now = OffsetDateTime.now();
    NoticeSourceEntity source = createSourceEntity(request, contentHash, now);
    ActionExtractionResponse concurrentDuplicate = saveSourceOrResolveConcurrentDuplicate(source,
        contentHash);
    if (concurrentDuplicate != null) {
      return concurrentDuplicate;
    }

    List<ExtractedActionDto> savedActions = persistActions(extractionResult.actions(), source, now);

    return new ActionExtractionResponse(savedActions);
  }

  private static final Set<String> OVERRIDABLE_FIELDS = Set.of(
      "title", "actionSummary", "dueAtIso", "dueAtLabel",
      "eligibility", "requiredItems", "systemHint");

  @Transactional
  public SavedActionDetailDto updateAction(UUID actionId, ActionUpdateRequest request) {
    ExtractedActionEntity entity = actionRepository.findById(actionId)
        .orElseThrow(() -> new NoSuchElementException("Action not found: " + actionId));

    Map<String, String> machineValues = parseMachineValues(entity.getMachineValuesJson());

    applyReverts(entity, request.revertFields(), machineValues);
    applyUpdates(entity, request, machineValues);

    entity.setMachineValuesJson(serializeMachineValues(machineValues));
    actionRepository.save(entity);
    return toDetailDto(entity);
  }

  private void setFieldValue(ExtractedActionEntity entity, String fieldName, String value) {
    switch (fieldName) {
      case "title" -> entity.setTitle(value);
      case "actionSummary" -> entity.setActionSummary(value);
      case "dueAtIso" -> entity.setDueAtIso(value != null ? OffsetDateTime.parse(value) : null);
      case "dueAtLabel" -> entity.setDueAtLabel(value);
      case "eligibility" -> entity.setEligibility(value);
      case "requiredItems" -> entity.setRequiredItemsJson(value != null ? value : "[]");
      case "systemHint" -> entity.setSystemHint(value);
      default -> { /* unreachable due to OVERRIDABLE_FIELDS check */ }
    }
  }

  private Map<String, String> parseMachineValues(String json) {
    try {
      Map<String, String> map = objectMapper.readValue(json,
          new TypeReference<Map<String, String>>() {});
      return new HashMap<>(map);
    } catch (Exception e) {
      return new HashMap<>();
    }
  }

  private String serializeMachineValues(Map<String, String> map) {
    try {
      return objectMapper.writeValueAsString(map);
    } catch (JsonProcessingException e) {
      return "{}";
    }
  }

  private ActionExtractionResponse buildDuplicateResponse(NoticeSourceEntity existingSource) {
    List<ExtractedActionDto> actions = actionRepository
        .findAllBySourceIdOrderByCreatedAtDesc(existingSource.getId()).stream()
        .map(this::toExtractedActionDto)
        .toList();
    return new ActionExtractionResponse(actions, true);
  }

  @Transactional
  public void deleteAction(UUID actionId) {
    ExtractedActionEntity action = actionRepository.findById(actionId)
        .orElseThrow(() -> new NoSuchElementException("Action not found: " + actionId));
    UUID sourceId = action.getSource().getId();
    actionRepository.deleteById(actionId);
    actionRepository.flush();

    if (sourceRepository.countActionsBySourceId(sourceId) == 0) {
      sourceRepository.deleteById(sourceId);
    }
  }

  @Transactional(readOnly = true)
  public ActionListResponse listActions(ActionSearchCriteria criteria, int page, int size) {
    if (!hasFilters(criteria)) {
      return listActionsSimple(criteria.sort(), page, size);
    }

    Specification<ExtractedActionEntity> spec = buildSearchSpec(criteria);
    if (isDueSort(criteria.sort())) {
      spec = spec.and(dueNullsLastOrder());
    }

    Pageable pageable = resolvePageable(criteria.sort(), page, size);
    Page<ExtractedActionEntity> pageResult = actionRepository.findAll(spec, pageable);
    return toActionListResponse(pageResult);
  }

  private ActionListResponse listActionsSimple(String sortParam, int page, int size) {
    if ("due".equals(sortParam)) {
      Pageable pageable = PageRequest.of(page, size);
      Page<ExtractedActionEntity> pageResult = actionRepository.findAllOrderByDueAtIsoAscNullsLast(pageable);
      return toActionListResponse(pageResult);
    }

    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt")));
    Page<ExtractedActionEntity> pageResult = actionRepository.findAll(pageable);
    return toActionListResponse(pageResult);
  }

  @Transactional(readOnly = true)
  public List<ExtractedActionEntity> findActionsForCalendar(ActionSearchCriteria criteria) {
    Specification<ExtractedActionEntity> spec = buildSearchSpec(criteria)
        .and((root, query, cb) -> cb.isNotNull(root.get("dueAtIso")));

    Sort sort = isDueSort(criteria.sort())
        ? Sort.by(Sort.Order.asc("dueAtIso"))
        : Sort.by(Sort.Order.desc("createdAt"));

    return actionRepository.findAll(spec, sort);
  }

  @Transactional(readOnly = true)
  public SavedActionDetailDto getActionDetail(UUID actionId) {
    ExtractedActionEntity entity = actionRepository.findById(actionId)
        .orElseThrow(() -> new NoSuchElementException("Action not found: " + actionId));

    return toDetailDto(entity);
  }

  private SavedActionSummaryDto toSummaryDto(ExtractedActionEntity entity) {
    NoticeSourceEntity source = entity.getSource();
    return new SavedActionSummaryDto(
        entity.getId(),
        entity.getTitle(),
        entity.getActionSummary(),
        toIsoString(entity.getDueAtIso()),
        entity.getDueAtLabel(),
        entity.getEligibility(),
        source != null ? source.getSourceCategory() : null,
        source != null ? source.getTitle() : null,
        entity.getConfidenceScore(),
        entity.getCreatedAt()
    );
  }

  private static Specification<ExtractedActionEntity> dueNullsLastOrder() {
    return (root, query, cb) -> {
      if (query != null) {
        query.orderBy(
            cb.asc(cb.selectCase()
                .when(cb.isNull(root.get("dueAtIso")), 1)
                .otherwise(0)),
            cb.asc(root.get("dueAtIso")),
            cb.desc(root.get("createdAt"))
        );
      }
      return cb.conjunction();
    };
  }

  private ActionListResponse toActionListResponse(Page<ExtractedActionEntity> pageResult) {
    List<SavedActionSummaryDto> summaries = pageResult.getContent().stream()
        .map(this::toSummaryDto)
        .toList();

    return new ActionListResponse(
        summaries,
        pageResult.getNumber(),
        pageResult.getSize(),
        pageResult.getTotalElements(),
        pageResult.getTotalPages(),
        pageResult.hasNext()
    );
  }

  private SavedActionDetailDto toDetailDto(ExtractedActionEntity entity) {
    NoticeSourceEntity source = entity.getSource();

    SourceInfoDto sourceInfo = source != null
        ? new SourceInfoDto(source.getId(), source.getTitle(),
            source.getSourceCategory(), source.getCreatedAt())
        : null;

    List<EvidenceSnippetDto> evidence = entity.getEvidenceSnippets().stream()
        .map(e -> new EvidenceSnippetDto(e.getFieldName(), e.getSnippet(), e.getConfidence()))
        .toList();

    Map<String, String> machineValues = parseMachineValues(entity.getMachineValuesJson());
    List<FieldOverrideInfoDto> overrides = machineValues.entrySet().stream()
        .map(e -> new FieldOverrideInfoDto(e.getKey(), e.getValue()))
        .toList();

    return new SavedActionDetailDto(
        entity.getId(),
        entity.getTitle(),
        entity.getActionSummary(),
        toIsoString(entity.getDueAtIso()),
        entity.getDueAtLabel(),
        entity.getEligibility(),
        fromJson(entity.getRequiredItemsJson()),
        entity.getSystemHint(),
        entity.isInferred(),
        entity.getConfidenceScore(),
        entity.getCreatedAt(),
        sourceInfo,
        evidence,
        overrides
    );
  }

  private String toJson(List<String> items) {
    try {
      return objectMapper.writeValueAsString(items);
    } catch (JsonProcessingException e) {
      return "[]";
    }
  }

  private List<String> fromJson(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<>() {});
    } catch (Exception e) {
      return List.of();
    }
  }

  private OffsetDateTime parseDueAtIso(String isoString, String fieldName) {
    if (isoString == null || isoString.isBlank()) {
      return null;
    }
    try {
      return OffsetDateTime.parse(isoString);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "잘못된 날짜 형식입니다: " + fieldName + " = " + isoString
      );
    }
  }

  private ActionExtractionResponse resolveDuplicateByContentHash(String contentHash) {
    if (contentHash == null) {
      return null;
    }
    return resolveDuplicateSource(sourceRepository.findByContentHash(contentHash));
  }

  private ActionExtractionResponse resolveDuplicateBySourceUrl(String sourceUrl) {
    if (sourceUrl == null || sourceUrl.isBlank()) {
      return null;
    }
    return resolveDuplicateSource(sourceRepository.findBySourceUrl(sourceUrl));
  }

  private ActionExtractionResponse resolveDuplicateSource(
      java.util.Optional<NoticeSourceEntity> existingSource) {
    if (existingSource.isEmpty()) {
      return null;
    }
    NoticeSourceEntity source = existingSource.get();
    if (sourceRepository.countActionsBySourceId(source.getId()) > 0) {
      return buildDuplicateResponse(source);
    }
    sourceRepository.delete(source);
    return null;
  }

  private NoticeSourceEntity createSourceEntity(ActionExtractionRequest request, String contentHash,
      OffsetDateTime now) {
    NoticeSourceEntity source = new NoticeSourceEntity(
        UUID.randomUUID(),
        request.sourceTitle(),
        request.sourceCategory(),
        request.sourceText(),
        request.sourceUrl(),
        now
    );
    source.setContentHash(contentHash);
    return source;
  }

  private ActionExtractionResponse saveSourceOrResolveConcurrentDuplicate(NoticeSourceEntity source,
      String contentHash) {
    try {
      sourceRepository.saveAndFlush(source);
      return null;
    } catch (DataIntegrityViolationException e) {
      if (contentHash != null) {
        var concurrent = sourceRepository.findByContentHash(contentHash);
        if (concurrent.isPresent()) {
          return buildDuplicateResponse(concurrent.get());
        }
      }
      throw e;
    }
  }

  private List<ExtractedActionDto> persistActions(List<ExtractedActionDto> extractedActions,
      NoticeSourceEntity source, OffsetDateTime now) {
    List<ExtractedActionDto> savedActions = new ArrayList<>();
    for (ExtractedActionDto extractedAction : extractedActions) {
      ExtractedActionEntity entity = toExtractedActionEntity(extractedAction, source, now);
      addEvidence(entity, extractedAction.evidence(), now);
      actionRepository.save(entity);
      savedActions.add(toExtractedActionDto(entity));
    }
    return savedActions;
  }

  private ExtractedActionEntity toExtractedActionEntity(ExtractedActionDto extractedAction,
      NoticeSourceEntity source, OffsetDateTime now) {
    return new ExtractedActionEntity(
        UUID.randomUUID(),
        source,
        extractedAction.title(),
        extractedAction.actionSummary(),
        parseDueAtIso(extractedAction.dueAtIso(), "dueAtIso"),
        extractedAction.dueAtLabel(),
        extractedAction.eligibility(),
        toJson(extractedAction.requiredItems()),
        extractedAction.systemHint(),
        extractedAction.inferred(),
        extractedAction.confidenceScore(),
        now
    );
  }

  private void addEvidence(ExtractedActionEntity actionEntity, List<EvidenceSnippetDto> evidences,
      OffsetDateTime now) {
    for (EvidenceSnippetDto evidence : evidences) {
      EvidenceSnippetEntity evidenceEntity = new EvidenceSnippetEntity(
          UUID.randomUUID(),
          actionEntity,
          evidence.fieldName(),
          evidence.snippet(),
          evidence.confidence(),
          now
      );
      actionEntity.addEvidence(evidenceEntity);
    }
  }

  private ExtractedActionDto toExtractedActionDto(ExtractedActionEntity entity) {
    NoticeSourceEntity source = entity.getSource();
    return new ExtractedActionDto(
        entity.getId(),
        source != null ? source.getId() : null,
        entity.getTitle(),
        entity.getActionSummary(),
        toIsoString(entity.getDueAtIso()),
        entity.getDueAtLabel(),
        entity.getEligibility(),
        fromJson(entity.getRequiredItemsJson()),
        entity.getSystemHint(),
        source != null ? source.getSourceCategory() : null,
        entity.getEvidenceSnippets().stream()
            .map(e -> new EvidenceSnippetDto(e.getFieldName(), e.getSnippet(), e.getConfidence()))
            .toList(),
        entity.isInferred(),
        entity.getConfidenceScore(),
        entity.getCreatedAt()
    );
  }

  private void applyReverts(ExtractedActionEntity entity, List<String> revertFields,
      Map<String, String> machineValues) {
    if (revertFields == null) {
      return;
    }
    for (String fieldName : revertFields) {
      if (!OVERRIDABLE_FIELDS.contains(fieldName)) {
        throw new IllegalArgumentException("되돌릴 수 없는 필드입니다: " + fieldName);
      }
      String machineValue = machineValues.get(fieldName);
      if (machineValue != null) {
        setFieldValue(entity, fieldName, machineValue);
        machineValues.remove(fieldName);
      }
    }
  }

  private void applyUpdates(ExtractedActionEntity entity, ActionUpdateRequest request,
      Map<String, String> machineValues) {
    applyTitleUpdate(entity, request.title(), machineValues);
    applyActionSummaryUpdate(entity, request.actionSummary(), machineValues);
    applyDueAtIsoUpdate(entity, request.dueAtIso(), machineValues);
    applyDueAtLabelUpdate(entity, request.dueAtLabel(), machineValues);
    applyEligibilityUpdate(entity, request.eligibility(), machineValues);
    applyRequiredItemsUpdate(entity, request.requiredItems(), machineValues);
    applySystemHintUpdate(entity, request.systemHint(), machineValues);
  }

  private void applyTitleUpdate(ExtractedActionEntity entity, String title,
      Map<String, String> machineValues) {
    if (title == null) {
      return;
    }
    if (title.isBlank()) {
      throw new IllegalArgumentException("제목은 비워둘 수 없습니다.");
    }
    rememberMachineValueIfAbsent(machineValues, "title", entity.getTitle());
    entity.setTitle(title);
  }

  private void applyActionSummaryUpdate(ExtractedActionEntity entity, String actionSummary,
      Map<String, String> machineValues) {
    if (actionSummary == null) {
      return;
    }
    rememberMachineValueIfAbsent(machineValues, "actionSummary", entity.getActionSummary());
    entity.setActionSummary(actionSummary);
  }

  private void applyDueAtIsoUpdate(ExtractedActionEntity entity, String dueAtIso,
      Map<String, String> machineValues) {
    if (dueAtIso == null) {
      return;
    }
    rememberMachineValueIfAbsent(machineValues, "dueAtIso", toIsoString(entity.getDueAtIso()));
    entity.setDueAtIso(parseDueAtIso(dueAtIso, "dueAtIso"));
  }

  private void applyDueAtLabelUpdate(ExtractedActionEntity entity, String dueAtLabel,
      Map<String, String> machineValues) {
    if (dueAtLabel == null) {
      return;
    }
    rememberMachineValueIfAbsent(machineValues, "dueAtLabel", entity.getDueAtLabel());
    entity.setDueAtLabel(dueAtLabel);
  }

  private void applyEligibilityUpdate(ExtractedActionEntity entity, String eligibility,
      Map<String, String> machineValues) {
    if (eligibility == null) {
      return;
    }
    rememberMachineValueIfAbsent(machineValues, "eligibility", entity.getEligibility());
    entity.setEligibility(eligibility);
  }

  private void applyRequiredItemsUpdate(ExtractedActionEntity entity, List<String> requiredItems,
      Map<String, String> machineValues) {
    if (requiredItems == null) {
      return;
    }
    rememberMachineValueIfAbsent(machineValues, "requiredItems", entity.getRequiredItemsJson());
    entity.setRequiredItemsJson(toJson(requiredItems));
  }

  private void applySystemHintUpdate(ExtractedActionEntity entity, String systemHint,
      Map<String, String> machineValues) {
    if (systemHint == null) {
      return;
    }
    rememberMachineValueIfAbsent(machineValues, "systemHint", entity.getSystemHint());
    entity.setSystemHint(systemHint);
  }

  private void rememberMachineValueIfAbsent(Map<String, String> machineValues, String fieldName,
      String machineValue) {
    if (!machineValues.containsKey(fieldName)) {
      machineValues.put(fieldName, machineValue);
    }
  }

  private boolean hasFilters(ActionSearchCriteria criteria) {
    return (criteria.q() != null && !criteria.q().isBlank())
        || criteria.category() != null
        || criteria.dueDateFrom() != null
        || criteria.dueDateTo() != null;
  }

  private Specification<ExtractedActionEntity> buildSearchSpec(ActionSearchCriteria criteria) {
    Specification<ExtractedActionEntity> spec = (root, query, cb) -> cb.conjunction();
    if (criteria.q() != null && !criteria.q().isBlank()) {
      spec = spec.and(ActionSpecifications.titleOrSummaryContains(criteria.q()));
    }
    if (criteria.category() != null) {
      spec = spec.and(ActionSpecifications.sourceCategoryEquals(criteria.category()));
    }
    if (criteria.dueDateFrom() != null) {
      spec = spec.and(ActionSpecifications.dueAtFrom(criteria.dueDateFrom()));
    }
    if (criteria.dueDateTo() != null) {
      spec = spec.and(ActionSpecifications.dueAtTo(criteria.dueDateTo()));
    }
    return spec;
  }

  private Pageable resolvePageable(String sort, int page, int size) {
    if (isDueSort(sort)) {
      return PageRequest.of(page, size);
    }
    return PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt")));
  }

  private boolean isDueSort(String sort) {
    return "due".equals(sort);
  }

  private String toIsoString(OffsetDateTime dateTime) {
    return dateTime != null ? dateTime.toString() : null;
  }
}
