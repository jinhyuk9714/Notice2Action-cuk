package com.cuk.notice2action.extraction.service;

import com.cuk.notice2action.extraction.api.dto.ActionExtractionRequest;
import com.cuk.notice2action.extraction.api.dto.ActionExtractionResponse;
import com.cuk.notice2action.extraction.api.dto.ActionListResponse;
import com.cuk.notice2action.extraction.api.dto.EvidenceSnippetDto;
import com.cuk.notice2action.extraction.api.dto.ExtractedActionDto;
import com.cuk.notice2action.extraction.api.dto.SavedActionDetailDto;
import com.cuk.notice2action.extraction.api.dto.SavedActionSummaryDto;
import com.cuk.notice2action.extraction.api.dto.SourceInfoDto;
import com.cuk.notice2action.extraction.persistence.entity.EvidenceSnippetEntity;
import com.cuk.notice2action.extraction.persistence.entity.ExtractedActionEntity;
import com.cuk.notice2action.extraction.persistence.entity.NoticeSourceEntity;
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

    OffsetDateTime now = OffsetDateTime.now();

    NoticeSourceEntity source = new NoticeSourceEntity(
        UUID.randomUUID(),
        request.sourceTitle(),
        request.sourceCategory(),
        request.sourceText(),
        request.sourceUrl(),
        now
    );
    sourceRepository.save(source);

    List<ExtractedActionDto> savedActions = new ArrayList<>();

    for (ExtractedActionDto dto : extractionResult.actions()) {
      UUID actionId = UUID.randomUUID();
      String requiredItemsJson = toJson(dto.requiredItems());
      OffsetDateTime dueAtIso = parseDueAtIso(dto.dueAtIso());

      ExtractedActionEntity actionEntity = new ExtractedActionEntity(
          actionId, source, dto.title(), dto.actionSummary(),
          dueAtIso, dto.dueAtLabel(), dto.eligibility(),
          requiredItemsJson, dto.systemHint(), dto.inferred(), now
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
          dto.inferred(), now
      ));
    }

    return new ActionExtractionResponse(savedActions);
  }

  @Transactional(readOnly = true)
  public ActionListResponse listActions(String sort) {
    List<ExtractedActionEntity> entities;
    if ("due".equals(sort)) {
      entities = actionRepository.findAllOrderByDueAtIsoAscNullsLast();
    } else {
      entities = actionRepository.findAllByOrderByCreatedAtDesc();
    }

    List<SavedActionSummaryDto> summaries = entities.stream()
        .map(this::toSummaryDto)
        .toList();

    return new ActionListResponse(summaries);
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
        source != null ? source.getSourceCategory() : null,
        source != null ? source.getTitle() : null,
        entity.getCreatedAt()
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

  private OffsetDateTime parseDueAtIso(String isoString) {
    if (isoString == null || isoString.isBlank()) {
      return null;
    }
    try {
      return OffsetDateTime.parse(isoString);
    } catch (Exception e) {
      return null;
    }
  }
}
