package com.cuk.notice2action.extraction.service;

import com.cuk.notice2action.extraction.api.dto.ActionExtractionRequest;
import com.cuk.notice2action.extraction.api.dto.ActionExtractionResponse;

public interface ActionExtractionService {
  ActionExtractionResponse extract(ActionExtractionRequest request);
}
