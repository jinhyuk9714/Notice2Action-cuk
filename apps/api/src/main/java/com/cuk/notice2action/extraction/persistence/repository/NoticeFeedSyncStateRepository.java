package com.cuk.notice2action.extraction.persistence.repository;

import com.cuk.notice2action.extraction.persistence.entity.NoticeFeedSyncStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeFeedSyncStateRepository extends JpaRepository<NoticeFeedSyncStateEntity, String> {}
