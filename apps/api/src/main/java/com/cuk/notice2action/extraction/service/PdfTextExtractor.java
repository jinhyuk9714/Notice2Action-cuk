package com.cuk.notice2action.extraction.service;

import java.io.IOException;
import java.io.InputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

@Service
public class PdfTextExtractor {

  private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

  public String extractText(InputStream inputStream, long fileSize, String fileName) {
    validateFile(fileSize, fileName);

    try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
      PDFTextStripper stripper = new PDFTextStripper();
      String text = stripper.getText(document);

      if (text == null || text.isBlank()) {
        throw new IllegalArgumentException(
            "PDF에서 텍스트를 추출할 수 없습니다. 이미지 기반 PDF는 아직 지원되지 않습니다.");
      }

      return text;
    } catch (IOException e) {
      throw new IllegalArgumentException("PDF 파일을 읽을 수 없습니다: " + e.getMessage());
    }
  }

  private void validateFile(long fileSize, String fileName) {
    if (fileSize > MAX_FILE_SIZE) {
      throw new IllegalArgumentException(
          "PDF 파일 크기가 10MB를 초과합니다. (" + (fileSize / 1024 / 1024) + "MB)");
    }
    if (fileName == null || !fileName.toLowerCase().endsWith(".pdf")) {
      throw new IllegalArgumentException("PDF 파일만 업로드할 수 있습니다.");
    }
  }
}
