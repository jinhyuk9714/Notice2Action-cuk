package com.cuk.notice2action.extraction.persistence.repository;

import com.cuk.notice2action.extraction.persistence.entity.EvidenceSnippetEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceSnippetRepository extends JpaRepository<EvidenceSnippetEntity, UUID> {}
