package com.legalflow.notification.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import com.legalflow.notification.domain.NotificationRecipientType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TenantContext {

    private TenantContext() {
    }

    public static UUID requireLawFirmId() {
        JwtAuthenticationToken authentication = requireJwtAuthentication();
        Object claim = authentication.getToken().getClaim("lawFirmId");
        if (claim == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "JWT claim 'lawFirmId' is missing.");
        }
        try {
            return UUID.fromString(claim.toString());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "JWT claim 'lawFirmId' is malformed.");
        }
    }

    public static UUID requireRecipientId() {
        String subject = requireJwt().getSubject();
        if (subject == null || subject.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "JWT subject 'sub' is missing.");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "JWT subject 'sub' is malformed.");
        }
    }

    public static Jwt requireJwt() {
        return requireJwtAuthentication().getToken();
    }

    public static NotificationRecipientType recipientTypeForCurrentRecipient() {
        Object realmAccessClaim = requireJwt().getClaim("realm_access");
        if (!(realmAccessClaim instanceof java.util.Map<?, ?> realmAccess)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "JWT realm roles are missing.");
        }
        Object rolesClaim = realmAccess.get("roles");
        if (!(rolesClaim instanceof List<?> roles)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "JWT realm roles are missing.");
        }

        List<NotificationRecipientType> recipientTypes = new ArrayList<>();
        for (Object role : roles) {
            if ("CLIENT".equals(role)) {
                recipientTypes.add(NotificationRecipientType.CLIENT);
            } else if ("LAWYER".equals(role)) {
                recipientTypes.add(NotificationRecipientType.LAWYER);
            } else if ("SECRETARY".equals(role)) {
                recipientTypes.add(NotificationRecipientType.SECRETARY);
            }
        }
        if (recipientTypes.isEmpty() && roles.contains("ADMIN")) {
            return null;
        }
        if (recipientTypes.size() != 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "JWT must identify exactly one notification recipient role.");
        }
        return recipientTypes.get(0);
    }

    private static JwtAuthenticationToken requireJwtAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing authenticated JWT.");
        }
        return jwtAuthentication;
    }
}
