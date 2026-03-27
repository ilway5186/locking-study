package com.ilway.couponsystem.common.exception;

import com.ilway.couponsystem.common.api.ApiResponse;
import jakarta.validation.ConstraintDeclarationException;
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
  public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
    ErrorCode errorCode = e.errorCode();
    return ResponseEntity
      .status(errorCode.status())
      .body(ApiResponse.failure(errorCode.code(), errorCode.message()));
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    IllegalArgumentException.class,
    ConstraintDeclarationException.class,
    HttpMessageNotReadableException.class
  })
  public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
    ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
    String message = extractMessage(e);
    return ResponseEntity.status(errorCode.status())
      .body(ApiResponse.failure(errorCode.code(), message));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<Void>>  handleDataIntegrityViolationException(DataIntegrityViolationException e) {
    ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
    return ResponseEntity
      .status(errorCode.status())
      .body(ApiResponse.failure(errorCode.code(), "데이터 제약조건을 위배했습니다."));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception e) {
    ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
    return ResponseEntity
      .status(errorCode.status())
      .body(ApiResponse.failure(errorCode.code(), errorCode.message()));
  }

  private String extractMessage(Exception e) {
    if (e instanceof MethodArgumentNotValidException validationException) {
      FieldError fieldError = validationException.getBindingResult().getFieldError();
      if (fieldError != null) {
        return fieldError.getDefaultMessage();
      }
    }
    return ErrorCode.INTERNAL_ERROR.message();
  }

}
