package com.cuk.notice2action.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cuk.notice2action.extraction.api.dto.ActionExtractionRequest;
import com.cuk.notice2action.extraction.api.dto.ActionExtractionResponse;
import com.cuk.notice2action.extraction.api.dto.EvidenceSnippetDto;
import com.cuk.notice2action.extraction.api.dto.ExtractedActionDto;
import com.cuk.notice2action.extraction.domain.SourceCategory;
import com.cuk.notice2action.extraction.service.extractor.ActionSegmenter;
import com.cuk.notice2action.extraction.service.extractor.ActionSummaryBuilder;
import com.cuk.notice2action.extraction.service.extractor.ActionVerbExtractor;
import com.cuk.notice2action.extraction.service.extractor.DateExtractor;
import com.cuk.notice2action.extraction.service.extractor.EligibilityExtractor;
import com.cuk.notice2action.extraction.service.extractor.RequiredItemExtractor;
import com.cuk.notice2action.extraction.service.extractor.SystemHintExtractor;
import com.cuk.notice2action.extraction.service.extractor.TaskPhraseExtractor;
import com.cuk.notice2action.extraction.service.extractor.TextNormalizer;
import com.cuk.notice2action.extraction.service.extractor.TitleDeriver;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HeuristicActionExtractionServiceTest {

  private static final LocalDate FIXED_TODAY = LocalDate.of(2026, 3, 1); // Sunday

  private final DateExtractor dateExtractor = new DateExtractor() {
    @Override
    protected LocalDate today() {
      return FIXED_TODAY;
    }
  };

  private final ActionVerbExtractor actionVerbExtractor = new ActionVerbExtractor();
  private final TaskPhraseExtractor taskPhraseExtractor = new TaskPhraseExtractor();

  private final HeuristicActionExtractionService service = new HeuristicActionExtractionService(
      new TextNormalizer(),
      dateExtractor,
      new SystemHintExtractor(),
      new RequiredItemExtractor(),
      actionVerbExtractor,
      new EligibilityExtractor(),
      new ActionSegmenter(actionVerbExtractor, taskPhraseExtractor),
      new ActionSummaryBuilder(),
      new TitleDeriver(),
      taskPhraseExtractor
  );

  private ActionExtractionRequest request(String text) {
    return new ActionExtractionRequest(text, null, null, SourceCategory.NOTICE, List.of());
  }

  private ActionExtractionRequest request(String text, String title) {
    return new ActionExtractionRequest(text, null, title, SourceCategory.NOTICE, List.of());
  }

  private String currentYearPrefix() {
    return String.valueOf(FIXED_TODAY.getYear());
  }

  // --- Backward compatibility: existing test must still pass ---

  @Test
  void extracts_due_date_and_system_hint() {
    String text =
        """
        2026년 3월 12일 18시까지 TRINITY에서 공결 신청을 완료하고 증빙서류를 업로드해야 합니다.
        신청 대상은 재학생입니다.
        """;

    ActionExtractionRequest req =
        new ActionExtractionRequest(
            text,
            null,
            "공결 신청 안내",
            SourceCategory.NOTICE,
            List.of("복학생")
        );

    ActionExtractionResponse response = service.extract(req);

    assertThat(response.actions()).hasSize(1);
    assertThat(response.actions().getFirst().systemHint()).isEqualTo("TRINITY");
    assertThat(response.actions().getFirst().dueAtIso()).startsWith("2026-03-12T18:00");
    assertThat(response.actions().getFirst().requiredItems()).contains("증빙서류");
  }

  // --- Date Pattern Tests ---

  @Nested
  class DatePatternTests {

    @Test
    void korean_full_date_with_day_of_week_and_ampm() {
      ActionExtractionResponse response =
          service.extract(request("2026년 3월 12일(수) 오후 6시 30분까지 제출하세요"));

      ExtractedActionDto action = response.actions().getFirst();
      assertThat(action.dueAtIso()).isEqualTo("2026-03-12T18:30:00+09:00");
      assertThat(action.dueAtLabel()).contains("2026년 3월 12일");
    }

    @Test
    void korean_full_date_with_ampm_morning() {
      ActionExtractionResponse response =
          service.extract(request("2026년 5월 1일 오전 9시까지 접수"));

      assertThat(response.actions().getFirst().dueAtIso()).startsWith("2026-05-01T09:00");
    }

    @Test
    void iso_date_with_dot_separator_and_time() {
      ActionExtractionResponse response =
          service.extract(request("2026.03.12 18:00 마감"));

      assertThat(response.actions().getFirst().dueAtIso()).isEqualTo("2026-03-12T18:00:00+09:00");
    }

    @Test
    void iso_date_with_slash_separator() {
      ActionExtractionResponse response =
          service.extract(request("2026/3/12 신청 마감"));

      assertThat(response.actions().getFirst().dueAtIso()).startsWith("2026-03-12T00:00");
    }

    @Test
    void korean_month_day_no_year_infers_current_year() {
      ActionExtractionResponse response =
          service.extract(request("3월 12일까지 신청서를 TRINITY에 제출하세요"));

      assertThat(response.actions().getFirst().dueAtIso())
          .startsWith(currentYearPrefix() + "-03-12T00:00");
    }

    @Test
    void korean_month_day_with_24h_time() {
      ActionExtractionResponse response =
          service.extract(request("3월 12일(수) 18:00까지 접수"));

      assertThat(response.actions().getFirst().dueAtIso())
          .startsWith(currentYearPrefix() + "-03-12T18:00");
    }

    @Test
    void korean_month_day_with_ampm() {
      ActionExtractionResponse response =
          service.extract(request("3월 12일(수) 오후 6시까지 지원서 제출"));

      assertThat(response.actions().getFirst().dueAtIso())
          .startsWith(currentYearPrefix() + "-03-12T18:00");
    }

    @Test
    void short_slash_date_with_time() {
      ActionExtractionResponse response =
          service.extract(request("3/12(수) 18:00까지 등록"));

      assertThat(response.actions().getFirst().dueAtIso())
          .startsWith(currentYearPrefix() + "-03-12T18:00");
    }

    @Test
    void range_end_korean() {
      ActionExtractionResponse response =
          service.extract(request("신청 기간: 3월 10일 ~ 3월 15일"));

      assertThat(response.actions().getFirst().dueAtIso()).isNotNull();
      assertThat(response.actions().getFirst().dueAtLabel()).isNotNull();
    }

    @Test
    void date_range_with_year_and_time_prefers_range_end() {
      ActionExtractionResponse response =
          service.extract(request("수강과목 취소 신청기간 : 2026. 3. 24.(화) 09:00 ~ 3. 25.(수) 17:00"));

      assertThat(response.actions().getFirst().dueAtIso()).isEqualTo("2026-03-25T17:00:00+09:00");
      assertThat(response.actions().getFirst().dueAtLabel()).contains("3. 25. (수) 17:00");
    }

    @Test
    void lecture_schedule_range_is_not_treated_as_deadline() {
      ActionExtractionResponse response =
          service.extract(request("강의일정 : 2026년 3월 16일(월) ~ 6월 5일(금), 12주간 진행"));

      assertThat(response.actions().getFirst().dueAtIso()).isNull();
      assertThat(response.actions().getFirst().dueAtLabel()).isNull();
    }

    @Test
    void deadline_keyword_boosts_confidence() {
      ActionExtractionResponse response =
          service.extract(request("3월 12일까지 제출"));

      List<EvidenceSnippetDto> dateEvidence = response.actions().getFirst().evidence().stream()
          .filter(e -> "dueAtLabel".equals(e.fieldName()))
          .toList();
      assertThat(dateEvidence).isNotEmpty();
      assertThat(dateEvidence.getFirst().confidence()).isGreaterThanOrEqualTo(0.76);
    }

    @Test
    void no_date_returns_null() {
      ActionExtractionResponse response =
          service.extract(request("학교 공지사항입니다. TRINITY를 확인하세요."));

      assertThat(response.actions().getFirst().dueAtIso()).isNull();
      assertThat(response.actions().getFirst().dueAtLabel()).isNull();
    }
  }

  // --- Text Normalization Tests ---

  @Nested
  class TextNormalizationTests {

    @Test
    void full_width_digits_normalized() {
      // ３월 １２일 (full-width digits with Korean month/day markers)
      ActionExtractionResponse response =
          service.extract(request("\uFF13월 \uFF11\uFF12일까지 제출"));
      assertThat(response.actions().getFirst().dueAtIso()).isNotNull();
    }

    @Test
    void bullet_characters_normalized() {
      ActionExtractionResponse response =
          service.extract(request("ㅇ 신청서 제출\nㅇ 성적증명서 첨부"));
      assertThat(response.actions().getFirst().requiredItems())
          .contains("신청서", "성적증명서");
    }

    @Test
    void zero_width_characters_removed() {
      ActionExtractionResponse response =
          service.extract(request("TRIN\u200BITY에서 신청"));
      assertThat(response.actions().getFirst().systemHint()).isEqualTo("TRINITY");
    }

    @Test
    void tab_separated_table_text_handled() {
      ActionExtractionResponse response =
          service.extract(request("제출기한\t\t\t3월 12일\n준비물\t\t\t성적증명서"));
      assertThat(response.actions().getFirst().dueAtIso()).isNotNull();
      assertThat(response.actions().getFirst().requiredItems()).contains("성적증명서");
    }

    @Test
    void multiple_spaces_collapsed() {
      ActionExtractionResponse response =
          service.extract(request("TRINITY 에서     신청서     제출하세요"));
      assertThat(response.actions().getFirst().systemHint()).isEqualTo("TRINITY");
      assertThat(response.actions().getFirst().requiredItems()).contains("신청서");
    }

    @Test
    void excessive_newlines_collapsed() {
      ActionExtractionResponse response =
          service.extract(request("공지사항\n\n\n\n\n3월 12일까지 제출"));
      assertThat(response.actions().getFirst().dueAtIso()).isNotNull();
    }
  }

  // --- Relative Date Tests ---

  @Nested
  class RelativeDateTests {

    // FIXED_TODAY = 2026-03-01 (Sunday)

    @Test
    void 내일_resolves_to_tomorrow() {
      ActionExtractionResponse response =
          service.extract(request("내일까지 제출하세요"));
      assertThat(response.actions().getFirst().dueAtIso()).startsWith("2026-03-02T00:00");
    }

    @Test
    void 모레_resolves_to_day_after_tomorrow() {
      ActionExtractionResponse response =
          service.extract(request("모레까지 신청서 제출"));
      assertThat(response.actions().getFirst().dueAtIso()).startsWith("2026-03-03T00:00");
    }

    @Test
    void 글피_resolves_to_three_days_later() {
      ActionExtractionResponse response =
          service.extract(request("글피까지 완료해야 합니다"));
      assertThat(response.actions().getFirst().dueAtIso()).startsWith("2026-03-04T00:00");
    }

    @Test
    void 다음_주_금요일_resolves_correctly() {
      // 2026-03-01 is Sunday. Next Monday = 03-02, next Friday = 03-06
      ActionExtractionResponse response =
          service.extract(request("다음 주 금요일까지 등록"));
      assertThat(response.actions().getFirst().dueAtIso()).startsWith("2026-03-06");
    }

    @Test
    void 이번_주_수요일_resolves_to_this_week() {
      // 2026-03-01 is Sunday. nextOrSame(WED) = 03-04
      ActionExtractionResponse response =
          service.extract(request("이번 주 수요일까지 접수"));
      assertThat(response.actions().getFirst().dueAtIso()).startsWith("2026-03-04");
    }

    @Test
    void three_days_이내_resolves() {
      ActionExtractionResponse response =
          service.extract(request("3일 이내 제출하세요"));
      assertThat(response.actions().getFirst().dueAtIso()).startsWith("2026-03-04T00:00");
    }

    @Test
    void two_weeks_후_resolves() {
      ActionExtractionResponse response =
          service.extract(request("2주 후까지 신청"));
      assertThat(response.actions().getFirst().dueAtIso()).startsWith("2026-03-15T00:00");
    }

    @Test
    void one_month_이내_resolves() {
      ActionExtractionResponse response =
          service.extract(request("1개월 이내 등록 완료"));
      assertThat(response.actions().getFirst().dueAtIso()).startsWith("2026-04-01T00:00");
    }

    @Test
    void 이번_달_말_resolves_to_end_of_month() {
      ActionExtractionResponse response =
          service.extract(request("이번 달 말까지 서류 제출"));
      assertThat(response.actions().getFirst().dueAtIso()).startsWith("2026-03-31T00:00");
    }

    @Test
    void 이번_주_말_resolves_to_sunday() {
      // 2026-03-01 is Sunday. nextOrSame(SUNDAY) = 03-01
      ActionExtractionResponse response =
          service.extract(request("이번 주 말까지 확인"));
      assertThat(response.actions().getFirst().dueAtIso()).startsWith("2026-03-01T00:00");
    }

    @Test
    void relative_date_confidence_lower_than_absolute() {
      ActionExtractionResponse relResponse =
          service.extract(request("내일까지 제출"));
      ActionExtractionResponse absResponse =
          service.extract(request("3월 12일까지 제출"));

      double relConf = relResponse.actions().getFirst().evidence().stream()
          .filter(e -> "dueAtLabel".equals(e.fieldName()))
          .mapToDouble(EvidenceSnippetDto::confidence).findFirst().orElse(0);
      double absConf = absResponse.actions().getFirst().evidence().stream()
          .filter(e -> "dueAtLabel".equals(e.fieldName()))
          .mapToDouble(EvidenceSnippetDto::confidence).findFirst().orElse(0);

      assertThat(relConf).isLessThan(absConf);
    }

    @Test
    void absolute_date_takes_priority_over_relative() {
      // P3 (3월 15일) matches before P8 (내일)
      ActionExtractionResponse response =
          service.extract(request("내일 확인하고 3월 15일까지 제출"));
      assertThat(response.actions().getFirst().dueAtIso()).startsWith("2026-03-15");
    }
  }

  // --- Multi-Date Ranking Tests ---

  @Nested
  class MultiDateRankingTests {

    @Test
    void prefers_deadline_date_over_start_date() {
      ActionExtractionResponse response =
          service.extract(request("접수기간: 2026.3.1 ~ 2026.3.15 18:00 마감"));
      assertThat(response.actions().getFirst().dueAtIso()).startsWith("2026-03-15T18:00");
    }

    @Test
    void prefers_까지_date() {
      ActionExtractionResponse response =
          service.extract(request("3월 1일부터 접수 시작, 3월 15일까지 제출"));
      assertThat(response.actions().getFirst().dueAtIso())
          .startsWith(currentYearPrefix() + "-03-15");
    }

    @Test
    void prefers_마감_keyword_date() {
      ActionExtractionResponse response =
          service.extract(request("3월 10일 설명회 예정이며 서류 마감은 3월 20일까지입니다"));
      assertThat(response.actions().getFirst().dueAtIso())
          .startsWith(currentYearPrefix() + "-03-20");
    }

    @Test
    void 부터_까지_format_prefers_end_date() {
      ActionExtractionResponse response =
          service.extract(request("3월 1일부터 3월 15일까지 신청"));
      assertThat(response.actions().getFirst().dueAtIso())
          .startsWith(currentYearPrefix() + "-03-15");
    }

    @Test
    void year_date_wins_over_yearless_when_both_near_deadline() {
      ActionExtractionResponse response =
          service.extract(request("3월 12일까지 확인, 2026년 3월 15일까지 제출"));
      assertThat(response.actions().getFirst().dueAtIso()).startsWith("2026-03-15");
    }

    @Test
    void single_date_still_works() {
      ActionExtractionResponse response =
          service.extract(request("3월 12일까지 제출"));
      assertThat(response.actions().getFirst().dueAtIso())
          .startsWith(currentYearPrefix() + "-03-12");
    }

    @Test
    void start_date_penalized_with_tilde() {
      ActionExtractionResponse response =
          service.extract(request("2026.3.1 ~ 2026.3.15 접수"));
      assertThat(response.actions().getFirst().dueAtIso()).startsWith("2026-03-15");
    }
  }

  // --- Date Validation Tests ---

  @Nested
  class DateValidationTests {

    @Test
    void rejects_feb_30() {
      ActionExtractionResponse response =
          service.extract(request("2026년 2월 30일까지 제출"));
      assertThat(response.actions().getFirst().dueAtIso()).isNull();
    }

    @Test
    void rejects_feb_29_non_leap_year() {
      ActionExtractionResponse response =
          service.extract(request("2025년 2월 29일까지 제출"));
      assertThat(response.actions().getFirst().dueAtIso()).isNull();
    }

    @Test
    void accepts_feb_29_leap_year() {
      ActionExtractionResponse response =
          service.extract(request("2028년 2월 29일까지 제출"));
      assertThat(response.actions().getFirst().dueAtIso()).startsWith("2028-02-29");
    }

    @Test
    void rejects_apr_31() {
      ActionExtractionResponse response =
          service.extract(request("2026.4.31 마감"));
      assertThat(response.actions().getFirst().dueAtIso()).isNull();
    }

    @Test
    void rejects_month_13() {
      ActionExtractionResponse response =
          service.extract(request("2026.13.1 마감"));
      assertThat(response.actions().getFirst().dueAtIso()).isNull();
    }

    @Test
    void rejects_invalid_time_hour_25() {
      ActionExtractionResponse response =
          service.extract(request("2026년 3월 12일 25시까지 접수"));
      assertThat(response.actions().getFirst().dueAtIso()).isNull();
    }

    @Test
    void rejects_invalid_time_minute_99_even_with_zero_hour() {
      ActionExtractionResponse response =
          service.extract(request("2026.3.12 00:99 마감"));
      assertThat(response.actions().getFirst().dueAtIso()).isNull();
    }

    @Test
    void rejects_short_slash_feb_30() {
      ActionExtractionResponse response =
          service.extract(request("2/30까지 제출"));
      assertThat(response.actions().getFirst().dueAtIso()).isNull();
    }
  }

  // --- System Hint Tests ---

  @Nested
  class SystemHintTests {

    @Test
    void detects_trinity() {
      ActionExtractionResponse response =
          service.extract(request("TRINITY에서 수강신청하세요"));

      assertThat(response.actions().getFirst().systemHint()).isEqualTo("TRINITY");
    }

    @Test
    void detects_종정넷() {
      ActionExtractionResponse response =
          service.extract(request("종정넷을 통해 성적을 확인하세요"));

      assertThat(response.actions().getFirst().systemHint()).isEqualTo("종정넷");
    }

    @Test
    void detects_lms() {
      ActionExtractionResponse response =
          service.extract(request("과제는 LMS에 업로드하세요"));

      assertThat(response.actions().getFirst().systemHint()).isEqualTo("LMS");
    }

    @Test
    void detects_장학포털() {
      ActionExtractionResponse response =
          service.extract(request("장학포털에서 장학금 신청을 완료하세요"));

      assertThat(response.actions().getFirst().systemHint()).isEqualTo("장학포털");
    }

    @Test
    void system_hint_evidence_includes_context() {
      ActionExtractionResponse response =
          service.extract(request("3월 12일까지 장학포털에서 장학금을 신청하세요"));

      List<EvidenceSnippetDto> hintEvidence = response.actions().getFirst().evidence().stream()
          .filter(e -> "systemHint".equals(e.fieldName()))
          .toList();
      assertThat(hintEvidence).isNotEmpty();
      assertThat(hintEvidence.getFirst().snippet().length()).isGreaterThan("장학포털".length());
    }

    @Test
    void case_insensitive_portal_match() {
      ActionExtractionResponse response =
          service.extract(request("Portal 접속 후 확인"));

      assertThat(response.actions().getFirst().systemHint()).isEqualTo("portal");
    }
  }

  // --- Required Item Tests ---

  @Nested
  class RequiredItemTests {

    @Test
    void detects_multiple_existing_items() {
      ActionExtractionResponse response =
          service.extract(request("신청서와 성적증명서를 제출하세요"));

      assertThat(response.actions().getFirst().requiredItems())
          .containsExactlyInAnyOrder("신청서", "성적증명서");
    }

    @Test
    void detects_new_items() {
      ActionExtractionResponse response =
          service.extract(request("여권사본, 자기소개서, 포트폴리오를 준비하세요"));

      assertThat(response.actions().getFirst().requiredItems())
          .contains("여권사본", "자기소개서", "포트폴리오");
    }

    @Test
    void no_items_returns_empty_list() {
      ActionExtractionResponse response =
          service.extract(request("내일 수업에 참석하세요"));

      assertThat(response.actions().getFirst().requiredItems()).isEmpty();
    }

    @Test
    void detects_서약서_and_동의서() {
      ActionExtractionResponse response =
          service.extract(request("서약서와 개인정보 동의서를 작성 후 제출"));

      assertThat(response.actions().getFirst().requiredItems())
          .contains("서약서", "동의서");
    }
  }

  // --- Eligibility Tests ---

  @Nested
  class EligibilityTests {

    @Test
    void extracts_full_sentence_for_대상() {
      ActionExtractionResponse response =
          service.extract(request("지원 대상은 2학년 이상 재학생입니다.\n제출 기한은 3월 15일입니다."));

      String eligibility = response.actions().getFirst().eligibility();
      assertThat(eligibility).contains("2학년 이상 재학생");
      assertThat(eligibility).contains("지원");
    }

    @Test
    void extracts_졸업예정자_signal() {
      ActionExtractionResponse response =
          service.extract(request("졸업예정자는 반드시 확인바랍니다"));

      assertThat(response.actions().getFirst().eligibility()).contains("졸업예정자");
    }

    @Test
    void no_eligibility_returns_null() {
      ActionExtractionResponse response =
          service.extract(request("오늘 학교 식당 메뉴 안내입니다"));

      assertThat(response.actions().getFirst().eligibility()).isNull();
    }

    @Test
    void caps_at_200_chars() {
      String longSentence = "신청 대상은 " + "가".repeat(250) + "입니다";
      ActionExtractionResponse response = service.extract(request(longSentence));

      assertThat(response.actions().getFirst().eligibility().length()).isLessThanOrEqualTo(200);
    }

    @Test
    void eligibility_confidence_varies_by_signal() {
      ActionExtractionResponse resp1 =
          service.extract(request("신청 대상은 재학생입니다"));
      ActionExtractionResponse resp2 =
          service.extract(request("전체 학생 대상입니다"));

      double conf1 = resp1.actions().getFirst().evidence().stream()
          .filter(e -> "eligibility".equals(e.fieldName()))
          .mapToDouble(EvidenceSnippetDto::confidence)
          .findFirst().orElse(0);

      double conf2 = resp2.actions().getFirst().evidence().stream()
          .filter(e -> "eligibility".equals(e.fieldName()))
          .mapToDouble(EvidenceSnippetDto::confidence)
          .findFirst().orElse(0);

      assertThat(conf1).isGreaterThan(conf2);
    }
  }

  // --- Action Verb Tests ---

  @Nested
  class ActionVerbTests {

    @Test
    void extracts_신청_verb_in_evidence() {
      ActionExtractionResponse response =
          service.extract(request("장학금 신청을 완료하세요"));

      List<EvidenceSnippetDto> verbEvidence = response.actions().getFirst().evidence().stream()
          .filter(e -> "actionVerb".equals(e.fieldName()))
          .toList();
      assertThat(verbEvidence).isNotEmpty();
      assertThat(verbEvidence.getFirst().snippet()).contains("신청");
    }

    @Test
    void verb_appears_in_action_summary() {
      ActionExtractionResponse response =
          service.extract(request("3월 12일까지 TRINITY에서 신청하세요"));

      assertThat(response.actions().getFirst().actionSummary())
          .isEqualTo("할 일: 신청. 마감: 3월 12일. 시스템: TRINITY.");
    }

    @Test
    void summary_fallback_when_no_structured_info() {
      ActionExtractionResponse response =
          service.extract(request("학교 공지사항을 읽어주세요"));

      assertThat(response.actions().getFirst().actionSummary()).contains("학교 공지사항");
    }
  }

  // --- Multi-Action Tests ---

  @Nested
  class MultiActionTests {

    @Test
    void extracts_two_actions_from_two_verb_sentences() {
      String text = """
          3월 12일까지 TRINITY에서 신청서를 제출하세요.
          3월 15일까지 면접에 참석하세요.
          """;
      ActionExtractionResponse response = service.extract(request(text, "장학금 안내"));

      assertThat(response.actions()).hasSize(2);
      assertThat(response.actions().get(0).requiredItems()).contains("신청서");
    }

    @Test
    void single_verb_notice_returns_one_action() {
      ActionExtractionResponse response =
          service.extract(request("3월 12일까지 신청서를 제출하세요.", "안내"));

      assertThat(response.actions()).hasSize(1);
    }

    @Test
    void multi_action_titles_do_not_include_counter_when_titles_differ() {
      String text = """
          3월 12일까지 서류 제출하세요.
          3월 15일까지 면접 참석하세요.
          """;
      ActionExtractionResponse response = service.extract(request(text, "장학금"));

      assertThat(response.actions().get(0).title()).doesNotContain("(1/2)");
      assertThat(response.actions().get(1).title()).doesNotContain("(2/2)");
    }

    @Test
    void task_title_prefers_notice_specific_phrase_over_source_title() {
      ActionExtractionResponse response = service.extract(request(
          "수강과목 취소 신청기간 : 2026. 3. 24.(화) 09:00 ~ 3. 25.(수) 17:00\n"
              + "신청이 필요한 학생은 기간 내 신청을 완료하시기 바랍니다.",
          "[학사지원팀] 2026-1학기 수강과목 취소 기간 안내"
      ));

      assertThat(response.actions()).singleElement().satisfies(action -> {
        assertThat(action.title()).isEqualTo("수강과목 취소 신청");
        assertThat(action.actionSummary())
            .isEqualTo("할 일: 수강과목 취소 신청. 마감: 3. 25. (수) 17:00.");
      });
    }

    @Test
    void procedural_steps_stay_in_single_action_when_they_share_same_task() {
      String text = """
          1. 수강과목 취소 신청기간 : 2026. 3. 24.(화) 09:00 ~ 3. 25.(수) 17:00
          2. 수강과목 취소 절차
          가. [트리니티] - [수업/성적] - [수강신청] - [수강취소신청]
          나. 취소신청 버튼 클릭
          다. 취소 결과 확인
          """;

      ActionExtractionResponse response = service.extract(request(text, "[학사지원팀] 2026-1학기 수강과목 취소 기간 안내"));

      assertThat(response.actions()).singleElement().satisfies(action -> {
        assertThat(action.title()).isEqualTo("수강과목 취소 신청");
        assertThat(action.systemHint()).isEqualTo("TRINITY");
      });
    }

    @Test
    void image_only_notice_uses_attachment_keywords_to_build_title_and_summary() {
      ActionExtractionResponse response = service.extract(request(
          "본문이 이미지로만 제공된 공지입니다.\n"
              + "첨부파일: 1. 공결허가원(취업).hwp, 2. 개인정보 수집활용 동의서(재직조회).hwp, 3. 취업공결 확인서(학기 중 취업학생).hwp",
          "[학사지원팀] 2026학년도 1학기 학기 중 취업학생 출결 사항 안내"
      ));

      assertThat(response.actions()).singleElement().satisfies(action -> {
        assertThat(action.title()).isEqualTo("취업공결 관련 서류 준비 및 제출");
        assertThat(action.actionSummary()).isEqualTo("할 일: 취업공결 관련 서류 준비 및 제출. 준비물: 공결허가원, 동의서, 확인서.");
      });
    }

    @Test
    void idesign_notice_collapses_to_single_registration_action() {
      ActionExtractionResponse response = service.extract(request(
          """
          2026학년도 1학기 <I-DESIGN> 수강신청 관련 안내
          해당 내용을 확인하시어 수강신청하시기 바랍니다.
          ◎ 수강신청 기간
          - 재수강 분반 수강신청: 2/3(화)~2/5(목)
          - 신입생 분반 수강신청: 2/25(수)~2/26(목)
          - 수강신청 변경기간: 3/3(화)~3/9(월), 09:00 ~ 17:00
          """,
          "[학부대학] 2026학년도 1학기 <I-DESIGN> 수강신청 관련 안내"
      ));

      assertThat(response.actions()).singleElement().satisfies(action -> {
        assertThat(action.title()).isEqualTo("I-DESIGN 수강신청");
        assertThat(action.dueAtLabel()).isEqualTo("~ 3/9");
      });
    }

    @Test
    void new_student_notice_prefers_notice_level_task_over_step_titles() {
      ActionExtractionResponse response = service.extract(request(
          """
          2026학년도 신입생 수강신청 안내
          수강신청 변경기간:
          2026.03.03.(화) ~ 03.9.(월) 09:00 ~ 17:00
          STEP 1. 교양영역에서 필수로 수강해야할 과목을 우선적으로 수강신청합니다.
          ➊ 1학년 1학기에 필수 수강 교과목: 기초교양필수에서 [인간학1], 중핵교양선택필수 [I-DESIGN]
          STEP 2. 본인의 학과 또는 선택할 학과의 전공기초 선수(필수) 과목을 수강신청합니다.
          첨부파일: 2026 신입생 수강신청 방법 안내(최종).pptx
          """,
          "2026학년도 신입생 수강신청 안내"
      ));

      assertThat(response.actions()).singleElement().satisfies(action -> {
        assertThat(action.title()).isEqualTo("신입생 수강신청");
        assertThat(action.actionSummary()).contains("할 일: 신입생 수강신청.");
        assertThat(action.dueAtLabel()).isEqualTo("~ 3/9");
      });
    }
  }

  // --- Comprehensive Keyword Tests ---

  @Nested
  class ComprehensiveSystemHintTests {

    @Test
    void detects_사이버캠퍼스() {
      assertThat(service.extract(request("사이버캠퍼스에서 과제 제출")).actions().getFirst().systemHint())
          .isEqualTo("사이버캠퍼스");
    }

    @Test
    void detects_웹메일() {
      assertThat(service.extract(request("웹메일로 서류 발송")).actions().getFirst().systemHint())
          .isEqualTo("웹메일");
    }

    @Test
    void detects_우리WON() {
      assertThat(service.extract(request("우리WON에서 등록금 납부")).actions().getFirst().systemHint())
          .isEqualTo("우리WON");
    }

    @Test
    void detects_eClass() {
      assertThat(service.extract(request("e-class에 레포트 업로드")).actions().getFirst().systemHint())
          .isEqualTo("e-class");
    }

    @Test
    void detects_eCampus() {
      assertThat(service.extract(request("e-Campus에서 출석 확인")).actions().getFirst().systemHint())
          .isEqualTo("e-Campus");
    }

    @Test
    void detects_학생생활관() {
      assertThat(service.extract(request("학생생활관 입사 신청")).actions().getFirst().systemHint())
          .isEqualTo("학생생활관");
    }

    @Test
    void detects_국제교류원() {
      assertThat(service.extract(request("국제교류원에서 교환학생 신청")).actions().getFirst().systemHint())
          .isEqualTo("국제교류원");
    }

    @Test
    void detects_취업지원센터() {
      assertThat(service.extract(request("취업지원센터에서 상담 예약")).actions().getFirst().systemHint())
          .isEqualTo("취업지원센터");
    }

    @Test
    void detects_학과사무실() {
      assertThat(service.extract(request("학과사무실에 서류 제출")).actions().getFirst().systemHint())
          .isEqualTo("학과사무실");
    }

    @Test
    void detects_통합정보시스템() {
      assertThat(service.extract(request("통합정보시스템에서 성적 확인")).actions().getFirst().systemHint())
          .isEqualTo("통합정보시스템");
    }

    @Test
    void detects_도서관() {
      assertThat(service.extract(request("도서관에서 좌석 예약")).actions().getFirst().systemHint())
          .isEqualTo("도서관");
    }

    @Test
    void detects_포털() {
      assertThat(service.extract(request("포털에서 공지 확인")).actions().getFirst().systemHint())
          .isEqualTo("포털");
    }
  }

  @Nested
  class ComprehensiveRequiredItemTests {

    @Test
    void detects_증빙서류() {
      assertThat(service.extract(request("증빙서류를 제출하세요")).actions().getFirst().requiredItems())
          .contains("증빙서류");
    }

    @Test
    void detects_재학증명서() {
      assertThat(service.extract(request("재학증명서 첨부 후 제출")).actions().getFirst().requiredItems())
          .contains("재학증명서");
    }

    @Test
    void detects_학생증() {
      assertThat(service.extract(request("학생증 지참하여 참석")).actions().getFirst().requiredItems())
          .contains("학생증");
    }

    @Test
    void detects_통장사본() {
      assertThat(service.extract(request("통장사본 제출 필수")).actions().getFirst().requiredItems())
          .contains("통장사본");
    }

    @Test
    void detects_사유서() {
      assertThat(service.extract(request("사유서를 작성하여 제출")).actions().getFirst().requiredItems())
          .contains("사유서");
    }

    @Test
    void detects_등록금납입증명서() {
      assertThat(service.extract(request("등록금납입증명서 발급 후 제출")).actions().getFirst().requiredItems())
          .contains("등록금납입증명서");
    }

    @Test
    void detects_졸업증명서() {
      assertThat(service.extract(request("졸업증명서 첨부 필요")).actions().getFirst().requiredItems())
          .contains("졸업증명서");
    }

    @Test
    void detects_추천서() {
      assertThat(service.extract(request("교수 추천서를 제출하세요")).actions().getFirst().requiredItems())
          .contains("추천서");
    }

    @Test
    void detects_이력서() {
      assertThat(service.extract(request("이력서와 자기소개서 준비")).actions().getFirst().requiredItems())
          .contains("이력서");
    }

    @Test
    void detects_사진() {
      assertThat(service.extract(request("증명사진 제출 바랍니다")).actions().getFirst().requiredItems())
          .contains("사진");
    }

    @Test
    void detects_지원서() {
      assertThat(service.extract(request("지원서 작성 완료 후 제출")).actions().getFirst().requiredItems())
          .contains("지원서");
    }

    @Test
    void detects_계획서() {
      assertThat(service.extract(request("연구 계획서 제출 요망")).actions().getFirst().requiredItems())
          .contains("계획서");
    }

    @Test
    void detects_보고서() {
      assertThat(service.extract(request("활동 보고서 작성하여 제출")).actions().getFirst().requiredItems())
          .contains("보고서");
    }

    @Test
    void detects_반명함판() {
      assertThat(service.extract(request("반명함판 사진 제출")).actions().getFirst().requiredItems())
          .contains("반명함판");
    }

    @Test
    void detects_가족관계증명서() {
      assertThat(service.extract(request("가족관계증명서를 첨부하여 제출")).actions().getFirst().requiredItems())
          .contains("가족관계증명서");
    }
  }

  @Nested
  class ComprehensiveEligibilityTests {

    @Test
    void detects_지원자격() {
      assertThat(service.extract(request("지원자격은 재학생입니다")).actions().getFirst().eligibility())
          .contains("지원자격");
    }

    @Test
    void detects_참여자격() {
      assertThat(service.extract(request("참여자격: 2학년 이상")).actions().getFirst().eligibility())
          .contains("참여자격");
    }

    @Test
    void detects_대학원생() {
      assertThat(service.extract(request("대학원생도 신청 가능합니다")).actions().getFirst().eligibility())
          .contains("대학원생");
    }

    @Test
    void detects_해당_학과() {
      assertThat(service.extract(request("해당 학과 학생만 신청 가능")).actions().getFirst().eligibility())
          .contains("해당 학과");
    }

    @Test
    void detects_복학생() {
      assertThat(service.extract(request("복학생 대상 안내입니다")).actions().getFirst().eligibility())
          .contains("복학생");
    }

    @Test
    void detects_신입생() {
      assertThat(service.extract(request("신입생 오리엔테이션 안내")).actions().getFirst().eligibility())
          .contains("신입생");
    }

    @Test
    void detects_수료자() {
      assertThat(service.extract(request("수료자 대상 특별 안내")).actions().getFirst().eligibility())
          .contains("수료자");
    }

    @Test
    void detects_휴학생() {
      assertThat(service.extract(request("휴학생은 별도 절차 필요")).actions().getFirst().eligibility())
          .contains("휴학생");
    }

    @Test
    void detects_전공() {
      assertThat(service.extract(request("해당 전공 학생 신청 가능")).actions().getFirst().eligibility())
          .contains("전공");
    }
  }

  @Nested
  class ComprehensiveActionVerbTests {

    @Test
    void detects_완료() {
      assertThat(service.extract(request("수강신청을 완료하세요")).actions().getFirst().evidence().stream()
          .filter(e -> "actionVerb".equals(e.fieldName())).findFirst()).isPresent();
    }

    @Test
    void detects_등록() {
      assertThat(service.extract(request("프로그램 등록 바랍니다")).actions().getFirst().evidence().stream()
          .filter(e -> "actionVerb".equals(e.fieldName())).findFirst()).isPresent();
    }

    @Test
    void detects_납부() {
      assertThat(service.extract(request("등록금을 납부하세요")).actions().getFirst().evidence().stream()
          .filter(e -> "actionVerb".equals(e.fieldName())).findFirst()).isPresent();
    }

    @Test
    void detects_수강() {
      assertThat(service.extract(request("안전교육을 수강하세요")).actions().getFirst().evidence().stream()
          .filter(e -> "actionVerb".equals(e.fieldName())).findFirst()).isPresent();
    }

    @Test
    void detects_접수() {
      assertThat(service.extract(request("온라인으로 접수해주세요")).actions().getFirst().evidence().stream()
          .filter(e -> "actionVerb".equals(e.fieldName())).findFirst()).isPresent();
    }

    @Test
    void detects_지원() {
      assertThat(service.extract(request("장학금에 지원하세요")).actions().getFirst().evidence().stream()
          .filter(e -> "actionVerb".equals(e.fieldName())).findFirst()).isPresent();
    }

    @Test
    void detects_작성() {
      assertThat(service.extract(request("설문조사를 작성하세요")).actions().getFirst().evidence().stream()
          .filter(e -> "actionVerb".equals(e.fieldName())).findFirst()).isPresent();
    }

    @Test
    void detects_출석() {
      assertThat(service.extract(request("특강에 출석해주세요")).actions().getFirst().evidence().stream()
          .filter(e -> "actionVerb".equals(e.fieldName())).findFirst()).isPresent();
    }

    @Test
    void detects_발급() {
      assertThat(service.extract(request("증명서를 발급받으세요")).actions().getFirst().evidence().stream()
          .filter(e -> "actionVerb".equals(e.fieldName())).findFirst()).isPresent();
    }

    @Test
    void detects_예약() {
      assertThat(service.extract(request("상담 시간을 예약하세요")).actions().getFirst().evidence().stream()
          .filter(e -> "actionVerb".equals(e.fieldName())).findFirst()).isPresent();
    }

    @Test
    void detects_다운로드() {
      assertThat(service.extract(request("양식을 다운로드하세요")).actions().getFirst().evidence().stream()
          .filter(e -> "actionVerb".equals(e.fieldName())).findFirst()).isPresent();
    }
  }

  // --- Inferred Logic Tests ---

  @Nested
  class InferredLogicTests {

    @Test
    void confirmed_when_all_evidence_high_confidence() {
      // Absolute date with year (0.80+) + system hint (0.78) + action verb (0.76) → all >= 0.75
      ActionExtractionResponse response =
          service.extract(request("2026년 3월 12일까지 TRINITY에서 신청하세요"));
      assertThat(response.actions().getFirst().inferred()).isFalse();
    }

    @Test
    void inferred_when_relative_date_only() {
      // Relative date confidence 0.50 < 0.75
      ActionExtractionResponse response =
          service.extract(request("내일까지 제출하세요"));
      assertThat(response.actions().getFirst().inferred()).isTrue();
    }

    @Test
    void inferred_when_required_item_only() {
      // Required item confidence 0.72 < 0.75
      ActionExtractionResponse response =
          service.extract(request("성적증명서를 준비하세요"));
      assertThat(response.actions().getFirst().inferred()).isTrue();
    }

    @Test
    void inferred_when_no_evidence() {
      ActionExtractionResponse response =
          service.extract(request("학교 공지사항입니다"));
      assertThat(response.actions().getFirst().inferred()).isTrue();
    }
  }

  // --- End-to-End Tests ---

  @Nested
  class EndToEndTests {

    @Test
    void realistic_scholarship_notice() {
      String text = """
          [2026학년도 1학기 교내장학금 신청 안내]

          1. 신청 대상: 2학년 이상 재학생 (평균평점 3.5 이상)
          2. 신청 기간: 2026.3.1 ~ 2026.3.15 18:00
          3. 신청 방법: 장학포털에서 온라인 신청
          4. 제출 서류: 성적증명서, 자기소개서, 통장사본
          5. 문의: 학생복지팀 (02-XXX-XXXX)
          """;

      ActionExtractionResponse response =
          service.extract(request(text, "교내장학금 신청 안내"));

      ExtractedActionDto action = response.actions().getFirst();

      assertThat(action.systemHint()).isEqualTo("장학포털");
      assertThat(action.requiredItems())
          .containsExactlyInAnyOrder("성적증명서", "자기소개서", "통장사본");
      assertThat(action.eligibility()).contains("2학년 이상 재학생");

      List<String> evidenceFields = action.evidence().stream()
          .map(EvidenceSnippetDto::fieldName)
          .distinct()
          .toList();
      assertThat(evidenceFields).contains("systemHint", "requiredItems", "eligibility");

      assertThat(action.dueAtIso()).isNotNull();
    }
  }
}
