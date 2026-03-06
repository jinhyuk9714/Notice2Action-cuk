package com.cuk.notice2action.extraction.service.notice;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class CukNoticeHtmlParserTest {

  private CukNoticeHtmlParser parser;

  @BeforeEach
  void setUp() {
    parser = new CukNoticeHtmlParser();
  }

  @Test
  void parsesNoticeListItemsFromBoardListHtml() throws IOException {
    String html = readFixture("fixtures/notice-feed/list-page.html");

    List<CukNoticeListItem> items = parser.parseList(
        html,
        URI.create("https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=list&srCategoryId=21")
    );

    assertThat(items).hasSize(2);
    assertThat(items.get(0).externalNoticeId()).isEqualTo("269152");
    assertThat(items.get(0).title()).isEqualTo("2026학년도 1학기 학부 수강신청 안내");
    assertThat(items.get(0).detailUrl())
        .isEqualTo("https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=269152&article.offset=0&articleLimit=10");
  }

  @Test
  void parsesNoticeDetailBodyWithoutBoardChrome() throws IOException {
    String html = readFixture("fixtures/notice-feed/detail-page.html");

    CukNoticeDetail detail = parser.parseDetail(
        html,
        URI.create("https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=268986&article.offset=20&articleLimit=10")
    );

    assertThat(detail.externalNoticeId()).isEqualTo("268986");
    assertThat(detail.title()).isEqualTo("2026학년도 신입생 및 편입생 ID카드(학생증) 신청 안내");
    assertThat(detail.publishedAt()).isEqualTo(LocalDate.of(2026, 2, 27));
    assertThat(detail.body())
        .contains("개인정보 제3자 동의")
        .contains("우리WON뱅킹")
        .contains("학생지원팀(니콜스관 N109)")
        .doesNotContain("조회수")
        .doesNotContain("공지사항");
    assertThat(detail.attachments())
        .extracting(CukNoticeAttachment::name)
        .containsExactly("학생증 발급 신청서.hwp", "우리WON뱅킹 대학 학생증 카드 신청 안내(2026년).pdf");
  }

  @Test
  void parsesRealTableHeavyNoticeWithoutLosingTitleOrBodyText() throws IOException {
    String html = readFixture("fixtures/notice-feed/real/detail-269089-table-body.html");

    CukNoticeDetail detail = parser.parseDetail(
        html,
        URI.create("https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=269089&srCategoryId=21")
    );

    assertThat(detail.externalNoticeId()).isEqualTo("269089");
    assertThat(detail.title()).isEqualTo("[학사지원팀] 2026-1학기 개강미사 및 수업운영 안내");
    assertThat(detail.publishedAt()).isEqualTo(LocalDate.of(2026, 3, 4));
    assertThat(detail.body())
        .contains("2026-1학기 개강미사 및 수업운영 안내")
        .contains("개강미사는")
        .doesNotContain("조회수")
        .doesNotContain("QUICK MENU")
        .doesNotContain("전체메뉴보기");
  }

  @Test
  void keepsImageOnlyNoticeIngestibleWithFallbackBody() throws IOException {
    String html = readFixture("fixtures/notice-feed/real/detail-269154-image-only.html");

    CukNoticeDetail detail = parser.parseDetail(
        html,
        URI.create("https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=269154&srCategoryId=21")
    );

    assertThat(detail.externalNoticeId()).isEqualTo("269154");
    assertThat(detail.title()).isEqualTo("2026학년도 1학기 강의변경(폐강 등) 10차 안내(교양)");
    assertThat(detail.publishedAt()).isEqualTo(LocalDate.of(2026, 3, 5));
    assertThat(detail.body())
        .isEqualTo("본문이 이미지로만 제공된 공지입니다.")
        .doesNotContain("사이버캠퍼스")
        .doesNotContain("QUICK MENU");
  }

  @Test
  void deduplicatesNestedScheduleBodyWhileKeepingStructuredBlocks() throws IOException {
    String html = readFixture("fixtures/notice-feed/real/detail-268547-schedule-heavy.html");

    CukNoticeDetail detail = parser.parseDetail(
        html,
        URI.create("https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=268547&srCategoryId=21")
    );

    assertThat(detail.title()).isEqualTo("2026학년도 신입생 수강신청 안내");
    assertThat(detail.body())
        .contains("수강신청:")
        .contains("수강신청 변경기간:")
        .contains("STEP 1. 교양영역에서 필수로 수강해야할 과목을 우선적으로 수강신청합니다.")
        .contains("교시 | 수업시간")
        .doesNotContain("수강신청: 2026.02.25.(수) 09:00 ~ 17:00 2026.02.26.(목) 09:00 ~ 17:00 수강신청 변경기간:");

    assertThat(detail.body().lines().filter(line -> line.contains("2026.03.03.(화) ~ 03.9.(월) 09:00 ~ 17:00")).count())
        .isEqualTo(1);
  }

  private String readFixture(String path) throws IOException {
    return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
  }
}
