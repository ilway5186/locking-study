package com.ilway.reservation.common.exception;

import com.ilway.reservation.common.api.ErrorResponse;
import com.ilway.reservation.reservation.domain.ReservationFailureReason;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ReservationException.class)
  public ResponseEntity<ErrorResponse> handleReservationException(ReservationException exception) {
    ReservationFailureReason reason = exception.getReason();
    return ResponseEntity.status(reason.getHttpStatus())
        .body(new ErrorResponse(reason.name(), reason.getMessage(), Instant.now()));
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
  public ResponseEntity<ErrorResponse> handleValidationException(Exception exception) {
    ReservationFailureReason reason = ReservationFailureReason.INVALID_REQUEST;
    return ResponseEntity.status(reason.getHttpStatus())
        .body(new ErrorResponse(reason.name(), reason.getMessage(), Instant.now()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
    ReservationFailureReason reason = ReservationFailureReason.INTERNAL_ERROR;
    return ResponseEntity.status(reason.getHttpStatus())
        .body(new ErrorResponse(reason.name(), reason.getMessage(), Instant.now()));
  }
}
