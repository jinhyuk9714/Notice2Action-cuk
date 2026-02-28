package com.cuk.notice2action.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cuk.notice2action.extraction.api.dto.ActionExtractionRequest;
import com.cuk.notice2action.extraction.api.dto.ActionExtractionResponse;
import com.cuk.notice2action.extraction.api.dto.EvidenceSnippetDto;
import com.cuk.notice2action.extraction.api.dto.ExtractedActionDto;
import com.cuk.notice2action.extraction.domain.SourceCategory;
import java.time.Year;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HeuristicActionExtractionServiceTest {

  private final HeuristicActionExtractionService service = new HeuristicActionExtractionService();

  private ActionExtractionRequest request(String text) {
    return new ActionExtractionRequest(text, null, null, SourceCategory.NOTICE, List.of());
  }

  private ActionExtractionRequest request(String text, String title) {
    return new ActionExtractionRequest(text, null, title, SourceCategory.NOTICE, List.of());
  }

  private String currentYearPrefix() {
    return String.valueOf(Year.now().getValue());
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

      assertThat(response.actions().getFirst().actionSummary()).startsWith("[신청]");
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
    void multi_action_titles_include_counter() {
      String text = """
          3월 12일까지 서류 제출하세요.
          3월 15일까지 면접 참석하세요.
          """;
      ActionExtractionResponse response = service.extract(request(text, "장학금"));

      assertThat(response.actions().get(0).title()).contains("(1/2)");
      assertThat(response.actions().get(1).title()).contains("(2/2)");
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
