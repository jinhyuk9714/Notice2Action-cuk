package com.cuk.notice2action.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class PdfTextExtractorTest {

  private final PdfTextExtractor extractor = new PdfTextExtractor(
      "/opt/homebrew/share/tessdata", "kor+eng");

  @Test
  void rejects_file_exceeding_10mb() {
    long overSize = 11 * 1024 * 1024;
    InputStream empty = new ByteArrayInputStream(new byte[0]);

    assertThatThrownBy(() -> extractor.extractText(empty, overSize, "test.pdf"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("10MB");
  }

  @Test
  void rejects_non_pdf_extension() {
    InputStream empty = new ByteArrayInputStream(new byte[0]);

    assertThatThrownBy(() -> extractor.extractText(empty, 100, "test.docx"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("PDF 파일만");
  }

  @Test
  void rejects_null_filename() {
    InputStream empty = new ByteArrayInputStream(new byte[0]);

    assertThatThrownBy(() -> extractor.extractText(empty, 100, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("PDF 파일만");
  }

  @Test
  void extracts_text_from_valid_pdf() throws Exception {
    try (InputStream pdfStream =
             getClass().getClassLoader().getResourceAsStream("sample.pdf")) {
      assertThat(pdfStream).isNotNull();
      byte[] bytes = pdfStream.readAllBytes();
      InputStream inputStream = new ByteArrayInputStream(bytes);
      String text = extractor.extractText(inputStream, bytes.length, "sample.pdf");
      assertThat(text).isNotBlank();
      assertThat(text).contains("TRINITY");
    }
  }
}
