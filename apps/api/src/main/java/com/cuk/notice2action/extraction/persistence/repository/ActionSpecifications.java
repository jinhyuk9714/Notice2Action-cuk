package com.cuk.notice2action.extraction.persistence.repository;

import com.cuk.notice2action.extraction.domain.SourceCategory;
import com.cuk.notice2action.extraction.persistence.entity.ExtractedActionEntity;
import com.cuk.notice2action.extraction.persistence.entity.NoticeSourceEntity;
import jakarta.persistence.criteria.Join;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.domain.Specification;

public final class ActionSpecifications {

  private ActionSpecifications() {}

  public static Specification<ExtractedActionEntity> titleOrSummaryContains(String keyword) {
    String pattern = "%" + keyword.toLowerCase() + "%";
    return (root, query, cb) -> cb.or(
        cb.like(cb.lower(root.get("title")), pattern),
        cb.like(cb.lower(root.get("actionSummary")), pattern)
    );
  }

  public static Specification<ExtractedActionEntity> sourceCategoryEquals(SourceCategory category) {
    return (root, query, cb) -> {
      Join<ExtractedActionEntity, NoticeSourceEntity> source = root.join("source");
      return cb.equal(source.get("sourceCategory"), category);
    };
  }

  public static Specification<ExtractedActionEntity> dueAtFrom(OffsetDateTime from) {
    return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("dueAtIso"), from);
  }

  public static Specification<ExtractedActionEntity> dueAtTo(OffsetDateTime to) {
    return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("dueAtIso"), to);
  }
}
