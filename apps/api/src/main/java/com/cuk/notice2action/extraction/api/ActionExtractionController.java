package com.cuk.notice2action.extraction.api;

import com.cuk.notice2action.extraction.api.dto.ActionExtractionRequest;
import com.cuk.notice2action.extraction.api.dto.ActionExtractionResponse;
import com.cuk.notice2action.extraction.api.dto.ActionListResponse;
import com.cuk.notice2action.extraction.api.dto.SavedActionDetailDto;
import com.cuk.notice2action.extraction.service.ActionExtractionService;
import com.cuk.notice2action.extraction.service.ActionPersistenceService;
import com.cuk.notice2action.extraction.service.PdfTextExtractor;
import com.cuk.notice2action.extraction.service.UrlContentFetcher;
import com.cuk.notice2action.extraction.domain.SourceCategory;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class ActionExtractionController {

  private final ActionExtractionService actionExtractionService;
  private final ActionPersistenceService actionPersistenceService;
  private final UrlContentFetcher urlContentFetcher;
  private final PdfTextExtractor pdfTextExtractor;

  public ActionExtractionController(ActionExtractionService actionExtractionService,
      ActionPersistenceService actionPersistenceService,
      UrlContentFetcher urlContentFetcher,
      PdfTextExtractor pdfTextExtractor) {
    this.actionExtractionService = actionExtractionService;
    this.actionPersistenceService = actionPersistenceService;
    this.urlContentFetcher = urlContentFetcher;
    this.pdfTextExtractor = pdfTextExtractor;
  }

  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "ok");
  }

  @PostMapping("/extractions/actions")
  public ActionExtractionResponse extractActions(
      @RequestBody ActionExtractionRequest request
  ) {
    ActionExtractionRequest resolved = resolveRequest(request);
    ActionExtractionResponse extractionResult = actionExtractionService.extract(resolved);
    return actionPersistenceService.persistExtraction(resolved, extractionResult);
  }

  @PostMapping("/extractions/pdf")
  public ActionExtractionResponse extractActionsFromPdf(
      @RequestParam("file") MultipartFile file,
      @RequestParam(name = "sourceTitle", required = false) String sourceTitle
  ) throws IOException {
    String extractedText = pdfTextExtractor.extractText(
        file.getInputStream(),
        file.getSize(),
        file.getOriginalFilename()
    );

    String resolvedTitle = (sourceTitle != null && !sourceTitle.isBlank())
        ? sourceTitle
        : stripPdfExtension(file.getOriginalFilename());

    ActionExtractionRequest request = new ActionExtractionRequest(
        extractedText,
        null,
        resolvedTitle,
        SourceCategory.PDF,
        List.of()
    );

    ActionExtractionResponse extractionResult = actionExtractionService.extract(request);
    return actionPersistenceService.persistExtraction(request, extractionResult);
  }

  @GetMapping("/actions")
  public ActionListResponse listActions(
      @RequestParam(name = "sort", required = false, defaultValue = "recent") String sort
  ) {
    return actionPersistenceService.listActions(sort);
  }

  @GetMapping("/actions/{id}")
  public SavedActionDetailDto getActionDetail(@PathVariable UUID id) {
    return actionPersistenceService.getActionDetail(id);
  }

  private static String stripPdfExtension(String filename) {
    if (filename == null) return null;
    return filename.toLowerCase().endsWith(".pdf")
        ? filename.substring(0, filename.length() - 4)
        : filename;
  }

  private ActionExtractionRequest resolveRequest(ActionExtractionRequest request) {
    boolean hasText = request.sourceText() != null && !request.sourceText().isBlank();
    boolean hasUrl = request.sourceUrl() != null && !request.sourceUrl().isBlank();

    if (!hasText && !hasUrl) {
      throw new IllegalArgumentException("sourceText 또는 sourceUrl 중 하나는 필수입니다.");
    }

    if (!hasUrl) {
      return request;
    }

    UrlContentFetcher.FetchedContent fetched = urlContentFetcher.fetch(request.sourceUrl());

    String resolvedText = hasText ? request.sourceText() : fetched.text();
    String resolvedTitle = (request.sourceTitle() != null && !request.sourceTitle().isBlank())
        ? request.sourceTitle()
        : fetched.title();

    return new ActionExtractionRequest(
        resolvedText,
        request.sourceUrl(),
        resolvedTitle,
        request.sourceCategory(),
        request.focusProfile()
    );
  }
}
