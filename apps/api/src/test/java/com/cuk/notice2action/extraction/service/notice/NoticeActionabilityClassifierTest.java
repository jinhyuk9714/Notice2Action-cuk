package com.cuk.notice2action.extraction.service.notice;

import static org.assertj.core.api.Assertions.assertThat;

import com.cuk.notice2action.extraction.api.dto.ExtractedActionDto;
import com.cuk.notice2action.extraction.domain.SourceCategory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoticeActionabilityClassifierTest {

  private NoticeActionabilityClassifier classifier;

  @BeforeEach
  void setUp() {
    classifier = new NoticeActionabilityClassifier();
  }

  @Test
  void classifiesLectureChangeNoticeAsInformationalEvenWhenExtractorFindsCheckVerb() {
    String result = classifier.classify(
        "[학사지원팀] 2026-1학기 강의시간표 등 변경사항 안내(전공강좌) / 일별 업데이트(2026.03.05.) / 폐강 포함",
        "강의시간표 등 수업 관련 변경사항을 안내하오니 확인하시기 바랍니다.",
        List.of(action("확인", null, null, List.of(), 0.76))
    );

    assertThat(result).isEqualTo("informational");
  }

  @Test
  void classifiesPolicyGuideAsInformationalWhenOnlyGenericApplicationVerbIsDetected() {
    String result = classifier.classify(
        "[학사지원팀] 2026학년도 학점이월제도 안내",
        "학생 여러분들께서는 확인하여, 수강신청 및 학점이수 등 학사관리 업무에 참고하시기 바랍니다.",
        List.of(action("신청", null, null, List.of(), 0.75))
    );

    assertThat(result).isEqualTo("informational");
  }

  @Test
  void classifiesFreshmanEntranceMassAsInformationalDespiteAttendanceDate() {
    String result = classifier.classify(
        "2026학년도 신입생 입학미사 안내",
        "신입생 여러분의 입학을 축하드리며 입학미사를 안내해 드리오니 참석하여 주시기 바랍니다.",
        List.of(action("참석", "2026년 3월 3일(화) 오후 3시", null, List.of(), 0.83))
    );

    assertThat(result).isEqualTo("informational");
  }

  @Test
  void classifiesImageOnlyMinorApplicationNoticeAsInformationalWithoutEvidence() {
    String result = classifier.classify(
        "[2~4학년] 2026학년도 1학기 부전공(2차) 신청/변경 안내",
        "본문이 이미지로만 제공된 공지입니다.",
        List.of()
    );

    assertThat(result).isEqualTo("informational");
  }

  @Test
  void classifiesImageOnlyActionTitleWithGenericAttachmentAsInformational() {
    String result = classifier.classify(
        "[2~4학년] 2026학년도 1학기 부전공(2차) 신청/변경 안내",
        """
        본문이 이미지로만 제공된 공지입니다.
        첨부파일: 부전공 안내문.pdf
        """,
        List.of()
    );

    assertThat(result).isEqualTo("informational");
  }

  @Test
  void classifiesImageOnlyActionTitleWithFormAttachmentAsActionRequired() {
    String result = classifier.classify(
        "[2~4학년] 2026학년도 1학기 부전공(2차) 신청/변경 안내",
        """
        본문이 이미지로만 제공된 공지입니다.
        첨부파일: 부전공 신청서.hwp, 부전공 변경 신청서.hwp
        """,
        List.of()
    );

    assertThat(result).isEqualTo("action_required");
  }

  @Test
  void classifiesImageOnlyActionTitleAsActionRequiredWhenDeterministicEvidenceExists() {
    String result = classifier.classify(
        "2026학년도 신·편입생(등록완료자) 학번조회 안내",
        "본문이 이미지로만 제공된 공지입니다.",
        List.of(action("학번조회", null, "TRINITY", List.of(), 0.82))
    );

    assertThat(result).isEqualTo("action_required");
  }

  @Test
  void classifiesDropPeriodNoticeAsActionRequiredFromBodyAndDueDate() {
    String result = classifier.classify(
        "[학사지원팀] 2026-1학기 수강과목 취소 기간 안내",
        "신청이 필요한 학생은 기간 내 신청을 완료하시기 바랍니다.",
        List.of(action("수강취소 신청", "2026. 3. 24.(화) 09:00 ~ 3. 25.(수) 17:00", "TRINITY", List.of(), 0.82))
    );

    assertThat(result).isEqualTo("action_required");
  }

  @Test
  void classifiesStudentNumberLookupAsActionRequiredFromTitleAndLinks() {
    String result = classifier.classify(
        "2026학년도 신·편입생(등록완료자) 학번조회 안내",
        "학번조회 바로가기, 트리니티 회원가입 바로가기",
        List.of(action("학번조회", null, null, List.of(), 0.55))
    );

    assertThat(result).isEqualTo("action_required");
  }

  @Test
  void classifiesEmploymentAttendanceNoticeAsActionRequiredWhenFormsAreAttached() {
    String result = classifier.classify(
        "[학사지원팀] 2026학년도 1학기 학기 중 취업학생 출결 사항 안내",
        """
        본문이 이미지로만 제공된 공지입니다.
        첨부파일: 1. 공결허가원(취업).hwp, 2. 개인정보 수집활용 동의서(재직조회).hwp,
        3. 취업공결 확인서(학기 중 취업학생).hwp, 4. 예비졸업사정 확인서.hwp
        """,
        List.of()
    );

    assertThat(result).isEqualTo("action_required");
  }

  private ExtractedActionDto action(
      String summary,
      String dueAtLabel,
      String systemHint,
      List<String> requiredItems,
      double confidence
  ) {
    return new ExtractedActionDto(
        null,
        null,
        summary,
        "[" + summary + "]",
        dueAtLabel == null ? null : "2026-03-25T17:00:00+09:00",
        dueAtLabel,
        null,
        requiredItems,
        systemHint,
        SourceCategory.NOTICE,
        List.of(),
        false,
        confidence,
        null
    );
  }
}
