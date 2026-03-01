package com.cuk.notice2action.extraction.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GlobalExceptionHandlerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void notFound_returns_404_with_safe_message() throws Exception {
    String unknownId = "00000000-0000-0000-0000-000000000000";

    mockMvc.perform(get("/api/v1/actions/" + unknownId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("not_found"))
        .andExpect(jsonPath("$.message").exists());
  }

  @Test
  void invalidCategory_returns_400() throws Exception {
    mockMvc.perform(get("/api/v1/actions")
            .param("category", "INVALID_CATEGORY"))
        .andExpect(status().isBadRequest());
  }
}
