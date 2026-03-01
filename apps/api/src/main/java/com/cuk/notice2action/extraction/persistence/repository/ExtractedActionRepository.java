package com.cuk.notice2action.extraction.persistence.repository;

import com.cuk.notice2action.extraction.persistence.entity.ExtractedActionEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExtractedActionRepository extends JpaRepository<ExtractedActionEntity, UUID> {

  List<ExtractedActionEntity> findAllByOrderByCreatedAtDesc();

  Page<ExtractedActionEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

  @Query("SELECT e FROM ExtractedActionEntity e ORDER BY "
      + "CASE WHEN e.dueAtIso IS NULL THEN 1 ELSE 0 END ASC, "
      + "e.dueAtIso ASC")
  List<ExtractedActionEntity> findAllOrderByDueAtIsoAscNullsLast();

  @Query("SELECT e FROM ExtractedActionEntity e ORDER BY "
      + "CASE WHEN e.dueAtIso IS NULL THEN 1 ELSE 0 END ASC, "
      + "e.dueAtIso ASC")
  Page<ExtractedActionEntity> findAllOrderByDueAtIsoAscNullsLast(Pageable pageable);
}
