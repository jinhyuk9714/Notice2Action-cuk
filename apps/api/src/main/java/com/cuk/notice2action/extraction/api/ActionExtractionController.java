package com.cuk.notice2action.extraction.api;

import com.cuk.notice2action.extraction.api.dto.ActionExtractionRequest;
import com.cuk.notice2action.extraction.api.dto.ActionExtractionResponse;
import com.cuk.notice2action.extraction.api.dto.ActionListResponse;
import com.cuk.notice2action.extraction.api.dto.SavedActionDetailDto;
import com.cuk.notice2action.extraction.service.ActionExtractionService;
import com.cuk.notice2action.extraction.service.ActionPersistenceService;
import com.cuk.notice2action.extraction.service.ICalendarService;
import com.cuk.notice2action.extraction.service.PdfTextExtractor;
import com.cuk.notice2action.extraction.service.ScreenshotTextExtractor;
import com.cuk.notice2action.extraction.service.UrlContentFetcher;
import com.cuk.notice2action.extraction.persistence.entity.ExtractedActionEntity;
import com.cuk.notice2action.extraction.persistence.repository.ExtractedActionRepository;
import com.cuk.notice2action.extraction.domain.SourceCategory;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.HttpHeaders;
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
  private final ScreenshotTextExtractor screenshotTextExtractor;
  private final ICalendarService iCalendarService;
  private final ExtractedActionRepository actionRepository;

  public ActionExtractionController(ActionExtractionService actionExtractionService,
      ActionPersistenceService actionPersistenceService,
      UrlContentFetcher urlContentFetcher,
      PdfTextExtractor pdfTextExtractor,
      ScreenshotTextExtractor screenshotTextExtractor,
      ICalendarService iCalendarService,
      ExtractedActionRepository actionRepository) {
    this.actionExtractionService = actionExtractionService;
    this.actionPersistenceService = actionPersistenceService;
    this.urlContentFetcher = urlContentFetcher;
    this.pdfTextExtractor = pdfTextExtractor;
    this.screenshotTextExtractor = screenshotTextExtractor;
    this.iCalendarService = iCalendarService;
    this.actionRepository = actionRepository;
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

  @PostMapping("/extractions/screenshot")
  public ActionExtractionResponse extractActionsFromScreenshot(
      @RequestParam("file") MultipartFile file,
      @RequestParam(name = "sourceTitle", required = false) String sourceTitle
  ) throws IOException {
    String extractedText = screenshotTextExtractor.extractText(
        file.getInputStream(),
        file.getSize(),
        file.getOriginalFilename()
    );

    String resolvedTitle = (sourceTitle != null && !sourceTitle.isBlank())
        ? sourceTitle
        : stripImageExtension(file.getOriginalFilename());

    ActionExtractionRequest request = new ActionExtractionRequest(
        extractedText,
        null,
        resolvedTitle,
        SourceCategory.SCREENSHOT,
        List.of()
    );

    ActionExtractionResponse extractionResult = actionExtractionService.extract(request);
    return actionPersistenceService.persistExtraction(request, extractionResult);
  }

  @GetMapping("/actions")
  public ActionListResponse listActions(
      @RequestParam(name = "sort", required = false, defaultValue = "recent") String sort,
      @RequestParam(name = "page", required = false, defaultValue = "0") int page,
      @RequestParam(name = "size", required = false, defaultValue = "20") int size
  ) {
    return actionPersistenceService.listActions(sort, page, size);
  }

  @DeleteMapping("/actions/{id}")
  public ResponseEntity<Void> deleteAction(@PathVariable UUID id) {
    actionPersistenceService.deleteAction(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/actions/{id}")
  public SavedActionDetailDto getActionDetail(@PathVariable UUID id) {
    return actionPersistenceService.getActionDetail(id);
  }

  @GetMapping(value = "/actions/calendar.ics", produces = "text/calendar")
  public ResponseEntity<String> exportCalendar() {
    List<ExtractedActionEntity> actions =
        actionRepository.findAllByDueAtIsoIsNotNullOrderByDueAtIsoAsc();
    String ics = iCalendarService.generateCalendar(actions);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"notice2action.ics\"")
        .body(ics);
  }

  @GetMapping(value = "/actions/{id}/calendar.ics", produces = "text/calendar")
  public ResponseEntity<String> exportSingleActionCalendar(@PathVariable UUID id) {
    ExtractedActionEntity action = actionRepository.findById(id)
        .orElseThrow(() -> new NoSuchElementException("Action not found: " + id));
    String ics = iCalendarService.generateSingleEvent(action);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"action-" + id + ".ics\"")
        .body(ics);
  }

  private static String stripImageExtension(String filename) {
    if (filename == null) return null;
    String lower = filename.toLowerCase();
    for (String ext : List.of(".png", ".jpg", ".jpeg", ".webp")) {
      if (lower.endsWith(ext)) {
        return filename.substring(0, filename.length() - ext.length());
      }
    }
    return filename;
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
