package com.legalflow.case_service.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

public final class TenantContext {

    private TenantContext() {
    }

    public static UUID requireLawFirmId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing authenticated JWT.");
        }

        Jwt jwt = jwtAuthenticationToken.getToken();
        Object claim = jwt.getClaim("lawFirmId");
        if (claim == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "JWT claim 'lawFirmId' is missing.");
        }

        try {
            return UUID.fromString(claim.toString());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "JWT claim 'lawFirmId' is malformed.");
        }
    }
}
