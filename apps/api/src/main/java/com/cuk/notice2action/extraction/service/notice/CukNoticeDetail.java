package com.cuk.notice2action.extraction.service.notice;

import java.time.LocalDate;
import java.util.List;

public record CukNoticeDetail(
    String externalNoticeId,
    String title,
    LocalDate publishedAt,
    String body,
    List<CukNoticeAttachment> attachments,
    String detailUrl,
    String boardLabel
) {
  public CukNoticeDetail {
    attachments = attachments == null ? List.of() : List.copyOf(attachments);
  }

  public CukNoticeDetail(
      String externalNoticeId,
      String title,
      LocalDate publishedAt,
      String body,
      List<CukNoticeAttachment> attachments,
      String detailUrl
  ) {
    this(externalNoticeId, title, publishedAt, body, attachments, detailUrl, null);
  }
}
