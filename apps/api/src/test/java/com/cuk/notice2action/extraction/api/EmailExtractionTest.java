package com.cuk.notice2action.extraction.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmailExtractionTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void extractEmail_success() throws Exception {
    String body = """
        {
          "emailBody": "2026년 3월 15일까지 TRINITY에서 수강정정을 완료해야 합니다. 대상: 재학생",
          "subject": "수강정정 안내",
          "senderAddress": "admin@catholic.ac.kr"
        }
        """;

    mockMvc.perform(post("/api/v1/extractions/email")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.actions").isArray())
        .andExpect(jsonPath("$.actions").isNotEmpty());
  }

  @Test
  void extractEmail_success_without_subject() throws Exception {
    String body = """
        {
          "emailBody": "2026년 3월 20일까지 장학포털에서 장학금 신청을 완료하세요."
        }
        """;

    mockMvc.perform(post("/api/v1/extractions/email")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.actions").isArray());
  }

  @Test
  void extractEmail_rejects_blank_body() throws Exception {
    String body = """
        {
          "emailBody": "   ",
          "subject": "테스트"
        }
        """;

    mockMvc.perform(post("/api/v1/extractions/email")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void extractEmail_rejects_missing_body() throws Exception {
    String body = """
        {
          "subject": "테스트"
        }
        """;

    mockMvc.perform(post("/api/v1/extractions/email")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest());
  }
}
