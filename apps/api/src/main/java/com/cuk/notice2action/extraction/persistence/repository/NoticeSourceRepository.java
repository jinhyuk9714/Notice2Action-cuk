package com.cuk.notice2action.extraction.persistence.repository;

import com.cuk.notice2action.extraction.persistence.entity.NoticeSourceEntity;
import java.util.Collection;
import java.util.List;
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

  @Query("SELECT a.source.id AS sourceId, COUNT(a) AS actionCount "
      + "FROM ExtractedActionEntity a "
      + "WHERE a.source.id IN :sourceIds "
      + "GROUP BY a.source.id")
  List<SourceActionCountView> countActionsBySourceIds(Collection<UUID> sourceIds);

  Optional<NoticeSourceEntity> findByContentHash(String contentHash);

  Optional<NoticeSourceEntity> findBySourceUrl(String sourceUrl);
}
