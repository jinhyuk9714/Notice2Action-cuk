package com.cuk.notice2action.extraction.persistence.repository;

import com.cuk.notice2action.extraction.persistence.entity.NoticeSourceEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NoticeSourceRepository extends JpaRepository<NoticeSourceEntity, UUID> {

  Page<NoticeSourceEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

  @Query("SELECT COUNT(a) FROM ExtractedActionEntity a WHERE a.source.id = :sourceId")
  int countActionsBySourceId(UUID sourceId);

  Optional<NoticeSourceEntity> findByContentHash(String contentHash);

  Optional<NoticeSourceEntity> findBySourceUrl(String sourceUrl);
}
