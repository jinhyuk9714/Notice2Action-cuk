package com.cuk.notice2action.extraction.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PdfTextExtractor {

  private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
  private static final int MAX_OCR_PAGES = 10;
  private static final float OCR_DPI = 300f;

  private final String tessdataPath;
  private final String languages;

  public PdfTextExtractor(
      @Value("${app.ocr.tessdata-path:/opt/homebrew/share/tessdata}") String tessdataPath,
      @Value("${app.ocr.languages:kor+eng}") String languages) {
    this.tessdataPath = tessdataPath;
    this.languages = languages;
  }

  public String extractText(InputStream inputStream, long fileSize, String fileName) {
    validateFile(fileSize, fileName);

    try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
      PDFTextStripper stripper = new PDFTextStripper();
      String text = stripper.getText(document);

      if (text != null && !text.isBlank()) {
        return text;
      }

      return extractTextWithOcr(document);
    } catch (IOException e) {
      throw new IllegalArgumentException("PDF 파일을 읽을 수 없습니다: " + e.getMessage());
    }
  }

  private String extractTextWithOcr(PDDocument document) {
    int pageCount = Math.min(document.getNumberOfPages(), MAX_OCR_PAGES);
    PDFRenderer renderer = new PDFRenderer(document);
    Tesseract tesseract = new Tesseract();
    tesseract.setDatapath(tessdataPath);
    tesseract.setLanguage(languages);

    StringBuilder result = new StringBuilder();

    for (int i = 0; i < pageCount; i++) {
      try {
        BufferedImage image = renderer.renderImageWithDPI(i, OCR_DPI);
        String pageText = tesseract.doOCR(image);
        if (pageText != null && !pageText.isBlank()) {
          result.append(pageText).append("\n");
        }
      } catch (IOException | TesseractException e) {
        // skip failed pages
      }
    }

    if (result.isEmpty()) {
      throw new IllegalArgumentException(
          "PDF에서 텍스트를 추출할 수 없습니다. 이미지 품질이 낮거나 텍스트가 포함되어 있지 않습니다.");
    }

    return result.toString();
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
