package com.cuk.notice2action.extraction.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cuk.notice2action.common.api.GlobalExceptionHandler;
import com.cuk.notice2action.extraction.api.dto.NoticeActionBlockDto;
import com.cuk.notice2action.extraction.api.dto.NoticeAttachmentDto;
import com.cuk.notice2action.extraction.api.dto.NoticeDueHintDto;
import com.cuk.notice2action.extraction.api.dto.NoticeFeedResponse;
import com.cuk.notice2action.extraction.api.dto.PersonalizedNoticeDetailDto;
import com.cuk.notice2action.extraction.api.dto.PersonalizedNoticeSummaryDto;
import com.cuk.notice2action.extraction.service.notice.NoticeFeedService;
import com.cuk.notice2action.extraction.service.notice.NoticeProfile;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = NoticeFeedController.class)
@Import(GlobalExceptionHandler.class)
class NoticeFeedControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private NoticeFeedService noticeFeedService;

  @Test
  void returnsPersonalizedNoticeFeed() throws Exception {
    when(noticeFeedService.getFeed(eq(new NoticeProfile("컴퓨터공학과", 1, "신입생", List.of("학생증"))), eq(0), eq(20)))
        .thenReturn(new NoticeFeedResponse(
            List.of(new PersonalizedNoticeSummaryDto(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "학생증 신청 안내",
                "2026-02-27T00:00:00+09:00",
                "https://example.com/notice/268986",
                List.of("신입생 해당", "학생증 키워드"),
                "action_required",
                new NoticeDueHintDto("2026-03-05T23:59:59+09:00", "3월 5일까지"),
                105
            )),
            0,
            20,
            1,
            1,
            false
        ));

    mockMvc.perform(get("/api/v1/notices/feed")
            .param("department", "컴퓨터공학과")
            .param("year", "1")
            .param("status", "신입생")
            .param("keyword", "학생증"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.notices[0].title").value("학생증 신청 안내"))
        .andExpect(jsonPath("$.notices[0].importanceReasons[0]").value("신입생 해당"))
        .andExpect(jsonPath("$.notices[0].actionability").value("action_required"));
  }

  @Test
  void returnsNoticeDetail() throws Exception {
    UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    when(noticeFeedService.getDetail(eq(id), eq(new NoticeProfile(null, null, null, List.of()))))
        .thenReturn(new PersonalizedNoticeDetailDto(
            id,
            "학생증 신청 안내",
            "2026-02-27T00:00:00+09:00",
            "https://example.com/notice/268986",
            List.of("행동 필요 공지", "7일 이내 마감"),
            "action_required",
            new NoticeDueHintDto("2026-03-05T23:59:59+09:00", "3월 5일까지"),
            50,
            "정제된 원문",
            List.of(new NoticeAttachmentDto("학생증 발급 신청서.hwp", "https://example.com/download/1")),
            List.of(new NoticeActionBlockDto("학생증 신청", "요약", null, null, List.of(), "TRINITY", List.of(), 0.91))
        ));

    mockMvc.perform(get("/api/v1/notices/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.body").value("정제된 원문"))
        .andExpect(jsonPath("$.attachments[0].name").value("학생증 발급 신청서.hwp"))
        .andExpect(jsonPath("$.actionBlocks[0].title").value("학생증 신청"));
  }
}
