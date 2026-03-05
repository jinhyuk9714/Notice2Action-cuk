package com.cuk.notice2action.extraction.service.notice;

import java.util.List;

public interface CukNoticeClient {
  List<CukNoticeDetail> fetchLatestNotices(int maxPages);
}
