package com.cuk.notice2action.common.api;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
    List<String> details =
        exception.getBindingResult().getFieldErrors().stream()
            .map(this::formatFieldError)
            .toList();

    ApiErrorResponse response =
        new ApiErrorResponse(
            "validation_error",
            "요청 값이 올바르지 않습니다.",
            details,
            OffsetDateTime.now()
        );

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
    ApiErrorResponse response =
        new ApiErrorResponse(
            "bad_request",
            exception.getMessage(),
            List.of(),
            OffsetDateTime.now()
        );

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  @ExceptionHandler(NoSuchElementException.class)
  public ResponseEntity<ApiErrorResponse> handleNotFound(NoSuchElementException exception) {
    ApiErrorResponse response =
        new ApiErrorResponse(
            "not_found",
            exception.getMessage(),
            List.of(),
            OffsetDateTime.now()
        );

    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
      MethodArgumentTypeMismatchException exception) {
    ApiErrorResponse response =
        new ApiErrorResponse(
            "bad_request",
            "잘못된 파라미터 값입니다: " + exception.getName(),
            List.of(),
            OffsetDateTime.now()
        );

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(
      MaxUploadSizeExceededException exception) {
    ApiErrorResponse response =
        new ApiErrorResponse(
            "file_too_large",
            "파일 크기가 허용 한도를 초과합니다. 최대 10MB까지 업로드 가능합니다.",
            List.of(),
            OffsetDateTime.now()
        );

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  @ExceptionHandler(IOException.class)
  public ResponseEntity<ApiErrorResponse> handleIOException(IOException exception) {
    log.error("파일 처리 중 오류 발생", exception);
    ApiErrorResponse response =
        new ApiErrorResponse(
            "io_error",
            "파일 처리 중 오류가 발생했습니다.",
            List.of(),
            OffsetDateTime.now()
        );

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleGeneral(Exception exception) {
    log.error("처리되지 않은 서버 오류 발생", exception);
    ApiErrorResponse response =
        new ApiErrorResponse(
            "internal_error",
            "서버 오류가 발생했습니다.",
            List.of(),
            OffsetDateTime.now()
        );

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
  }

  private String formatFieldError(FieldError fieldError) {
    return fieldError.getField() + ": " + fieldError.getDefaultMessage();
  }
}
