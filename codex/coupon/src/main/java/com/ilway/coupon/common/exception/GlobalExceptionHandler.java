package com.ilway.coupon.common.exception;

import com.ilway.coupon.common.api.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
    ErrorCode errorCode = exception.getErrorCode();
    return ResponseEntity.status(errorCode.getStatus())
        .body(ApiResponse.failure(errorCode.getCode(), exception.getMessage()));
  }

  @ExceptionHandler({
      MethodArgumentNotValidException.class,
      ConstraintViolationException.class,
      IllegalArgumentException.class,
      HttpMessageNotReadableException.class
  })
  public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception exception) {
    String message = extractMessage(exception);
    return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getStatus())
        .body(ApiResponse.failure(ErrorCode.INVALID_REQUEST.getCode(), message));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
    return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getStatus())
        .body(ApiResponse.failure(ErrorCode.INVALID_REQUEST.getCode(), "데이터 제약조건을 위반했습니다."));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
    return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
        .body(ApiResponse.failure(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage()));
  }

  private String extractMessage(Exception exception) {
    if (exception instanceof MethodArgumentNotValidException validationException) {
      FieldError fieldError = validationException.getBindingResult().getFieldError();
      if (fieldError != null) {
        return fieldError.getDefaultMessage();
      }
    }
    return ErrorCode.INVALID_REQUEST.getMessage();
  }
}
