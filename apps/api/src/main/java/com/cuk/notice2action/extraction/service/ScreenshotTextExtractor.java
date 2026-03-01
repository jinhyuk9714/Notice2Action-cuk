package com.cuk.notice2action.extraction.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import javax.imageio.ImageIO;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ScreenshotTextExtractor {

  private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
  private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".webp");

  private final String tessdataPath;
  private final String languages;

  public ScreenshotTextExtractor(
      @Value("${app.ocr.tessdata-path:/opt/homebrew/share/tessdata}") String tessdataPath,
      @Value("${app.ocr.languages:kor+eng}") String languages) {
    this.tessdataPath = tessdataPath;
    this.languages = languages;
  }

  public String extractText(InputStream inputStream, long fileSize, String fileName) {
    validateFile(fileSize, fileName);

    try {
      BufferedImage image = ImageIO.read(inputStream);
      if (image == null) {
        throw new IllegalArgumentException(
            "이미지 파일을 읽을 수 없습니다. 손상된 파일일 수 있습니다.");
      }

      Tesseract tesseract = new Tesseract();
      tesseract.setDatapath(tessdataPath);
      tesseract.setLanguage(languages);

      String text = tesseract.doOCR(image);

      if (text == null || text.isBlank()) {
        throw new IllegalArgumentException(
            "스크린샷에서 텍스트를 추출할 수 없습니다. 이미지에 텍스트가 포함되어 있는지 확인해 주세요.");
      }

      return text;
    } catch (IOException e) {
      throw new IllegalArgumentException("이미지 파일을 읽을 수 없습니다: " + e.getMessage());
    } catch (TesseractException e) {
      throw new IllegalArgumentException("OCR 처리 중 오류가 발생했습니다: " + e.getMessage());
    }
  }

  private void validateFile(long fileSize, String fileName) {
    if (fileSize > MAX_FILE_SIZE) {
      throw new IllegalArgumentException(
          "이미지 파일 크기가 5MB를 초과합니다. (" + (fileSize / 1024 / 1024) + "MB)");
    }
    if (fileName == null) {
      throw new IllegalArgumentException(
          "이미지 파일만 업로드할 수 있습니다. (PNG, JPG, JPEG, WEBP)");
    }
    String lowerName = fileName.toLowerCase();
    boolean valid = ALLOWED_EXTENSIONS.stream().anyMatch(lowerName::endsWith);
    if (!valid) {
      throw new IllegalArgumentException(
          "이미지 파일만 업로드할 수 있습니다. (PNG, JPG, JPEG, WEBP)");
    }
  }
}
