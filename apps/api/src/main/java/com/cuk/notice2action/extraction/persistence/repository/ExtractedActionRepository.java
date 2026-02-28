package com.cuk.notice2action.extraction.persistence.repository;

import com.cuk.notice2action.extraction.persistence.entity.ExtractedActionEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractedActionRepository extends JpaRepository<ExtractedActionEntity, UUID> {

  List<ExtractedActionEntity> findAllByOrderByCreatedAtDesc();
}
