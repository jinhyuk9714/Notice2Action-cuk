package com.cuk.notice2action.extraction.service;

import com.cuk.notice2action.extraction.service.notice.CukNoticeDetail;
import com.cuk.notice2action.extraction.service.notice.CukNoticeHtmlParser;
import java.io.IOException;
import java.net.URI;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

@Service
public class UrlContentFetcher {

  private static final int TIMEOUT_MS = 10_000;
  private static final int MAX_BODY_SIZE_BYTES = 2 * 1024 * 1024;
  private final CukNoticeHtmlParser cukNoticeHtmlParser = new CukNoticeHtmlParser();

  public record FetchedContent(String text, String title) {}

  public FetchedContent fetch(String url) {
    validateScheme(url);

    try {
      Document document = Jsoup.connect(url)
          .timeout(TIMEOUT_MS)
          .maxBodySize(MAX_BODY_SIZE_BYTES)
          .followRedirects(true)
          .userAgent("Notice2Action/1.0")
          .get();

      String title = document.title();
      String text = document.body() != null ? document.body().text() : "";
      if (isCukNoticePage(url, document)) {
        CukNoticeDetail detail = cukNoticeHtmlParser.parseDetail(document.outerHtml(), URI.create(url));
        title = detail.title();
        text = detail.body();
      }

      if (text.isBlank()) {
        throw new IllegalArgumentException("URL에서 텍스트 콘텐츠를 추출할 수 없습니다: " + url);
      }

      return new FetchedContent(text, title);
    } catch (IOException e) {
      throw new IllegalArgumentException("URL을 가져올 수 없습니다: " + e.getMessage());
    }
  }

  private void validateScheme(String url) {
    if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
      throw new IllegalArgumentException("http 또는 https URL만 지원됩니다.");
    }
  }

  private boolean isCukNoticePage(String url, Document document) {
    return url.contains("catholic.ac.kr")
        && url.contains("notice.do")
        && document.selectFirst(".b-content-box .fr-view") != null;
  }
}
