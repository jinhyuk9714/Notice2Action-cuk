package com.cuk.notice2action.extraction.service.notice;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class JsoupCukNoticeClientTest {

  @Test
  void sendsBrowserLikeHeadersForListAndDetailRequests() throws Exception {
    AtomicReference<String> listUserAgent = new AtomicReference<>();
    AtomicReference<String> listAccept = new AtomicReference<>();
    AtomicReference<String> detailUserAgent = new AtomicReference<>();
    AtomicReference<String> detailAcceptLanguage = new AtomicReference<>();

    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/ko/campuslife/notice.do", exchange -> {
      String query = exchange.getRequestURI().getRawQuery();
      if (query != null && query.contains("mode=list")) {
        listUserAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
        listAccept.set(exchange.getRequestHeaders().getFirst("Accept"));
        respond(exchange, listPageHtml());
        return;
      }
      detailUserAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
      detailAcceptLanguage.set(exchange.getRequestHeaders().getFirst("Accept-Language"));
      respond(exchange, detailPageHtml());
    });
    server.start();

    try {
      int port = server.getAddress().getPort();
      NoticeFeedProperties properties = new NoticeFeedProperties(
          true,
          "http://127.0.0.1:" + port + "/ko/campuslife/notice.do?mode=list&srCategoryId=&srSearchKey=&srSearchVal=",
          3600000,
          1,
          true
      );
      JsoupCukNoticeClient client = new JsoupCukNoticeClient(properties);

      List<CukNoticeDetail> notices = client.fetchLatestNotices(1);

      assertThat(notices).hasSize(1);
      assertThat(notices.getFirst().externalNoticeId()).isEqualTo("123456");
      assertThat(listUserAgent.get()).contains("Mozilla/5.0");
      assertThat(listAccept.get()).contains("text/html");
      assertThat(detailUserAgent.get()).contains("Mozilla/5.0");
      assertThat(detailAcceptLanguage.get()).contains("ko-KR");
    } finally {
      server.stop(0);
    }
  }

  private static void respond(HttpExchange exchange, String html) throws IOException {
    byte[] body = html.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
    exchange.sendResponseHeaders(200, body.length);
    try (OutputStream outputStream = exchange.getResponseBody()) {
      outputStream.write(body);
    }
  }

  private static String listPageHtml() {
    return """
        <html><body>
          <table>
            <tr class="b-cate-notice">
              <td>
                <div class="b-title-box">
                  <div class="b-title-list-box">
                    <a class="b-title" data-article-no="123456" href="?mode=view&amp;articleNo=123456&amp;article.offset=0&amp;articleLimit=10">
                      테스트 공지
                    </a>
                    <ul><li><span class="b-con b-cate">학사</span></li></ul>
                  </div>
                </div>
              </td>
            </tr>
          </table>
        </body></html>
        """;
  }

  private static String detailPageHtml() {
    return """
        <html><body>
          <input type="hidden" name="articleNo" value="123456" />
          <div class="b-top-box">
            <div class="b-title-box"><div class="b-title">테스트 공지</div></div>
          </div>
          <div class="b-etc-box">
            <div class="b-hit-box"><span class="title">등록일</span><span>2026.03.07</span></div>
          </div>
          <div class="b-content-box"><div class="fr-view"><p>본문</p></div></div>
        </body></html>
        """;
  }
}
