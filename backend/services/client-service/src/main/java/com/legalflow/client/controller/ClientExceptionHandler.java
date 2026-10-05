package com.legalflow.client.controller;

import com.legalflow.client.exception.ClientConflictException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ClientExceptionHandler {

    @ExceptionHandler(ClientConflictException.class)
    public ResponseEntity<Map<String, Object>> handleClientConflict(
            ClientConflictException exception, HttpServletRequest request) {
        HttpStatusCode status = exception.getStatusCode();
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "path", request.getRequestURI(),
                "error", HttpStatus.valueOf(status.value()).getReasonPhrase(),
                "code", exception.getErrorCode(),
                "message", exception.getReason()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(
            ResponseStatusException exception, HttpServletRequest request) {
        HttpStatusCode status = exception.getStatusCode();
        String message = exception.getReason() == null ? status.toString() : exception.getReason();
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(), "status", status.value(),
                "error", HttpStatus.valueOf(status.value()).getReasonPhrase(),
                "message", message, "path", request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
                return badRequest("Request validation failed.", request);
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<Map<String, Object>> handleUnreadableBody(
                        HttpMessageNotReadableException exception, HttpServletRequest request) {
                return badRequest("Request body is invalid.", request);
        }

        private ResponseEntity<Map<String, Object>> badRequest(String message, HttpServletRequest request) {
                return ResponseEntity.badRequest().body(Map.of(
                                "timestamp", Instant.now().toString(), "status", 400, "error", "Bad Request",
                                "message", message, "path", request.getRequestURI()));
    }
}
