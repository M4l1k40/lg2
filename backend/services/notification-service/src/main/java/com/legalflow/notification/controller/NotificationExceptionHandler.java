package com.legalflow.notification.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class NotificationExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(
            ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String message = exception.getReason() == null ? status.toString() : exception.getReason();
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(), "status", status.value(),
                "error", status.getReasonPhrase(), "message", message, "path", request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<Map<String, Object>> handleValidationException(
                        MethodArgumentNotValidException exception, HttpServletRequest request) {
                return badRequest("Request validation failed.", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<Map<String, Object>> handleUnreadableMessage(
                        HttpMessageNotReadableException exception, HttpServletRequest request) {
                return badRequest("Request body is invalid.", request);
        }

        private ResponseEntity<Map<String, Object>> badRequest(String message, HttpServletRequest request) {
                return ResponseEntity.badRequest().body(Map.of(
                                "timestamp", Instant.now().toString(), "status", 400, "error", HttpStatus.BAD_REQUEST.getReasonPhrase(),
                                "message", message, "path", request.getRequestURI()));
    }
}
