package com.cuk.notice2action.extraction.service.notice;

import java.time.LocalDate;
import java.util.List;

public record CukNoticeDetail(
    String externalNoticeId,
    String title,
    LocalDate publishedAt,
    String body,
    List<CukNoticeAttachment> attachments,
    String detailUrl
) {
  public CukNoticeDetail {
    attachments = attachments == null ? List.of() : List.copyOf(attachments);
  }
}
