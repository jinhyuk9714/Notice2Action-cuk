package com.cuk.notice2action.extraction.api.dto;

import jakarta.validation.constraints.NotBlank;

public record EmailExtractionRequest(
    @NotBlank String emailBody,
    String subject,
    String senderAddress
) {}
