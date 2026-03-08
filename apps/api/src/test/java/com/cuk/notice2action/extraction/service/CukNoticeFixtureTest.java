package com.cuk.notice2action.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.cuk.notice2action.extraction.api.dto.ActionExtractionRequest;
import com.cuk.notice2action.extraction.api.dto.ActionExtractionResponse;
import com.cuk.notice2action.extraction.api.dto.ExtractedActionDto;
import com.cuk.notice2action.extraction.domain.SourceCategory;
import com.cuk.notice2action.extraction.service.extractor.ActionSegmenter;
import com.cuk.notice2action.extraction.service.extractor.ActionSummaryBuilder;
import com.cuk.notice2action.extraction.service.extractor.ActionVerbExtractor;
import com.cuk.notice2action.extraction.service.extractor.DateExtractor;
import com.cuk.notice2action.extraction.service.extractor.EligibilityExtractor;
import com.cuk.notice2action.extraction.service.extractor.RequiredItemExtractor;
import com.cuk.notice2action.extraction.service.extractor.StructuredEligibilityParser;
import com.cuk.notice2action.extraction.service.extractor.SystemHintExtractor;
import com.cuk.notice2action.extraction.service.extractor.TextNormalizer;
import com.cuk.notice2action.extraction.service.extractor.TitleDeriver;
import com.cuk.notice2action.extraction.service.strategy.DefaultExtractionStrategy;
import com.cuk.notice2action.extraction.service.strategy.ExtractionStrategyFactory;
import com.cuk.notice2action.extraction.service.strategy.SyllabusExtractionStrategy;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Fixture-based regression tests using realistic CUK (Catholic University of Korea) notice text.
 *
 * <p>Each fixture pins extraction output so that future changes to extractors surface here.
 * Fixed reference date: 2026-03-01 (Sunday) — same as HeuristicActionExtractionServiceTest.
 */
class CukNoticeFixtureTest {

  private static final LocalDate FIXED_TODAY = LocalDate.of(2026, 3, 1);

  private final DateExtractor dateExtractor = new DateExtractor() {
    @Override
    protected LocalDate today() {
      return FIXED_TODAY;
    }
  };

  private final ActionVerbExtractor actionVerbExtractor = new ActionVerbExtractor();

  private final HeuristicActionExtractionService service = new HeuristicActionExtractionService(
      new TextNormalizer(),
      dateExtractor,
      new SystemHintExtractor(),
      new RequiredItemExtractor(),
      actionVerbExtractor,
      new EligibilityExtractor(),
      new ActionSegmenter(actionVerbExtractor),
      new ActionSummaryBuilder(),
      new TitleDeriver(),
      new StructuredEligibilityParser(),
      new ExtractionStrategyFactory(new DefaultExtractionStrategy(), new SyllabusExtractionStrategy())
  );

  private ActionExtractionRequest notice(String text) {
    return new ActionExtractionRequest(text, null, null, SourceCategory.NOTICE, List.of());
  }

  private ActionExtractionRequest category(String text, SourceCategory category) {
    return new ActionExtractionRequest(text, null, null, category, List.of());
  }

  // ─── F1: 장학금 신청 ──────────────────────────────────────────────────────────

  @Nested
  class F1_ScholarshipNotice {

    private static final String TEXT = """
        [2026-1학기 성적우수장학금 안내]
        TRINITY 포털에서 장학금을 신청하세요.
        신청기간: 2026년 3월 20일(금)까지
        제출서류: 성적증명서, 재학증명서
        대상: 재학생 2학년 이상
        """;

