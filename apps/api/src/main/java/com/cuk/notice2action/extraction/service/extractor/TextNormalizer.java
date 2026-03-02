package com.cuk.notice2action.extraction.service.extractor;

import org.springframework.stereotype.Component;

@Component
public class TextNormalizer {

  public String normalize(String text) {
    String result = text;

    // 1. Remove zero-width characters (ZWJ, ZWNJ, ZWSP, BOM, etc.)
    result = result.replaceAll("[\\u200B-\\u200F\\u2028-\\u202F\\uFEFF]", "");

    // 2. Convert full-width digits/letters/punctuation to ASCII
    result = convertFullWidthToAscii(result);

    // 3. Normalize bullet-like characters at line start to "- "
    result = result.replaceAll("(?m)^\\s*[ㅇ○●•★※▶▷◆◇◈]\\s*", "- ");

    // 4. Normalize whitespace characters
    result = result.replace('\u00A0', ' ');
    result = result.replace('\t', ' ');

    // 5. Table-row reconstruction: 3+ consecutive spaces → " | "
    result = result.replaceAll(" {3,}", " | ");

    // 6. Collapse remaining multiple spaces to single space
    result = result.replaceAll(" {2,}", " ");

    // 7. Collapse excessive newlines (3+) to double newline
    result = result.replaceAll("\\n{3,}", "\n\n");

    // 8. Trim each line
    result = result.lines()
        .map(String::trim)
        .reduce((a, b) -> a + "\n" + b)
        .orElse("");

    return result.trim();
  }

  private static String convertFullWidthToAscii(String text) {
    StringBuilder sb = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c >= '\uFF01' && c <= '\uFF5E') {
        sb.append((char) (c - 0xFF01 + 0x0021));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}
