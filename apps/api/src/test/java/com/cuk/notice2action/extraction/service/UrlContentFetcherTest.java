package com.cuk.notice2action.extraction.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class UrlContentFetcherTest {

  private final UrlContentFetcher fetcher = new UrlContentFetcher();

  @Test
  void rejects_non_http_url() {
    assertThatThrownBy(() -> fetcher.fetch("ftp://example.com"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("http 또는 https");
  }

  @Test
  void rejects_null_url() {
    assertThatThrownBy(() -> fetcher.fetch(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("http 또는 https");
  }

  @Test
  void sends_browser_like_headers() throws Exception {
    AtomicReference<String> userAgent = new AtomicReference<>();
    AtomicReference<String> acceptLanguage = new AtomicReference<>();
    AtomicReference<String> accept = new AtomicReference<>();

    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> respondWithHtml(exchange, userAgent, acceptLanguage, accept));
    server.start();

    try {
      int port = server.getAddress().getPort();
      UrlContentFetcher.FetchedContent content = fetcher.fetch("http://127.0.0.1:" + port + "/");

      assertThat(content.title()).isEqualTo("테스트");
      assertThat(content.text()).contains("본문");
      assertThat(userAgent.get()).contains("Mozilla/5.0");
      assertThat(acceptLanguage.get()).contains("ko-KR");
      assertThat(accept.get()).contains("text/html");
    } finally {
      server.stop(0);
    }
  }

  private void respondWithHtml(
      HttpExchange exchange,
      AtomicReference<String> userAgent,
      AtomicReference<String> acceptLanguage,
      AtomicReference<String> accept
  ) throws IOException {
    userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
    acceptLanguage.set(exchange.getRequestHeaders().getFirst("Accept-Language"));
    accept.set(exchange.getRequestHeaders().getFirst("Accept"));

    byte[] body = "<html><head><title>테스트</title></head><body>본문</body></html>"
        .getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
    exchange.sendResponseHeaders(200, body.length);
    try (OutputStream outputStream = exchange.getResponseBody()) {
      outputStream.write(body);
    }
  }
}
