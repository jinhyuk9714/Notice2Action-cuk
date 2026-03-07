package com.cuk.notice2action.extraction.service.notice;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class JsoupCukNoticeClient implements CukNoticeClient {

  private static final Logger log = LoggerFactory.getLogger(JsoupCukNoticeClient.class);
  private static final int TIMEOUT_MS = 10_000;
  private static final int MAX_BODY_SIZE_BYTES = 2 * 1024 * 1024;
  private static final int PAGE_SIZE = 10;
  private static final String BROWSER_USER_AGENT =
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
          + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

  private final NoticeFeedProperties properties;
  private final CukNoticeHtmlParser parser = new CukNoticeHtmlParser();

  public JsoupCukNoticeClient(NoticeFeedProperties properties) {
    this.properties = properties;
  }

  @Override
  public List<CukNoticeDetail> fetchLatestNotices(int maxPages) {
    Map<String, CukNoticeListItem> uniqueItems = new LinkedHashMap<>();

    for (int page = 0; page < Math.max(1, maxPages); page++) {
      String pageUrl = buildListPageUrl(properties.listUrl(), page * PAGE_SIZE);
      String html = fetchHtml(pageUrl);
      for (CukNoticeListItem item : parser.parseList(html, URI.create(pageUrl))) {
        uniqueItems.putIfAbsent(item.externalNoticeId(), item);
      }
    }

    List<CukNoticeDetail> details = new ArrayList<>();
    for (CukNoticeListItem item : uniqueItems.values()) {
      try {
        String html = fetchHtml(item.detailUrl());
        CukNoticeDetail parsed = parser.parseDetail(html, URI.create(item.detailUrl()));
        details.add(new CukNoticeDetail(
            parsed.externalNoticeId(),
            parsed.title(),
            parsed.publishedAt(),
            parsed.body(),
            parsed.attachments(),
            parsed.detailUrl(),
            item.boardLabel()
        ));
      } catch (IllegalArgumentException exception) {
        log.warn("Skipping malformed notice detail: articleNo={}, url={}", item.externalNoticeId(), item.detailUrl(), exception);
      }
    }
    return details;
  }

  private String fetchHtml(String url) {
    try {
      Document document = Jsoup.connect(url)
          .timeout(TIMEOUT_MS)
          .maxBodySize(MAX_BODY_SIZE_BYTES)
          .followRedirects(true)
          .userAgent(BROWSER_USER_AGENT)
          .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
          .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8")
          .header("Accept-Encoding", "gzip, deflate, br")
          .referrer("https://www.catholic.ac.kr/")
          .get();
      return document.outerHtml();
    } catch (IOException e) {
      throw new IllegalArgumentException("공지 페이지를 가져올 수 없습니다: " + url, e);
    }
  }

  private String buildListPageUrl(String baseUrl, int offset) {
    try {
      URI uri = new URI(baseUrl);
      return UriComponentsBuilder.fromUri(uri)
          .replaceQueryParam("mode", "list")
          .replaceQueryParam("article.offset", offset)
          .replaceQueryParam("articleLimit", PAGE_SIZE)
          .build(true)
          .toUriString();
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("잘못된 공지 목록 URL입니다: " + baseUrl, e);
    }
  }
}
