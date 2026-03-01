package com.cuk.notice2action.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

class ScreenshotTextExtractorTest {

  private final ScreenshotTextExtractor extractor =
      new ScreenshotTextExtractor("/opt/homebrew/share/tessdata", "eng");

  @Test
  void rejects_file_exceeding_5mb() {
    long overSize = 6 * 1024 * 1024;
    InputStream empty = new ByteArrayInputStream(new byte[0]);

    assertThatThrownBy(() -> extractor.extractText(empty, overSize, "test.png"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("5MB");
  }

  @Test
  void rejects_non_image_extension() {
    InputStream empty = new ByteArrayInputStream(new byte[0]);

    assertThatThrownBy(() -> extractor.extractText(empty, 100, "test.docx"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("이미지 파일만");
  }

  @Test
  void rejects_null_filename() {
    InputStream empty = new ByteArrayInputStream(new byte[0]);

    assertThatThrownBy(() -> extractor.extractText(empty, 100, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("이미지 파일만");
  }

  @Test
  void accepts_all_supported_extensions() {
    for (String ext : new String[]{".png", ".jpg", ".jpeg", ".webp"}) {
      InputStream empty = new ByteArrayInputStream(new byte[0]);
      assertThatThrownBy(() -> extractor.extractText(empty, 100, "test" + ext))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("읽을 수 없습니다");
    }
  }

  @Test
  @EnabledIf("isTesseractAvailable")
  void extracts_text_from_valid_screenshot() throws Exception {
    try (InputStream imgStream =
             getClass().getClassLoader().getResourceAsStream("sample-screenshot.png")) {
      assertThat(imgStream).isNotNull();
      byte[] bytes = imgStream.readAllBytes();
      InputStream inputStream = new ByteArrayInputStream(bytes);
      String text = extractor.extractText(inputStream, bytes.length, "sample-screenshot.png");
      assertThat(text).isNotBlank();
    }
  }

  static boolean isTesseractAvailable() {
    try {
      Process process = new ProcessBuilder("tesseract", "--version")
          .redirectErrorStream(true).start();
      return process.waitFor() == 0;
    } catch (Exception e) {
      return false;
    }
  }
}
