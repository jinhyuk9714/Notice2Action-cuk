package com.cuk.notice2action.extraction.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cuk.notice2action.extraction.api.dto.ActionExtractionRequest;
import com.cuk.notice2action.extraction.api.dto.ActionExtractionResponse;
import com.cuk.notice2action.extraction.api.dto.ActionListResponse;
import com.cuk.notice2action.extraction.api.dto.ActionSearchCriteria;
import com.cuk.notice2action.extraction.api.dto.ActionUpdateRequest;
import com.cuk.notice2action.extraction.domain.SourceCategory;
import com.cuk.notice2action.extraction.service.ActionExtractionService;
import com.cuk.notice2action.extraction.service.ActionPersistenceService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ActionListQueryParamTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ActionExtractionService extractionService;

  @Autowired
  private ActionPersistenceService persistenceService;

  private UUID persistSample(String text, String title) {
    ActionExtractionRequest request = new ActionExtractionRequest(
        text, null, title, SourceCategory.NOTICE, List.of()
    );
    ActionExtractionResponse extracted = extractionService.extract(request);
    return persistenceService.persistExtraction(request, extracted).actions().getFirst().id();
  }

  @Test
  void listActions_filters_by_date_range() {
    persistSample("2026년 3월 12일까지 신청하세요.", "3월 액션");
    persistSample("2026년 4월 3일까지 제출하세요.", "4월 액션");

    OffsetDateTime from = OffsetDateTime.of(2026, 3, 1, 0, 0, 0, 0, ZoneOffset.ofHours(9));
    OffsetDateTime to = OffsetDateTime.of(2026, 3, 31, 23, 59, 59, 0, ZoneOffset.ofHours(9));
    ActionSearchCriteria criteria = new ActionSearchCriteria(null, null, from, to, "due");
    ActionListResponse result = persistenceService.listActions(criteria, 0, 20);

    assertThat(result.actions()).hasSize(1);
    assertThat(result.actions().getFirst().dueAtIso()).startsWith("2026-03-12T00:00");
  }

  @Test
  void listActions_rejects_malformed_date_param() throws Exception {
    mockMvc.perform(get("/api/v1/actions")
            .param("dueDateFrom", "2026-13-40"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("bad_request"))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("dueDateFrom")));
  }

  @Test
  void listActions_due_sort_with_filters_keeps_null_due_last() {
    persistSample("정렬테스트 2026년 3월 12일까지 신청하세요.", "정렬테스트-마감");
    persistSample("정렬테스트 안내문입니다.", "정렬테스트-무마감");

    ActionSearchCriteria criteria = new ActionSearchCriteria("정렬테스트", null, null, null, "due");
    ActionListResponse result = persistenceService.listActions(criteria, 0, 20);

    assertThat(result.actions()).hasSize(2);
    assertThat(result.actions().get(0).dueAtIso()).isNotNull();
    assertThat(result.actions().get(1).dueAtIso()).isNull();
  }

  @Test
  void listActions_filters_by_status() {
    persistSample("2026년 3월 12일까지 신청하세요.", "진행중 액션");
    UUID completedId = persistSample("2026년 3월 20일까지 제출하세요.", "완료 액션");

    persistenceService.updateAction(
        completedId,
        new ActionUpdateRequest(null, null, null, null, null, null, null, null, "completed")
    );

    ActionListResponse result = persistenceService.listActions(
        new ActionSearchCriteria(null, null, null, null, "recent", "completed"),
        0,
        20
    );

    assertThat(result.actions()).hasSize(1);
    assertThat(result.actions().getFirst().id()).isEqualTo(completedId);
    assertThat(result.actions().getFirst().status()).isEqualTo("completed");
  }

  @Test
  void exportCalendar_filters_by_status() throws Exception {
    persistSample("2026년 3월 12일까지 신청하세요.", "달력 진행중");
    UUID completedId = persistSample("2026년 3월 20일까지 제출하세요.", "달력 완료");

    persistenceService.updateAction(
        completedId,
        new ActionUpdateRequest(null, null, null, null, null, null, null, null, "completed")
    );

    String ics = mockMvc.perform(get("/api/v1/actions/calendar.ics")
            .param("status", "completed"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();

    assertThat(ics).contains("DTSTART:20260319T150000Z");
    assertThat(ics).doesNotContain("DTSTART:20260311T150000Z");
    assertThat(ics.split("BEGIN:VEVENT")).hasSize(2);
  }
}
