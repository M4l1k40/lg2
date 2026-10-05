package com.legalflow.client.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ClientConflictException extends ResponseStatusException {

    private final String errorCode;

    public ClientConflictException(String errorCode, String message, Throwable cause) {
        super(HttpStatus.CONFLICT, message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
