package com.cuk.notice2action.extraction.api;

import com.cuk.notice2action.extraction.api.dto.NoticeFeedResponse;
import com.cuk.notice2action.extraction.api.dto.PersonalizedNoticeDetailDto;
import com.cuk.notice2action.extraction.service.notice.NoticeFeedService;
import com.cuk.notice2action.extraction.service.notice.NoticeProfile;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notices")
public class NoticeFeedController {

  private final NoticeFeedService noticeFeedService;

  public NoticeFeedController(NoticeFeedService noticeFeedService) {
    this.noticeFeedService = noticeFeedService;
  }

  @GetMapping("/feed")
  public NoticeFeedResponse getFeed(
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size,
      @RequestParam(name = "department", required = false) String department,
      @RequestParam(name = "year", required = false) Integer year,
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "keyword", required = false) List<String> keywords
  ) {
    return noticeFeedService.getFeed(new NoticeProfile(department, year, status, normalizeKeywords(keywords)), page, size);
  }

  @GetMapping("/{id}")
  public PersonalizedNoticeDetailDto getDetail(
      @PathVariable UUID id,
      @RequestParam(name = "department", required = false) String department,
      @RequestParam(name = "year", required = false) Integer year,
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "keyword", required = false) List<String> keywords
  ) {
    return noticeFeedService.getDetail(id, new NoticeProfile(department, year, status, normalizeKeywords(keywords)));
  }

  private List<String> normalizeKeywords(List<String> keywords) {
    if (keywords == null) {
      return List.of();
    }
    return keywords.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).toList();
  }
}