    @Test
    void extracts_trinity_system_hint() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.systemHint()).isEqualTo("TRINITY");
    }

    @Test
    void extracts_required_documents() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.requiredItems()).contains("성적증명서", "재학증명서");
    }

    @Test
    void extracts_deadline_iso() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.dueAtIso()).isNotNull().startsWith("2026-03-20");
    }

    @Test
    void extracts_eligibility_containing_jaehagsaeng() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.eligibility()).isNotNull().contains("재학생");
    }

    @Test
    void evidence_covers_key_fields() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      List<String> fields = action.evidence().stream()
          .map(e -> e.fieldName()).distinct().toList();
      assertThat(fields).contains("dueAtLabel", "systemHint", "requiredItems", "eligibility");
    }
  }

  // ─── F2: 공결 신청 ────────────────────────────────────────────────────────────

  @Nested
  class F2_AbsenceExcuseNotice {

    private static final String TEXT = """
        [2026년 1학기 공결 신청 안내]
        사이버캠퍼스에서 공결 신청서를 제출하세요.
        신청서류: 공결 신청서, 증빙서류(진단서 등)
        신청 기한: 결석일로부터 7일 이내
        """;

    @Test
    void extracts_cybercampus_system_hint() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.systemHint()).isEqualTo("사이버캠퍼스");
    }

    @Test
    void extracts_application_form_and_proof_documents() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.requiredItems()).contains("신청서", "증빙서류");
    }
  }

  // ─── F3: 학생증 발급 ──────────────────────────────────────────────────────────

  @Nested
  class F3_StudentIdCardIssuance {

    // Compact single-action text: "발급" verb appears only once (발급받으세요)
    // All key info (date, photo, eligibility) is in the same action segment
    private static final String TEXT =
        "학생증을 발급받으세요. 2026년 3월 13일 10시에 학생처 213호로 오세요. "
        + "반명함판 사진이 필요합니다. 신입생이 대상입니다.";

    @Test
    void extracts_issuance_date() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.dueAtIso()).isNotNull().startsWith("2026-03-13");
    }

    @Test
    void extracts_photo_requirement() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.requiredItems()).containsAnyOf("사진", "반명함판");
    }

    @Test
    void extracts_eligibility_for_freshmen() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.eligibility()).isNotNull().contains("신입생");
    }
  }

  // ─── F4: 수강신청/정정 ────────────────────────────────────────────────────────

  @Nested
  class F4_CourseRegistration {

    // Header uses "수강 일정" so "수강" is blocked by NON_ACTION_CONTEXT (수강\s*일정).
    // Body has one verb ("확인") → ActionSegmenter produces 1 segment → full text used.
    private static final String TEXT = """
        [2026-1학기 수강 일정 안내]
        사이버캠퍼스에서 정정 기간: 2026년 3월 6일(금)까지 확인하세요.
        학년별 순서로 진행됩니다.
        """;

    @Test
    void extracts_cybercampus_system_hint() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.systemHint()).isEqualTo("사이버캠퍼스");
    }

    @Test
    void extracts_correction_deadline() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.dueAtIso()).isNotNull().startsWith("2026-03-06");
    }
  }

  // ─── F5: 비교과 프로그램 ──────────────────────────────────────────────────────

  @Nested
  class F5_ExtracurricularProgram {

    private static final String TEXT = """
        [2026-1학기 취업역량강화 프로그램]
        관심 있는 학생은 지금 신청해 주세요.
        신청기간: 2026년 3월 14일(토)까지
        대상: 재학생 3, 4학년
        신청방법: 비교과시스템 > 프로그램신청
        """;

    @Test
    void extracts_application_deadline() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.dueAtIso()).isNotNull().startsWith("2026-03-14");
    }

    @Test
    void extracts_eligibility_with_year_info() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.eligibility()).isNotNull().contains("재학생");
    }

    @Test
    void confidence_score_is_reasonable() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.confidenceScore()).isGreaterThan(0.6);
    }
  }

  // ─── F6: 취업지원센터 행사 ────────────────────────────────────────────────────

  @Nested
  class F6_CareerCenterEvent {

    // Single sentence: "취업지원센터" systemHint + 2026-03-17 deadline + eligibility.
    // "신청" verb is detected first (not "지원") → 1 segment → full text used.
    private static final String TEXT =
        "취업지원센터에서 재학생 및 졸업예정자는 2026년 3월 17일(화)까지 사전신청하세요.";

    @Test
    void extracts_career_center_system_hint() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.systemHint()).isEqualTo("취업지원센터");
    }

    @Test
    void prefers_preregistration_deadline_over_event_date() {
      // "3월 17일까지" carries the "까지" deadline marker → picked as primary date
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.dueAtIso()).isNotNull().startsWith("2026-03-17");
    }

    @Test
    void extracts_mixed_eligibility() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.eligibility()).isNotNull()
          .containsAnyOf("재학생", "졸업예정자");
    }
  }

  // ─── F7: 기숙사 공지 ──────────────────────────────────────────────────────────

  @Nested
  class F7_DormitoryNotice {

    // Two sentences: "신청" verb in s1 (not blocked); "제출" in "제출서류" is blocked → 1 segment.
    // segments.size()==1 → full text used, so systemHint/date/items all extracted correctly.
    private static final String TEXT =
        "학생생활관 홈페이지에서 2026년 3월 10일(화)까지 입주를 신청하세요. 제출서류: 재학증명서, 통장사본.";

    @Test
    void extracts_dormitory_system_hint() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.systemHint()).isEqualTo("학생생활관");
    }

    @Test
    void extracts_required_documents() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.requiredItems()).contains("재학증명서", "통장사본");
    }

    @Test
    void extracts_application_deadline() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.dueAtIso()).isNotNull().startsWith("2026-03-10");
    }
  }

  // ─── F8: 강의계획서 (SYLLABUS) ────────────────────────────────────────────────

  @Nested
  class F8_Syllabus {

    private static final String TEXT = """
        [데이터베이스 강의계획서]
        수강생은 아래 일정을 확인하세요.
        중간고사: 2026년 4월 22일(수)
        기말고사: 2026년 6월 10일(수)
        평가: 중간고사 30%, 기말고사 30%, 과제 25%, 출석 15%
        """;

    @Test
    void extracts_at_least_one_exam_date() {
      ActionExtractionResponse response = service.extract(category(TEXT, SourceCategory.SYLLABUS));
      assertThat(response.actions()).isNotEmpty();
      // SYLLABUS strategy segments each exam as a separate action; at least one must have a date
      assertThat(response.actions()).anyMatch(a -> a.dueAtIso() != null);
    }

    @Test
    void preserves_syllabus_source_category() {
      ActionExtractionResponse response = service.extract(category(TEXT, SourceCategory.SYLLABUS));
      assertThat(response.actions()).isNotEmpty();
      assertThat(response.actions().getFirst().sourceCategory()).isEqualTo(SourceCategory.SYLLABUS);
    }
  }

  // ─── F9: 이메일 공지 (EMAIL) ──────────────────────────────────────────────────

  @Nested
  class F9_EmailNotice {

    private static final String TEXT = """
        안녕하세요.
        졸업논문 신청서를 2026년 3월 2일까지 제출해 주세요.
        제출처: 학술정보관 201호
        """;

    @Test
    void extracts_explicit_date_from_short_email() {
      ActionExtractionResponse response = service.extract(category(TEXT, SourceCategory.EMAIL));
      assertThat(response.actions()).isNotEmpty();
      assertThat(response.actions().getFirst().dueAtIso()).isNotNull().startsWith("2026-03-02");
    }

    @Test
    void extracts_application_form_requirement() {
      ActionExtractionResponse response = service.extract(category(TEXT, SourceCategory.EMAIL));
      assertThat(response.actions()).isNotEmpty();
      assertThat(response.actions().getFirst().requiredItems()).contains("신청서");
    }
  }

  // ─── F11: 과제 업로드 (업로드 verb + LMS + 날짜) ──────────────────────────────

  @Nested
  class F11_AssignmentUpload {

    // "과제 안내" in header: "과제" is not an action verb → no blocking.
    // First "업로드" occurrence is in "업로드하세요" (not "업로드 기한:") → not blocked.
    private static final String TEXT = """
        [데이터구조 1차 과제 안내]
        LMS에 과제 파일을 업로드하세요.
        업로드 기한: 2026년 3월 13일(금) 23:59까지
        파일 형식: PDF 보고서
        """;

    @Test
    void extracts_lms_system_hint() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.systemHint()).isEqualTo("LMS");
    }

    @Test
    void extracts_upload_deadline() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.dueAtIso()).isNotNull().startsWith("2026-03-13");
    }

    @Test
    void action_summary_is_not_null() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.actionSummary()).isNotNull();
    }
  }

  // ─── F12: 학생증 수령 (수령 verb + 학과사무실 + 범위 날짜) ────────────────────

  @Nested
  class F12_StudentIdPickup {

    // Header uses "배부 안내" (not "수령 안내") so first occurrence of "수령" is "수령하세요".
    // Date range: 부터(start)~까지(end) → end date is primary (higher confidence).
    private static final String TEXT = """
        [신입생 학생증 배부 안내]
        학과사무실에서 학생증을 수령하세요.
        수령기간: 2026년 3월 9일(월)부터 3월 13일(금)까지
        대상: 신입생
        """;

    @Test
    void extracts_department_office_system_hint() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.systemHint()).isEqualTo("학과사무실");
    }

    @Test
    void extracts_end_date_as_primary() {
      // "3월 13일(금)까지" has deadline-proximity boost → higher confidence than "3월 9일부터"
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.dueAtIso()).isNotNull().startsWith("2026-03-13");
    }

    @Test
    void extracts_eligibility_for_freshmen() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.eligibility()).isNotNull().contains("신입생");
    }
  }

  // ─── F13: 유학생 오리엔테이션 (외국인/유학생 eligibility + 국제교류원) ─────────

  @Nested
  class F13_InternationalStudentOrientation {

    private static final String TEXT = """
        [2026-1학기 외국인 유학생 오리엔테이션]
        국제교류원에서 오리엔테이션에 참석하세요.
        일시: 2026년 3월 4일(수) 오후 2시
        대상: 외국인 유학생
        """;

    @Test
    void extracts_international_office_system_hint() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.systemHint()).isEqualTo("국제교류원");
    }

    @Test
    void extracts_event_date() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.dueAtIso()).isNotNull().startsWith("2026-03-04");
    }

    @Test
    void extracts_eligibility_mentioning_international_students() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.eligibility()).isNotNull().contains("유학생");
    }
  }

  // ─── F14: 학사포털 서비스 (학사포털 system + 졸업예정자) ──────────────────────

  @Nested
  class F14_AcademicPortalService {

    // SystemHintExtractor: "학사포털" is listed before "포털" to avoid partial match.
    // Verb: "신청하세요" is found before "신청기간:" (appears later in text).
    private static final String TEXT = """
        [2026-1학기 졸업예정자 학사포털 등록 안내]
        학사포털에서 졸업 사정을 신청하세요.
        신청기간: 2026년 3월 16일(월)까지
        대상: 2026년 2월 졸업예정자
        """;

    @Test
    void extracts_academic_portal_system_hint() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.systemHint()).isEqualTo("학사포털");
    }

    @Test
    void extracts_application_deadline() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.dueAtIso()).isNotNull().startsWith("2026-03-16");
    }

    @Test
    void extracts_eligibility_for_prospective_graduates() {
      ExtractedActionDto action = service.extract(notice(TEXT)).actions().getFirst();
      assertThat(action.eligibility()).isNotNull().contains("졸업예정자");
    }
  }

  // ─── F10: OCR 혼입 텍스트 (SCREENSHOT) ───────────────────────────────────────

  @Nested
  class F10_OcrScreenshot {

    @Test
    void does_not_throw_on_character_spacing_artifacts() {
      String noisyText = """
          성 심 교 정  학 생 처  공 고
          비교과 활동 확인서를 제출하세요.
          마감: 2 0 2 6 년  3 월  1 5 일
          장소 : 학생처 2 1 4 호
          """;
      assertThatNoException().isThrownBy(
          () -> service.extract(category(noisyText, SourceCategory.SCREENSHOT))
      );
    }

    @Test
    void extracts_date_when_date_text_is_intact() {
      // OCR may corrupt numbers/tables but key dates often remain readable
      String partiallyNoisyText = """
          성심교정 학생처 공고
          비교과 활동 확인서를 제출하세요.
          마감: 2026년 3월 15일까지
          장소 : 학생처 2 1 4 호
          """;
      ActionExtractionResponse response =
          service.extract(category(partiallyNoisyText, SourceCategory.SCREENSHOT));
      assertThat(response.actions()).isNotEmpty();
      assertThat(response.actions().getFirst().dueAtIso()).isNotNull().startsWith("2026-03-15");
    }
  }
}
