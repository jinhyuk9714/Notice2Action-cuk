package com.cuk.notice2action.extraction.service;

import com.cuk.notice2action.extraction.api.dto.SavedActionSummaryDto;
import com.cuk.notice2action.extraction.api.dto.SourceDetailDto;
import com.cuk.notice2action.extraction.api.dto.SourceListResponse;
import com.cuk.notice2action.extraction.api.dto.SourceSummaryDto;
import com.cuk.notice2action.extraction.persistence.entity.ExtractedActionEntity;
import com.cuk.notice2action.extraction.persistence.entity.NoticeSourceEntity;
import com.cuk.notice2action.extraction.persistence.repository.ExtractedActionRepository;
import com.cuk.notice2action.extraction.persistence.repository.NoticeSourceRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SourceHistoryService {

  private final NoticeSourceRepository sourceRepository;
  private final ExtractedActionRepository actionRepository;

  public SourceHistoryService(NoticeSourceRepository sourceRepository,
      ExtractedActionRepository actionRepository) {
    this.sourceRepository = sourceRepository;
    this.actionRepository = actionRepository;
  }

  @Transactional(readOnly = true)
  public SourceListResponse listSources(int page, int size) {
    Page<NoticeSourceEntity> pageResult =
        sourceRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));

    List<SourceSummaryDto> summaries = pageResult.getContent().stream()
        .map(this::toSummaryDto)
        .toList();

    return new SourceListResponse(
        summaries,
        pageResult.getNumber(),
        pageResult.getSize(),
        pageResult.getTotalElements(),
        pageResult.getTotalPages(),
        pageResult.hasNext()
    );
  }

  @Transactional(readOnly = true)
  public SourceDetailDto getSourceDetail(UUID sourceId) {
    NoticeSourceEntity source = sourceRepository.findById(sourceId)
        .orElseThrow(() -> new NoSuchElementException("Source not found: " + sourceId));

    List<ExtractedActionEntity> actionEntities =
        actionRepository.findAllBySourceIdOrderByCreatedAtDesc(sourceId);

    List<SavedActionSummaryDto> actions = actionEntities.stream()
        .map(this::toActionSummary)
        .toList();

    return new SourceDetailDto(
        source.getId(),
        source.getTitle(),
        source.getSourceCategory(),
        source.getSourceUrl(),
        source.getCreatedAt(),
        actions
    );
  }

  private SourceSummaryDto toSummaryDto(NoticeSourceEntity entity) {
    int actionCount = sourceRepository.countActionsBySourceId(entity.getId());
    return new SourceSummaryDto(
        entity.getId(),
        entity.getTitle(),
        entity.getSourceCategory(),
        entity.getSourceUrl(),
        entity.getCreatedAt(),
        actionCount
    );
  }

  private SavedActionSummaryDto toActionSummary(ExtractedActionEntity entity) {
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
}
