package com.cuk.notice2action.extraction.service.notice;

import com.cuk.notice2action.extraction.api.dto.ExtractedActionDto;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class NoticeActionabilityClassifier {

  private static final List<String> ACTION_TITLE_KEYWORDS = List.of(
      "신청", "취소", "학번조회", "조회", "수강신청",
      "학점인정", "부전공", "공결", "이수여부 확인",
      "수요조사", "조기졸업", "모집", "요청"
  );

  private static final List<String> INFORMATIONAL_TITLE_KEYWORDS = List.of(
      "강의변경", "변경사항 안내", "강의시간표", "수업운영 안내",
      "개강미사", "입학미사", "학점이월제도", "예비 졸업사정 일정",
      "출결 사항 안내", "폐강 포함"
  );
  private static final List<String> CONDITIONAL_INFORMATIONAL_ACTION_TITLES = List.of(
      "출결 사항 안내"
  );

  private static final List<String> STRONG_SUMMARY_KEYWORDS = List.of(
      "신청", "제출", "등록", "조회", "취소", "확인", "모집", "요청"
  );

  private static final List<String> ACTION_BODY_KEYWORDS = List.of(
      "신청기간", "신청 방법", "신청방법", "바로가기", "회원가입",
      "기간 내 신청", "완료하시기 바랍니다", "제출합니다", "제출하여", "송부"
  );
  private static final List<String> ATTACHMENT_FORM_KEYWORDS = List.of(
      "신청서", "허가원", "동의서", "확인서"
  );

  public String classify(String title, String body, List<ExtractedActionDto> actions) {
    String normalizedTitle = normalize(title);
    String normalizedBody = normalize(body);

    boolean informationalTitle = containsAny(normalizedTitle, INFORMATIONAL_TITLE_KEYWORDS);
    boolean actionableTitle = containsAny(normalizedTitle, ACTION_TITLE_KEYWORDS);
    boolean imageOnlyBody = normalizedBody.contains("본문이 이미지로만 제공된 공지입니다.");
    boolean attachmentFormSignals = hasAttachmentFormSignals(normalizedTitle, normalizedBody);
    boolean strongActionSignals = hasStrongActionSignals(actions, normalizedBody);

    if (imageOnlyBody) {
      if (attachmentFormSignals || hasEvidenceBackedSignals(actions)) {
        return "action_required";
      }
      return "informational";
    }

    if (informationalTitle && !actionableTitle) {
      if (attachmentFormSignals) {
        return "action_required";
      }
      return "informational";
    }
    if (actionableTitle) {
      return "action_required";
    }
    return strongActionSignals ? "action_required" : "informational";
  }

  private boolean hasAttachmentFormSignals(String normalizedTitle, String normalizedBody) {
    if (!normalizedBody.contains("첨부파일:")) {
      return false;
    }
    if (!containsAny(normalizedTitle, CONDITIONAL_INFORMATIONAL_ACTION_TITLES)) {
      return false;
    }
    return containsAny(normalizedBody, ATTACHMENT_FORM_KEYWORDS);
  }

  private boolean hasStrongActionSignals(List<ExtractedActionDto> actions, String normalizedBody) {
    if (actions == null || actions.isEmpty()) {
      return false;
    }
    for (ExtractedActionDto action : actions) {
      if (action.confidenceScore() < 0.65) {
        continue;
      }
      if (action.dueAtIso() != null || action.systemHint() != null || !action.requiredItems().isEmpty()) {
        return true;
      }
      String summary = normalize(action.actionSummary());
      if (containsAny(summary, STRONG_SUMMARY_KEYWORDS) && containsAny(normalizedBody, ACTION_BODY_KEYWORDS)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasEvidenceBackedSignals(List<ExtractedActionDto> actions) {
    if (actions == null || actions.isEmpty()) {
      return false;
    }
    for (ExtractedActionDto action : actions) {
      if (action.dueAtIso() != null || action.dueAtLabel() != null || action.systemHint() != null || !action.requiredItems().isEmpty()) {
        return true;
      }
      if (action.evidence() != null && action.evidence().stream().anyMatch(evidence -> evidence.snippet() != null && !evidence.snippet().isBlank())) {
        return true;
      }
    }
    return false;
  }

  private boolean containsAny(String value, List<String> keywords) {
    return keywords.stream().anyMatch(value::contains);
  }

  private String normalize(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }
}
