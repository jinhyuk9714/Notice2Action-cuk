package com.cuk.notice2action.extraction.service.extractor;

import com.cuk.notice2action.extraction.api.dto.EvidenceSnippetDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RequiredItemExtractor {

  private static final List<String> REQUIRED_ITEM_KEYWORDS = List.of(
      "공결허가원", "신청서", "성적증명서", "증빙서류", "재학증명서", "학생증", "통장사본", "사유서",
      "여권사본", "등록금납입증명서", "졸업증명서", "추천서", "자기소개서",
      "이력서", "포트폴리오", "사진", "지원서", "동의서", "서약서",
      "계획서", "보고서", "반명함판", "가족관계증명서", "허가원", "확인서"
  );

  public List<String> extract(String text, List<EvidenceSnippetDto> evidence) {
    List<MatchedRequiredItem> matchedItems = new ArrayList<>();
    for (String keyword : REQUIRED_ITEM_KEYWORDS) {
      int index = text.indexOf(keyword);
      if (index >= 0) {
        matchedItems.add(new MatchedRequiredItem(index, keyword));
      }
    }
    List<String> normalizedItems = matchedItems.stream()
        .sorted(Comparator.comparingInt(MatchedRequiredItem::index))
        .map(MatchedRequiredItem::value)
        .distinct()
        .collect(ArrayList::new, (items, candidate) -> {
          boolean coveredByLongerItem = items.stream().anyMatch(existing -> existing.contains(candidate));
          if (!coveredByLongerItem) {
            items.add(candidate);
          }
        }, ArrayList::addAll);
    normalizedItems.forEach(item -> evidence.add(new EvidenceSnippetDto("requiredItems", item, 0.72)));
    return normalizedItems;
  }

  private record MatchedRequiredItem(int index, String value) {}
}
