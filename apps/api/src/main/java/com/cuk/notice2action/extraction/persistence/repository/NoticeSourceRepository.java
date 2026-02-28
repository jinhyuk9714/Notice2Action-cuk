package com.cuk.notice2action.extraction.persistence.repository;

import com.cuk.notice2action.extraction.persistence.entity.NoticeSourceEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeSourceRepository extends JpaRepository<NoticeSourceEntity, UUID> {}
