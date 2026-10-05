package com.legalflow.consultation.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

public final class TenantContext {

    private TenantContext() {
    }

    public static JwtAuthenticationToken requireJwtAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing authenticated JWT.");
        }
        return jwtAuthentication;
    }

    public static UUID requireLawFirmId() {
        Object claim = requireJwtAuthentication().getToken().getClaim("lawFirmId");
        if (claim == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "JWT claim 'lawFirmId' is missing.");
        }
        try {
            return UUID.fromString(claim.toString());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "JWT claim 'lawFirmId' is malformed.");
        }
    }

    public static String currentUserSubjectOrNull() {
        String subject = requireJwtAuthentication().getToken().getSubject();
        return (subject == null || subject.isBlank()) ? null : subject;
    }
}
