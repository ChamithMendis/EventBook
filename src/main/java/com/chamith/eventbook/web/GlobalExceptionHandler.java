package com.chamith.eventbook.web;

import com.chamith.eventbook.concurrency.SeatLockTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Object> handleNotFound(NoSuchElementException ex) {
        return body(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Object> handleConflict(IllegalStateException ex) {
        return body(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(SeatLockTimeoutException.class)
    public ResponseEntity<Object> handleLockTimeout(SeatLockTimeoutException ex) {
        return body(HttpStatus.LOCKED, ex.getMessage());
    }

    private ResponseEntity<Object> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message
        ));
    }

    // Catches JSON parsing errors (malformed JSON payload)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<String> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.error("400 Bad Request - JSON Parsing Error: {}", ex.getMessage());
        return ResponseEntity.badRequest().body("Malformed JSON payload.");
    }

    // Catches DTO validation failures (@Valid validation)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidationException(MethodArgumentNotValidException ex) {
        log.error("400 Bad Request - Validation Failed: {}", ex.getBindingResult().toString());
        return ResponseEntity.badRequest().body("Validation failed for parameters.");
    }

    // Catches missing mandatory query parameters (@RequestParam)
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<String> handleMissingParam(MissingServletRequestParameterException ex) {
        log.error("400 Bad Request - Missing Parameter: {}", ex.getParameterName());
        return ResponseEntity.badRequest().body("Required parameter is missing.");
    }
}
