package com.cuk.notice2action.extraction.service;

import com.cuk.notice2action.extraction.api.dto.ActionExtractionRequest;
import com.cuk.notice2action.extraction.api.dto.ActionExtractionResponse;
import com.cuk.notice2action.extraction.api.dto.ActionListResponse;
import com.cuk.notice2action.extraction.api.dto.ActionSearchCriteria;
import com.cuk.notice2action.extraction.api.dto.ActionUpdateRequest;
import com.cuk.notice2action.extraction.api.dto.EvidenceSnippetDto;
import com.cuk.notice2action.extraction.api.dto.ExtractedActionDto;
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
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
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

    // Duplicate detection by content hash
    String contentHash = ContentHashUtil.sha256(request.sourceText());
    if (contentHash != null) {
      var existing = sourceRepository.findByContentHash(contentHash);
      if (existing.isPresent()) {
        if (sourceRepository.countActionsBySourceId(existing.get().getId()) > 0) {
          return buildDuplicateResponse(existing.get());
        }
        sourceRepository.delete(existing.get());
      }
    }

    // Duplicate detection by URL
    if (request.sourceUrl() != null && !request.sourceUrl().isBlank()) {
      var existing = sourceRepository.findBySourceUrl(request.sourceUrl());
      if (existing.isPresent()) {
        if (sourceRepository.countActionsBySourceId(existing.get().getId()) > 0) {
          return buildDuplicateResponse(existing.get());
        }
        sourceRepository.delete(existing.get());
      }
    }

    OffsetDateTime now = OffsetDateTime.now();

    NoticeSourceEntity source = new NoticeSourceEntity(
        UUID.randomUUID(),
        request.sourceTitle(),
        request.sourceCategory(),
        request.sourceText(),
        request.sourceUrl(),
        now
    );
    source.setContentHash(contentHash);
    sourceRepository.save(source);

    List<ExtractedActionDto> savedActions = new ArrayList<>();

    for (ExtractedActionDto dto : extractionResult.actions()) {
      UUID actionId = UUID.randomUUID();
      String requiredItemsJson = toJson(dto.requiredItems());
      OffsetDateTime dueAtIso = parseDueAtIso(dto.dueAtIso(), "dueAtIso");

      ExtractedActionEntity actionEntity = new ExtractedActionEntity(
          actionId, source, dto.title(), dto.actionSummary(),
          dueAtIso, dto.dueAtLabel(), dto.eligibility(),
          requiredItemsJson, dto.systemHint(), dto.inferred(),
          dto.confidenceScore(), now
      );

      for (EvidenceSnippetDto ev : dto.evidence()) {
        EvidenceSnippetEntity evidenceEntity = new EvidenceSnippetEntity(
            UUID.randomUUID(), actionEntity,
            ev.fieldName(), ev.snippet(), ev.confidence(), now
        );
        actionEntity.addEvidence(evidenceEntity);
      }

      actionRepository.save(actionEntity);

      savedActions.add(new ExtractedActionDto(
          actionId, source.getId(),
          dto.title(), dto.actionSummary(), dto.dueAtIso(),
          dto.dueAtLabel(), dto.eligibility(), dto.requiredItems(),
          dto.systemHint(), dto.sourceCategory(), dto.evidence(),
          dto.inferred(), dto.confidenceScore(), now
      ));
    }

    return new ActionExtractionResponse(savedActions);
  }

  @Transactional
  public SavedActionDetailDto updateAction(UUID actionId, ActionUpdateRequest request) {
    ExtractedActionEntity entity = actionRepository.findById(actionId)
        .orElseThrow(() -> new NoSuchElementException("Action not found: " + actionId));

    if (request.title() != null) {
      if (request.title().isBlank()) {
        throw new IllegalArgumentException("제목은 비워둘 수 없습니다.");
      }
      entity.setTitle(request.title());
    }
    if (request.actionSummary() != null) {
      entity.setActionSummary(request.actionSummary());
    }
    if (request.dueAtIso() != null) {
      entity.setDueAtIso(parseDueAtIso(request.dueAtIso(), "dueAtIso"));
    }
    if (request.dueAtLabel() != null) {
      entity.setDueAtLabel(request.dueAtLabel());
    }
    if (request.eligibility() != null) {
      entity.setEligibility(request.eligibility());
    }
    if (request.requiredItems() != null) {
      entity.setRequiredItemsJson(toJson(request.requiredItems()));
    }
    if (request.systemHint() != null) {
      entity.setSystemHint(request.systemHint());
    }

    actionRepository.save(entity);
    return toDetailDto(entity);
  }

  private ActionExtractionResponse buildDuplicateResponse(NoticeSourceEntity existingSource) {
    List<ExtractedActionDto> actions = actionRepository
        .findAllBySourceIdOrderByCreatedAtDesc(existingSource.getId()).stream()
        .map(entity -> new ExtractedActionDto(
            entity.getId(), existingSource.getId(),
            entity.getTitle(), entity.getActionSummary(),
            entity.getDueAtIso() != null ? entity.getDueAtIso().toString() : null,
            entity.getDueAtLabel(), entity.getEligibility(),
            fromJson(entity.getRequiredItemsJson()),
            entity.getSystemHint(),
            existingSource.getSourceCategory(),
            entity.getEvidenceSnippets().stream()
                .map(e -> new EvidenceSnippetDto(e.getFieldName(), e.getSnippet(), e.getConfidence()))
                .toList(),
            entity.isInferred(), entity.getConfidenceScore(), entity.getCreatedAt()
        ))
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
    boolean hasFilters = (criteria.q() != null && !criteria.q().isBlank())
        || criteria.category() != null
        || criteria.dueDateFrom() != null
        || criteria.dueDateTo() != null;

    if (!hasFilters) {
      return listActionsSimple(criteria.sort(), page, size);
    }

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

    Pageable pageable;
    if ("due".equals(criteria.sort())) {
      spec = spec.and(dueNullsLastOrder());
      pageable = PageRequest.of(page, size);
    } else {
      pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt")));
    }
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
        entity.getDueAtIso() != null ? entity.getDueAtIso().toString() : null,
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

    return new SavedActionDetailDto(
        entity.getId(),
        entity.getTitle(),
        entity.getActionSummary(),
        entity.getDueAtIso() != null ? entity.getDueAtIso().toString() : null,
        entity.getDueAtLabel(),
        entity.getEligibility(),
        fromJson(entity.getRequiredItemsJson()),
        entity.getSystemHint(),
        entity.isInferred(),
        entity.getConfidenceScore(),
        entity.getCreatedAt(),
        sourceInfo,
        evidence
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
}
