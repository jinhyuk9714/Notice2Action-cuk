package com.cuk.notice2action.extraction.service.notice;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class CukNoticeHtmlParser {

  private static final DateTimeFormatter PUBLISHED_AT_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.KOREA);

  public List<CukNoticeListItem> parseList(String html, URI baseUri) {
    Document document = Jsoup.parse(html, baseUri.toString());
    List<CukNoticeListItem> items = new ArrayList<>();
    Set<String> seenIds = new LinkedHashSet<>();

    for (Element anchor : document.select("a.b-title[data-article-no]")) {
      String externalNoticeId = anchor.attr("data-article-no").trim();
      String title = normalizeWhitespace(anchor.text());
      String detailUrl = anchor.absUrl("href");
      if (externalNoticeId.isEmpty() || title.isEmpty() || detailUrl.isEmpty() || !seenIds.add(externalNoticeId)) {
        continue;
      }
      items.add(new CukNoticeListItem(externalNoticeId, title, detailUrl));
    }

    return items;
  }

  public CukNoticeDetail parseDetail(String html, URI detailUri) {
    Document document = Jsoup.parse(html, detailUri.toString());

    String externalNoticeId = document.selectFirst("input[name=articleNo]") != null
        ? document.selectFirst("input[name=articleNo]").attr("value").trim()
        : extractArticleNo(detailUri.getQuery());

    Elements titleElements = document.select(".b-top-box .b-title-box .b-title");
    Element bodyElement = document.selectFirst(".b-content-box .fr-view");
    if (titleElements.isEmpty() || bodyElement == null) {
      throw new IllegalArgumentException("가톨릭대학교 공지 본문을 찾을 수 없습니다.");
    }

    String title = titleElements.stream()
        .map(Element::text)
        .map(CukNoticeHtmlParser::normalizeWhitespace)
        .filter(text -> !text.isEmpty() && !"공지".equals(text))
        .reduce((first, second) -> second)
        .orElse("");
    LocalDate publishedAt = parsePublishedAt(document.select(".b-etc-box .b-hit-box"));
    List<CukNoticeAttachment> attachments = parseAttachments(document.select(".b-file-box a.file-down-btn"));
    String body = normalizeBody(bodyElement, attachments);

    if (body.isBlank()) {
      throw new IllegalArgumentException("공지 본문이 비어 있습니다.");
    }

    return new CukNoticeDetail(externalNoticeId, title, publishedAt, body, attachments, detailUri.toString());
  }

  private static String extractArticleNo(String query) {
    if (query == null || query.isBlank()) {
      return "";
    }
    for (String pair : query.split("&")) {
      String[] parts = pair.split("=", 2);
      if (parts.length == 2 && "articleNo".equals(parts[0])) {
        return parts[1];
      }
    }
    return "";
  }

  private static LocalDate parsePublishedAt(Elements hitBoxes) {
    for (Element hitBox : hitBoxes) {
      String label = normalizeWhitespace(hitBox.selectFirst(".title") != null
          ? hitBox.selectFirst(".title").text()
          : "");
      if (!label.startsWith("등록일")) {
        continue;
      }
      Elements spans = hitBox.select("span");
      if (spans.size() < 2) {
        break;
      }
      return LocalDate.parse(normalizeWhitespace(spans.get(1).text()), PUBLISHED_AT_FORMATTER);
    }
    return null;
  }

  private static List<CukNoticeAttachment> parseAttachments(Elements links) {
    List<CukNoticeAttachment> attachments = new ArrayList<>();
    for (Element link : links) {
      String name = normalizeWhitespace(link.text());
      String url = link.absUrl("href");
      if (name.isEmpty() || url.isEmpty()) {
        continue;
      }
      attachments.add(new CukNoticeAttachment(name, url));
    }
    return attachments;
  }

  private static String normalizeBody(Element bodyElement, List<CukNoticeAttachment> attachments) {
    Set<String> blockSet = new LinkedHashSet<>();
    for (Element element : bodyElement.select("p, li, td, th, caption")) {
      String text = normalizeWhitespace(element.text());
      if (!text.isEmpty()) {
        blockSet.add(text);
      }
    }
    List<String> blocks = new ArrayList<>(blockSet);
    if (blocks.isEmpty()) {
      String fallback = normalizeWhitespace(bodyElement.wholeText());
      if (!fallback.isEmpty()) {
        blocks.add(fallback);
      }
    }
    if (blocks.isEmpty() && !bodyElement.select("img, video, iframe").isEmpty()) {
      blocks.add("본문이 이미지로만 제공된 공지입니다.");
    }
    if (attachments.isEmpty()) {
      return String.join("\n", blocks);
    }
    List<String> merged = new ArrayList<>(blocks);
    merged.add("첨부파일: " + attachments.stream().map(CukNoticeAttachment::name).distinct().reduce((a, b) -> a + ", " + b).orElse(""));
    return String.join("\n", merged);
  }

  private static String normalizeWhitespace(String value) {
    return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
  }
}
